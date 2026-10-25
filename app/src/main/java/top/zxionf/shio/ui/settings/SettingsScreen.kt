package top.zxionf.shio.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.zxionf.shio.BuildConfig
import top.zxionf.shio.kernel.KernelInfo
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.StackedList
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.theme.ShioTheme

/** 自动锁定时长（M16/M17 会写入偏好并生效）。 */
private val AutoLockOptions = listOf("立即", "1 分钟", "5 分钟", "15 分钟")

/**
 * 设置页（M3E）。
 *
 * 分组列表：自动锁定（单选对话框）、外观、原型 Demo、关于。
 * 自动锁定目前只在页面内保存选择；持久化与真正生效在 M16/M17。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var autoLockIndex by rememberSaveable { mutableIntStateOf(0) }
    var autoLockDialogVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val databasePath = remember(context) { context.filesDir.resolve("vault.db").absolutePath }

    // 关于：应用版本、内核版本与 ABI、运行环境、数据库路径。
    // 内核版本/ABI 版本来自运行时 shio_version()/shio_abi_version()；提交号只是产物溯源。
    val aboutItems = remember {
        val appVersion = "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"
        val kernelVersion = KernelInfo.kernelVersion ?: "不可用"
        val abiVersion = KernelInfo.abiVersion?.let { running ->
            if (KernelInfo.abiVersionMatches) {
                "$running"
            } else {
                "$running（期望 ${KernelInfo.expectedAbiVersion}）"
            }
        } ?: "不可用"
        val kernelCommit = "${KernelInfo.KERNEL_NAME} @ " +
            if (BuildConfig.DEBUG) KernelInfo.kernelCommit else KernelInfo.shortCommit
        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "未知"
        val system = "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）"
        listOf(
            "版本" to appVersion,
            "内核版本" to kernelVersion,
            "ABI 版本" to abiVersion,
            "内核提交" to kernelCommit,
            "设备架构" to deviceAbi,
            "系统" to system,
            "数据库" to databasePath,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("安全")
            StackedList(itemCount = 1) { _, shape ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    SettingRow(
                        icon = AppIcons.Lock,
                        label = "自动锁定",
                        value = AutoLockOptions[autoLockIndex],
                        onClick = { autoLockDialogVisible = true },
                    )
                }
            }

            SectionTitle("外观与体验")
            StackedList(itemCount = 2) { index, shape ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    if (index == 0) {
                        SettingRow(
                            icon = AppIcons.Palette,
                            label = "外观",
                            value = "主题与配色",
                            onClick = onAppearance,
                        )
                    } else {
                        SettingRow(
                            icon = AppIcons.AutoAwesome,
                            label = "原型 Demo",
                            value = "内核能力演示",
                            onClick = onDemo,
                        )
                    }
                }
            }

            SectionTitle("关于")
            StackedList(itemCount = aboutItems.size) { index, shape ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    val (label, value) = aboutItems[index]
                    AboutRow(label = label, value = value)
                }
            }
        }
    }

    if (autoLockDialogVisible) {
        AlertDialog(
            onDismissRequest = { autoLockDialogVisible = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("自动锁定") },
            text = {
                Column {
                    AutoLockOptions.forEachIndexed { index, option ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                RadioButton(
                                    selected = index == autoLockIndex,
                                    onClick = {
                                        autoLockIndex = index
                                        autoLockDialogVisible = false
                                    },
                                )
                            },
                            content = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { autoLockDialogVisible = false }) { Text("完成") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: AppIcons,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            AppIcon(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        overlineContent = null,
        supportingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            AppIcon(
                icon = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        },
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

@Composable
private fun AboutRow(label: String, value: String) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SettingsPreview() {
    ShioTheme {
        SettingsScreen(onBack = {}, onAppearance = {}, onDemo = {})
    }
}
