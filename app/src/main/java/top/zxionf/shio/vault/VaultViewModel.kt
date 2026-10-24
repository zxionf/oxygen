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
    private val source: List<VaultEntry> = SampleEntries,
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

/**
 * M4 假数据：覆盖「有用户名」「只有网址」「只有备注」「中文标题」几种列表项形态。
 * M13 接入真实数据后删除。
 */
private val SampleEntries = listOf(
    VaultEntry(
        id = "01a168ba-327b-7202-a239-e10c138df11a",
        title = "GitHub",
        username = "zxionf@example.com",
        password = "gh-fake-password-1",
        url = "https://github.com",
        notes = "两步验证已开启，恢复码在保险箱 recovery-codes.txt",
    ),
    VaultEntry(
        id = "01a168bb-11c2-7a41-9a82-4d8e0c7b5f10",
        title = "银行 App",
        username = "6222••••••••1234",
        password = "bank-fake-password",
        url = "",
        notes = "登录密码与取款密码不同",
    ),
    VaultEntry(
        id = "01a168bc-2f30-7c11-8b70-9a1d2c3e4f50",
        title = "公司 VPN",
        username = "zxionf",
        password = "vpn-fake-password",
        url = "https://vpn.example.com",
        notes = "",
    ),
    VaultEntry(
        id = "01a168bd-3a41-7d22-9c81-0b2e3d4f5a60",
        title = "路由器管理",
        username = "admin",
        password = "router-fake-password",
        url = "http://192.168.1.1",
        notes = "仅内网可访问",
    ),
    VaultEntry(
        id = "01a168be-4b52-7e33-8d92-1c3f4e5a6b70",
        title = "Netflix",
        username = "",
        password = "netflix-fake-password",
        url = "https://www.netflix.com",
        notes = "家庭共享账号",
    ),
    VaultEntry(
        id = "01a168bf-5c63-7f44-9ea3-2d4a5f6b7c80",
        title = "邮箱",
        username = "zxionf@example.com",
        password = "mail-fake-password",
        url = "https://mail.example.com",
        notes = "个人主邮箱",
    ),
    VaultEntry(
        id = "01a168c0-6d74-7045-8fb4-3e5b6a7c8d90",
        title = "开发服务器",
        username = "root",
        password = "server-fake-password",
        url = "",
        notes = "端口 22，仅跳板机可达",
    ),
    VaultEntry(
        id = "01a168c1-7e85-7156-9ac5-4f6c7b8d9ea0",
        title = "Wi-Fi 密码",
        username = "",
        password = "wifi-fake-password",
        url = "",
        notes = "办公室访客网络",
    ),
)
