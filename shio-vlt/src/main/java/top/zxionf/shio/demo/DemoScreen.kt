package top.zxionf.shio.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.zxionf.shio.kernel.ShioException
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.theme.ShioTheme

/** 界面回调集合，便于 `DemoContent` 在预览中零依赖渲染。 */
@Stable
class DemoActions(
    val onPasswordChange: (String) -> Unit = {},
    val onDraftChange: (String) -> Unit = {},
    val onOpen: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onStats: () -> Unit = {},
    val onBatch: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: DemoViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        DemoActions(
            onPasswordChange = viewModel::onPasswordChange,
            onDraftChange = viewModel::onDraftChange,
            onOpen = viewModel::openOrCreate,
            onClose = viewModel::closeKernel,
            onRefresh = viewModel::refresh,
            onStats = viewModel::showStats,
            onBatch = viewModel::batchWrite,
            onAdd = viewModel::addNote,
            onDeleteSelected = viewModel::deleteSelected,
            onSelect = viewModel::selectNote,
        )
    }
    DemoContent(state = state, actions = actions, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoContent(
    state: DemoUiState,
    actions: DemoActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val error = state.error
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("shio 内核 Demo") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            AppIcon(AppIcons.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DatabaseSection(state, actions) }

            if (state.databasePath.isNotEmpty()) {
                item { DatabaseInfoSection(state) }
            }

            if (error != null) {
                item { ErrorSection(error) }
            }

            item { StatusSection(state) }

            if (state.isKernelOpen) {
                item { OperationSection(state, actions) }
                item { AddSection(state, actions) }
                item { RecordsHeader(state, actions) }
                if (state.notes.isEmpty()) {
                    item {
                        Text(
                            text = "暂无记录，添加一条或批量写入 3 条。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        selected = note.id == state.selectedId,
                        onSelect = actions.onSelect,
                    )
                }
            } else if (state.canReopen) {
                item {
                    Text(
                        text = "输入口令后可重新打开；关闭状态下的操作会返回 SHIO_CLOSED。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseSection(state: DemoUiState, actions: DemoActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.password,
            onValueChange = actions.onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("口令") },
            singleLine = true,
            enabled = !state.busy && !state.isKernelOpen,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { actions.onOpen() }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isKernelOpen) {
                OutlinedButton(
                    onClick = actions.onClose,
                    enabled = !state.busy,
                ) {
                    Text("关闭")
                }
            } else {
                Button(
                    onClick = actions.onOpen,
                    enabled = !state.busy && state.password.isNotEmpty(),
                ) {
                    Text(if (state.canReopen) "重新打开" else "打开 / 创建")
                }
            }
        }
    }
}

@Composable
private fun DatabaseInfoSection(state: DemoUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("数据库", style = MaterialTheme.typography.titleSmall)
            Text(
                text = state.databasePath,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "ABI 版本：${state.abiVersion ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
            )
            state.stats?.let { stats ->
                Text(
                    text = "统计",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ErrorSection(error: ShioError) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "错误 ${codeLabel(error.code)} (${error.code})",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusSection(state: DemoUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = state.status,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OperationSection(state: DemoUiState, actions: DemoActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.onRefresh, enabled = !state.busy) {
                Text("刷新")
            }
            OutlinedButton(onClick = actions.onStats, enabled = !state.busy) {
                Text("统计")
            }
        }
        OutlinedButton(
            onClick = actions.onBatch,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        ) {
            Text("批量写入 3 条")
        }
    }
}

@Composable
private fun AddSection(state: DemoUiState, actions: DemoActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.draft,
            onValueChange = actions.onDraftChange,
            modifier = Modifier.weight(1f),
            label = { Text("新记录正文") },
            singleLine = true,
            enabled = !state.busy,
        )
        Button(
            onClick = actions.onAdd,
            enabled = !state.busy && state.draft.isNotBlank(),
        ) {
            Text("添加")
        }
    }
}

@Composable
private fun RecordsHeader(state: DemoUiState, actions: DemoActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "记录（${state.notes.size}）",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = actions.onDeleteSelected,
            enabled = !state.busy && state.selectedId != null,
        ) {
            Text("删除选中")
        }
    }
}

@Composable
private fun NoteRow(
    note: NoteItem,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    val colors = if (selected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(note.id) },
        colors = colors,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = note.id.take(8),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = note.text.ifBlank { "(空)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Text(
                    text = "已选中",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun codeLabel(code: Int): String = when (code) {
    ShioException.INVALID_ARGUMENT -> "INVALID_ARGUMENT"
    ShioException.INVALID_NAME -> "INVALID_NAME"
    ShioException.INVALID_KEY -> "INVALID_KEY"
    ShioException.NOT_FOUND -> "NOT_FOUND"
    ShioException.CLOSED -> "CLOSED"
    ShioException.READ_ONLY -> "READ_ONLY"
    ShioException.BUSY -> "BUSY"
    ShioException.LOCKED -> "LOCKED"
    ShioException.VALUE_TOO_LARGE -> "VALUE_TOO_LARGE"
    ShioException.CORRUPTED -> "CORRUPTED"
    ShioException.UNSUPPORTED_FORMAT -> "UNSUPPORTED_FORMAT"
    ShioException.SERIALIZE -> "SERIALIZE"
    ShioException.DESERIALIZE -> "DESERIALIZE"
    ShioException.IO -> "IO"
    ShioException.DATABASE -> "DATABASE"
    ShioException.INTERNAL -> "INTERNAL"
    else -> "ERROR"
}

private const val PREVIEW_ID = "01a168ba-327b-7202-a239-e10c138df11a"

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun DemoContentPreview() {
    ShioTheme {
        DemoContent(
            state = DemoUiState(
                password = "demo-password",
                databasePath = "filesDir/shio-demo.db",
                abiVersion = 1,
                stats = "format_version = 1\nkey_id = 5f2c0a9e...\ndb_bytes = 1056768",
                isKernelOpen = true,
                notes = listOf(
                    NoteItem(PREVIEW_ID, "第一条演示记录"),
                    NoteItem("01a168bb-11c2-7a41-9a82-4d8e0c7b5f10", "第二条演示记录"),
                ),
                selectedId = PREVIEW_ID,
                status = "已刷新，共 2 条（最多 50 条）",
            ),
            actions = DemoActions(),
        )
    }
}
