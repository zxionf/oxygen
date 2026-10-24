package top.zxionf.shio.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.zxionf.shio.ui.theme.AppearancePreferences
import top.zxionf.shio.ui.theme.DynamicColorSupport
import top.zxionf.shio.ui.theme.DynamicColorSwatch
import top.zxionf.shio.ui.theme.Palette
import top.zxionf.shio.ui.theme.ShioTheme
import top.zxionf.shio.ui.theme.ThemeMode
import top.zxionf.shio.ui.theme.paletteSeedColor
import top.zxionf.shio.ui.theme.shioColorScheme

/** 外观页的回调集合，便于 `AppearanceContent` 在预览中零依赖渲染。 */
@Stable
class AppearanceActions(
    val onThemeModeChange: (ThemeMode) -> Unit = {},
    val onPaletteChange: (Palette) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appearance = remember(context) { AppearancePreferences.get(context) }
    val actions = remember(appearance) {
        AppearanceActions(
            onThemeModeChange = appearance::updateThemeMode,
            onPaletteChange = appearance::updatePalette,
        )
    }
    AppearanceContent(
        themeMode = appearance.themeMode,
        palette = appearance.palette,
        actions = actions,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceContent(
    themeMode: ThemeMode,
    palette: Palette,
    actions: AppearanceActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    // 以「系统确实提供动态色资源」为准，而不是只看 API 级别。
    val dynamicAvailable = remember(context) { DynamicColorSupport.isAvailable(context) }
    val useDynamic = palette == Palette.DYNAMIC && dynamicAvailable
    val previewSchemes = when {
        !useDynamic -> shioColorScheme(palette, false) to shioColorScheme(palette, true)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicPreviewSchemes(context)
        else -> shioColorScheme(Palette.SHIO_BLUE, false) to shioColorScheme(Palette.SHIO_BLUE, true)
    }
    val previewLight = previewSchemes.first
    val previewDark = previewSchemes.second

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("外观") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VaultPreviewCard()

            SectionTitle("主题")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ThemeModeCard(
                        mode = mode,
                        selected = mode == themeMode,
                        lightScheme = previewLight,
                        darkScheme = previewDark,
                        onClick = { actions.onThemeModeChange(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionTitle("颜色")
            PaletteGrid(
                palette = palette,
                darkTheme = darkTheme,
                dynamicSupported = dynamicAvailable,
                onSelect = actions.onPaletteChange,
            )
            if (!dynamicAvailable) {
                Text(
                    text = "动态取色在当前设备上不可用：需要 Android 12 及以上且系统提供动态色板。当前回退 Shio 蓝，已保存的选择不会被清除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 顶部预览：用迷你列表示意当前配色在密码库页上的样子。 */
@Composable
private fun VaultPreviewCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniBar(width = 72.dp, height = 10.dp, color = colors.onSurface)
                Spacer(Modifier.weight(1f))
                MiniBar(width = 12.dp, height = 12.dp, color = colors.onSurfaceVariant, circle = true)
                Spacer(Modifier.width(8.dp))
                MiniBar(width = 12.dp, height = 12.dp, color = colors.onSurfaceVariant, circle = true)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                shape = RoundedCornerShape(15.dp),
                color = colors.surfaceContainerHighest,
            ) {
                Box(
                    modifier = Modifier.padding(start = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    MiniBar(width = 96.dp, height = 6.dp, color = colors.onSurfaceVariant)
                }
            }
            MiniEntryRow(colors.primary, colors)
            MiniEntryRow(colors.secondary, colors)
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp),
                    shape = CircleShape,
                    color = colors.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniEntryRow(accent: Color, colors: ColorScheme) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = accent.copy(alpha = 0.18f),
        ) {}
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            MiniBar(width = 108.dp, height = 7.dp, color = colors.onSurface)
            MiniBar(width = 72.dp, height = 6.dp, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniBar(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    color: Color,
    circle: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(if (circle) CircleShape else RoundedCornerShape(height / 2))
            .background(color.copy(alpha = 0.55f)),
    )
}

/** 主题模式卡片：用小窗口示意浅色/深色，跟随系统则左右各一半。 */
@Composable
private fun ThemeModeCard(
    mode: ThemeMode,
    selected: Boolean,
    lightScheme: ColorScheme,
    darkScheme: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                when (mode) {
                    ThemeMode.SYSTEM -> Row(modifier = Modifier.fillMaxSize()) {
                        MiniWindow(lightScheme, Modifier.weight(1f).fillMaxHeight())
                        MiniWindow(darkScheme, Modifier.weight(1f).fillMaxHeight())
                    }

                    ThemeMode.LIGHT -> MiniWindow(lightScheme, Modifier.fillMaxSize())
                    ThemeMode.DARK -> MiniWindow(darkScheme, Modifier.fillMaxSize())
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已选择",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/** 迷你窗口：只画主色块与两条中性线，保证在 100dp 宽度下仍可辨认。 */
@Composable
private fun MiniWindow(colors: ColorScheme, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(colors.surface)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.primary),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surfaceVariant),
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
        )
    }
}

/** 色板网格：动态取色 + 5 个预设，两行三列。 */
@Composable
private fun PaletteGrid(
    palette: Palette,
    darkTheme: Boolean,
    dynamicSupported: Boolean,
    onSelect: (Palette) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Palette.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    val enabled = option != Palette.DYNAMIC || dynamicSupported
                    PaletteOption(
                        palette = option,
                        selected = option == palette,
                        darkTheme = darkTheme,
                        enabled = enabled,
                        onClick = { if (enabled) onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteOption(
    palette: Palette,
    selected: Boolean,
    darkTheme: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = shioColorScheme(palette, darkTheme)
    val checkTint = if (palette == Palette.DYNAMIC) Color.White else scheme.onPrimary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (palette == Palette.DYNAMIC) {
                        // 首尾同色，让扫描渐变闭合，不留硬边。
                        Brush.sweepGradient(DynamicColorSwatch + DynamicColorSwatch.first())
                    } else {
                        Brush.linearGradient(
                            listOf(
                                paletteSeedColor(palette, darkTheme),
                                paletteSeedColor(palette, darkTheme),
                            )
                        )
                    }
                )
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = checkTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = palette.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 动态取色预览用的浅/深 scheme；集中在这里处理 API 31 的版本约束。 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun dynamicPreviewSchemes(context: Context): Pair<ColorScheme, ColorScheme> =
    dynamicLightColorScheme(context) to dynamicDarkColorScheme(context)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AppearanceContentPreview() {
    ShioTheme {
        AppearanceContent(
            themeMode = ThemeMode.SYSTEM,
            palette = Palette.SHIO_BLUE,
            actions = AppearanceActions(),
            onBack = {},
        )
    }
}
