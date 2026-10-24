package top.zxionf.shio.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 中性色：所有预设色板共用，保证文字与背景的对比度稳定。
// 容器色（surfaceContainer*）取自同一套灰度阶梯，避免回退到 M3 基线的紫调默认值。
private val LightSurface = Color(0xFFFFFBFE)
private val LightOnSurface = Color(0xFF1A1C1E)
private val LightSurfaceVariant = Color(0xFFE2E2E9)
private val LightOnSurfaceVariant = Color(0xFF45464F)
private val LightOutline = Color(0xFF757680)
private val LightOutlineVariant = Color(0xFFC6C6D0)

private val DarkSurface = Color(0xFF111418)
private val DarkOnSurface = Color(0xFFE3E2E6)
private val DarkSurfaceVariant = Color(0xFF45464F)
private val DarkOnSurfaceVariant = Color(0xFFC6C5D0)
private val DarkOutline = Color(0xFF90909A)
private val DarkOutlineVariant = Color(0xFF45464F)

/**
 * 手写浅色 scheme：只覆盖品牌相关槽位，中性色取自上面的共享基准。
 *
 * 不使用取色库，也不从种子色自动推导，保证 5 套色板的外观可预期、可截图比对。
 */
private fun shioLight(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = primary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = primary,
    surfaceBright = Color(0xFFFFFBFE),
    surfaceDim = Color(0xFFDFDDE2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3F7),
    surfaceContainer = Color(0xFFEFEDF1),
    surfaceContainerHigh = Color(0xFFE9E7EB),
    surfaceContainerHighest = Color(0xFFE3E1E6),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

/** 手写深色 scheme，槽位与 [shioLight] 一一对应。 */
private fun shioDark(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = primary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = primary,
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111418),
    surfaceContainerLowest = Color(0xFF0C0F12),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

internal val ShioBlue = Color(0xFF2F6FED)
internal val ShioBlueDark = Color(0xFF9DC0FF)

private val ShioBlueLightScheme = shioLight(
    primary = ShioBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE4FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
)

private val ShioBlueDarkScheme = shioDark(
    primary = ShioBlueDark,
    onPrimary = Color(0xFF002E6A),
    primaryContainer = Color(0xFF1C4C9B),
    onPrimaryContainer = Color(0xFFDBE4FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFD6BDE3),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
)

private val TealLightScheme = shioLight(
    primary = Color(0xFF00696E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1F6),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8EA),
    onSecondaryContainer = Color(0xFF051F21),
    tertiary = Color(0xFF456179),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCBE6FF),
    onTertiaryContainer = Color(0xFF001E31),
)

private val TealDarkScheme = shioDark(
    primary = Color(0xFF80D4DB),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F54),
    onPrimaryContainer = Color(0xFF9CF1F6),
    secondary = Color(0xFFB0CCCE),
    onSecondary = Color(0xFF1B3537),
    secondaryContainer = Color(0xFF324B4D),
    onSecondaryContainer = Color(0xFFCCE8EA),
    tertiary = Color(0xFFADCAE5),
    onTertiary = Color(0xFF153349),
    tertiaryContainer = Color(0xFF2C4961),
    onTertiaryContainer = Color(0xFFCBE6FF),
)

private val GreenLightScheme = shioLight(
    primary = Color(0xFF2F6E3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F2B4),
    onPrimaryContainer = Color(0xFF002105),
    secondary = Color(0xFF52634F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F0F),
    tertiary = Color(0xFF38656A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002022),
)

private val GreenDarkScheme = shioDark(
    primary = Color(0xFF97D897),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF175124),
    onPrimaryContainer = Color(0xFFB2F2B4),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243423),
    secondaryContainer = Color(0xFF3B4B38),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1E4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
)

private val PurpleLightScheme = shioLight(
    primary = Color(0xFF6B4EAA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF25005A),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF31101C),
)

private val PurpleDarkScheme = shioDark(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E2),
)

private val OrangeLightScheme = shioLight(
    primary = Color(0xFF8F4C00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2E1400),
    secondary = Color(0xFF745943),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DEC8),
    onSecondaryContainer = Color(0xFF29180A),
    tertiary = Color(0xFF5B6236),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDFE6B4),
    onTertiaryContainer = Color(0xFF181E00),
)

private val OrangeDarkScheme = shioDark(
    primary = Color(0xFFFFB870),
    onPrimary = Color(0xFF4C2700),
    primaryContainer = Color(0xFF6D3A00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE2C0A8),
    onSecondary = Color(0xFF412D1A),
    secondaryContainer = Color(0xFF59422E),
    onSecondaryContainer = Color(0xFFF6DEC8),
    tertiary = Color(0xFFC3CA9A),
    onTertiary = Color(0xFF2D3409),
    tertiaryContainer = Color(0xFF434B1E),
    onTertiaryContainer = Color(0xFFDFE6B4),
)

/**
 * 取色板对应的 scheme。
 *
 * `DYNAMIC` 不在这里处理：动态取色依赖 API 31+ 的 `dynamicColorScheme`，
 * 由 [ShioTheme] 判断可用性，不可用时回退 [Palette.SHIO_BLUE]。
 */
internal fun shioColorScheme(palette: Palette, darkTheme: Boolean): ColorScheme =
    when (palette) {
        Palette.SHIO_BLUE, Palette.DYNAMIC -> if (darkTheme) ShioBlueDarkScheme else ShioBlueLightScheme
        Palette.TEAL -> if (darkTheme) TealDarkScheme else TealLightScheme
        Palette.GREEN -> if (darkTheme) GreenDarkScheme else GreenLightScheme
        Palette.PURPLE -> if (darkTheme) PurpleDarkScheme else PurpleLightScheme
        Palette.ORANGE -> if (darkTheme) OrangeDarkScheme else OrangeLightScheme
    }

/** 动态取色卡片的示意渐变：五个预设主色的色环。 */
internal val DynamicColorSwatch = listOf(
    Color(0xFF2F6FED),
    Color(0xFF00696E),
    Color(0xFF2F6E3A),
    Color(0xFF6B4EAA),
    Color(0xFF8F4C00),
)

/** 色板预览用的主色（外观页的色板圆点/卡片）。 */
internal fun paletteSeedColor(palette: Palette, darkTheme: Boolean): Color = when (palette) {
    Palette.SHIO_BLUE, Palette.DYNAMIC -> if (darkTheme) ShioBlueDark else ShioBlue
    Palette.TEAL -> if (darkTheme) TealDarkScheme.primary else TealLightScheme.primary
    Palette.GREEN -> if (darkTheme) GreenDarkScheme.primary else GreenLightScheme.primary
    Palette.PURPLE -> if (darkTheme) PurpleDarkScheme.primary else PurpleLightScheme.primary
    Palette.ORANGE -> if (darkTheme) OrangeDarkScheme.primary else OrangeLightScheme.primary
}
