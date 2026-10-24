package top.zxionf.shio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.zxionf.shio.ui.theme.ListStackShapes

/**
 * M3E 堆叠列表：相邻项 3dp 间距，组整体 28dp 外角、相邻内角 8dp。
 *
 * 调用方只关心「第几项」和对应形状，不需要自己算首/中/尾。
 */
@Composable
fun StackedList(
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(index: Int, shape: CornerBasedShape) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListStackShapes.gap),
    ) {
        repeat(itemCount) { index ->
            val shape = when {
                itemCount == 1 -> ListStackShapes.single
                index == 0 -> ListStackShapes.first
                index == itemCount - 1 -> ListStackShapes.last
                else -> ListStackShapes.middle
            }
            content(index, shape)
        }
    }
}
