package top.zxionf.shio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * 按下缩放：配合 ripple 使用（M3E 的「轻微按下缩放」）。
 *
 * 动效取全局 `MotionScheme.expressive()`：按下用快的空间曲线，回弹用默认曲线，
 * 页面不再各自定义弹簧参数。
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.98f,
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val spec = if (pressed) {
        MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    } else {
        MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spec,
        label = "pressScale",
    )
    return scale
}
