package top.zxionf.shio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.zxionf.shio.kernel.ShioException
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.AppTextField
import top.zxionf.shio.vault.VaultComponent
import top.zxionf.shio.vault.VaultRepository

/** 主口令最短长度，与创建/解锁页保持一致。 */
private const val MIN_PASSWORD_LENGTH = 8

/**
 * 修改主口令（M3E）。
 *
 * 只要求新口令 + 确认：会话已解锁即代表本次启动通过了内核校验，内核的
 * `change_password` 也只接受新口令（不要求旧口令的理由见变更设计与 DECISIONS）。
 *
 * 成功后回调 [onChanged]，由调用方回退到设置页；失败按 `ShioException` 错误码给文案。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    repository: VaultRepository = VaultComponent.repository,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy) return
        val localError = when {
            password.length < MIN_PASSWORD_LENGTH -> "新主口令至少 $MIN_PASSWORD_LENGTH 个字符"
            confirm != password -> "两次输入的主口令不一致"
            else -> null
        }
        if (localError != null) {
            error = localError
            return
        }
        error = null
        busy = true
        scope.launch {
            try {
                repository.changePassword(password)
                password = ""
                confirm = ""
                revealed = false
                snackbarHostState.showSnackbar("主口令已修改，下次解锁请使用新口令")
                onChanged()
            } catch (failure: Throwable) {
                error = describeChangeFailure(failure)
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("修改主口令", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                text = "设置新的主口令",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "修改后旧主口令立即失效，条目内容不受影响。忘记新口令将无法恢复数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AppTextField(
                value = password,
                onValueChange = { password = it },
                label = "新主口令",
                helperText = "至少 $MIN_PASSWORD_LENGTH 个字符",
                errorText = error,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
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
                            icon = if (revealed) AppIcons.VisibilityOff else AppIcons.Visibility,
                            contentDescription = if (revealed) "隐藏口令" else "显示口令",
                        )
                    }
                },
            )

            AppTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "确认新主口令",
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

            Spacer(Modifier.height(8.dp))

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
                Text(text = "修改主口令", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                text = "主口令只保存在本机，不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/** 把内核异常翻译成界面文案；错误码写进括号，方便真机对照。 */
private fun describeChangeFailure(failure: Throwable): String = when (failure) {
    is ShioException -> when (failure.code) {
        ShioException.BUSY -> "上次密钥轮换未完成，请重试（BUSY (${failure.code})）"
        ShioException.CLOSED -> "密码库已锁定，请重新解锁（CLOSED (${failure.code})）"
        ShioException.READ_ONLY -> "数据库为只读（READ_ONLY (${failure.code})）"
        ShioException.INVALID_ARGUMENT -> "口令无效（INVALID_ARGUMENT (${failure.code})）"
        ShioException.CORRUPTED -> "密钥数据损坏（CORRUPTED (${failure.code})）"
        ShioException.IO -> "磁盘读写失败（IO (${failure.code})）"
        else -> "修改失败（错误码 ${failure.code}）"
    }
    else -> failure.message ?: "修改失败"
}
