package top.zxionf.shio.vault

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 条目的导入导出编解码（纯 Kotlin，无 Android 依赖）。
 *
 * 字段为 `title`、`username`、`password`、`url`、`notes`、`tags`、`favorite`；记录 ID、
 * 时间戳与 `type` 都不参与传输（导入时一律新建）。
 *
 * - CSV：首行表头，按 RFC 4180 转义，`\n` 换行，UTF-8；`tags` 列用 `;` 连接标签名，
 *   名称内的 `\` 与 `;` 分别转义为 `\\` 与 `\;`；`favorite` 用 `1` / `0`。
 * - JSON：`org.json` 生成，缩进 2 空格；`tags` 是字符串数组，`favorite` 是布尔。
 * - 导入只新增不覆盖，`title` 与 `password` 必填，缺任一则该条计入跳过。
 * - 标签以「名字」传输，ID 由导入方按名字匹配或创建（见 SPEC 的导入导出小节）。
 */
object VaultTransfer {

    /** 单次导入上限，与列表加载上限一致。 */
    const val MAX_IMPORT_ENTRIES = 2000

    /** CSV 列名 / JSON 字段名。 */
    const val FIELD_TAGS = "tags"
    const val FIELD_FAVORITE = "favorite"

    /** CSV `tags` 列的标签名分隔符。 */
    const val TAG_SEPARATOR = ';'

    private val COLUMNS = listOf(
        VaultEntryCodec.FIELD_TITLE,
        VaultEntryCodec.FIELD_USERNAME,
        VaultEntryCodec.FIELD_PASSWORD,
        VaultEntryCodec.FIELD_URL,
        VaultEntryCodec.FIELD_NOTES,
        FIELD_TAGS,
        FIELD_FAVORITE,
    )

    private const val CSV_HEADER = "title,username,password,url,notes,tags,favorite"

    /** 一条可导入记录：条目本体 + 按名字引用的标签。 */
    data class Item(
        val entry: VaultEntry,
        val tagNames: List<String> = emptyList(),
    )

    /** 解析结果：可导入的记录 + 被跳过的行数。 */
    data class ParseResult(
        val items: List<Item> = emptyList(),
        val skipped: Int = 0,
    ) {
        /** 只关心条目本体的调用方（如未处理标签的导入路径）用这个视图。 */
        val entries: List<VaultEntry> get() = items.map { it.entry }
    }

    // ---- 导出 ----

    /** 导出为 CSV：表头 + 每条一行，字段按 RFC 4180 转义。 */
    fun encodeCsv(entries: List<VaultEntry>, tagNames: Map<String, String> = emptyMap()): String {
        val builder = StringBuilder(CSV_HEADER).append('\n')
        entries.forEach { entry ->
            builder
                .append(csvField(entry.title)).append(',')
                .append(csvField(entry.username)).append(',')
                .append(csvField(entry.password)).append(',')
                .append(csvField(entry.url)).append(',')
                .append(csvField(entry.notes)).append(',')
                .append(csvField(encodeTags(namesOf(entry, tagNames)))).append(',')
                .append(if (entry.favorite) '1' else '0')
                .append('\n')
        }
        return builder.toString()
    }

    /** 导出为 JSON 数组，缩进 2 空格便于人工查看。 */
    fun encodeJson(entries: List<VaultEntry>, tagNames: Map<String, String> = emptyMap()): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put(VaultEntryCodec.FIELD_TITLE, entry.title)
                    put(VaultEntryCodec.FIELD_USERNAME, entry.username)
                    put(VaultEntryCodec.FIELD_PASSWORD, entry.password)
                    put(VaultEntryCodec.FIELD_URL, entry.url)
                    put(VaultEntryCodec.FIELD_NOTES, entry.notes)
                    put(FIELD_TAGS, JSONArray(namesOf(entry, tagNames)))
                    put(FIELD_FAVORITE, entry.favorite)
                },
            )
        }
        return array.toString(2)
    }

    // ---- 导入 ----

    /** 按内容自动识别格式：首字符是 `[` 或 `{` 走 JSON，否则按 CSV。 */
    fun detectAndDecode(text: String): ParseResult {
        val trimmed = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return when (trimmed.firstOrNull()) {
            '[' , '{' -> decodeJson(trimmed)
            else -> decodeCsv(trimmed)
        }
    }

    /** 解析 CSV：首条非空行当表头，按列名取值，未知列忽略。 */
    fun decodeCsv(text: String): ParseResult {
        val rows = parseCsvRows(text)
        if (rows.isEmpty()) return ParseResult()
        val header = rows.first().map { it.trim().lowercase() }
        val indexOf = COLUMNS.associateWith { column -> header.indexOf(column) }
        if (indexOf.values.all { it < 0 }) {
            // 完全没有我们认识的列，视为不可解析（不是「跳过」，而是格式不对）。
            return ParseResult(skipped = 0)
        }
        val entries = mutableListOf<Item>()
        var skipped = 0
        rows.drop(1).forEach { cells ->
            if (cells.all { it.isBlank() }) return@forEach
            val entry = VaultEntry(
                id = "",
                title = indexOf.getValue(VaultEntryCodec.FIELD_TITLE).let { cells.getOrNull(it) }.orEmpty(),
                username = indexOf.getValue(VaultEntryCodec.FIELD_USERNAME).let { cells.getOrNull(it) }.orEmpty(),
                password = indexOf.getValue(VaultEntryCodec.FIELD_PASSWORD).let { cells.getOrNull(it) }.orEmpty(),
                url = indexOf.getValue(VaultEntryCodec.FIELD_URL).let { cells.getOrNull(it) }.orEmpty(),
                notes = indexOf.getValue(VaultEntryCodec.FIELD_NOTES).let { cells.getOrNull(it) }.orEmpty(),
                favorite = parseFavorite(indexOf.getValue(FIELD_FAVORITE).let { cells.getOrNull(it) }),
            )
            val tagNames = decodeTags(indexOf.getValue(FIELD_TAGS).let { cells.getOrNull(it) }.orEmpty())
            when {
                entry.title.isBlank() || entry.password.isBlank() -> skipped += 1
                entries.size >= MAX_IMPORT_ENTRIES -> skipped += 1
                else -> entries += Item(entry = entry, tagNames = tagNames)
            }
        }
        return ParseResult(items = entries, skipped = skipped)
    }

    /** 解析 JSON：既接受数组也接受单个对象。 */
    fun decodeJson(text: String): ParseResult {
        val array = try {
            when (val root = runCatching { org.json.JSONTokener(text).nextValue() }.getOrNull()) {
                is JSONArray -> root
                is JSONObject -> JSONArray().put(root)
                else -> return ParseResult()
            }
        } catch (_: JSONException) {
            return ParseResult()
        }
        val entries = mutableListOf<Item>()
        var skipped = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item == null) {
                skipped += 1
                continue
            }
            val entry = VaultEntry(
                id = "",
                title = item.optString(VaultEntryCodec.FIELD_TITLE),
                username = item.optString(VaultEntryCodec.FIELD_USERNAME),
                password = item.optString(VaultEntryCodec.FIELD_PASSWORD),
                url = item.optString(VaultEntryCodec.FIELD_URL),
                notes = item.optString(VaultEntryCodec.FIELD_NOTES),
                favorite = parseFavorite(item.opt(FIELD_FAVORITE)),
            )
            val tagNames = decodeTags(item.opt(FIELD_TAGS))
            when {
                entry.title.isBlank() || entry.password.isBlank() -> skipped += 1
                entries.size >= MAX_IMPORT_ENTRIES -> skipped += 1
                else -> entries += Item(entry = entry, tagNames = tagNames)
            }
        }
        return ParseResult(items = entries, skipped = skipped)
    }

    // ---- 内部 ----

    /** 把条目的 `tagIds` 按顺序翻译成名字；悬空 ID 直接跳过。 */
    private fun namesOf(entry: VaultEntry, tagNames: Map<String, String>): List<String> =
        entry.tagIds.mapNotNull { id -> tagNames[id]?.takeIf { it.isNotBlank() } }

    /** `tags` 列编码：`;` 连接，名称内的 `\` 与 `;` 加反斜杠转义。 */
    private fun encodeTags(names: List<String>): String = names.joinToString(TAG_SEPARATOR.toString()) { name ->
        buildString(name.length) {
            name.forEach { char ->
                if (char == '\\' || char == TAG_SEPARATOR) append('\\')
                append(char)
            }
        }
    }

    /**
     * `tags` 解码：既接受 JSON 数组，也接受 `;` 分隔的字符串（CSV 与手写 JSON 两种来源）。
     *
     * 只有 `\\` 与 `\;` 是转义，其余位置的 `\` 原样保留；空名与规范化后为空的片断丢弃。
     */
    private fun decodeTags(raw: Any?): List<String> {
        val names = when (raw) {
            null, JSONObject.NULL -> return emptyList()
            is JSONArray -> (0 until raw.length()).map { index -> raw.optString(index) }
            else -> splitTags(raw.toString())
        }
        return names.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 按 `;` 切分并处理转义。 */
    private fun splitTags(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val names = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            if (char == '\\' && (next == '\\' || next == TAG_SEPARATOR)) {
                current.append(next)
                index += 2
                continue
            }
            if (char == TAG_SEPARATOR) {
                names += current.toString()
                current.setLength(0)
                index += 1
                continue
            }
            current.append(char)
            index += 1
        }
        names += current.toString()
        return names
    }

    /** `favorite` 解码：布尔、`1`/`0`、`true`/`false`、`yes`/`no` 都认，其余当未收藏。 */
    private fun parseFavorite(raw: Any?): Boolean = when (raw) {
        null, JSONObject.NULL -> false
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        else -> raw.toString().trim().lowercase() in TRUTHY
    }

    private val TRUTHY = setOf("1", "true", "yes")

    /** 单字段转义：含逗号、双引号、回车或换行时整体加引号，内部 `"` 变 `""`。 */
    private fun csvField(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        if (!needsQuote) return value
        return buildString(value.length + 2) {
            append('"')
            value.forEach { char ->
                if (char == '"') append('"')
                append(char)
            }
            append('"')
        }
    }

    /**
     * 把 CSV 文本切成行/列：支持引号内的逗号、换行与 `""` 转义；`\r\n` 视作行结束。
     */
    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var index = 0
        fun endCell() {
            cells += cell.toString()
            cell.setLength(0)
        }
        fun endRow() {
            endCell()
            rows += cells
            cells = mutableListOf()
        }
        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                !inQuotes && char == ',' -> endCell()
                !inQuotes && char == '\n' -> endRow()
                !inQuotes && char == '\r' -> Unit // 交给 \n 结束行；孤立 \r 直接丢弃
                else -> cell.append(char)
            }
            index += 1
        }
        // 收尾：最后一行没有换行时也要收进来。
        if (cell.isNotEmpty() || cells.isNotEmpty()) endRow()
        return rows.filter { row -> row.any { it.isNotBlank() } }
    }
}
