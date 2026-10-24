package top.zxionf.shio.ui.vault

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.StackedList
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.theme.ShioTheme
import top.zxionf.shio.vault.FakeVaultStore
import top.zxionf.shio.vault.SampleVaultEntries

/**
 * 条目详情（M3E）。
 *
 * 字段放在一张 `extraLarge`（28dp）圆角卡里，行用 M3 ListItem（overline=字段名、
 * content=值、trailing=复制/显隐），备注作为独立区块。密码默认隐藏，显隐用 ToggleButton，
 * 状态用 `remember`：离开页面或旋转即复位为隐藏（安全优先）。
 *
 * 复制在 M5 只弹 Snackbar；M14 接真实剪贴板并加敏感标记。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allEntries by FakeVaultStore.entries.collectAsStateWithLifecycle()
    val entry = remember(entryId, allEntries) { allEntries.firstOrNull { it.id == entryId } }
    var passwordVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notifyCopied(label: String) {
        scope.launch { snackbarHostState.showSnackbar("已复制$label") }
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
        if (entry == null) {
            NotFound(onBack = onBack, modifier = Modifier.padding(innerPadding))
        } else {
            Column(
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
                                onCopy = { notifyCopied("用户名") },
                            )

                            1 -> DetailField(
                                label = "密码",
                                value = if (passwordVisible) entry.password else PasswordMask,
                                monospace = passwordVisible,
                                dimmed = !passwordVisible,
                                onCopy = { notifyCopied("密码") },
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
                                onCopy = { notifyCopied("连接") },
                            )
                        }
                    }
                }

                NotesSection(
                    notes = entry.notes,
                    onCopy = { notifyCopied("备注") },
                )
            }
        }
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
                        // M5 只是假数据，不做实际删除；M14 接内核 delete 后再真正移除。
                        onBack()
                    },
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

