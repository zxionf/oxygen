package top.zxionf.shio.ui.theme

import androidx.compose.material3.Typography

/**
 * 排版：M3E 默认字体（Android 上即 Roboto）+ 标准字重。
 *
 * 强调层级统一用 `MaterialTheme.typography.*Emphasized`（M3E 提供，
 * 例如 `headlineSmallEmphasized`、`titleLargeEmphasized`），
 * 不再在页面里手写 `fontWeight = SemiBold` 之类的覆盖。
 */
val ShioTypography = Typography()
