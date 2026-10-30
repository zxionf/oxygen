package top.zxionf.shio.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import top.zxionf.shio.kernel.ShioDatabase
import top.zxionf.shio.kernel.ShioException
import top.zxionf.shio.kernel.UuidCodec
import java.io.File
import java.util.concurrent.ThreadLocalRandom

/** 列表里的一条记录：ID 为完整 UUID，正文来自逐条 `get`。 */
data class NoteItem(val id: String, val text: String)

/** 内核错误在界面上的展示形态。 */
data class ShioError(val code: Int, val message: String)

data class DemoUiState(
    val password: String = "",
    val draft: String = "",
    val databasePath: String = "",
    val abiVersion: Int? = null,
    val stats: String? = null,
    val isKernelOpen: Boolean = false,
    val notes: List<NoteItem> = emptyList(),
    val selectedId: String? = null,
    val busy: Boolean = false,
    val status: String = "输入口令后打开或创建数据库",
    val error: ShioError? = null,
) {
    /** 曾经打开过且当前已关闭时，可以复用同一路径与口令重新打开。 */
    val canReopen: Boolean
        get() = !isKernelOpen && databasePath.isNotEmpty()
}

/**
 * Demo 的状态与操作。
 *
 * 所有数据库调用都是 suspend 函数，内部切到单线程 IO 调度器；本类只在
 * `viewModelScope`（Main）上编排状态，不直接触 native。
 *
 * 口令只存在于内存中的 [DemoUiState]（ViewModel 存活期间），不写入
 * SavedStateHandle，因此进程被杀后不会恢复，需要重新输入。
 */
class DemoViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ShioDatabase()
    private val databasePath = File(application.filesDir, DB_FILE_NAME).absolutePath

    // ViewModel 结束时用它释放句柄；不阻塞主线程。
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(DemoUiState())
    val state: StateFlow<DemoUiState> = _state.asStateFlow()

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
    }

    fun selectNote(id: String) {
        _state.update { it.copy(selectedId = if (it.selectedId == id) null else id) }
    }

    /** 首次打开、关闭后重新打开都走这里；[ShioDatabase.open] 会先销毁旧句柄。 */
    fun openOrCreate() {
        val current = _state.value
        if (current.busy) return
        if (current.password.isEmpty()) {
            _state.update {
                it.copy(error = ShioError(ShioException.INVALID_ARGUMENT, "请输入口令"))
            }
            return
        }
        launchOperation(if (current.canReopen) "正在重新打开数据库…" else "正在打开数据库…") {
            database.open(databasePath, current.password)
            val abi = database.abiVersion()
            val stats = database.stats()
            _state.update {
                it.copy(
                    isKernelOpen = true,
                    databasePath = databasePath,
                    abiVersion = abi,
                    stats = formatStats(stats),
                )
            }
            loadNotesInternal()
            _state.update { it.copy(status = "已打开数据库，共 ${it.notes.size} 条记录") }
        }
    }

    fun closeKernel() {
        launchOperation("正在关闭数据库…") {
            database.close()
            _state.update {
                it.copy(
                    isKernelOpen = false,
                    notes = emptyList(),
                    selectedId = null,
                    stats = null,
                    status = "数据库已关闭",
                )
            }
        }
    }

    fun refresh() {
        launchOperation("正在刷新列表…") {
            loadNotesInternal()
            _state.update { it.copy(status = "已刷新，共 ${it.notes.size} 条（最多 50 条）") }
        }
    }

    fun showStats() {
        launchOperation("正在读取统计…") {
            val stats = formatStats(database.stats())
            _state.update { it.copy(stats = stats, status = "统计已更新") }
        }
    }

    fun addNote() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) {
            _state.update {
                it.copy(error = ShioError(ShioException.INVALID_ARGUMENT, "请输入记录正文"))
            }
            return
        }
        launchOperation("正在写入记录…") {
            val value = JSONObject().put("text", text).toString()
            val id = database.insert(NAMESPACE, COLLECTION, value)
            _state.update { it.copy(draft = "", selectedId = id) }
            loadNotesInternal()
            _state.update { it.copy(status = "已添加记录 ${id.take(8)}") }
        }
    }

    fun deleteSelected() {
        val id = _state.value.selectedId
        if (id == null) {
            _state.update {
                it.copy(error = ShioError(ShioException.INVALID_ARGUMENT, "请先选择一条记录"))
            }
            return
        }
        launchOperation("正在删除记录…") {
            val existed = database.delete(NAMESPACE, COLLECTION, id)
            _state.update { it.copy(selectedId = null) }
            loadNotesInternal()
            _state.update {
                it.copy(
                    status = if (existed) {
                        "已删除记录 ${id.take(8)}"
                    } else {
                        "记录已不存在：${id.take(8)}"
                    },
                )
            }
        }
    }

    /** 用 `batch` 一次写入 3 条；ID 由客户端按 UUIDv7 形态生成。 */
    fun batchWrite() {
        launchOperation("正在批量写入 3 条…") {
            val operations = JSONArray()
            repeat(3) { index ->
                operations.put(
                    JSONObject()
                        .put("id", newRecordId())
                        .put(
                            "value",
                            JSONObject().put("text", "批量记录 #${index + 1}"),
                        ),
                )
            }
            val result = JSONArray(database.batch(NAMESPACE, COLLECTION, operations.toString()))
            loadNotesInternal()
            _state.update {
                it.copy(
                    status = "批量写入完成：${result.length()} 条",
                    draft = "",
                )
            }
        }
    }

    override fun onCleared() {
        // 用独立作用域释放句柄，避免在 Main 上阻塞；重复调用是空操作。
        cleanupScope.launch { database.destroy() }
    }

    private fun launchOperation(status: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, status = status, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: ShioException) {
                reportError(error.code, error.message ?: "shio error")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportError(ShioException.INTERNAL, error.message ?: error.javaClass.simpleName)
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun reportError(code: Int, message: String) {
        _state.update {
            it.copy(
                error = ShioError(code, message),
                status = "操作失败",
            )
        }
    }

    /** `list` 只给元信息，正文逐条 `get`；单次最多 50 条。 */
    private suspend fun loadNotesInternal() {
        val page = JSONObject(
            database.list(
                namespace = NAMESPACE,
                collection = COLLECTION,
                limit = LIST_LIMIT,
            ),
        )
        val items = page.optJSONArray("items") ?: JSONArray()
        val notes = ArrayList<NoteItem>(items.length())
        for (index in 0 until items.length()) {
            val id = items.optJSONObject(index)?.optString("id").orEmpty()
            if (id.isEmpty()) continue
            val fetched = try {
                database.get(NAMESPACE, COLLECTION, id)
            } catch (error: ShioException) {
                if (error.code == ShioException.NOT_FOUND) continue
                throw error
            }
            val value = JSONObject(fetched).optJSONObject("value")
            val text = value?.optString("text") ?: value?.toString().orEmpty()
            notes += NoteItem(id = id, text = text)
        }
        _state.update { current ->
            current.copy(
                notes = notes,
                selectedId = current.selectedId?.takeIf { selected ->
                    notes.any { note -> note.id == selected }
                },
            )
        }
    }

    private fun formatStats(raw: String): String = runCatching {
        val stats = JSONObject(raw)
        buildString {
            append("format_version = ").append(stats.optInt("format_version"))
            append('\n').append("key_id = ").append(stats.optString("key_id"))
            append('\n').append("db_bytes = ").append(stats.optLong("db_bytes"))
        }
    }.getOrDefault(raw)

    /** `batch` 需要调用方提供 ID；这里生成 UUIDv7 形态，保持与 `insert` 一致的排序语义。 */
    private fun newRecordId(): String {
        val timestamp = System.currentTimeMillis()
        val random = ByteArray(10).also { ThreadLocalRandom.current().nextBytes(it) }
        val bytes = ByteArray(16)
        for (index in 0 until 6) {
            bytes[index] = (timestamp ushr (8 * (5 - index))).toByte()
        }
        bytes[6] = (0x70 or (random[0].toInt() and 0x0F)).toByte()
        bytes[7] = random[1]
        bytes[8] = (0x80 or (random[2].toInt() and 0x3F)).toByte()
        for (index in 9 until 16) {
            bytes[index] = random[index - 6]
        }
        return UuidCodec.encode(bytes)
    }

    companion object {
        const val NAMESPACE = "demo"
        const val COLLECTION = "notes"
        const val LIST_LIMIT = 50

        private const val DB_FILE_NAME = "shio-demo.db"
    }
}
