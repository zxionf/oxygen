package top.zxionf.shio.vault

import java.io.File
import org.json.JSONObject
import top.zxionf.shio.kernel.ShioDatabase
import top.zxionf.shio.kernel.ShioException

/**
 * 一次成功解锁后的会话信息。
 *
 * 首次建库走 `sync_enable`（内核要求 Android 首版库为 `format=2`，见 SPEC 的
 * 「内核 ABI 对齐」），所以新建的库 [formatVersion] 为 2；[dbId] 与 [deviceId]
 * 由内核生成，是后续多端同步的身份，当前只做展示与诊断。
 */
data class VaultSession(
    val databasePath: String,
    val dbId: String,
    val deviceId: String,
    val formatVersion: Int,
)

/**
 * 密码库仓库（M11）：打开/创建/关闭内核库，并提供 `vault/entries` 的读写门面。
 *
 * 线程：所有 native 调用经 [ShioDatabase] 串行在单线程 IO 上，调用方在协程里用即可。
 * 生命周期：`create` / `unlock` 成功后才能读写；`lock` 销毁句柄并清空会话。
 *
 * 首次建库流程（内核 `.design/api.md` 与 DECISIONS[40] 的要求）：
 * `open(create=true)` → `sync_enable` → 校验 `sync_info` 与 `format_version == 2`；
 * 任何一步失败都关闭并删除这个尚未使用的库（连同 `<db>.lock`），不留半成品。
 */
