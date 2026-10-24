package top.zxionf.shio.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.zxionf.shio.ui.theme.ShioTheme
import top.zxionf.shio.vault.SampleVaultEntries

/**
 * 条目详情（M5 只读假数据）。
 *
 * 密码默认隐藏；显隐状态用 `remember` 而非 `rememberSaveable`，离开页面或旋转后
 * 都会复位为隐藏，属于安全优先的取舍。复制在 M5 只是 Snackbar 提示，M14 接真实剪贴板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = remember(entryId) { SampleVaultEntries.firstOrNull { it.id == entryId } }
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
                title = { Text(entry?.title ?: "条目详情") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "条目不存在",
                    style = MaterialTheme.typography.titleMedium,
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FieldRow(
                    label = "用户名",
                    value = entry.username,
                    onCopy = { notifyCopied("用户名") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                FieldRow(
                    label = "密码",
                    value = if (passwordVisible) entry.password else PasswordMask,
                    onCopy = { notifyCopied("密码") },
                    monospace = passwordVisible,
                    hidden = !passwordVisible,
                    trailing = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "隐藏" else "显示")
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (entry.url.isNotEmpty()) {
                    FieldRow(
                        label = "网址",
                        value = entry.url,
                        onCopy = { notifyCopied("网址") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                FieldRow(
                    label = "备注",
                    value = entry.notes,
                    onCopy = { notifyCopied("备注") },
                    singleLine = false,
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

@Composable
private fun MoreMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "更多")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    hidden: Boolean = false,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val empty = value.isEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (empty) "—" else value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = when {
                    empty -> MaterialTheme.colorScheme.outline
                    hidden -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
            TextButton(onClick = onCopy, enabled = !empty) {
                Text("复制")
            }
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
private fun EntryDetailPreview() {
    val entry = SampleVaultEntries.first()
    ShioTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("预览：" + entry.title, style = MaterialTheme.typography.titleMedium)
            FieldRow(label = "用户名", value = entry.username, onCopy = {})
            HorizontalDivider()
            FieldRow(
                label = "密码",
                value = "••••••••",
                onCopy = {},
                hidden = true,
                trailing = { TextButton(onClick = {}) { Text("显示") } },
            )
            HorizontalDivider()
            FieldRow(label = "备注", value = entry.notes, onCopy = {}, singleLine = false)
        }
    }
}
