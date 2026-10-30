package top.zxionf.shio.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3E 形状阶梯：8 / 12 / 16 / 20 / 28。
 *
 * 页面上只能引用 `MaterialTheme.shapes` 的档位或这里的 [ListStackShapes]，
 * 不再手写零散圆角。
 */
val ShioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = ShapeDefaults.LargeIncreased,
    extraLarge = ShapeDefaults.ExtraLarge,
)

/** 堆叠列表：组整体 28dp 外角，相邻项之间 8dp 内角，间距 3dp。 */
object ListStackShapes {
    val first: CornerBasedShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 8.dp,
        bottomEnd = 8.dp,
    )
    val middle: CornerBasedShape = RoundedCornerShape(8.dp)
    val last: CornerBasedShape = RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 8.dp,
        bottomStart = 28.dp,
        bottomEnd = 28.dp,
    )
    val single: CornerBasedShape = RoundedCornerShape(28.dp)

    /** 堆叠列表项之间的间距。 */
    val gap = 3.dp
}
