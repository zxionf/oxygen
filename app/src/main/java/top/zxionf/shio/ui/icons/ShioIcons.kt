package top.zxionf.shio.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 项目自带的少量图标。
 *
 * `material-icons-core` 只包含 40 余个核心图标，没有复制、显示/隐藏密码这类常用图标，
 * 而设计上不允许引入 `material-icons-extended`（体积约 10 MB）。这里用 Material Icons
 * 的 24dp 路径数据（Apache-2.0）在本地构建 ImageVector，体积为零、无额外依赖。
 */
object ShioIcons {
    /** 复制（Material Icons `content_copy`）。 */
    val Copy: ImageVector = icon(
        name = "ContentCopy",
        path = "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11" +
            "c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
    )

    /** 显示明文密码（Material Icons `visibility`）。 */
    val Visibility: ImageVector = icon(
        name = "Visibility",
        path = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5z" +
            "M12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3" +
            " 3-1.34 3-3-1.34-3-3-3z",
    )

    /** 隐藏明文密码（Material Icons `visibility_off`）。 */
    val VisibilityOff: ImageVector = icon(
        name = "VisibilityOff",
        path = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75" +
            "-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7z" +
            "M2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84" +
            "l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3" +
            ".22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2z",
    )

    /** 网址（Material Icons `link`）。 */
    val Link: ImageVector = icon(
        name = "Link",
        path = "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7" +
            "c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4" +
            "c2.76 0 5-2.24 5-5s-2.24-5-5-5z",
    )

    /** 标题（Material Icons `title`）。 */
    val Title: ImageVector = icon(
        name = "Title",
        path = "M5 4v3h5.5v12h3V7H19V4z",
    )

    /** 密码 / 密钥（Material Icons `key`）。 */
    val Key: ImageVector = icon(
        name = "Key",
        path = "M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4" +
            "H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z",
    )

    /** 备注（Material Icons `description`）。 */
    val Notes: ImageVector = icon(
        name = "Description",
        path = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2z" +
            "m-3-5V3.5L18.5 9H13z",
    )

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(path),
            fill = SolidColor(Color.Black),
        ).build()
}
