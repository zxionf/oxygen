package top.zxionf.shio.vault

/**
 * 密码库条目。
 *
 * M4–M10 只用假数据；M11 起由内核 `vault/entries` 的 JSON 解码得到，字段名与
 * `.design/changes/20261023-password-vault/design.md` 中的数据模型保持一致。
 */
data class VaultEntry(
    val id: String,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    /** 引用的标签 ID（`vault/tags`）；找不到对应标签的 ID 在展示时被忽略。 */
    val tagIds: List<String> = emptyList(),
    /** 收藏标记：属于记录自身而非分类维度，因此是布尔字段而不是特殊标签。 */
    val favorite: Boolean = false,
) {
    /** 列表副标题：优先用户名，其次网址；两者都为空时为空串。 */
    val subtitle: String
        get() = username.ifBlank { url }

    /** 列表项首字符，用作类型占位图标；空标题用 "?" 兜底。 */
    val initial: String
        get() = title.trim().firstOrNull()?.uppercase() ?: "?"

    /**
     * 搜索命中：标题、用户名、网址、备注四个字段，忽略大小写。
     * 标签不参与全文搜索（标签有自己的筛选入口）。
     */
    fun matches(query: String): Boolean {
        val keyword = query.trim()
        if (keyword.isEmpty()) return true
        return title.contains(keyword, ignoreCase = true) ||
            username.contains(keyword, ignoreCase = true) ||
            url.contains(keyword, ignoreCase = true) ||
            notes.contains(keyword, ignoreCase = true)
    }
}
