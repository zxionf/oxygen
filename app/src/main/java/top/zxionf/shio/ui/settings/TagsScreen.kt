package top.zxionf.shio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.AppTextField
import top.zxionf.shio.ui.components.EmptyState
import top.zxionf.shio.ui.components.StackedList
import top.zxionf.shio.vault.VaultComponent
import top.zxionf.shio.vault.VaultRepository
import top.zxionf.shio.vault.VaultTag
import top.zxionf.shio.vault.VaultTagCodec

/**
 * 标签管理页（M3E）。
 *
 * 顶部输入框新建标签；下方列出全部标签（名称 + 该标签下的条目数），
 * 每行可重命名或删除。删除会连带清理条目上的引用，删除前会提示受影响条目数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    repository: VaultRepository = VaultComponent.repository,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var tags by remember { mutableStateOf<List<VaultTag>>(emptyList()) }
    var usage by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<VaultTag?>(null) }
    var deleting by remember { mutableStateOf<VaultTag?>(null) }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    suspend fun reload() {
        tags = repository.tags()
        usage = repository.tagUsage()
    }

    LaunchedEffect(repository) {
        runCatching { reload() }.onFailure { error = it.message ?: "读取标签失败" }
    }

    fun create() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val tag = repository.insertTag(draft)
                draft = ""
                error = null
                reload()
                notify("已新建标签「${tag.name}」")
            } catch (failure: Throwable) {
                error = failure.message ?: "新建标签失败"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("标签", style = MaterialTheme.typography.titleLarge) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    error = null
                },
                label = "新标签名",
                helperText = "最长 ${VaultTagCodec.MAX_NAME_LENGTH} 个字符，不许重名",
                errorText = error,
                singleLine = true,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { create() }),
            )
            Button(
                onClick = ::create,
                enabled = !busy && draft.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("新建标签", style = MaterialTheme.typography.titleSmall)
            }

            if (tags.isEmpty()) {
                EmptyState(
                    icon = AppIcons.Notes.icon,
                    title = "还没有标签",
                    description = "在上方输入名称新建，之后可在条目里勾选。",
                )
            } else {
                StackedList(itemCount = tags.size) { index, shape ->
                    val tag = tags[index]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                AppIcon(
                                    icon = AppIcons.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            content = {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${usage[tag.id] ?: 0} 条条目",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { renaming = tag }) {
                                        AppIcon(
                                            icon = AppIcons.Edit,
                                            contentDescription = "重命名${tag.name}",
                                            size = 20.dp,
                                        )
                                    }
                                    IconButton(onClick = { deleting = tag }) {
                                        AppIcon(
                                            icon = AppIcons.Delete,
                                            contentDescription = "删除${tag.name}",
                                            size = 20.dp,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { target ->
        RenameTagDialog(
            initialName = target.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                scope.launch {
                    try {
                        repository.renameTag(target.id, name)
                        reload()
                        notify("已重命名为「$name」")
                    } catch (failure: Throwable) {
                        notify(failure.message ?: "重命名失败")
                    }
                }
            },
        )
    }

    deleting?.let { target ->
        val affected = usage[target.id] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("删除标签？") },
            text = {
                Text(
                    if (affected > 0) {
                        "「${target.name}」将从 $affected 条条目上移除，条目本身不会被删除。"
                    } else {
                        "「${target.name}」没有被任何条目使用，删除后不可恢复。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch {
                            try {
                                val cleaned = repository.deleteTag(target.id)
                                reload()
                                notify("已删除标签，清理了 $cleaned 条条目")
                            } catch (failure: Throwable) {
                                notify(failure.message ?: "删除标签失败")
                            }
                        }
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

/** 重命名对话框：单个线框输入框 + 确定。 */
@Composable
private fun RenameTagDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("重命名标签") },
        text = {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "标签名",
                helperText = "最长 ${VaultTagCodec.MAX_NAME_LENGTH} 个字符",
                singleLine = true,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { onConfirm(name) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