class VaultRepository(
    private val database: ShioDatabase = ShioDatabase(),
) {

    var session: VaultSession? = null
        private set

    val isUnlocked: Boolean
        get() = session != null

    /**
     * 创建新库并启用同步。
     *
     * 已存在同名文件时内核会拒绝（`create=true` 不覆盖），错误码为 `SHIO_INVALID_ARGUMENT`。
     */
    suspend fun create(databasePath: String, password: String): VaultSession {
        open(databasePath, password, create = true)
        try {
            val syncInfo = JSONObject(database.enableSync())
            val formatVersion = formatVersion()
            val dbId = syncInfo.optString("db_id")
            check(dbId.isNotEmpty()) { "sync_info 缺少 db_id" }
            check(formatVersion == FORMAT_VERSION_SYNCED) {
                "首次建库后 format_version 应为 $FORMAT_VERSION_SYNCED，实际 $formatVersion"
            }
            return VaultSession(
                databasePath = databasePath,
                dbId = dbId,
                deviceId = syncInfo.optString("device_id"),
                formatVersion = formatVersion,
            ).also { session = it }
        } catch (failure: Throwable) {
            // 启用同步失败：关库并删掉刚建的文件，避免留下 format=1 的半成品。
            discard(databasePath)
            throw failure
        }
    }

    /** 解锁已有库（显式 `create=false`）；口令错误抛 [ShioException.INVALID_KEY]。 */
    suspend fun unlock(databasePath: String, password: String): VaultSession {
        open(databasePath, password, create = false)
        val syncInfo = try {
            JSONObject(database.syncInfo())
        } catch (_: ShioException) {
            // 未启用同步的 format=1 旧库也能解锁，只是没有同步身份。
            JSONObject()
        }
        return VaultSession(
            databasePath = databasePath,
            dbId = syncInfo.optString("db_id"),
            deviceId = syncInfo.optString("device_id"),
            formatVersion = formatVersion(),
        ).also { session = it }
    }

    /** 关闭并销毁句柄，会话信息一并清空（对应界面上的「锁定」）。 */
    suspend fun lock() {
        database.destroy()
        session = null
    }

    /**
     * 修改主口令：内核重包裹密钥，条目内容不变，旧口令立即失效。
     *
     * 不要求输入旧口令：会话已解锁即代表本次启动已通过内核校验（参见变更设计）。
     * 失败按 [ShioException] 上抛，由界面翻译成文案。
     */
    suspend fun changePassword(newPassword: String) {
        requireUnlocked()
        database.changePassword(newPassword)
    }

    /** 在线备份到 [outPath]（目标必须不存在），返回内核的摘要信息。 */
    suspend fun backup(outPath: String): BackupSummary {
        requireUnlocked()
        val json = JSONObject(database.backupTo(outPath))
        return BackupSummary(
            formatVersion = json.optInt("format_version"),
            dbBytes = json.optLong("db_bytes"),
            tableCount = json.optInt("table_count"),
            rowCount = json.optLong("row_count"),
        )
    }

    /**
     * 从备份恢复，并用恢复后的库**替换**当前库。
     *
     * 顺序（任何一步失败都不动现有 `vault.db`）：
     * 1. 先恢复到一个旁路文件 `vault-restored.db`（残留先删）；
     * 2. 内核校验口令与结构，成功返回指向旁路文件的新句柄；
     * 3. 销毁旧句柄、删除旧 `vault.db` 与 `.lock`，把旁路文件改名为 `vault.db`；
     * 4. 清空会话，由调用方回到解锁页——恢复后的口令是备份当时的那个。
     */
    suspend fun restoreInPlace(backupPath: String, password: String): VaultSession? {
        requireUnlocked()
        val databaseFile = File(session?.databasePath ?: error("密码库未解锁"))
        val restoredFile = File(databaseFile.parentFile, RESTORED_NAME)
        // 旁路文件的残留会让内核拒绝恢复（目标必须不存在）。
        deleteWithLock(restoredFile)

        // ① 恢复到旁路文件，拿新句柄只为校验；校验完立即释放，把锁与 fd 还回。
        //    失败时也要把旁路文件与它的 lock 清干净，不留「半个库」。
        try {
            val verified = database.restoreToVerifiedFile(
                backupPath = backupPath,
                targetPath = restoredFile.absolutePath,
                password = password,
            )
            database.destroyHandle(verified)
        } catch (failure: Throwable) {
            deleteWithLock(restoredFile)
            throw failure
        }

        // ② 到这里旁路文件口令与结构都已被内核验证，开始替换：
        //    先释放当前库的句柄（否则 flock 占着，重命名后状态难料），再换文件。
        database.destroy()
        session = null
        deleteWithLock(databaseFile)
        if (!restoredFile.renameTo(databaseFile)) {
            throw ShioException(ShioException.IO, "failed to move restored database into place")
        }
        // 重命名后原来旁路的 lock 文件名已经失效，一并清掉，避免下次开库时两个锁文件并存。
        File("${restoredFile.absolutePath}.lock").delete()
        return null
    }

    /** 删除数据库文件及其副本锁文件（`<db>.lock`）。 */
    private fun deleteWithLock(file: File) {
        file.delete()
        File("${file.absolutePath}.lock").delete()
    }

    /** 读取全部条目；按 `next` 分页直到读完。需要已解锁。 */
    suspend fun entries(): List<VaultEntry> {
        requireUnlocked()
        val result = mutableListOf<VaultEntry>()
        var after: String? = null
        do {
            val page = JSONObject(
                database.list(
                    namespace = NAMESPACE,
                    collection = COLLECTION,
                    after = after,
                    limit = PAGE_SIZE,
                ),
            )
            val items = page.optJSONArray("items")
            for (index in 0 until (items?.length() ?: 0)) {
                val meta = items?.optJSONObject(index) ?: continue
                if (meta.optBoolean("deleted", false)) continue
                val id = meta.optString("id").ifEmpty { continue }
                val value = JSONObject(database.get(NAMESPACE, COLLECTION, id)).opt("value")
                val entry = VaultEntryCodec.decode(id, value?.toString() ?: continue)
                if (entry != null) result += entry
            }
            after = page.optString("next").takeIf { it.isNotEmpty() && it != "null" }
        } while (after != null && result.size < MAX_ENTRIES)
        return result
    }

    /** 按 ID 读取单条；不存在（含墓碑）返回 null。 */
    suspend fun entry(entryId: String): VaultEntry? {
        requireUnlocked()
        val raw = try {
            database.get(NAMESPACE, COLLECTION, entryId)
        } catch (missing: ShioException) {
            if (missing.code == ShioException.NOT_FOUND) return null else throw missing
        }
        val value = JSONObject(raw).opt("value") ?: return null
        return VaultEntryCodec.decode(entryId, value.toString())
    }

    /** 新建条目，返回内核生成的记录 ID。 */
    suspend fun insert(entry: VaultEntry): String {
        requireUnlocked()
        return database.insert(NAMESPACE, COLLECTION, VaultEntryCodec.encode(entry))
    }

    /**
     * 批量新增（导入用）：逐条 `insert`，返回成功条数。
     *
     * 不查重、不覆盖：记录 ID 由内核生成，导入方也没有稳定标识可用于匹配。
     * 单条失败不影响后续条目，失败数由 [VaultTransfer.ParseResult.skipped] 与调用方合并统计。
     */
    suspend fun insertAll(entries: List<VaultEntry>): Int {
        requireUnlocked()
        var inserted = 0
        entries.forEach { entry ->
            runCatching { insert(entry.copy(id = "")) }
                .onSuccess { inserted += 1 }
        }
        return inserted
    }

    /** 覆盖已有条目。 */
    suspend fun update(entry: VaultEntry) {
        requireUnlocked()
        database.put(NAMESPACE, COLLECTION, entry.id, VaultEntryCodec.encode(entry))
    }

    /** 删除条目；返回删除前是否存在。 */
    suspend fun delete(entryId: String): Boolean {
        requireUnlocked()
        return database.delete(NAMESPACE, COLLECTION, entryId)
    }

    private suspend fun open(databasePath: String, password: String, create: Boolean) {
        // 同一实例重复解锁时先释放旧句柄：内核在 Android 上用 <db>.lock 独占写句柄。
        if (database.isOpen) database.destroy()
        session = null
        database.open(
            path = databasePath,
            password = password,
            optionsJson = if (create) OPTIONS_CREATE else OPTIONS_OPEN,
        )
    }

    private suspend fun formatVersion(): Int =
        JSONObject(database.stats()).optInt("format_version")

    private suspend fun discard(databasePath: String) {
        database.destroy()
        session = null
        File(databasePath).delete()
        File("$databasePath.lock").delete()
    }

    private fun requireUnlocked() {
        check(session != null) { "密码库未解锁" }
    }

    companion object {
        /** 密码库的命名空间与集合。 */
        const val NAMESPACE = "vault"
        const val COLLECTION = "entries"

        /** 启用同步后的存储格式版本；首次建库必须达到它。 */
        const val FORMAT_VERSION_SYNCED = 2

        /** 显式区分建库与解锁，避免误用 `create=true` 打开已有库。 */
        const val OPTIONS_CREATE = """{"create":true}"""
        const val OPTIONS_OPEN = """{"create":false}"""

        private const val PAGE_SIZE = 100
        private const val MAX_ENTRIES = 2000

        /** 恢复时的旁路文件名：先校验完毕再替换正式库。 */
        const val RESTORED_NAME = "vault-restored.db"
    }
}

/** 备份摘要（来自内核 `shio_backup` 返回的 JSON）。 */
data class BackupSummary(
    val formatVersion: Int,
    val dbBytes: Long,
    val tableCount: Int,
    val rowCount: Long,
)
