package top.zxionf.shio.ui.vault

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.StackedList
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.theme.ShioTheme
import top.zxionf.shio.vault.SampleVaultEntries
import top.zxionf.shio.vault.SensitiveClipboard
import top.zxionf.shio.vault.VaultTag
import top.zxionf.shio.vault.VaultUrl

/**
 * 条目详情（M3E）。
 *
 * 字段放在一张 `extraLarge`（28dp）圆角卡里，行用 M3 ListItem（overline=字段名、
 * content=值、trailing=复制/显隐），备注作为独立区块。密码默认隐藏，显隐用 ToggleButton，
 * 状态用 `remember`：离开页面或旋转即复位为隐藏（安全优先）。
 *
 * 数据：按 ID 从 [VaultComponent.repository] 读取（`vault/entries`）；复制到系统剪贴板时
 * API 33+ 标记 `EXTRA_IS_SENSITIVE`；只有 `http/https` 的网址才能在外部打开；删除真实
 * 调用内核 `delete`，成功后回退，列表靠 resume 刷新。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntryDetailViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entry = state.entry
    var allTags by remember { mutableStateOf<List<VaultTag>>(emptyList()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    var imeClipboardNoticeVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entryId) { viewModel.bind(entryId) }
    // 标签在条目变化时重读：新建标签后返回详情能看到新名称。
    LaunchedEffect(state.entry?.id) { allTags = viewModel.allTags() }
    // 从编辑器返回、旋转、回前台都重读一次，保证看到最新值。
    LifecycleResumeEffect(entryId) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    fun copyToClipboard(label: String, value: String) {
        if (value.isEmpty()) return
        // 统一走 SensitiveClipboard：打敏感标记，30 秒后自动清除（锁定时立即清）。
        SensitiveClipboard.copy(context, label, value)
        // 输入法的剪贴板历史不归应用管，首次复制时提醒一次。
        if (SensitiveClipboard.consumeImeNotice(context)) imeClipboardNoticeVisible = true
        scope.launch { snackbarHostState.showSnackbar("已复制$label，30 秒后自动清除") }
    }

    fun openUrl(url: String) {
        if (!VaultUrl.isHttpUrl(url)) {
            scope.launch { snackbarHostState.showSnackbar("只支持 http/https 链接") }
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbarHostState.showSnackbar("没有可打开链接的应用") }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = entry?.title ?: "条目详情",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (entry != null) {
                        IconToggleButton(
                            checked = entry.favorite,
                            onCheckedChange = { viewModel.toggleFavorite() },
                        ) {
                            AppIcon(
                                icon = if (entry.favorite) AppIcons.Star else AppIcons.StarBorder,
                                contentDescription = if (entry.favorite) "取消收藏" else "收藏",
                            )
                        }
                        IconButton(onClick = { onEdit(entry.id) }) {
                            AppIcon(AppIcons.Edit, contentDescription = "编辑")
                        }
                        MoreMenu(onDelete = { deleteDialogVisible = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading && entry == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }

            entry == null -> NotFound(onBack = onBack, modifier = Modifier.padding(innerPadding))

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StackedList(itemCount = if (entry.url.isNotEmpty()) 3 else 2) { index, shape ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        when (index) {
                            0 -> DetailField(
                                label = "用户名",
                                value = entry.username,
                                onCopy = { copyToClipboard("用户名", entry.username) },
                            )

                            1 -> DetailField(
                                label = "密码",
                                value = if (passwordVisible) entry.password else PasswordMask,
                                monospace = passwordVisible,
                                dimmed = !passwordVisible,
                                onCopy = { copyToClipboard("密码", entry.password) },
                                trailing = {
                                    IconToggleButton(
                                        checked = passwordVisible,
                                        onCheckedChange = { passwordVisible = it },
                                    ) {
                                        AppIcon(
                                            icon = if (passwordVisible) {
                                                AppIcons.VisibilityOff
                                            } else {
                                                AppIcons.Visibility
                                            },
                                            contentDescription = if (passwordVisible) {
                                                "隐藏密码"
                                            } else {
                                                "显示密码"
                                            },
                                        )
                                    }
                                },
                            )

                            else -> DetailField(
                                label = "连接",
                                value = entry.url,
                                onCopy = { copyToClipboard("连接", entry.url) },
                                trailing = if (VaultUrl.isHttpUrl(entry.url)) {
                                    {
                                        IconButton(onClick = { openUrl(entry.url) }) {
                                            AppIcon(
                                                icon = AppIcons.OpenInNew,
                                                contentDescription = "在浏览器打开",
                                                size = 20.dp,
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }

                NotesSection(
                    notes = entry.notes,
                    onCopy = { copyToClipboard("备注", entry.notes) },
                )

                // 标签按条目里的 tagIds 解析；找不到的名称（标签被删）自动忽略。
                val entryTags = entry.tagIds.mapNotNull { id ->
                    allTags.firstOrNull { it.id == id }
                }
                if (entryTags.isNotEmpty()) {
                    TagsSection(tags = entryTags)
                }
            }
        }
    }

    if (imeClipboardNoticeVisible) {
        ImeClipboardNoticeDialog(
            onDismiss = { imeClipboardNoticeVisible = false },
            onOpenImeSettings = {
                imeClipboardNoticeVisible = false
                openInputMethodSettings(context)
            },
        )
    }

    if (deleteDialogVisible && entry != null) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("删除条目？") },
            text = { Text("「${entry.title}」将被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogVisible = false
                        viewModel.delete()
                    },
                    enabled = !state.isDeleting,
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 首次复制后的提示：系统剪贴板已被我们定时清理，但输入法自带的「剪贴板历史」会另存一份，
 * 应用拿不到也删不掉，只能提醒用户自己去输入法设置里关闭。
 */
