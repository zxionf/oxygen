package top.zxionf.shio.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.zxionf.shio.kernel.ShioException

/** 列表筛选状态：收藏 + 标签集合 + 可用标签（用于渲染 chips）。 */
data class VaultFilterState(
    val onlyFavorite: Boolean = false,
    val selectedTagIds: Set<String> = emptySet(),
    val allTags: List<VaultTag> = emptyList(),
) {
    val isActive: Boolean
        get() = onlyFavorite || selectedTagIds.isNotEmpty()
}

/** 密码库列表的界面状态。 */
data class VaultUiState(
    val query: String = "",
    val entries: List<VaultEntry> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val filters: VaultFilterState = VaultFilterState(),
) {
    /** 库真的为空（已读完且没有任何条目，且没有出错）。 */
    val isEmptyLibrary: Boolean
        get() = !isLoading && errorMessage == null && totalCount == 0

    /** 有数据但当前关键词/筛选无命中。 */
    val isEmptySearch: Boolean
        get() = !isLoading && errorMessage == null && totalCount > 0 && entries.isEmpty()

    val hasError: Boolean
        get() = errorMessage != null
}

/**
 * 密码库列表状态（真实数据）。
 *
 * 数据来自 [repository]（`vault/entries`）：解锁后 `list` + 逐条 `get` 载入内存，
 * 搜索与按标题排序都在内存里做。写入（新建/编辑/删除）由列表页 resume 时的
 * [refresh] 拉取最新结果，不做增量合并，避免与内核 meta 不一致。
 *
 * 不再向真实库播种样例数据（C22 起改由导入功能提供样例，见
 * `.design/changes/20261027-import-export/design.md`）。
 */
class VaultViewModel(
    private val repository: VaultRepository = VaultComponent.repository,
) : ViewModel() {

    private val entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val filters = MutableStateFlow(VaultFilterState())

    val state: StateFlow<VaultUiState> = combine(entries, query, loading, error, filters) {
            all, keyword, isLoading, failure, filterState ->
        VaultUiState(
            query = keyword,
            entries = filterEntries(all, keyword, filterState),
            totalCount = all.size,
            isLoading = isLoading,
            errorMessage = failure,
            filters = filterState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultUiState(isLoading = true),
    )

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun onQueryChange(keyword: String) {
        query.value = keyword
    }

    fun onClearQuery() = onQueryChange("")

    /** 切换「只看收藏」筛选。 */
    fun toggleFavoriteFilter() {
        filters.value = filters.value.copy(onlyFavorite = !filters.value.onlyFavorite)
    }

    /** 切换单个标签筛选；多个标签之间是「同时包含」关系。 */
    fun toggleTagFilter(tagId: String) {
        val current = filters.value.selectedTagIds
        val next = if (tagId in current) current - tagId else current + tagId
        filters.value = filters.value.copy(selectedTagIds = next)
    }

    /** 清空全部筛选（关键词不受影响）。 */
    fun clearFilters() {
        filters.value = filters.value.copy(onlyFavorite = false, selectedTagIds = emptySet())
    }

    /** 重新读取整库；列表页每次回到前台都调一次，保证写入后可见。 */
    fun refresh() {
        if (!repository.isUnlocked) {
            loading.value = false
            error.value = "密码库未解锁"
            return
        }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                entries.value = repository.entries()
                // 标签与条目同读：标签被删后，筛选里残留的 ID 要一并清掉，
                // 否则会出现「筛选着不存在的标签、列表恒为空」且看不到任何 chip 的状态。
                val tags = runCatching { repository.tags() }.getOrDefault(emptyList())
                val validIds = tags.map { it.id }.toSet()
                val current = filters.value
                filters.value = current.copy(
                    allTags = tags,
                    selectedTagIds = current.selectedTagIds intersect validIds,
                )
            } catch (failure: Throwable) {
                error.value = describeFailure(failure)
            } finally {
                loading.value = false
            }
        }
    }

    private fun filterEntries(
        all: List<VaultEntry>,
        keyword: String,
        filters: VaultFilterState,
    ): List<VaultEntry> {
        var list = all
        if (keyword.isNotBlank()) list = list.filter { it.matches(keyword) }
        if (filters.onlyFavorite) list = list.filter { it.favorite }
        if (filters.selectedTagIds.isNotEmpty()) {
            list = list.filter { entry -> filters.selectedTagIds.all { it in entry.tagIds } }
        }
        return list.sortedBy { it.title.lowercase() }
    }

    private fun describeFailure(failure: Throwable): String = when (failure) {
        is ShioException -> when (failure.code) {
            ShioException.CLOSED -> "密码库已锁定（CLOSED (${failure.code})）"
            ShioException.CORRUPTED -> "密码库文件损坏（CORRUPTED (${failure.code})）"
            ShioException.LOCKED -> "密码库被其它进程占用（LOCKED (${failure.code})）"
            else -> "读取失败（错误码 ${failure.code}）"
        }
        else -> failure.message ?: "读取失败"
    }
}
