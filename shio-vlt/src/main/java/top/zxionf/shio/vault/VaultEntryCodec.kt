package top.zxionf.shio.vault

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * `vault/entries` 记录的 JSON 编解码。
 *
 * 记录值只放业务字段，记录 ID 由内核元信息给出（`insert` 生成或 `batch` 客户端生成），
 * 不写进 JSON。字段：`title`、`username`、`password`、`url`、`notes`，以及标签能力
 * 引入的 `tagIds`（引用 `vault/tags` 的 ID 数组）与 `favorite`（布尔）。
 *
 * `tagIds` 与 `favorite` 是纯字段增量，旧记录缺失时按「无标签 / 未收藏」处理，
 * 不改 `format`、无需迁移。
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
        if (entry.tagIds.isNotEmpty()) {
            put(FIELD_TAG_IDS, JSONArray(entry.tagIds))
        }
        if (entry.favorite) {
            put(FIELD_FAVORITE, true)
        }
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
            tagIds = readTagIds(parsed),
            favorite = parsed.optBoolean(FIELD_FAVORITE, false),
        )
    }

    /** 读标签 ID 数组：非字符串、空串与重复项一律忽略，顺序保持原样。 */
    private fun readTagIds(parsed: JSONObject): List<String> {
        val array = parsed.optJSONArray(FIELD_TAG_IDS) ?: return emptyList()
        val ids = LinkedHashSet<String>(array.length())
        for (index in 0 until array.length()) {
            val value = (array.opt(index) as? String)?.trim().orEmpty()
            if (value.isNotEmpty()) ids += value
        }
        return ids.toList()
    }

    const val FIELD_TITLE = "title"
    const val FIELD_USERNAME = "username"
    const val FIELD_PASSWORD = "password"
    const val FIELD_URL = "url"
    const val FIELD_NOTES = "notes"
    const val FIELD_TAG_IDS = "tagIds"
    const val FIELD_FAVORITE = "favorite"
}
