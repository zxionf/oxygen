package top.zxionf.shio.ui.settings

import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.zxionf.shio.BuildConfig
import top.zxionf.shio.kernel.KernelInfo
import top.zxionf.shio.vault.AutoLockPreferences
import top.zxionf.shio.vault.AutoLockController
import top.zxionf.shio.vault.VaultComponent
import top.zxionf.shio.vault.SensitiveClipboard
import top.zxionf.shio.vault.AutoLockTimeout
import top.zxionf.shio.vault.VaultSession
import top.zxionf.shio.ui.components.AppIcons
import top.zxionf.shio.ui.components.StackedList
import top.zxionf.shio.ui.components.AppIcon
import top.zxionf.shio.ui.theme.ListStackShapes
import top.zxionf.shio.ui.theme.ShioTheme

/** 自动锁定时长的选项见 [AutoLockTimeout]，默认「立即」。 */

/**
 * 设置页（M3E）。
 *
 * 分组：安全（自动锁定 / 修改主口令）、数据（导入导出 / 备份恢复 / 标签管理）、
 * 外观与体验、关于。偏好均已持久化（见 SPEC 的「偏好持久化」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onDemo: () -> Unit,
    onChangePassword: () -> Unit = {},
    onManageTags: () -> Unit = {},
    onSwitchVault: () -> Unit = {},
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var autoLockDialogVisible by remember { mutableStateOf(false) }
    var diagnosticsVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // 自动锁定时长持久化在 SharedPreferences（"session"），冷启动自动恢复。
    val autoLockPreferences = remember(context) { AutoLockPreferences.get(context) }
    val autoLockTimeout = autoLockPreferences.timeout
    // 解锁后的会话诊断：debug 才展示格式版本与同步身份，release 界面不暴露库内部标识。
    val session = VaultComponent.repository.session
    // 多库下路径来自当前会话，不再有全局固定路径。
    val databasePath = session?.databasePath ?: "未解锁"
    val debugDiagnostics = remember(session) {
        if (!BuildConfig.DEBUG || session == null) {
            emptyList()
        } else {
            listOf(
                "存储格式" to "format=${session.formatVersion}",
                "同步身份" to session.dbId.ifEmpty { "未启用" },
            )
        }
    }

    // 关于：应用版本、内核版本与 ABI、运行环境、数据库路径。
    // 内核版本/ABI 版本来自运行时 shio_version()/shio_abi_version()；提交号只是产物溯源。
    val aboutItems = remember {
        val appVersion = "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"
        val kernelVersion = KernelInfo.kernelVersion ?: "不可用"
        val abiVersion = KernelInfo.abiVersion?.let { running ->
            if (KernelInfo.abiVersionMatches) {
                "$running"
            } else {
                "$running（期望 ${KernelInfo.expectedAbiVersion}）"
            }
        } ?: "不可用"
        val kernelCommit = "${KernelInfo.KERNEL_NAME} @ " +
            if (BuildConfig.DEBUG) KernelInfo.kernelCommit else KernelInfo.shortCommit
        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "未知"
        val system = "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）"
        listOf(
            "版本" to appVersion,
            "内核版本" to kernelVersion,
            "ABI 版本" to abiVersion,
            "内核提交" to kernelCommit,
            "设备架构" to deviceAbi,
            "系统" to system,
        ) + debugDiagnostics + listOf(
            "数据库" to databasePath,
        )
    }

    // ---- 备份 / 恢复 ----
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var backupBusy by remember { mutableStateOf(false) }
    var restoreBusy by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var restoreDialogVisible by remember { mutableStateOf(false) }
    var stagedBackup by remember { mutableStateOf<java.io.File?>(null) }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // ---- 导入 / 导出 ----
    var exportBusy by remember { mutableStateOf(false) }
    var importBusy by remember { mutableStateOf(false) }

    fun startExport(format: VaultTransferFiles.Format, launcher: androidx.activity.result.ActivityResultLauncher<String>) {
        if (exportBusy || importBusy || backupBusy || restoreBusy) return
        AutoLockController.beginExternalInteraction()
        launcher.launch(VaultTransferFiles.defaultFileName(format))
    }

    fun runExport(uri: android.net.Uri, format: VaultTransferFiles.Format) {
        exportBusy = true
        scope.launch {
            try {
                val entries = VaultComponent.repository.entries()
                val tagNames = VaultComponent.repository.tags().associate { tag -> tag.id to tag.name }
                val count = VaultTransferFiles.exportTo(context, entries, tagNames, uri, format)
                notify("已导出 $count 条到 ${format.label} 文件（含明文密码，请妥善保管）")
            } catch (failure: Throwable) {
                VaultBackupFiles.discardTarget(context, uri)
                notify("导出失败：${failure.message ?: "未知错误"}")
            } finally {
                exportBusy = false
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(VaultTransferFiles.Format.CSV.mimeType),
    ) { uri ->
        AutoLockController.endExternalInteraction()
        if (uri != null) runExport(uri, VaultTransferFiles.Format.CSV)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(VaultTransferFiles.Format.JSON.mimeType),
    ) { uri ->
        AutoLockController.endExternalInteraction()
        if (uri != null) runExport(uri, VaultTransferFiles.Format.JSON)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        AutoLockController.endExternalInteraction()
        if (uri == null) return@rememberLauncherForActivityResult
        importBusy = true
        scope.launch {
            try {
                val parsed = VaultTransferFiles.importFrom(context, uri)
                if (parsed.entries.isEmpty() && parsed.skipped == 0) {
                    notify("文件里没有可识别的条目")
                } else {
                    val inserted = VaultComponent.repository.insertAll(parsed.entries)
                    // 内核写入失败的条目也要算进「跳过」。
                    val skipped = parsed.skipped + (parsed.entries.size - inserted)
                    notify("新增 $inserted 条，跳过 $skipped 条")
                }
            } catch (failure: Throwable) {
                notify("导入失败：${failure.message ?: "未知错误"}")
            } finally {
                importBusy = false
            }
        }
    }

    // 选保存位置：内核写临时文件后拷到用户选的 URI。
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        // 外部界面已返回，恢自动锁定判定。
        AutoLockController.endExternalInteraction()
        if (uri == null) return@rememberLauncherForActivityResult
        backupBusy = true
        scope.launch {
            try {
                val summary = VaultBackupFiles.backupTo(context, VaultComponent.repository, uri)
                notify("已备份 ${summary.rowCount} 条记录（${summary.dbBytes / 1024} KB）")
            } catch (failure: Throwable) {
                // 失败时清掉 SAF 刚建出的空文件，不留 0 字节残留。
                VaultBackupFiles.discardTarget(context, uri)
                notify(describeBackupFailure(failure, backup = true))
            } finally {
                backupBusy = false
            }
        }
    }

    // 选备份文件：先拷到缓存，再弹口信对话框。
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        AutoLockController.endExternalInteraction()
        if (uri == null) return@rememberLauncherForActivityResult
        restoreBusy = true
        scope.launch {
            try {
                stagedBackup = VaultBackupFiles.stageRestore(context, uri)
                restoreError = null
                restoreDialogVisible = true
            } catch (failure: Throwable) {
                notify(describeBackupFailure(failure, backup = false))
            } finally {
                restoreBusy = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("安全")
            StackedList(itemCount = 2) { index, shape ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    if (index == 0) {
                        SettingRow(
                            icon = AppIcons.Lock,
                            label = "自动锁定",
                            value = autoLockTimeout.label,
                            onClick = { autoLockDialogVisible = true },
                        )
                    } else {
                        SettingRow(
                            icon = AppIcons.Key,
                            label = "修改主口令",
                            value = "下次解锁使用新口令",
                            onClick = onChangePassword,
                        )
                    }
                }
            }

            SectionTitle("数据")
            StackedList(itemCount = 7) { index, shape ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    when (index) {
                        0 -> SettingRow(
                            icon = AppIcons.Copy,
                            label = "导出为 CSV",
                            value = if (exportBusy) "正在导出…" else "含明文密码，可导入回本应用",
                            onClick = { startExport(VaultTransferFiles.Format.CSV, exportCsvLauncher) },
                        )

                        1 -> SettingRow(
                            icon = AppIcons.Copy,
                            label = "导出为 JSON",
                            value = if (exportBusy) "正在导出…" else "字段更完整，便于备份与迁移",
                            onClick = { startExport(VaultTransferFiles.Format.JSON, exportJsonLauncher) },
                        )

                        2 -> SettingRow(
                            icon = AppIcons.Add,
                            label = "从文件导入",
                            value = if (importBusy) "正在导入…" else "新增条目，不覆盖现有数据",
                            onClick = {
                                if (!exportBusy && !importBusy && !backupBusy && !restoreBusy) {
                                    AutoLockController.beginExternalInteraction()
                                    importLauncher.launch(arrayOf("text/*", "application/json", "*/*"))
                                }
                            },
                        )

                        3 -> SettingRow(
                            icon = AppIcons.Shield,
                            label = "备份密码库",
                            value = if (backupBusy) "正在备份…" else "保存到文件，用当前主口令加密",
                            onClick = {
                                if (!backupBusy && !restoreBusy && !exportBusy && !importBusy) {
                                    // 先声明外部交互，否则「立即」锁定会在 SAF 弹出时销毁句柄。
                                    AutoLockController.beginExternalInteraction()
                                    backupLauncher.launch(VaultBackupFiles.defaultBackupName())
                                }
                            },
                        )

                        4 -> SettingRow(
                            icon = AppIcons.Edit,
                            label = "从备份恢复",
                            value = if (restoreBusy) "正在读取…" else "替换当前数据，需要备份的主口令",
                            onClick = {
                                if (!backupBusy && !restoreBusy && !exportBusy && !importBusy) {
                                    AutoLockController.beginExternalInteraction()
                                    restoreLauncher.launch(arrayOf("*/*"))
                                }
                            },
                        )

                        5 -> SettingRow(
                            icon = AppIcons.Notes,
                            label = "管理标签",
                            value = "新建、重命名或删除标签",
                            onClick = onManageTags,
                        )

                        else -> SettingRow(
                            icon = AppIcons.Shield,
                            label = "切换密码库",
                            value = "回到库列表，或新建另一个库",
                            onClick = onSwitchVault,
                        )
                    }
                }
            }

            SectionTitle("外观与体验")
            StackedList(itemCount = 2) { index, shape ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    if (index == 0) {
                        SettingRow(
                            icon = AppIcons.Palette,
                            label = "外观",
                            value = "主题与配色",
                            onClick = onAppearance,
                        )
                    } else {
                        SettingRow(
                            icon = AppIcons.AutoAwesome,
                            label = "原型 Demo",
                            value = "内核能力演示",
                            onClick = onDemo,
                        )
                    }
                }
            }

            SectionTitle("关于")
            StackedList(itemCount = aboutItems.size) { index, shape ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    val (label, value) = aboutItems[index]
                    AboutRow(
                        label = label,
                        value = value,
                        // 「版本」行长按进入诊断：release 也能看到库格式与同步身份，
                        // 单击不变（诊断入口是隐藏的，不进普通可见界面）。
                        modifier = if (index == 0) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (session != null) diagnosticsVisible = true
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }

    if (restoreDialogVisible) {
        RestoreConfirmDialog(
            busy = restoreBusy,
            errorText = restoreError,
            onDismiss = {
                if (!restoreBusy) {
                    restoreDialogVisible = false
                    VaultBackupFiles.clearStaged(stagedBackup)
                    stagedBackup = null
                    restoreError = null
                }
            },
            onConfirm = { password ->
                val staged = stagedBackup ?: return@RestoreConfirmDialog
                restoreBusy = true
                restoreError = null
                scope.launch {
                    try {
                        VaultComponent.repository.restoreInPlace(staged.absolutePath, password)
                        restoreDialogVisible = false
                        notify("已恢复，请用备份时的主口令解锁")
                        onRestored()
                    } catch (failure: Throwable) {
                        restoreError = describeBackupFailure(failure, backup = false)
                    } finally {
                        restoreBusy = false
                        VaultBackupFiles.clearStaged(staged)
                        stagedBackup = null
                    }
                }
            },
        )
    }

    if (diagnosticsVisible && session != null) {
        DiagnosticsDialog(session = session, onDismiss = { diagnosticsVisible = false })
    }

    if (autoLockDialogVisible) {
        AlertDialog(
            onDismissRequest = { autoLockDialogVisible = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("自动锁定") },
            text = {
                Column {
                    AutoLockTimeout.entries.forEach { option ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                RadioButton(
                                    selected = option == autoLockTimeout,
                                    onClick = {
                                        autoLockPreferences.updateTimeout(option)
                                        autoLockDialogVisible = false
                                    },
                                )
                            },
                            content = { Text(option.label, style = MaterialTheme.typography.bodyLarge) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { autoLockDialogVisible = false }) { Text("完成") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun SettingRow(
    icon: AppIcons,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            AppIcon(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        overlineContent = null,
        supportingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            AppIcon(
                icon = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        },
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

@Composable
private fun AboutRow(label: String, value: String, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier,
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
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SettingsPreview() {
    ShioTheme {
        SettingsScreen(onBack = {}, onAppearance = {}, onDemo = {})
    }
}

/**
 * 诊断信息弹窗：展示库格式与会话身份，可通过「复制」一次性带走。
 *
 * 内容来自解锁后的 [VaultComponent.repository.session] 与 [KernelInfo]；
 * 复制走与详情页一致的敏感标记，便于反馈问题时安全粘贴。
 */
@Composable
private fun DiagnosticsDialog(
    session: VaultSession,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val rows = remember(session) {
        listOf(
            "存储格式" to "format=${session.formatVersion}",
            "同步身份" to session.dbId.ifEmpty { "未启用" },
            "设备身份" to session.deviceId.ifEmpty { "未启用" },
            "内核版本" to (KernelInfo.kernelVersion ?: "不可用"),
            "ABI 版本" to (KernelInfo.abiVersion?.toString() ?: "不可用"),
            "数据库" to session.databasePath,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("诊断信息") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ListStackShapes.gap),
            ) {
                StackedList(itemCount = rows.size) { index, shape ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            content = {
                                Text(
                                    text = rows[index].first,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = rows[index].second,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val text = rows.joinToString(separator = "\n") { (k, v) -> "$k: $v" }
                    // 与详情页共用同一入口：打敏感标记 + 30 秒后自动清除。
                    SensitiveClipboard.copy(context, "诊断信息", text)
                },
            ) {
                Text("复制")
            }
        },
    )
}

/** 备份/恢复失败文案：错误码写进括号，方便真机对照。 */
private fun describeBackupFailure(failure: Throwable, backup: Boolean): String = when (failure) {
    is top.zxionf.shio.kernel.ShioException -> {
        val hint = when (failure.code) {
            top.zxionf.shio.kernel.ShioException.INVALID_KEY ->
                "备份的主口令不正确（INVALID_KEY (${failure.code})）"
            top.zxionf.shio.kernel.ShioException.CORRUPTED ->
                "备份文件损坏（CORRUPTED (${failure.code})）"
            top.zxionf.shio.kernel.ShioException.INVALID_ARGUMENT ->
                "所选位置或口令不可用（INVALID_ARGUMENT (${failure.code})）"
            top.zxionf.shio.kernel.ShioException.LOCKED ->
                "密码库被其它进程占用（LOCKED (${failure.code})）"
            top.zxionf.shio.kernel.ShioException.CLOSED ->
                "密码库已锁定（CLOSED (${failure.code})）"
            top.zxionf.shio.kernel.ShioException.IO ->
                "磁盘读写失败（IO (${failure.code})）"
            else -> "${if (backup) "备份" else "恢复"}失败（错误码 ${failure.code}）"
        }
        // 附上内核的原始描述：排查时比错误码更能说明问题（不含口令与正文）。
        val detail = failure.message?.takeIf { it.isNotBlank() }
        if (detail != null && detail !in hint) "$hint · $detail" else hint
    }
    else -> failure.message ?: if (backup) "备份失败" else "恢复失败"
}