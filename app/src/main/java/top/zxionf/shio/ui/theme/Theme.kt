package top.zxionf.shio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ShioBlue,
    onPrimary = Color.White,
    surface = ShioSurfaceLight,
    onSurface = ShioOnSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = ShioBlueDark,
    onPrimary = Color(0xFF002E6A),
    surface = ShioSurfaceDark,
    onSurface = ShioOnSurfaceDark,
)

/**
 * Demo 主题：固定配色，不启用动态取色，保证界面截图可比对。
 */
@Composable
fun ShioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
