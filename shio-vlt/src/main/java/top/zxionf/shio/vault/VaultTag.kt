package top.zxionf.shio.vault

import org.json.JSONException
import org.json.JSONObject

/**
 * 标签：存在 `vault/tags` 集合里，条目通过 `tagIds` 引用它的 ID。
 *
 * 选择独立集合而不是把标签名直接写进条目，是为了让重命名只改一处；
 * 代价是删除标签要清理条目里的引用（残留 ID 在读取时被忽略）。
 */
data class VaultTag(
    val id: String,
    val name: String,
)

/** `vault/tags` 记录的 JSON 编解码（只存名称，ID 来自内核 meta）。 */
object VaultTagCodec {

    const val FIELD_NAME = "name"

    /** 标签名最长长度。 */
    const val MAX_NAME_LENGTH = 24

    fun encode(tag: VaultTag): String = JSONObject().apply {
        put(FIELD_NAME, tag.name)
    }.toString()

    /** 解码；JSON 非法或名称为空时返回 null。 */
    fun decode(id: String, json: String): VaultTag? {
        val parsed = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        val name = parsed.optString(FIELD_NAME).trim()
        if (name.isEmpty()) return null
        return VaultTag(id = id, name = name)
    }

    /**
     * 校验并规范化标签名：去首尾空白、压缩内部空白；空、超长或含控制字符时返回 null。
     */
    fun normalizeName(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) return null
        if (name.any { it.isISOControl() }) return null
        return name
    }

    /** 导入时按名字解析标签的计划：已存在的直接复用 ID，缺失的按顺序新建。 */
    data class ImportPlan(
        /** 小写标签名 → 已有标签 ID（含本次要新建的名字之外的全部已有标签）。 */
        val resolved: Map<String, String> = emptyMap(),
        /** 需要新建的标签名，保持首次出现顺序，不重复。 */
        val toCreate: List<String> = emptyList(),
    ) {
        /** 小写名称对应的标签 ID；不在 [resolved] 里时到新建结果里找。 */
        fun idOf(name: String, created: Map<String, String> = emptyMap()): String? {
            val key = name.trim().lowercase()
            return resolved[key] ?: created[key]
        }
    }

    /**
     * 规划导入时的标签名 → ID 映射。
     *
     * - 忽略大小写与首尾空白匹配已有标签；
     * - 文件内同名（忽略大小写）只新建一条；
     * - 规范化失败或已达 [limit] 上限的名称被丢弃（不阻断导入）。
     */
    fun planImport(names: List<String>, existing: List<VaultTag>, limit: Int): ImportPlan {
        val resolved = existing.associate { it.name.trim().lowercase() to it.id }
        val toCreate = mutableListOf<String>()
        names.forEach { raw ->
            val name = normalizeName(raw) ?: return@forEach
            val key = name.lowercase()
            if (key in resolved) return@forEach
            if (toCreate.any { it.lowercase() == key }) return@forEach
            if (resolved.size + toCreate.size >= limit) return@forEach
            toCreate += name
        }
        return ImportPlan(resolved = resolved, toCreate = toCreate)
    }
}