@Composable
private fun ImeClipboardNoticeDialog(
    onDismiss: () -> Unit,
    onOpenImeSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("输入法可能保留一份副本") },
        text = {
            Text(
                text = "系统剪贴板里的内容会在 30 秒后（或锁定时）自动清除。" +
                    "但输入法自带的「剪贴板」功能会另存一份历史，应用无法删除。" +
                    "如不放心，可以在输入法设置里关闭「剪贴板历史」并清空已有记录。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        dismissButton = {
            TextButton(onClick = onOpenImeSettings) { Text("打开输入法设置") }
        },
    )
}

/** 打开系统的输入法设置页；没有对应 Activity 时静默失败。 */
private fun openInputMethodSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** 固定 8 个点：不随真实密码长度变化，避免泄漏长度信息。 */
private const val PasswordMask = "••••••••"

/** 空值占位，避免出现空白行。 */
private const val EmptyValue = "未设置"

/**
 * 详情字段行：`ListItem` 的 overline=字段名、content=值、trailing=操作。
 *
 * 值使用 `bodyLarge`；空值显示「未设置」并禁用复制；密码用等宽字体。
 */
@Composable
private fun DetailField(
    label: String,
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val empty = value.isEmpty()
    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        overlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                IconButton(onClick = onCopy, enabled = !empty) {
                    AppIcon(
                        icon = AppIcons.Copy,
                        contentDescription = "复制$label",
                        size = 20.dp,
                    )
                }
            }
        },
        content = {
            Text(
                text = if (empty) EmptyValue else value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = when {
                    empty -> MaterialTheme.colorScheme.outline
                    dimmed -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        },
    )
}

@Composable
private fun NotesSection(
    notes: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "备注",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = notes.ifEmpty { EmptyValue },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notes.isEmpty()) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onCopy, enabled = notes.isNotEmpty()) {
                    AppIcon(
                        icon = AppIcons.Copy,
                        contentDescription = "复制备注",
                        size = 20.dp,
                    )
                }
            }
        }
    }
}

/** 标签区块：横向滚动的 `AssistChip`，条目无标签时整个区块不出现。 */
@Composable
private fun TagsSection(
    tags: List<VaultTag>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "标签",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                AssistChip(
                    onClick = {},
                    label = { Text(tag.name) },
                    leadingIcon = {
                        AppIcon(
                            icon = AppIcons.Notes,
                            contentDescription = null,
                            size = 16.dp,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MoreMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, contentDescription = "更多")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "删除",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    AppIcon(
                        icon = AppIcons.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun NotFound(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(
            icon = AppIcons.Info,
            contentDescription = null,
            size = 48.dp,
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "条目不存在",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "这条记录可能已被删除。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("返回列表")
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun DetailPreview() {
    val entry = SampleVaultEntries.first()
    ShioTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StackedList(itemCount = 3) { index, shape ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    when (index) {
                        0 -> DetailField(label = "用户名", value = entry.username, onCopy = {})
                        1 -> DetailField(
                            label = "密码",
                            value = PasswordMask,
                            onCopy = {},
                            dimmed = true,
                            trailing = {
                                IconToggleButton(
                                    checked = false,
                                    onCheckedChange = {},
                                ) {
                                    AppIcon(
                                        icon = AppIcons.Visibility,
                                        contentDescription = "显示密码",
                                    )
                                }
                            },
                        )

                        else -> DetailField(label = "连接", value = entry.url, onCopy = {})
                    }
                }
            }
            NotesSection(notes = entry.notes, onCopy = {})
        }
    }
}

