package top.zxionf.shio.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 密码库列表的界面状态。 */
data class VaultUiState(
    val query: String = "",
    val entries: List<VaultEntry> = emptyList(),
    val totalCount: Int = 0,
) {
    /** 库为空（未解锁真实数据前，只有假数据为空时才会出现）。 */
    val isEmptyLibrary: Boolean get() = totalCount == 0

    /** 有数据但搜索无结果。 */
    val isEmptySearch: Boolean get() = totalCount > 0 && entries.isEmpty()
}

/**
 * 密码库列表状态。
 *
 * 数据来自 [source]（M6 为 [FakeVaultStore]，M13 换成解锁后的内核列表）；
 * 界面只依赖 [VaultUiState]，换数据源不改 UI。搜索与排序都在内存里做。
 */
class VaultViewModel(
    private val source: StateFlow<List<VaultEntry>> = FakeVaultStore.entries,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<VaultUiState> = combine(source, query) { entries, keyword ->
        buildState(entries, keyword)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildState(source.value, ""),
    )

    fun onQueryChange(keyword: String) {
        query.value = keyword
    }

    fun onClearQuery() = onQueryChange("")

    private fun buildState(entries: List<VaultEntry>, keyword: String): VaultUiState {
        val filtered = if (keyword.isBlank()) entries else entries.filter { it.matches(keyword) }
        return VaultUiState(
            query = keyword,
            entries = filtered.sortedBy { it.title.lowercase() },
            totalCount = entries.size,
        )
    }
}
