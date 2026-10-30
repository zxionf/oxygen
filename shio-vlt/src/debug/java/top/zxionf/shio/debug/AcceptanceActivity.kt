package top.zxionf.shio.debug

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import top.zxionf.shio.kernel.ShioDatabase
import top.zxionf.shio.kernel.ShioException
import java.io.File

/**
 * 仅 debug 变体的 P4 验收探针，用 `--es mode <lock|persist-write|persist-verify>` 触发。
 *
 * - `lock`：同一进程两个句柄打开同一路径，第二个必须返回 SHIO_LOCKED。
 * - `persist-write`：写入固定 marker 后销毁句柄，供脚本杀进程后验证。
 * - `persist-verify`：重新打开，确认 marker 与 stats 仍然一致。
 *
 * 结果写入 logcat 标签 `ShioAcceptance`，由 `scripts/acceptance.sh` 采集。
 * 不进入 release，不读取用户数据（固定使用 filesDir 下的探针库）。
 */
class AcceptanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val output = TextView(this).apply {
            gravity = Gravity.START
            textSize = 12f
            setPadding(32, 32, 32, 32)
        }
        setContentView(output)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LOCK
        lifecycleScope.launch {
            val message = runCatching { runProbe(mode) }
                .fold(
                    onSuccess = { "ACCEPT OK mode=$mode $it" },
                    onFailure = { "ACCEPT FAIL mode=$mode ${it.javaClass.simpleName}: ${it.message}" },
                )
            Log.i(TAG, message)
            output.text = message
        }
    }

    private suspend fun runProbe(mode: String): String = when (mode) {
        MODE_LOCK -> runLockProbe()
        MODE_PERSIST_WRITE -> runPersistWrite()
        MODE_PERSIST_VERIFY -> runPersistVerify()
        else -> error("unknown mode: $mode")
    }

    private suspend fun runLockProbe(): String {
        val path = File(filesDir, LOCK_DB).absolutePath
        removeDatabase(path)
        val first = ShioDatabase()
        val second = ShioDatabase()
        try {
            first.open(path, LOCK_PASSWORD)
            val locked = runCatching { second.open(path, LOCK_PASSWORD) }.exceptionOrNull()
            check(locked is ShioException && locked.code == ShioException.LOCKED) {
                "second open should fail with LOCKED, got $locked"
            }
            return "code=${ShioException.LOCKED}"
        } finally {
            second.destroy()
            first.destroy()
            removeDatabase(path)
        }
    }

    private suspend fun runPersistWrite(): String {
        val path = File(filesDir, PERSIST_DB).absolutePath
        removeDatabase(path)
        val database = ShioDatabase()
        try {
            database.open(path, PERSIST_PASSWORD)
            val id = database.insert(
                NAMESPACE,
                COLLECTION,
                JSONObject().put("text", PERSIST_MARKER).toString(),
            )
            return "id=$id"
        } finally {
            database.destroy()
        }
    }

    private suspend fun runPersistVerify(): String {
        val path = File(filesDir, PERSIST_DB).absolutePath
        val database = ShioDatabase()
        try {
            database.open(path, PERSIST_PASSWORD)
            val items = JSONObject(
                database.list(NAMESPACE, COLLECTION, limit = 50),
            ).optJSONArray("items") ?: JSONArray()

            var found = false
            for (index in 0 until items.length()) {
                val id = items.optJSONObject(index)?.optString("id").orEmpty()
                if (id.isEmpty()) continue
                val value = JSONObject(database.get(NAMESPACE, COLLECTION, id))
                    .optJSONObject("value")
                if (value?.optString("text") == PERSIST_MARKER) {
                    found = true
                    break
                }
            }
            check(found) { "marker not found after process restart" }

            val stats = JSONObject(database.stats())
            check(stats.optInt("format_version") == 1) { "unexpected format version: $stats" }
            check(stats.optString("key_id").isNotEmpty()) { "stats missing key_id: $stats" }
            check(stats.optLong("db_bytes") > 0L) { "stats db_bytes should be positive: $stats" }
            return "records=${items.length()} format=${stats.optInt("format_version")} " +
                "db_bytes=${stats.optLong("db_bytes")}"
        } finally {
            database.destroy()
        }
    }

    private fun removeDatabase(path: String) {
        File(path).delete()
        File("$path.lock").delete()
    }

    companion object {
        private const val TAG = "ShioAcceptance"
        private const val EXTRA_MODE = "mode"

        private const val MODE_LOCK = "lock"
        private const val MODE_PERSIST_WRITE = "persist-write"
        private const val MODE_PERSIST_VERIFY = "persist-verify"

        private const val NAMESPACE = "demo"
        private const val COLLECTION = "notes"
        private const val LOCK_DB = "lock-probe.db"
        private const val LOCK_PASSWORD = "lock-probe"
        private const val PERSIST_DB = "p4-persist.db"
        private const val PERSIST_PASSWORD = "p4-persist"
        private const val PERSIST_MARKER = "p4-persist-marker"
    }
}
