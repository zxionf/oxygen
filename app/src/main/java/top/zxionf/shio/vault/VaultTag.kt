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
}
