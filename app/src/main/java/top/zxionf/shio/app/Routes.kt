package top.zxionf.shio.app

import android.net.Uri

/**
 * 字符串路由表。
 *
 * 设计上不引入 kotlinx-serialization，路由统一用字符串表达；参数一律经
 * [Uri.encode] 编码后再拼接，避免条目 ID 中的特殊字符破坏匹配。
 */
object Routes {
    const val Gate = "gate"
    const val Create = "create"
    const val Unlock = "unlock"
    const val Vault = "vault"
    const val Settings = "settings"
    const val Appearance = "appearance"
    const val ChangePassword = "change-password"
    const val Demo = "demo"

    /** 详情路由参数名。 */
    const val EntryIdArg = "id"

    /** 详情路由模板：`entry/{id}`。 */
    const val EntryPattern = "entry/{$EntryIdArg}"

    /** 编辑器路由参数名；缺省表示新建。 */
    const val EditorEntryIdArg = "entryId"

    /** 编辑器路由模板：`editor?entryId={entryId}`。 */
    const val EditorPattern = "editor?$EditorEntryIdArg={$EditorEntryIdArg}"

    /** 拼接详情路由；[id] 为内核返回的记录 ID。 */
    fun entry(id: String): String = "entry/${Uri.encode(id)}"

    /** 拼接编辑器路由；[entryId] 为 null 时表示新建条目。 */
    fun editor(entryId: String? = null): String =
        if (entryId == null) "editor" else "editor?$EditorEntryIdArg=${Uri.encode(entryId)}"
}
