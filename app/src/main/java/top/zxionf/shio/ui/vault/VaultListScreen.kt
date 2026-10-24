package top.zxionf.shio.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.zxionf.shio.ui.theme.ShioTheme
import top.zxionf.shio.vault.VaultEntry
import top.zxionf.shio.vault.VaultUiState
import top.zxionf.shio.vault.VaultViewModel

/** 列表页回调集合，便于 `VaultListContent` 在预览中零依赖渲染。 */
@Stable
class VaultListActions(
    val onQueryChange: (String) -> Unit = {},
    val onClearQuery: () -> Unit = {},
    val onLock: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onCreateEntry: () -> Unit = {},
    val onOpenEntry: (String) -> Unit = {},
)

@Composable
fun VaultListScreen(
    onLock: () -> Unit,
    onSettings: () -> Unit,
    onCreateEntry: () -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        VaultListActions(
            onQueryChange = viewModel::onQueryChange,
            onClearQuery = viewModel::onClearQuery,
            onLock = onLock,
            onSettings = onSettings,
            onCreateEntry = onCreateEntry,
            onOpenEntry = onOpenEntry,
        )
    }
    VaultListContent(
        state = state,
        actions = actions,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultListContent(
    state: VaultUiState,
    actions: VaultListActions,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("密码库") },
                actions = {
                    IconButton(onClick = actions.onLock) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "锁定",
                        )
                    }
                    IconButton(onClick = actions.onSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = actions.onCreateEntry) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "新建条目")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索标题、用户名、网址、备注") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = actions.onClearQuery) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "清空搜索",
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )

            when {
                state.isEmptyLibrary -> EmptyState(
                    icon = Icons.Filled.Lock,
                    title = "密码库还是空的",
                    description = "点右下角的加号，新建第一个条目。",
                )

                state.isEmptySearch -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "没有匹配的条目",
                    description = "换个关键词试试，或清空搜索。",
                    actionLabel = "清空搜索",
                    onAction = actions.onClearQuery,
                )

                else -> EntryList(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun EntryList(
    state: VaultUiState,
    actions: VaultListActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(state.entries, key = { it.id }) { entry ->
            EntryRow(entry = entry, onClick = { actions.onOpenEntry(entry.id) })
        }
    }
}

@Composable
private fun EntryRow(
    entry: VaultEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = entry.initial,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.subtitle.isNotEmpty()) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
                Text(actionLabel)
            }
        }
    }
}

private val PreviewEntries = listOf(
    VaultEntry(
        id = "01a168ba-327b-7202-a239-e10c138df11a",
        title = "GitHub",
        username = "zxionf@example.com",
        url = "https://github.com",
    ),
    VaultEntry(
        id = "01a168bb-11c2-7a41-9a82-4d8e0c7b5f10",
        title = "银行 App",
        username = "6222••••••••1234",
    ),
    VaultEntry(
        id = "01a168be-4b52-7e33-8d92-1c3f4e5a6b70",
        title = "Netflix",
        url = "https://www.netflix.com",
    ),
)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun VaultListPreview() {
    ShioTheme {
        VaultListContent(
            state = VaultUiState(
                entries = PreviewEntries,
                totalCount = PreviewEntries.size,
            ),
            actions = VaultListActions(),
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun VaultEmptyPreview() {
    ShioTheme {
        VaultListContent(
            state = VaultUiState(),
            actions = VaultListActions(),
        )
    }
}
