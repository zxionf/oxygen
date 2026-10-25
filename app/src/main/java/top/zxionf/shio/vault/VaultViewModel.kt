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
import top.zxionf.shio.BuildConfig
import top.zxionf.shio.kernel.ShioException

/** 密码库列表的界面状态。 */
data class VaultUiState(
    val query: String = "",
    val entries: List<VaultEntry> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 库真的为空（已读完且没有任何条目，且没有出错）。 */
    val isEmptyLibrary: Boolean
        get() = !isLoading && errorMessage == null && totalCount == 0

    /** 有数据但当前关键词搜不到。 */
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
 * [refresh] 拉取最新结果，不做增量合并，避免与内核的 meta 不一致。
 *
 * debug 构建在同一次进程里首次刷新且库为空时写入样例条目（见 [SampleVaultEntries]），
 * release 不会带任何样例数据。
 */
class VaultViewModel(
    private val repository: VaultRepository = VaultComponent.repository,
) : ViewModel() {

    private val entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<VaultUiState> = combine(entries, query, loading, error) {
            all, keyword, isLoading, failure ->
        VaultUiState(
            query = keyword,
            entries = filterEntries(all, keyword),
            totalCount = all.size,
            isLoading = isLoading,
            errorMessage = failure,
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
                seedSamplesOnce()
                entries.value = repository.entries()
            } catch (failure: Throwable) {
                error.value = describeFailure(failure)
            } finally {
                loading.value = false
            }
        }
    }

    private suspend fun seedSamplesOnce() {
        if (!BuildConfig.DEBUG || VaultComponent.samplesSeeded) return
        VaultComponent.samplesSeeded = true
        if (repository.entries().isNotEmpty()) return
        SampleVaultEntries.forEach { sample ->
            repository.insert(sample.copy(id = ""))
        }
    }

    private fun filterEntries(all: List<VaultEntry>, keyword: String): List<VaultEntry> {
        val filtered = if (keyword.isBlank()) all else all.filter { it.matches(keyword) }
        return filtered.sortedBy { it.title.lowercase() }
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
