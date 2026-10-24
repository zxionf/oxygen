package top.zxionf.shio.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.zxionf.shio.BuildConfig
import java.util.UUID

/**
 * M6 的进程内假数据仓库。
 *
 * 只为让「保存后刷新来源页面」可验证：写入内存后列表/详情立即重组。
 * 示例数据仅在 debug 构建里作为种子，release APK 不携带任何样例条目。
 * M11 起由 [VaultRepository]（内核 `vault/entries`）取代，本文件删除。
 * 口令只存在内存里，不落盘、不写日志。
 */
object FakeVaultStore {
    private val _entries = MutableStateFlow(if (BuildConfig.DEBUG) SampleVaultEntries else emptyList())

    /** 全部条目；写入后通过 StateFlow 通知界面。 */
    val entries: StateFlow<List<VaultEntry>> = _entries.asStateFlow()

    fun find(id: String): VaultEntry? = _entries.value.firstOrNull { it.id == id }

    /** 存在则替换，不存在则追加（追加到末尾，排序由列表页负责）。 */
    fun upsert(entry: VaultEntry) {
        _entries.update { current ->
            val index = current.indexOfFirst { it.id == entry.id }
            if (index >= 0) {
                current.toMutableList().also { it[index] = entry }
            } else {
                current + entry
            }
        }
    }

    /** 新条目的临时 ID；M11 起改用内核 insert 返回的 UUIDv7。 */
    fun newId(): String = UUID.randomUUID().toString()
}
