package top.zxionf.shio.ui.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.zxionf.shio.ui.components.EmptyState
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.components.StackedList
import top.zxionf.shio.ui.components.rememberPressScale
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
    val onRefresh: () -> Unit = {},
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
            onRefresh = viewModel::refresh,
        )
    }
    // 从编辑器/详情返回、或旋转后重新可见时拉一次整库，保证写入后能看到最新结果。
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    VaultListContent(state = state, actions = actions, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VaultListContent(
    state: VaultUiState,
    actions: VaultListActions,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fabCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
        }
    }

    // LazyColumn 用记录 ID 做 key，新条目插到开头时会把旧的首项当锚点，导致新条目被挤到
    // 搜索框上方。列表本来就在顶部（前两项内）时滚回顶，让刚保存的条目完整可见；
    // 用户已经往下翻时不打扰。
    var previousEntryCount by remember { mutableIntStateOf(state.entries.size) }
    LaunchedEffect(state.entries.size) {
        val count = state.entries.size
        if (count > previousEntryCount && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
        previousEntryCount = count
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text("密码库", style = MaterialTheme.typography.titleLarge)
                },
                actions = {
                    IconButton(onClick = actions.onLock) {
                        AppIcon(AppIcons.Lock, contentDescription = "锁定")
                    }
                    IconButton(onClick = actions.onSettings) {
                        AppIcon(AppIcons.Settings, contentDescription = "设置")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            AnimatedContent(
                targetState = fabCollapsed,
                label = "fabCollapse",
            ) { collapsed ->
                if (collapsed) {
                    FloatingActionButton(onClick = actions.onCreateEntry) {
                        AppIcon(AppIcons.Add, contentDescription = "新建条目")
                    }
                } else {
                    ExtendedFloatingActionButton(
                        onClick = actions.onCreateEntry,
                        icon = { AppIcon(AppIcons.Add, contentDescription = null) },
                        text = { Text("新建条目") },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchField(
                query = state.query,
                onQueryChange = actions.onQueryChange,
                onClear = actions.onClearQuery,
                onSearch = { focusManager.clearFocus() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                // 首次加载：整页只有加载指示器，避免先闪一下「空库」空态。
                state.isLoading && state.entries.isEmpty() && state.totalCount == 0 -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }

                state.hasError -> EmptyState(
                    icon = AppIcons.Info.icon,
                    title = "读取失败",
                    description = state.errorMessage.orEmpty(),
                    actionLabel = "重试",
                    onAction = actions.onRefresh,
                )

                state.isEmptyLibrary -> EmptyState(
                    icon = AppIcons.Shield.icon,
                    title = "密码库还是空的",
                    description = "新建第一个条目，数据只保存在本机。",
                    actionLabel = "新建条目",
                    onAction = actions.onCreateEntry,
                )

                state.isEmptySearch -> EmptyState(
                    icon = AppIcons.Search.icon,
                    title = "没有匹配的条目",
                    description = "换个关键词试试，或清空搜索。",
                    actionLabel = "清空搜索",
                    onAction = actions.onClearQuery,
                )

                else -> EntryList(
                    state = state,
                    actions = actions,
                    listState = listState,
                )
            }
        }
    }
}

/** M3E 搜索栏：56dp 胶囊、`surfaceContainerHigh` 底、前置搜索图标、有输入时显示清空。 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                icon = AppIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索标题、用户名、连接、备注",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    AppIcon(AppIcons.Close, contentDescription = "清空搜索")
                }
            }
        }
    }
}

@Composable
private fun EntryList(
    state: VaultUiState,
    actions: VaultListActions,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(state.entries, key = { it.id }) { entry ->
            val index = state.entries.indexOf(entry)
            val shape = when {
                state.entries.size == 1 -> top.zxionf.shio.ui.theme.ListStackShapes.single
                index == 0 -> top.zxionf.shio.ui.theme.ListStackShapes.first
                index == state.entries.lastIndex -> top.zxionf.shio.ui.theme.ListStackShapes.last
                else -> top.zxionf.shio.ui.theme.ListStackShapes.middle
            }
            EntryRow(
                entry = entry,
                shape = shape,
                onClick = { actions.onOpenEntry(entry.id) },
            )
        }
    }
}

/** 72dp 列表项：40dp 圆形首字母、`bodyLarge` 标题、`bodyMedium` 副标题、trailing 箭头。 */
@Composable
private fun EntryRow(
    entry: VaultEntry,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interactionSource)

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(Modifier.scale(scale)),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
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
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.subtitle.isNotEmpty()) {
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AppIcon(
                icon = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
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
            state = VaultUiState(entries = PreviewEntries, totalCount = PreviewEntries.size),
            actions = VaultListActions(),
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun VaultEmptyPreview() {
    ShioTheme {
        VaultListContent(state = VaultUiState(), actions = VaultListActions())
    }
}
