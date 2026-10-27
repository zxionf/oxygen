package top.zxionf.shio.ui.settings

import android.content.Context
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.zxionf.shio.vault.VaultEntry
import top.zxionf.shio.vault.VaultTransfer

/**
 * 导入导出的文件读写（与 UI 分离）。
 *
 * 工作方式与 [VaultBackupFiles] 一致：内核只认文件路径，而这里走的是纯文本，
 * 所以直接在 `Dispatchers.IO` 上读/写 SAF 的 `content://` 流。
 */
object VaultTransferFiles {

    /** 导出格式。 */
    enum class Format(val label: String, val extension: String, val mimeType: String) {
        CSV("CSV", "csv", "text/csv"),
        JSON("JSON", "json", "application/json"),
    }

    /** 默认文件名：`shio-export-20261027-1930.csv`。 */
    fun defaultFileName(format: Format): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "shio-export-$stamp.${format.extension}"
    }

    /** 把条目写成文本并交给用户选定的 URI。 */
    suspend fun exportTo(
        context: Context,
        entries: List<VaultEntry>,
        target: Uri,
        format: Format,
    ): Int = withContext(Dispatchers.IO) {
        val text = when (format) {
            Format.CSV -> VaultTransfer.encodeCsv(entries)
            Format.JSON -> VaultTransfer.encodeJson(entries)
        }
        val output = context.contentResolver.openOutputStream(target)
            ?: error("无法写入所选位置")
        output.use { stream -> stream.write(text.toByteArray(Charsets.UTF_8)) }
        entries.size
    }

    /** 读取用户选定的文件并解析。 */
    suspend fun importFrom(context: Context, source: Uri): VaultTransfer.ParseResult =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(source)
                ?: error("无法读取所选文件")
            val text = input.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
            VaultTransfer.detectAndDecode(text)
        }

    /** 导入完成后清理（当前没有临时文件，保留接口以便后续扩展）。 */
    fun clearStaged() = Unit
}
