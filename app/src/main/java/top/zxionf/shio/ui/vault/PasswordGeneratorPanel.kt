package top.zxionf.shio.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.security.SecureRandom
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.StackedList
import top.zxionf.shio.vault.PasswordGenerator

/**
 * 密码生成面板（M7）。
 *
 * 生成结果用 tonal 卡片展示，右上角「重新生成」；下方长度滑块（12–64，步进 1）与
 * 五组开关（大写/小写/数字/符号/排除易混淆）。任一选项变化立即按 [PasswordGenerator]
 * 重新生成，保证「看到的就是将被写入的」。
 *
 * 状态留在面板内：旋转后长度、开关与当前密码都保留（[rememberSaveable]）。
 */
@Composable
fun PasswordGeneratorPanel(
    onUse: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val random = remember { SecureRandom() }
    var length by rememberSaveable { mutableIntStateOf(PasswordGenerator.DEFAULT_LENGTH) }
    var uppercase by rememberSaveable { mutableStateOf(true) }
    var lowercase by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(true) }
    var symbols by rememberSaveable { mutableStateOf(true) }
    var excludeAmbiguous by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }

    val options = PasswordGenerator.Options(
        length = length,
        uppercase = uppercase,
        lowercase = lowercase,
        digits = digits,
        symbols = symbols,
        excludeAmbiguous = excludeAmbiguous,
    )

    // 选项一变就重新生成，避免「滑块调完但密码没变」的错觉。
    LaunchedEffect(options) {
        password = PasswordGenerator.generate(options, random)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "生成密码",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                AppIcon(AppIcons.Close, contentDescription = "关闭生成器")
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = password.ifEmpty { "—" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { password = PasswordGenerator.generate(options, random) }) {
                    AppIcon(AppIcons.Refresh, contentDescription = "重新生成")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "长度",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = length.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Box(modifier = Modifier.padding(horizontal = 4.dp)) {
            // 新 API：Slider 只读 SliderState，值变化回写到 length，旋转后用 length 反向同步。
            val sliderState = remember {
                SliderState(
                    value = length.toFloat(),
                    steps = PasswordGenerator.MAX_LENGTH - PasswordGenerator.MIN_LENGTH - 1,
                    trackRange = PasswordGenerator.MIN_LENGTH.toFloat()..
                        PasswordGenerator.MAX_LENGTH.toFloat(),
                )
            }
            LaunchedEffect(length) {
                if (sliderState.value != length.toFloat()) sliderState.value = length.toFloat()
            }
            Slider(
                state = sliderState,
                onValueChange = { length = it.toInt() },
            )
        }

        StackedList(itemCount = 5) { index, shape ->
            val (label, hint, checked, onToggle) = when (index) {
                0 -> SwitchRow("大写字母", "A–Z", uppercase) { uppercase = it }
                1 -> SwitchRow("小写字母", "a–z", lowercase) { lowercase = it }
                2 -> SwitchRow("数字", "0–9", digits) { digits = it }
                3 -> SwitchRow("符号", "!#$%&*+-=?@^_", symbols) { symbols = it }
                else -> SwitchRow("排除易混淆字符", "I l 1 O 0 o 5 S 8 B 2 Z", excludeAmbiguous) {
                    excludeAmbiguous = it
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ListItem(
                    modifier = Modifier.clickable { onToggle(!checked) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    content = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(checked = checked, onCheckedChange = onToggle)
                    },
                )
            }
        }

        if (!options.hasSelection) {
            Text(
                text = "至少选择一种字符集",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = { onUse(password) },
            enabled = options.hasSelection && password.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            AppIcon(AppIcons.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("使用此密码")
        }
    }
}

/** 开关行的四元组，仅为让 `when` 分支保持一行。 */
private data class SwitchRow(
    val label: String,
    val hint: String,
    val checked: Boolean,
    val onToggle: (Boolean) -> Unit,
)
