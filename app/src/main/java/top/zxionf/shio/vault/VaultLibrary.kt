package top.zxionf.shio.vault

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 一个密码库在索引里的记录。
 *
 * 只含显示名与文件名，**不含口令、条目或任何敏感内容**；库本身仍由内核加密。
 */
data class VaultLibraryEntry(
    val id: String,
    val name: String,
    val fileName: String,
    val createdAt: Long,
) {
    /** 是否是升级前遗留的 `filesDir/vault.db`。 */
    val isLegacy: Boolean
        get() = id == VaultLibrary.LEGACY_ID
}

/**
 * 多密码库的索引（`SharedPreferences` 里的一个 JSON 数组）。
 *
 * 文件布局：
 * - 新库：`filesDir/vaults/<uuid>.db`
 * - 旧库：`filesDir/vault.db`（首次启动自动注册为 `default`，不移动文件）
 *
 * 索引与库文件的一致性靠「先写文件/先删索引」的顺序保证，见各方法的注释。
 */
object VaultLibrary {

    private const val PREFS_NAME = "vaults"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_LAST_VAULT = "lastVaultId"

    /** 库文件所在子目录。 */
    const val VAULTS_DIR = "vaults"

    const val LEGACY_ID = "default"
    const val LEGACY_FILE_NAME = "vault.db"
    const val LEGACY_NAME = "默认密码库"

    /** 默认库名（新建时使用）。 */
    const val DEFAULT_NAME = "我的密码库"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 全部库记录，按创建时间升序（旧库在前）。 */
    fun list(context: Context): List<VaultLibraryEntry> =
        decodeEntries(prefs(context).getString(KEY_ENTRIES, null))

    /**
     * 把升级前的 `filesDir/vault.db` 注册成索引里的 `default` 条目；已注册或文件不存在时返回 null。
     *
     * 幂等：重复调用不会产生重复条目。
     */
    fun ensureLegacyRegistered(context: Context): VaultLibraryEntry? {
        val existing = list(context)
        if (existing.any { it.id == LEGACY_ID }) return null
        val legacyFile = File(context.filesDir, LEGACY_FILE_NAME)
        if (!legacyFile.exists()) return null
        val entry = VaultLibraryEntry(
            id = LEGACY_ID,
            name = LEGACY_NAME,
            fileName = LEGACY_FILE_NAME,
            createdAt = legacyFile.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        save(context, existing + entry)
        return entry
    }

    /** 新建一个库的记录（不创建文件；文件由内核 `open(create=true)` 创建）。 */
    fun register(context: Context, name: String): VaultLibraryEntry {
        val id = UUID.randomUUID().toString()
        val entry = VaultLibraryEntry(
            id = id,
            name = name.trim().ifEmpty { DEFAULT_NAME },
            fileName = "$id.db",
            createdAt = System.currentTimeMillis(),
        )
        save(context, list(context) + entry)
        return entry
    }

    /** 重命名：只改索引，不动文件。 */
    fun rename(context: Context, id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        save(context, list(context).map { if (it.id == id) it.copy(name = trimmed) else it })
    }

    /**
     * 删除库：先删索引记录，再删文件与旁路锁。
     *
     * 先删索引可以保证「即使文件删除失败，也不会留下指向不存在文件的孤儿索引」；
     * 反过来会留下一个无法解锁、也无法在界面上删除的幽灵库。
     */
    fun remove(context: Context, id: String) {
        val entries = list(context)
        val target = entries.firstOrNull { it.id == id } ?: return
        save(context, entries.filterNot { it.id == id })
        val file = fileFor(context, target)
        file.delete()
        File("${file.absolutePath}.lock").delete()
    }

    /** 库文件的绝对路径。 */
    fun fileFor(context: Context, entry: VaultLibraryEntry): File =
        if (entry.isLegacy) {
            File(context.filesDir, entry.fileName)
        } else {
            File(File(context.filesDir, VAULTS_DIR), entry.fileName)
        }

    /**
     * 返回库文件并**确保父目录存在**。
     *
     * 新建库时 `filesDir/vaults/` 可能还不存在，内核 `open(create=true)` 不会替你建目录，
     * 直接传路径会得到 `IO (-14)`。所有「要写入库文件」的调用方都应该走这里。
     */
    fun prepareFile(context: Context, entry: VaultLibraryEntry): File {
        val file = fileFor(context, entry)
        file.parentFile?.mkdirs()
        return file
    }

    /** 上一次解锁成功的库 ID；用于库列表高亮。 */
    fun lastVaultId(context: Context): String? =
        prefs(context).getString(KEY_LAST_VAULT, null)

    fun rememberLast(context: Context, id: String) {
        prefs(context).edit().putString(KEY_LAST_VAULT, id).apply()
    }

    fun forgetId(context: Context) {
        prefs(context).edit().remove(KEY_LAST_VAULT).apply()
    }

    /** 按 ID 取记录；找不到返回 null。 */
    fun find(context: Context, id: String?): VaultLibraryEntry? {
        if (id == null) return null
        return list(context).firstOrNull { it.id == id }
    }

    private fun save(context: Context, entries: List<VaultLibraryEntry>) {
        prefs(context).edit().putString(KEY_ENTRIES, encodeEntries(entries)).apply()
    }

    // ---- 编解码（纯函数，便于单测） ----

    fun encodeEntries(entries: List<VaultLibraryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put(FIELD_ID, entry.id)
                    put(FIELD_NAME, entry.name)
                    put(FIELD_FILE_NAME, entry.fileName)
                    put(FIELD_CREATED_AT, entry.createdAt)
                },
            )
        }
        return array.toString()
    }

    /** 解码索引；坏项逐条跳过，不让一条坏数据毁掉整个库列表。 */
    fun decodeEntries(raw: String?): List<VaultLibraryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (_: JSONException) {
            return emptyList()
        }
        val entries = mutableListOf<VaultLibraryEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString(FIELD_ID).trim()
            val name = item.optString(FIELD_NAME).trim()
            val fileName = item.optString(FIELD_FILE_NAME).trim()
            if (id.isEmpty() || fileName.isEmpty()) continue
            entries += VaultLibraryEntry(
                id = id,
                name = name.ifEmpty { DEFAULT_NAME },
                fileName = fileName,
                createdAt = item.optLong(FIELD_CREATED_AT, 0L),
            )
        }
        // 去重（同一 ID 只保留最后一条）并按创建时间排序。
        return entries
            .associateBy { it.id }
            .values
            .sortedBy { it.createdAt }
    }

    private const val FIELD_ID = "id"
    private const val FIELD_NAME = "name"
    private const val FIELD_FILE_NAME = "fileName"
    private const val FIELD_CREATED_AT = "createdAt"
}
