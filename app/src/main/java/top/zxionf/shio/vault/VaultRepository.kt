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

    /** 新建条目，返回内核生成的记录 ID。 */
    suspend fun insert(entry: VaultEntry): String {
        requireUnlocked()
        return database.insert(NAMESPACE, COLLECTION, VaultEntryCodec.encode(entry))
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
    }
}
