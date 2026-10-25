package top.zxionf.shio.vault

import android.content.Context
import java.io.File

/**
 * 密码库在应用私有目录里的位置。
 *
 * 内核在 Android 上还会在数据库旁维护 `<db>.lock`（写句柄独占），它是可重建的旁路文件；
 * 判断「库是否存在」只看 [DATABASE_NAME]，不要用目录内容或 `.lock` 的存在性。
 */
object VaultPaths {

    const val DATABASE_NAME = "vault.db"

    fun databaseFile(context: Context): File = File(context.filesDir, DATABASE_NAME)

    fun databasePath(context: Context): String = databaseFile(context).absolutePath

    /** 本地是否已有密码库；`gate` 用它决定进 `unlock` 还是 `create`。 */
    fun exists(context: Context): Boolean = databaseFile(context).exists()
}
