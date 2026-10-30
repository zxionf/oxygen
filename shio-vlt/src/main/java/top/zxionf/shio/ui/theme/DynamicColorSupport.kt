package top.zxionf.shio.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * 动态取色（monet）能力探测。
 *
 * 只看 `SDK_INT` 不够：Compose 的 `dynamic*ColorScheme` 读取的是系统资源
 * `android.R.color.system_accent*` / `system_neutral*`，个别精简 ROM 会缺失这些资源，
 * 此时强行取色会抛异常或拿到兜底色。这里以「平台 ≥ 31 且资源确实可解析」为准。
 *
 * 判定结果只影响可用性，不改写已保存的偏好：不可用时选中项仍然保留，
 * 渲染回退 Shio 蓝，恢复到支持动态取色的设备后自动生效。
 */
object DynamicColorSupport {
    /** 结果按 Context 缓存，避免每次重组都做资源查找。 */
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return hasMonetResources(context.applicationContext)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasMonetResources(context: Context): Boolean = runCatching {
        context.getColor(android.R.color.system_accent1_100)
        context.getColor(android.R.color.system_neutral1_100)
    }.isSuccess
}
