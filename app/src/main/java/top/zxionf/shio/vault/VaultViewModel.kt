package top.zxionf.shio.vault

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * M4 只在内存里放假数据；M13 起改为「解锁后 list + 逐条 get」填充 [allEntries]，
 * 搜索仍在内存里做。界面只依赖 [VaultUiState]，换数据源不改 UI。
 */
class VaultViewModel(
    private val source: List<VaultEntry> = SampleVaultEntries,
) : ViewModel() {
    private val allEntries: List<VaultEntry> = source

    private val _state = MutableStateFlow(buildState(query = ""))
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    fun onQueryChange(query: String) {
        if (query == _state.value.query) return
        _state.value = buildState(query)
    }

    fun onClearQuery() = onQueryChange("")

    private fun buildState(query: String): VaultUiState {
        val filtered = if (query.isBlank()) {
            allEntries
        } else {
            allEntries.filter { it.matches(query) }
        }
        return VaultUiState(
            query = query,
            entries = filtered.sortedBy { it.title.lowercase() },
            totalCount = allEntries.size,
        )
    }
}
