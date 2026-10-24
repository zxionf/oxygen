package top.zxionf.shio.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 应用主题。
 *
 * - [themeMode] 决定深浅色：`SYSTEM` 跟随系统，`LIGHT`/`DARK` 强制指定。
 * - [palette] 决定配色；[Palette.DYNAMIC] 只在系统确实提供动态色时生效，
 *   否则（API < 31 或资源缺失）回退 Shio 蓝。
 * - 状态栏与导航栏图标颜色跟随最终深浅色，edge-to-edge 下不再由系统默认值决定。
 */
@Composable
fun ShioTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: Palette = Palette.SHIO_BLUE,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val dynamicAvailable = remember(context) { DynamicColorSupport.isAvailable(context) }
    val useDynamic = palette == Palette.DYNAMIC && dynamicAvailable

    val colorScheme = when {
        !useDynamic -> shioColorScheme(palette, darkTheme)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicScheme(context, darkTheme)
        else -> shioColorScheme(Palette.SHIO_BLUE, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** 动态取色只在 API 31+ 存在，集中在这里做一次版本判断。 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun dynamicScheme(context: Context, darkTheme: Boolean): ColorScheme =
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

/** LocalView 的 context 可能被 `ContextThemeWrapper` 包过，需要逐层解包。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
