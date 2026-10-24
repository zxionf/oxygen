package top.zxionf.shio.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import top.zxionf.shio.ui.icons.ShioIcons
import top.zxionf.shio.ui.theme.ShioTheme
import top.zxionf.shio.vault.FakeVaultStore
import top.zxionf.shio.vault.SampleVaultEntries

/**
 * 条目详情（M5 只读假数据）。
 *
 * 按 M3 的分组列表（grouped list）组织：字段放在 `surfaceContainerLow` 圆角容器里，
 * 每行是 `ListItem`（overline = 字段名，headline = 值，trailing = 图标按钮），
 * 备注作为独立区块。密码默认隐藏，显隐状态用 `remember`，离开页面或旋转即复位。
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = entry?.title ?: "条目详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (entry != null) {
                        IconButton(onClick = { onEdit(entry.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "编辑",
                            )
                        }
                        MoreMenu(onDelete = { deleteDialogVisible = true })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                FieldGroup(
                    username = entry.username,
                    password = entry.password,
                    url = entry.url,
                    passwordVisible = passwordVisible,
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    onCopy = { label -> notifyCopied(label) },
                )
                NotesSection(
                    notes = entry.notes,
                    onCopy = { notifyCopied("备注") },
                )
            }
        }
    }

    if (deleteDialogVisible && entry != null) {
        DeleteConfirmDialog(
            title = entry.title,
            onConfirm = {
                deleteDialogVisible = false
                // M5 只是假数据，不做实际删除；M14 接内核 delete 后再真正移除。
                onBack()
            },
            onDismiss = { deleteDialogVisible = false },
        )
    }
}

/** 固定 8 个点：不随真实密码长度变化，避免泄漏长度信息。 */
private const val PasswordMask = "••••••••"

/** 空值占位，避免出现空白行。 */
private const val EmptyValue = "未设置"

@Composable
private fun FieldGroup(
    username: String,
    password: String,
    url: String,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            DetailField(
                label = "用户名",
                value = username,
                onCopy = { onCopy("用户名") },
            )
            FieldDivider()
            DetailField(
                label = "密码",
                value = if (passwordVisible) password else PasswordMask,
                monospace = passwordVisible,
                dimmed = !passwordVisible,
                onCopy = { onCopy("密码") },
                trailing = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                ShioIcons.VisibilityOff
                            } else {
                                ShioIcons.Visibility
                            },
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )
            if (url.isNotEmpty()) {
                FieldDivider()
                DetailField(
                    label = "网址",
                    value = url,
                    onCopy = { onCopy("网址") },
                )
            }
        }
    }
}

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
        headlineContent = {
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
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                IconButton(onClick = onCopy, enabled = !empty) {
                    Icon(
                        imageVector = ShioIcons.Copy,
                        contentDescription = "复制$label",
                    )
                }
            }
        },
    )
}

@Composable
private fun FieldDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
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
            shape = MaterialTheme.shapes.large,
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
                        .padding(top = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onCopy, enabled = notes.isNotEmpty()) {
                    Icon(
                        imageVector = ShioIcons.Copy,
                        contentDescription = "复制备注",
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "更多")
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
                    Icon(
                        imageVector = Icons.Filled.Delete,
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
        Text(text = "条目不存在", style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun DeleteConfirmDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除条目？") },
        text = { Text("「$title」将被删除，此操作不可撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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
            FieldGroup(
                username = entry.username,
                password = entry.password,
                url = entry.url,
                passwordVisible = false,
                onTogglePassword = {},
                onCopy = {},
            )
            NotesSection(notes = entry.notes, onCopy = {})
        }
    }
}
