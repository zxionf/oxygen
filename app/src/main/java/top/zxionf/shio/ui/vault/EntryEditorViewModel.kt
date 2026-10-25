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

/** 编辑器状态。 */
data class EditorUiState(
    val initial: VaultEntry? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * 编辑器状态：编辑时按 ID 读入原值，保存时新建走 `insert`、编辑走 `put`。
 *
 * 字段编辑状态留在 Compose（`rememberSaveable`，旋转不丢），这里只管
 * 「读原值 / 写回内核 / 结果标志」三件事。
 */
class EntryEditorViewModel(
    private val repository: VaultRepository = VaultComponent.repository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var newEntry: Boolean = true
    private var loadJob: Job? = null

    /** [entryId] 为 null 表示新建，直接进入可编辑状态。 */
    fun load(entryId: String?) {
        newEntry = entryId == null
        if (entryId == null) {
            _state.value = EditorUiState(isLoading = false)
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = EditorUiState(isLoading = true)
            try {
                val entry = repository.entry(entryId)
                _state.value = EditorUiState(
                    initial = entry,
                    isLoading = false,
                    errorMessage = if (entry == null) "条目不存在（可能已被删除）" else null,
                )
            } catch (failure: Throwable) {
                _state.value = EditorUiState(
                    isLoading = false,
                    errorMessage = describeFailure(failure),
                )
            }
        }
    }

    /** 保存：新建 `insert`、编辑 `put`；成功后 `saved = true`。 */
    fun save(entry: VaultEntry) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            try {
                if (newEntry) {
                    repository.insert(entry)
                } else {
                    repository.update(entry)
                }
                _state.value = _state.value.copy(isSaving = false, saved = true)
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = describeFailure(failure),
                )
            }
        }
    }

    private fun describeFailure(failure: Throwable): String = when (failure) {
        is ShioException -> when (failure.code) {
            ShioException.CLOSED -> "密码库已锁定（CLOSED (${failure.code})）"
            ShioException.VALUE_TOO_LARGE -> "内容过长，无法保存（VALUE_TOO_LARGE (${failure.code})）"
            ShioException.NOT_FOUND -> "条目不存在（NOT_FOUND (${failure.code})）"
            ShioException.CORRUPTED -> "条目数据损坏（CORRUPTED (${failure.code})）"
            else -> "保存失败（错误码 ${failure.code}）"
        }
        else -> failure.message ?: "保存失败"
    }
}
