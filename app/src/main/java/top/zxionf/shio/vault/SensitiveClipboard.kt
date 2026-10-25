package top.zxionf.shio.vault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 敏感文本的剪贴板入口：写入 + 定时自动清除。
 *
 * 规则（见 `.design/changes/20261025-clipboard-auto-clear/design.md`）：
 * - 写入时打 `EXTRA_IS_SENSITIVE`（API 33+），避免剪贴板浮层与输入法联想展示明文；
 * - [CLEAR_AFTER_MILLIS] 后自动清除；再次复制会重置计时；
 * - [clearNow] 供锁定流程调用，立即清除并取消计时；
 * - [clearIfExpired] 供回到前台时调用：进程被冻结或计时未跑完时，按写入时间戳补偿清除；
 * - 清除前比对当前剪贴板内容，只有仍是我们写入的那条才清，避免误删用户后来复制的别的东西；
 * - 读不到剪贴板（后台限制等）时按「宁可多清」处理；清除失败（后台被拒）时保留待清除
 *   记录，等下次 [clearIfExpired] 或 [clearNow] 重试；
 * - **不在切后台时清除**：复制后切到别的应用粘贴是主要用法，30 秒窗口内必须还能粘。
 *
 * 进程被系统杀死后计时消失，剪贴板残留由系统自行回收（平台限制，不引入 AlarmManager）。
 *
 * 已知限制：输入法（尤其是国产 ROM 自带输入法）会把复制内容另存到自己的「剪贴板历史」，
 * 应用只能控制系统剪贴板，**无法删除输入法侧的副本**；因此首次复制时用
 * [consumeImeNotice] 弹一次提示，告知用在哪里关闭。
 */
object SensitiveClipboard {

    /** 自动清除延时：30 秒。 */
    const val CLEAR_AFTER_MILLIS: Long = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appContext: Context? = null
    private var pendingText: String? = null
    private var copiedAtElapsed: Long = 0L
    private var clearJob: Job? = null

    /** 兜底覆盖用的剪贴板标签（不带任何敏感信息）。 */
    private const val PLACEHOLDER_LABEL = "shio"

    private const val NOTICE_PREFS = "clipboard"
    private const val NOTICE_KEY = "ime_notice_shown"

    /** 复制敏感文本：写入剪贴板、打敏感标记，并在 [CLEAR_AFTER_MILLIS] 后清除。 */
    fun copy(context: Context, label: String, value: String) {
        if (value.isEmpty()) return
        val app = context.applicationContext
        val manager = app.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
        appContext = app
        pendingText = value
        copiedAtElapsed = SystemClock.elapsedRealtime()
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_AFTER_MILLIS)
            clearIfStillOurs()
        }
    }

    /** 立即清除（锁定流程调用）：取消计时并按「只清自己的内容」的规则清空。 */
    fun clearNow() {
        clearJob?.cancel()
        clearJob = null
        clearIfStillOurs()
    }

    /**
     * 回到前台时调用：进程被系统冻结（或计时协程被中断）时，30 秒可能早过了但什么都没清，
     * 这里用 `elapsedRealtime` 的差值补一次，保证「复制 → 切应用 → 回来」也清得掉。
     */
    fun clearIfExpired(now: Long = SystemClock.elapsedRealtime()) {
        if (pendingText == null) return
        if (now - copiedAtElapsed < CLEAR_AFTER_MILLIS) return
        clearJob?.cancel()
        clearJob = null
        clearIfStillOurs()
    }

    /** 仅用于测试/诊断：当前是否还有一条待清除的敏感内容。 */
    val hasPending: Boolean
        get() = pendingText != null

    /**
     * 首次复制时返回 true（之后恒为 false），用于只弹一次的「输入法剪贴板」提示。
     *
     * 输入法自带的剪贴板历史不归应用管，只能提醒用户自行关闭；用独立的
     * `clipboard` 预置文件记录「已提示」，不混进外观/会话偏好。
     */
    fun consumeImeNotice(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(NOTICE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(NOTICE_KEY, false)) return false
        prefs.edit().putBoolean(NOTICE_KEY, true).apply()
        return true
    }

    private fun clearIfStillOurs() {
        val app = appContext ?: return
        val expected = pendingText ?: return
        val manager = app.getSystemService(ClipboardManager::class.java) ?: return
        val current = readText(manager)
        // current == null 可能是「剪贴板已空」也可能是「后台读取受限」；两种都按需要清除处理。
        if (current != null && current != expected) {
            // 已经是用户后来复制的其它内容：放弃，不动它。
            pendingText = null
            return
        }
        // ① 先试系统提供的 clearPrimaryClip。
        val cleared = runCatching { manager.clearPrimaryClip() }.isSuccess
        if (cleared && readText(manager) != expected) {
            pendingText = null
            return
        }
        // ② 有的 ROM（含 vivo）会忽略 clearPrimaryClip；用空文本覆盖，确保明文不再能从剪贴板读出。
        val overwritten = runCatching {
            manager.setPrimaryClip(ClipData.newPlainText(PLACEHOLDER_LABEL, ""))
        }.isSuccess
        if (overwritten || cleared) {
            pendingText = null
        }
        // 两者都失败时保留 pendingText，等下次 clearIfExpired / clearNow 重试。
    }

    /** 读剪贴板首项的文本；后台限制或异常时返回 null。 */
    private fun readText(manager: ClipboardManager): String? = runCatching {
        manager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()
    }.getOrNull()
}
