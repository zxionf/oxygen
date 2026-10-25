package top.zxionf.shio.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.zxionf.shio.kernel.ShioException
import top.zxionf.shio.vault.VaultComponent
import top.zxionf.shio.vault.VaultEntry
import top.zxionf.shio.vault.VaultRepository

/** 详情页状态。 */
data class EntryDetailUiState(
    val entry: VaultEntry? = null,
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * 详情页状态：按 ID 从仓库读取单条记录，并在每次回到前台时重读
 * （编辑保存后返回、旋转都能看到最新值）。删除走内核 `delete`，成功后把
 * [EntryDetailUiState.deleted] 置真，由页面负责回退。
 */
class EntryDetailViewModel(
    private val repository: VaultRepository = VaultComponent.repository,
) : ViewModel() {

    private val _state = MutableStateFlow(EntryDetailUiState())
    val state: StateFlow<EntryDetailUiState> = _state.asStateFlow()

    private var entryId: String? = null
    private var loadJob: Job? = null

    /** 绑定条目 ID；同一 ID 重复调用不会重复加载。 */
    fun bind(id: String) {
        if (entryId == id) return
        entryId = id
        refresh()
    }

    fun refresh() {
        val id = entryId ?: return
        if (!repository.isUnlocked) {
            _state.value = EntryDetailUiState(
                isLoading = false,
                errorMessage = "密码库未解锁",
            )
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val entry = repository.entry(id)
                _state.value = _state.value.copy(
                    entry = entry,
                    isLoading = false,
                    errorMessage = if (entry == null) null else _state.value.errorMessage,
                )
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = describeFailure(failure),
                )
            }
        }
    }

    /** 删除当前条目；成功后 `deleted = true`。 */
    fun delete() {
        val id = entryId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isDeleting = true)
            try {
                repository.delete(id)
                _state.value = _state.value.copy(isDeleting = false, deleted = true)
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isDeleting = false,
                    errorMessage = describeFailure(failure),
                )
            }
        }
    }

    private fun describeFailure(failure: Throwable): String = when (failure) {
        is ShioException -> when (failure.code) {
            ShioException.NOT_FOUND -> "条目不存在（NOT_FOUND (${failure.code})）"
            ShioException.CLOSED -> "密码库已锁定（CLOSED (${failure.code})）"
            ShioException.CORRUPTED -> "条目数据损坏（CORRUPTED (${failure.code})）"
            else -> "读取失败（错误码 ${failure.code}）"
        }
        else -> failure.message ?: "读取失败"
    }
}
