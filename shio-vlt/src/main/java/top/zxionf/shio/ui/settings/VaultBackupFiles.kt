package top.zxionf.shio.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.zxionf.shio.ui.components.AppTextField
import top.zxionf.shio.vault.BackupSummary
import top.zxionf.shio.vault.VaultRepository

/**
 * 备份/恢复的文件与流操作（与 UI 分离，便于阅读）。
 *
 * - 内核只接受文件系统路径，所以 SAF 的 `content://` URI 先落到应用缓存目录的临时文件；
 * - 备份：内核写临时文件 → 拷到用户选的 URI → 删临时文件；
 * - 恢复：拷用户选的 URI 到临时文件 → 交给内核恢复到旁路文件。
 */
object VaultBackupFiles {

    private const val BACKUP_TMP = "backup-tmp.db"
    private const val RESTORE_TMP = "restore-tmp.db"

    /** 备份时的默认文件名：`shio-backup-20261025-1830.db`。 */
    fun defaultBackupName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "shio-backup-$stamp.db"
    }

    /** 备份到用户选定的 URI，返回内核摘要。 */
    suspend fun backupTo(context: Context, repository: VaultRepository, target: Uri): BackupSummary {
        val tmp = File(context.cacheDir, BACKUP_TMP)
        tmp.delete()
        try {
            val summary = repository.backup(tmp.absolutePath)
            context.contentResolver.openOutputStream(target)?.use { output ->
                tmp.inputStream().use { input -> input.copyTo(output) }
            } ?: error("无法写入所选位置")
            return summary
        } finally {
            tmp.delete()
        }
    }

    /** 把备份拷到缓存并返回临时文件路径，供恢复使用。 */
    suspend fun stageRestore(context: Context, source: Uri): File {
        val tmp = File(context.cacheDir, RESTORE_TMP)
        tmp.delete()
        val input: InputStream = context.contentResolver.openInputStream(source)
            ?: error("无法读取所选文件")
        input.use { from ->
            tmp.outputStream().use { to -> from.copyTo(to as OutputStream) }
        }
        return tmp
    }

    /** 恢复完成后清理临时文件。 */
    fun clearStaged(file: File?) {
        file?.delete()
    }

    /**
     * 清理失败时 SAF 已创建出来的空文件：避免用户看到 0 字节的「备份」。
     * 删不掉就留着，不影响功能。
     */
    fun discardTarget(context: Context, target: Uri) {
        runCatching { context.contentResolver.delete(target, null, null) }
    }
}

/** 恢复前的确认对话框：说明会替换全部数据，并要求输入备份的主口令。 */
@Composable
fun RestoreConfirmDialog(
    busy: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    val canSubmit = password.isNotEmpty() && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("从备份恢复？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "当前密码库的全部条目会被备份中的内容替换，此操作不可撤销。" +
                        "请输入该备份当时的主口令。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "备份的主口令",
                    helperText = "恢复后需要用它解锁",
                    errorText = errorText,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit) onConfirm(password) }),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = canSubmit) {
                Text("恢复", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}
