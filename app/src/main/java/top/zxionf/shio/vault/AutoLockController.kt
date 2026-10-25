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
 */
object AutoLockController {

    /** 用 Main.immediate：Activity 停止瞬间也要尽快销毁句柄。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _lockCount = MutableStateFlow(0)

    /** 每次锁定自增；界面用它触发一次「清栈 + 进解锁页」。 */
    val lockCount: StateFlow<Int> = _lockCount.asStateFlow()

    /** 进入后台的时间戳（`elapsedRealtime`）；null 表示当前不在后台。 */
    private var backgroundedAt: Long? = null

    /** Activity `onStop`：按偏好决定立即锁定或只记时间戳。 */
    fun onActivityStopped(
        timeout: AutoLockTimeout,
        isChangingConfigurations: Boolean,
        now: Long = SystemClock.elapsedRealtime(),
    ) {
        if (isChangingConfigurations) return
        if (!VaultComponent.repository.isUnlocked) return
        if (timeout.millis <= 0L) {
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
        if (AutoLockPolicy.shouldLockAfterBackground(timeout, since, now)) {
            lockNow()
        }
    }

    /** 立即锁定：销毁句柄、清空会话，然后通知界面。 */
    fun lockNow() {
        backgroundedAt = null
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
