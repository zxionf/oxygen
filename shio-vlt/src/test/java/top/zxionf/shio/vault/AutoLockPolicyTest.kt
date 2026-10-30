package top.zxionf.shio.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自动锁定超时判定：立即/1/5/15 分钟与边界、时间倒流。 */
class AutoLockPolicyTest {

    @Test
    fun `立即模式总是锁定`() {
        assertTrue(AutoLockPolicy.shouldLockAfterBackground(AutoLockTimeout.IMMEDIATELY, 1_000, 1_000))
        assertTrue(AutoLockPolicy.shouldLockAfterBackground(AutoLockTimeout.IMMEDIATELY, 1_000, 1_001))
    }

    @Test
    fun `未到阈值不锁定`() {
        assertFalse(AutoLockPolicy.shouldLockAfterBackground(AutoLockTimeout.ONE_MINUTE, 1_000, 1_000))
        assertFalse(
            AutoLockPolicy.shouldLockAfterBackground(
                AutoLockTimeout.ONE_MINUTE,
                1_000,
                1_000 + 59_999,
            ),
        )
        assertFalse(
            AutoLockPolicy.shouldLockAfterBackground(
                AutoLockTimeout.FIVE_MINUTES,
                0,
                299_999,
            ),
        )
    }

    @Test
    fun `达到阈值即锁定`() {
        assertTrue(
            AutoLockPolicy.shouldLockAfterBackground(
                AutoLockTimeout.ONE_MINUTE,
                1_000,
                1_000 + 60_000,
            ),
        )
        assertTrue(
            AutoLockPolicy.shouldLockAfterBackground(
                AutoLockTimeout.FIVE_MINUTES,
                0,
                300_000,
            ),
        )
        assertTrue(
            AutoLockPolicy.shouldLockAfterBackground(
                AutoLockTimeout.FIFTEEN_MINUTES,
                0,
                900_000,
            ),
        )
    }

    @Test
    fun `时间倒流按未超时处理`() {
        assertFalse(
            AutoLockPolicy.shouldLockAfterBackground(
                AutoLockTimeout.FIVE_MINUTES,
                10_000,
                5_000,
            ),
        )
    }

    @Test
    fun `枚举阈值与文案一致`() {
        assertTrue(AutoLockTimeout.IMMEDIATELY.millis == 0L)
        assertTrue(AutoLockTimeout.ONE_MINUTE.millis == 60_000L)
        assertTrue(AutoLockTimeout.FIVE_MINUTES.millis == 300_000L)
        assertTrue(AutoLockTimeout.FIFTEEN_MINUTES.millis == 900_000L)
    }
}
