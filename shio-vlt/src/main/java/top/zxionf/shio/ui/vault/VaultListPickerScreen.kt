package top.zxionf.shio.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.AppTextField
import top.zxionf.shio.ui.components.EmptyState
import top.zxionf.shio.ui.components.StackedList
import top.zxionf.shio.vault.AutoLockController
import top.zxionf.shio.vault.VaultComponent
import top.zxionf.shio.vault.VaultLibrary
import top.zxionf.shio.vault.VaultLibraryEntry

/**
 * 密码库列表（多库选择页）。
 *
 * 不读取库内容，因此不需要解锁；每行的「条目数」需要解锁后才能统计，这里不显示。
 * 行点击进对应库的解锁页；行尾菜单可重命名或删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListPickerScreen(
    onBack: (() -> Unit)?,
    onUnlock: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var libraries by remember { mutableStateOf<List<VaultLibraryEntry>>(emptyList()) }
    var renaming by remember { mutableStateOf<VaultLibraryEntry?>(null) }
    var deleting by remember { mutableStateOf<VaultLibraryEntry?>(null) }
    val lastVaultId = remember(libraries) { VaultLibrary.lastVaultId(context) }

    fun reload() {
        libraries = VaultLibrary.list(context)
    }

    // 每次回到前台都重读索引：从创建页返回时能看到新库。
    LifecycleResumeEffect(Unit) {
        reload()
        onPauseOrDispose {}
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("密码库列表", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            AppIcon(AppIcons.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onCreate) {
                        AppIcon(AppIcons.Add, contentDescription = "新建库")
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
            if (libraries.isEmpty()) {
                EmptyState(
                    icon = AppIcons.Shield.icon,
                    title = "还没有密码库",
                    description = "新建一个库并设置主口令，数据只保存在本机。",
                    actionLabel = "新建库",
                    onAction = onCreate,
                )
            } else {
                StackedList(itemCount = libraries.size) { index, shape ->
                    val library = libraries[index]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        ListItem(
                            modifier = Modifier.clickableRow { onUnlock(library.id) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                AppIcon(
                                    icon = AppIcons.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            content = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = library.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (library.id == lastVaultId) {
                                        Text(
                                            text = "  · 上次打开",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    text = "创建于 ${formatDate(library.createdAt)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                RowMenu(
                                    onRename = { renaming = library },
                                    onDelete = { deleting = library },
                                )
                            },
                        )
                    }
                }

                Button(
                    onClick = onCreate,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppIcon(AppIcons.Add, contentDescription = null, size = 20.dp)
                    Text("  新建库", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }

    renaming?.let { target ->
        RenameVaultDialog(
            initialName = target.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                VaultLibrary.rename(context, target.id, name)
                reload()
                scope.launch { snackbarHostState.showSnackbar("已重命名为「$name」") }
            },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("删除密码库？") },
            text = {
                Text("「${target.name}」内的全部条目将被永久删除，且无法恢复。建议先导出或备份。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch {
                            // 删除当前已解锁的库之前先锁定，避免句柄还持有文件。
                            val session = VaultComponent.repository.session
                            if (session != null && session.databasePath.endsWith(target.fileName)) {
                                AutoLockController.lockNow()
                                delay(200)
                            }
                            VaultLibrary.remove(context, target.id)
                            if (target.id == lastVaultId) VaultLibrary.forgetId(context)
                            reload()
                            snackbarHostState.showSnackbar("已删除「${target.name}」")
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

@Composable
private fun RowMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("重命名") },
                leadingIcon = { AppIcon(AppIcons.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
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
private fun RenameVaultDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("重命名密码库") },
        text = {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "库名",
                helperText = "只改显示名，不影响数据",
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 整行可点的修饰符（避免把 clickable 直接写在 ListItem 上引起语义重复）。 */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.fillMaxWidth().clickable(onClick = onClick)

private fun formatDate(millis: Long): String {
    if (millis <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
}
