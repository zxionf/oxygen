package top.zxionf.shio.vault

import java.net.URI

/**
 * 网址校验：只有 `http`/`https` 且带主机名的地址才允许交给系统外部打开。
 *
 * 用 `java.net.URI` 而不是 `android.net.Uri`，这样规则可以在 JVM 单测里覆盖
 * （`javascript:`、`file:`、`intent:`、无 scheme 等一律拒绝）。
 */
object VaultUrl {

    fun isHttpUrl(raw: String): Boolean {
        val value = raw.trim()
        if (value.isEmpty()) return false
        val uri = try {
            URI(value)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        return !uri.host.isNullOrEmpty()
    }
}
