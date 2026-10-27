package top.zxionf.shio.vault

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 自动锁定/手动锁定的唯一入口。
 *
 * 规则（见 SPEC 的「自动锁定」）：
 * - `IMMEDIATELY`：只要 Activity 真的停到后台就立刻销毁内核句柄；
 * - 其它时长：只在后台记下时间戳，回到前台时按 `elapsedRealtime` 差值判断是否超时，
 *   超时才销毁；这样旋转、短暂切走不会打断使用；
 * - Activity 因配置变更（旋转）而 `onStop` 时不算切后台。
 *
 * 锁定成功后 [lockCount] 自增，界面（`AppNavHost`）据此清空回退栈并进入解锁页，
 * 保证「销毁句柄」与「导航到解锁页」只发生在一处。
 *
 * 锁定同时调用 [SensitiveClipboard.clearNow]，让剪贴板里的密码随会话一起消失。
 *
 * 外部交互豁免：用 `startActivityForResult` 唤起系统界面（SAF 选文件、相机等）时，
 * 我们的 Activity 会真的 `onStop`，不能因此当场锁死句柄，否则返回时已无法完成动作。
 * 用 [beginExternalInteraction] / [endExternalInteraction] 标记这种场景：豁免期内不立即锁定，
 * 但仍记时间戳，并在 [EXTERNAL_INTERACTION_GRACE_MILLIS] 后恢复按偏好判定。
 */
object AutoLockController {

    /**
     * 外部交互（如 SAF 文件选择）的宽限时长：超过它仍按偏好判定，避免用户一走了之就不锁。
     */
    const val EXTERNAL_INTERACTION_GRACE_MILLIS: Long = 120_000L

    /** 用 Main.immediate：Activity 停止瞬间也要尽快销毁句柄。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _lockCount = MutableStateFlow(0)

    /** 每次锁定自增；界面用它触发一次「清栈 + 进解锁页」。 */
    val lockCount: StateFlow<Int> = _lockCount.asStateFlow()

    /** 进入后台的时间戳（`elapsedRealtime`）；null 表示当前不在后台。 */
    private var backgroundedAt: Long? = null

    /** 正在等待外部界面返回（SAF 等）；深度计数以支持嵌套。 */
    private var externalInteractions: Int = 0

    val isAwaitingExternalResult: Boolean
        get() = externalInteractions > 0

    /** 声明即将启动外部界面：期间的 `onStop` 不触发立即锁定。 */
    fun beginExternalInteraction() {
        externalInteractions += 1
    }

    /** 外部界面返回：撤销一次声明。 */
    fun endExternalInteraction() {
        if (externalInteractions > 0) externalInteractions -= 1
    }

    /** Activity `onStop`：按偏好决定立即锁定或只记时间戳。 */
    fun onActivityStopped(
        timeout: AutoLockTimeout,
        isChangingConfigurations: Boolean,
        now: Long = SystemClock.elapsedRealtime(),
    ) {
        if (isChangingConfigurations) return
        if (!VaultComponent.repository.isUnlocked) return
        // 外部交互期间一律只记时间戳，由 onStart 按宽限时长判断。
        if (timeout.millis <= 0L && !isAwaitingExternalResult) {
            lockNow()
        } else {
            backgroundedAt = now
        }
    }

    /** Activity `onStart`：后台时长超过偏好就锁定。 */
    fun onActivityStarted(
        timeout: AutoLockTimeout,
        now: Long = SystemClock.elapsedRealtime(),
    ) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        // 外部交互（选文件等）给一个短宽限：否则「立即」模式下选完文件回来就已锁死。
        // 取 max 是为了不削弱用户自己设的更长时间偏好。
        val threshold = if (isAwaitingExternalResult) {
            maxOf(timeout.millis, EXTERNAL_INTERACTION_GRACE_MILLIS)
        } else {
            timeout.millis
        }
        val elapsed = now - since
        // threshold <= 0 等价于「立即」；时间倒流按未超时处理。
        val expired = if (threshold <= 0L) true else elapsed >= threshold
        if (expired && elapsed >= 0L) {
            lockNow()
        }
    }

    /** 立即锁定：销毁句柄、清空会话，然后通知界面。 */
    fun lockNow() {
        backgroundedAt = null
        // 锁定即清剪贴板：不把刚复制的密码留到解锁之后。
        SensitiveClipboard.clearNow()
        if (!VaultComponent.repository.isUnlocked) {
            // 已经没有会话（例如刚锁过），仍然发一次信号让界面回到解锁页。
            _lockCount.value += 1
            return
        }
        scope.launch {
            runCatching { VaultComponent.repository.lock() }
            _lockCount.value += 1
        }
    }

    /** 仅用于测试/诊断：当前是否记录了后台时间戳。 */
    val isBackgrounded: Boolean
        get() = backgroundedAt != null
}
