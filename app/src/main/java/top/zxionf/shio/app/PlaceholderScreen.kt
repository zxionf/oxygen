package top.zxionf.shio.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** 占位页上的一个跳转入口，仅在搭骨架阶段使用。 */
data class PlaceholderAction(val label: String, val onClick: () -> Unit)

/**
 * 导航骨架阶段的占位页：显示路由与说明，可带若干临时跳转。
 *
 * M2 起各页面逐步被真实实现替换，本文件最终删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    route: String,
    description: String,
    modifier: Modifier = Modifier,
    actions: List<PlaceholderAction> = emptyList(),
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(title) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = route,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "占位页：后续阶段实现",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            actions.forEach { action ->
                OutlinedButton(
                    onClick = action.onClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(action.label)
                }
            }
        }
    }
}
