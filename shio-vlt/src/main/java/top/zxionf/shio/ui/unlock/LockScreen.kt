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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.zxionf.shio.kernel.ShioException
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.components.AppTextField
import top.zxionf.shio.ui.theme.ShioTheme
import top.zxionf.shio.vault.VaultComponent
import top.zxionf.shio.vault.VaultLibrary
import top.zxionf.shio.vault.VaultLibraryEntry
import top.zxionf.shio.vault.VaultRepository
import top.zxionf.shio.vault.VaultSession

/** 创建 / 解锁两种形态。 */
enum class LockMode { Create, Unlock }

/**
 * 创建 / 解锁页（M3E）。
 *
 * 大标题 + 单个线框输入框 + 全宽胶囊主按钮；错误显示在内联错误区。
 * M12 起真正接内核：创建走 `VaultRepository.create`（含 `sync_enable` 与 format=2 校验），
 * 解锁走 `VaultRepository.unlock`，失败时按 [ShioException] 错误码给出可读提示。
 *
 * [onBack] 为 null 表示当前页是栈底（创建/解锁就是应用入口），不显示返回箭头，
 * 此时系统返回键直接退出应用。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockScreen(
    mode: LockMode,
    onBack: (() -> Unit)?,
    onSuccess: (VaultSession) -> Unit,
    modifier: Modifier = Modifier,
    vaultId: String? = null,
    repository: VaultRepository = VaultComponent.repository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by rememberSaveable(mode) { mutableStateOf("") }
    var confirm by rememberSaveable(mode) { mutableStateOf("") }
    var vaultName by rememberSaveable(mode) { mutableStateOf(VaultLibrary.DEFAULT_NAME) }
    var revealed by rememberSaveable(mode) { mutableStateOf(false) }
    var error by rememberSaveable(mode) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // 解锁模式：解析要解锁哪个库；参数缺失时回退到索引第一条。
    val targetEntry = remember(vaultId, mode) {
        if (mode == LockMode.Unlock) {
            VaultLibrary.find(context, vaultId) ?: VaultLibrary.list(context).firstOrNull()
        } else {
            null
        }
    }

    fun submit() {
        if (busy) return
        val localError = when {
            mode == LockMode.Create && vaultName.isBlank() -> "库名不能为空"
            password.length < MIN_PASSWORD_LENGTH -> "主口令至少 $MIN_PASSWORD_LENGTH 个字符"
            mode == LockMode.Create && confirm != password -> "两次输入的主口令不一致"
            mode == LockMode.Unlock && targetEntry == null -> "找不到要解锁的密码库"
            else -> null
        }
        if (localError != null) {
            error = localError
            return
        }
        error = null
        busy = true
        scope.launch {
            var registered: VaultLibraryEntry? = null
            try {
                val entry = if (mode == LockMode.Create) {
                    VaultLibrary.register(context, vaultName).also { registered = it }
                } else {
                    targetEntry!!
                }
                val path = VaultLibrary.prepareFile(context, entry).absolutePath
                val session = if (mode == LockMode.Create) {
                    repository.create(path, password)
                } else {
                    repository.unlock(path, password)
                }
                VaultLibrary.rememberLast(context, entry.id)
                // 口令用完后立即从界面状态里清掉（JVM 字符串本身无法擦除，已在设计中接受）。
                password = ""
                confirm = ""
                revealed = false
                onSuccess(session)
            } catch (failure: Throwable) {
                // 创建失败要把刚登记的空壳库回滚，避免列表里出现打不开的库。
                registered?.let { VaultLibrary.remove(context, it.id) }
                error = describeFailure(failure)
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            AppIcon(AppIcons.ArrowBack, contentDescription = "返回")
                        }
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
                    targetEntry?.let { "即将解锁「${it.name}」" } ?: "输入主口令继续。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (mode == LockMode.Create) {
                AppTextField(
                    value = vaultName,
                    onValueChange = { vaultName = it },
                    label = "库名",
                    helperText = "显示在库列表里，可重命名",
                    singleLine = true,
                    imeAction = ImeAction.Next,
                )
            }

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

/** 主口令最短长度（仅界面拦截；内核不限制长度）。 */
private const val MIN_PASSWORD_LENGTH = 8

/**
 * 把内核异常翻译成界面文案；错误码写进括号，方便真机对照 `ShioException`。
 */
private fun describeFailure(failure: Throwable): String = when (failure) {
    is ShioException -> when (failure.code) {
        ShioException.INVALID_KEY -> "主口令不正确（INVALID_KEY (${failure.code})）"
        ShioException.NOT_FOUND -> "找不到密码库（NOT_FOUND (${failure.code})）"
        ShioException.INVALID_ARGUMENT -> "密码库已存在，请重新启动（INVALID_ARGUMENT (${failure.code})）"
        ShioException.LOCKED -> "密码库正在被其它进程使用（LOCKED (${failure.code})）"
        ShioException.BUSY -> "上次密钥轮换未完成（BUSY (${failure.code})）"
        ShioException.CORRUPTED -> "密码库文件损坏（CORRUPTED (${failure.code})）"
        ShioException.UNSUPPORTED_FORMAT -> "存储格式不受支持（UNSUPPORTED_FORMAT (${failure.code})）"
        ShioException.READ_ONLY -> "数据库为只读（READ_ONLY (${failure.code})）"
        ShioException.IO -> "磁盘读写失败（IO (${failure.code})）"
        ShioException.DATABASE -> "数据库错误（DATABASE (${failure.code})）"
        else -> "创建/解锁失败（错误码 ${failure.code}）"
    }
    else -> failure.message ?: "未知错误"
}
