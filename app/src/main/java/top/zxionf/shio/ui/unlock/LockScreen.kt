package top.zxionf.shio.ui.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.components.AppTextField
import top.zxionf.shio.ui.theme.ShioTheme

/** 创建 / 解锁两种形态。 */
enum class LockMode { Create, Unlock }

/**
 * 创建 / 解锁页（M3E）。
 *
 * 大标题 + 单个 filled 输入框 + 全宽胶囊主按钮；错误显示在内联错误区。
 * M9 之前是假 gate：主口令长度 ≥ 8 且两次一致即可通过，成功后由调用方进入 `vault`。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockScreen(
    mode: LockMode,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by rememberSaveable(mode) { mutableStateOf("") }
    var confirm by rememberSaveable(mode) { mutableStateOf("") }
    var revealed by rememberSaveable(mode) { mutableStateOf(false) }
    var error by rememberSaveable(mode) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        error = when {
            password.length < 8 -> "主口令至少 8 个字符"
            mode == LockMode.Create && confirm != password -> "两次输入的主口令不一致"
            else -> null
        }
        if (error != null) return
        busy = true
        // M9 之前不触碰内核：短暂展示载入态后直接进入密码库；M12 换成真实 create/unlock。
        onSuccess()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (mode == LockMode.Create) "创建密码库" else "解锁",
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (mode == LockMode.Create) {
                    "设置一个主口令。忘记主口令将无法恢复数据，也没有后门。"
                } else {
                    "输入主口令继续。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AppTextField(
                value = password,
                onValueChange = { password = it },
                label = "主口令",
                helperText = "至少 8 个字符",
                errorText = error,
                keyboardType = KeyboardType.Password,
                imeAction = if (mode == LockMode.Create) ImeAction.Next else ImeAction.Done,
                keyboardActions = KeyboardActions(
                    onDone = { if (mode == LockMode.Unlock) submit() },
                ),
                visualTransformation = if (revealed) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingContent = {
                    IconToggleButton(
                        checked = revealed,
                        onCheckedChange = { revealed = it },
                    ) {
                        AppIcon(
                            icon = if (revealed) {
                                AppIcons.VisibilityOff
                            } else {
                                AppIcons.Visibility
                            },
                            contentDescription = if (revealed) "隐藏口令" else "显示口令",
                        )
                    }
                },
            )

            if (mode == LockMode.Create) {
                AppTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "确认主口令",
                    helperText = "再输一遍，两次需一致",
                    errorText = error,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    visualTransformation = if (revealed) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                )
            }

            error?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        icon = AppIcons.Info,
                        contentDescription = null,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Button(
                onClick = ::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                enabled = !busy,
            ) {
                if (busy) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = if (mode == LockMode.Create) "创建" else "解锁",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                text = "数据只保存在本机，不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun CreatePreview() {
    ShioTheme {
        LockScreen(mode = LockMode.Create, onBack = {}, onSuccess = {})
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun UnlockPreview() {
    ShioTheme {
        LockScreen(mode = LockMode.Unlock, onBack = {}, onSuccess = {})
    }
}
