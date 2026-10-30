package top.zxionf.shio.debug

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.zxionf.shio.kernel.NativeShio
import top.zxionf.shio.kernel.ShioDatabase
import top.zxionf.shio.kernel.ShioException
import java.io.File

/**
 * 仅 debug 变体的脚本化冒烟：open → insert → get → list → close → 再打开。
 *
 * 结果以 `ShioSmoke` 标签写入 logcat，由 `scripts/smoke.sh` 采集。
 * 不参与 release 构建，也不读取用户数据（固定使用 filesDir/smoke）。
 */
class SmokeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val output = TextView(this).apply {
            gravity = Gravity.START
            textSize = 12f
            setPadding(32, 32, 32, 32)
        }
        setContentView(output)

        lifecycleScope.launch {
            val message = runCatching { runSmoke() }
                .fold(
                    onSuccess = { "SMOKE OK $it" },
                    onFailure = { "SMOKE FAIL ${it.javaClass.simpleName}: ${it.message}" },
                )
            Log.i(TAG, message)
            output.text = message
        }
    }

    private suspend fun runSmoke(): String {
        val namespace = "demo"
        val collection = "notes"
        val password = "smoke-password"
        val directory = File(filesDir, "smoke").apply {
            deleteRecursively()
            check(mkdirs() || isDirectory) { "cannot create ${absolutePath}" }
        }
        val path = File(directory, "smoke.db").absolutePath
        val database = ShioDatabase()

        try {
            val handle = database.open(path, password)
            check(handle != 0L) { "open returned handle 0" }
            check(database.isOpen) { "database should be open" }

            val abi = NativeShio.abiVersion()
            check(abi == NativeShio.SUPPORTED_ABI_VERSION) { "unexpected ABI $abi" }

            val statsOpen = JSONObject(database.stats())
            check(statsOpen.getInt("format_version") == 1) { "unexpected format version: $statsOpen" }
            check(statsOpen.optString("key_id").isNotEmpty()) { "stats missing key_id: $statsOpen" }

            val recordId = database.insert(namespace, collection, """{"text":"hello smoke"}""")
            check(recordId.length == 36) { "unexpected record id $recordId" }

            val fetched = database.get(namespace, collection, recordId)
            check(fetched.contains("hello smoke")) { "get did not return inserted value: $fetched" }
            val meta = JSONObject(fetched).getJSONObject("meta")
            check(meta.getString("id") == recordId) { "meta id mismatch" }

            val page = database.list(namespace, collection, limit = 50)
            check(page.contains(recordId)) { "list did not include $recordId: $page" }
            val next = JSONObject(page).opt("next")
            check(next == null || next == JSONObject.NULL) { "unexpected next cursor $next" }

            val statsAfterInsert = JSONObject(database.stats())
            check(statsAfterInsert.getInt("format_version") == statsOpen.getInt("format_version")) {
                "format_version changed after insert: $statsOpen -> $statsAfterInsert"
            }
            check(statsAfterInsert.optString("key_id") == statsOpen.optString("key_id")) {
                "key_id changed after insert: $statsOpen -> $statsAfterInsert"
            }
            check(statsAfterInsert.getLong("db_bytes") >= statsOpen.getLong("db_bytes")) {
                "db_bytes shrank after insert: $statsOpen -> $statsAfterInsert"
            }
            check(statsAfterInsert.getLong("db_bytes") > 0L) {
                "stats db_bytes should be positive: $statsAfterInsert"
            }

            database.close()

            val closed = runCatching {
                database.insert(namespace, collection, """{"text":"after close"}""")
            }.exceptionOrNull()
            check(closed is ShioException && closed.code == ShioException.CLOSED) {
                "write after close should fail with CLOSED, got $closed"
            }
            // 重复关闭必须是空操作。
            database.close()

            val wrongPassword = runCatching {
                database.open(path, "wrong-password")
            }.exceptionOrNull()
            check(wrongPassword is ShioException && wrongPassword.code == ShioException.INVALID_KEY) {
                "wrong password should fail with INVALID_KEY, got $wrongPassword"
            }

            database.open(path, password)
            val reopened = database.get(namespace, collection, recordId)
            check(reopened.contains("hello smoke")) { "record lost after reopen: $reopened" }

            val statsReopened = JSONObject(database.stats())
            check(statsReopened.getInt("format_version") == statsOpen.getInt("format_version")) {
                "format_version changed after reopen: $statsOpen -> $statsReopened"
            }
            check(statsReopened.optString("key_id") == statsOpen.optString("key_id")) {
                "key_id changed after reopen: $statsOpen -> $statsReopened"
            }
            check(statsReopened.getLong("db_bytes") > 0L) {
                "db_bytes should be positive after reopen: $statsReopened"
            }
            database.destroy()

            return "abi=$abi id=$recordId format=${statsReopened.getInt("format_version")} " +
                "db_bytes=${statsReopened.getLong("db_bytes")}"
        } finally {
            database.destroy()
        }
    }

    companion object {
        private const val TAG = "ShioSmoke"
    }
}
