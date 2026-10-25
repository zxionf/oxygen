package top.zxionf.shio.vault

import org.json.JSONException
import org.json.JSONObject

/**
 * `vault/entries` 记录的 JSON 编解码。
 *
 * 记录值只放业务字段，记录 ID 由内核元信息给出（`insert` 生成或 `batch` 客户端生成），
 * 不写进 JSON。字段名与 `.design/changes/20261023-password-vault/design.md` 的数据模型
 * 一致：`title`、`username`、`password`、`url`、`notes`。
 *
 * 解码对字段全部宽容：缺失或类型不对时取默认值，不让一条坏记录毁掉整个列表；
 * 只有 JSON 本身不合法时才返回 null。
 */
object VaultEntryCodec {

    /** 编码为记录值 JSON。 */
    fun encode(entry: VaultEntry): String = JSONObject().apply {
        put(FIELD_TITLE, entry.title)
        put(FIELD_USERNAME, entry.username)
        put(FIELD_PASSWORD, entry.password)
        put(FIELD_URL, entry.url)
        put(FIELD_NOTES, entry.notes)
    }.toString()

    /** 解码记录值；[id] 来自内核元信息。JSON 非法时返回 null。 */
    fun decode(id: String, json: String): VaultEntry? {
        val parsed = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        return VaultEntry(
            id = id,
            title = parsed.optString(FIELD_TITLE),
            username = parsed.optString(FIELD_USERNAME),
            password = parsed.optString(FIELD_PASSWORD),
            url = parsed.optString(FIELD_URL),
            notes = parsed.optString(FIELD_NOTES),
        )
    }

    const val FIELD_TITLE = "title"
    const val FIELD_USERNAME = "username"
    const val FIELD_PASSWORD = "password"
    const val FIELD_URL = "url"
    const val FIELD_NOTES = "notes"
}
