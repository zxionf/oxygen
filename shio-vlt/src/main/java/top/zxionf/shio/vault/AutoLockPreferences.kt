package top.zxionf.shio.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * 自动锁定时长：应用切到后台后的宽限时间，超过就锁定并回到解锁页。
 *
 * 默认 [IMMEDIATELY]（切后台立即锁定），对应设计里的「安全优先」。
 */
enum class AutoLockTimeout(val label: String, val millis: Long) {
    IMMEDIATELY("立即", 0L),
    ONE_MINUTE("1 分钟", 60_000L),
    FIVE_MINUTES("5 分钟", 300_000L),
    FIFTEEN_MINUTES("15 分钟", 900_000L),
}

/**
 * 自动锁定偏好：`SharedPreferences` 持久化，进程内单例。
 *
 * 与外观偏好一样用 Compose 快照状态，读取即可触发重组；写入统一走 [updateTimeout]。
 * 冷启动由构造时的读取恢复，不需要额外的加载流程。
 */
class AutoLockPreferences private constructor(
    private val prefs: SharedPreferences,
) {
    var timeout: AutoLockTimeout by mutableStateOf(readTimeout())
        private set

    fun updateTimeout(value: AutoLockTimeout) {
        if (value == timeout) return
        timeout = value
        prefs.edit { putString(KEY_TIMEOUT, value.name) }
    }

    private fun readTimeout(): AutoLockTimeout =
        prefs.getString(KEY_TIMEOUT, null)
            ?.let { raw -> AutoLockTimeout.entries.firstOrNull { it.name == raw } }
            ?: AutoLockTimeout.IMMEDIATELY

    companion object {
        private const val PREFS_NAME = "session"
        private const val KEY_TIMEOUT = "auto_lock_timeout"

        @Volatile
        private var instance: AutoLockPreferences? = null

        /** 取进程内单例；只用 application context，避免持有 Activity。 */
        fun get(context: Context): AutoLockPreferences {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: AutoLockPreferences(
                    context.applicationContext.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE,
                    ),
                ).also { instance = it }
            }
        }
    }
}
