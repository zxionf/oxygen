package top.zxionf.shio.vault

/**
 * 自动锁定的纯判定逻辑（无 Android 依赖，便于单元测试）。
 *
 * 时间一律是单调时钟（`SystemClock.elapsedRealtime`）的毫秒值，用差值判断，
 * 不受系统时间被改动的影响。
 */
object AutoLockPolicy {

    /**
     * 后台时长是否已经超过 [timeout]。
     *
     * `IMMEDIATELY`（0 毫秒）恒为 true；其余按 `now - backgroundedAt >= timeout` 判断。
     * 时间戳倒流（理论上不会发生）按未超时处理，避免误锁。
     */
    fun shouldLockAfterBackground(
        timeout: AutoLockTimeout,
        backgroundedAt: Long,
        now: Long,
    ): Boolean {
        if (timeout.millis <= 0L) return true
        val elapsed = now - backgroundedAt
        if (elapsed < 0L) return false
        return elapsed >= timeout.millis
    }
}
