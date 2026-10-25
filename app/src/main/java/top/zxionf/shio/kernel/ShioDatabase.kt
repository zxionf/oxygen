package top.zxionf.shio.kernel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * shio 数据库的 Kotlin 门面：句柄生命周期、单线程调度与 UUID 转换。
 *
 * 所有 native 调用都在 `Dispatchers.IO.limitedParallelism(1)` 上串行执行。
 * JNI 层另用句柄表和互斥量兜底，两层都假设调用方可能并发。
 *
 * 生命周期：`open`（自动销毁旧句柄）→ 操作 → `close`（可重复）→ `destroy`。
 * `close` 之后句柄仍有效，操作会得到 [ShioException.CLOSED]；`destroy` 之后
 * 句柄失效，重复 `destroy` 是空操作。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShioDatabase {

    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val handle = AtomicLong(0L)

    val isOpen: Boolean
        get() = handle.get() != 0L

    /** 内核 ABI 版本；调用会切换 Dispatchers.IO，避免主线程加载 native 库。 */
    suspend fun abiVersion(): Int = withContext(dispatcher) {
        NativeShio.abiVersion()
    }

    /**
     * 打开或创建数据库，返回不透明句柄。
     *
     * [password] 在 JVM 上是不可擦除的 String，转换出的字节数组在本方法返回前
     * 清零；这一限制已在设计中接受。[optionsJson] 默认 `{"create":true}`。
     */
    suspend fun open(
        path: String,
        password: String,
        optionsJson: String? = DEFAULT_OPTIONS,
    ): Long = withContext(dispatcher) {
        require(path.isNotEmpty()) { "database path must not be empty" }

        // 重新打开前先释放旧句柄，避免同一进程持有两个内核实例。
        val previous = handle.getAndSet(0L)
        if (previous != 0L) {
            NativeShio.destroy(previous)
        }

        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        try {
            val opened = NativeShio.open(
                path = path,
                password = passwordBytes,
                optionsJson = optionsJson?.toByteArray(Charsets.UTF_8),
            )
            handle.set(opened)
            opened
        } finally {
            passwordBytes.fill(0)
        }
    }

    /** 关闭内核并等待在途操作；句柄保留，可重复调用。 */
    suspend fun close() = withContext(dispatcher) {
        val current = handle.get()
        if (current != 0L) {
            NativeShio.close(current)
        }
    }

    /** 释放句柄；重复调用是空操作。之后 [open] 可再次建立新句柄。 */
    suspend fun destroy() = withContext(dispatcher) {
        val current = handle.getAndSet(0L)
        if (current != 0L) {
            NativeShio.destroy(current)
        }
    }

    suspend fun insert(namespace: String, collection: String, valueJson: String): String =
        withContext(dispatcher) {
            val idBytes = NativeShio.insert(
                requireHandle(),
                namespace,
                collection,
                valueJson.toByteArray(Charsets.UTF_8),
            )
            UuidCodec.encode(idBytes)
        }

    /** Upsert，返回内核的元信息 JSON。 */
    suspend fun put(
        namespace: String,
        collection: String,
        recordId: String,
        valueJson: String,
    ): String = withContext(dispatcher) {
        NativeShio.put(
            requireHandle(),
            namespace,
            collection,
            UuidCodec.decode(recordId),
            valueJson.toByteArray(Charsets.UTF_8),
        ).decodeToString()
    }

    /** 返回 `{"meta":{...},"value":...}`；墓碑视为不存在。 */
    suspend fun get(namespace: String, collection: String, recordId: String): String =
        withContext(dispatcher) {
            NativeShio.get(
                requireHandle(),
                namespace,
                collection,
                UuidCodec.decode(recordId),
            ).decodeToString()
        }

    /** 返回删除前记录是否存在。 */
    suspend fun delete(namespace: String, collection: String, recordId: String): Boolean =
        withContext(dispatcher) {
            NativeShio.delete(
                requireHandle(),
                namespace,
                collection,
                UuidCodec.decode(recordId),
            )
        }

    /**
     * 按 ID 升序分页，返回 `{"items":[meta...],"next":uuid|null}`。
     * [after] 为上一页的 `next`，[limit] 为 0 时使用内核默认值。
     */
    suspend fun list(
        namespace: String,
        collection: String,
        after: String? = null,
        limit: Int = 0,
        includeDeleted: Boolean = false,
    ): String = withContext(dispatcher) {
        NativeShio.list(
            requireHandle(),
            namespace,
            collection,
            after?.let(UuidCodec::decode),
            limit,
            includeDeleted,
        ).decodeToString()
    }

    /** 原子批量写，输入/输出都是 JSON 数组。 */
    suspend fun batch(namespace: String, collection: String, opsJson: String): String =
        withContext(dispatcher) {
            NativeShio.batch(
                requireHandle(),
                namespace,
                collection,
                opsJson.toByteArray(Charsets.UTF_8),
            ).decodeToString()
        }

    /** 返回 `{"format_version":...,"key_id":...,"db_bytes":...}`。 */
    suspend fun stats(): String = withContext(dispatcher) {
        NativeShio.stats(requireHandle()).decodeToString()
    }

    /**
     * 修改主口令：内核重包裹密钥，条目内容原样保留，旧口令立即失效。
     *
     * 口令在 JVM 上是不可擦除的 String，转成字节数组后在本方法返回前清零，
     * 与 [open] 的处理一致。
     */
    suspend fun changePassword(newPassword: String) = withContext(dispatcher) {
        require(newPassword.isNotEmpty()) { "new password must not be empty" }
        val bytes = newPassword.toByteArray(Charsets.UTF_8)
        try {
            NativeShio.changePassword(requireHandle(), bytes)
        } finally {
            bytes.fill(0)
        }
    }

    /**
     * 启用多端同步并把库升级到 `format=2`（不可回退），返回 `sync_info` JSON。
     * 只用于**首次建库**：内核要求新库 open 后立即调用并校验结果，
     * 否则会留下 format=1 的半成品（见 `.design/api.md`）。
     */
    suspend fun enableSync(deviceId: ByteArray? = null): String = withContext(dispatcher) {
        require(deviceId == null || deviceId.size == 16) { "device id must be 16 bytes" }
        NativeShio.syncEnable(requireHandle(), deviceId).decodeToString()
    }

    /** 同步摘要 JSON；未启用同步抛 [ShioException.SYNC_DISABLED]。 */
    suspend fun syncInfo(): String = withContext(dispatcher) {
        NativeShio.syncInfo(requireHandle()).decodeToString()
    }

    /**
     * 当前 worker 线程最近一次内核错误；仅用于排查，异常消息里已包含该内容。
     * 由于调度器不保证同一线程，返回值可能为空。
     */
    suspend fun lastError(): String = withContext(dispatcher) {
        val current = handle.get()
        NativeShio.lastError(if (current == 0L) 0L else current)
    }

    private fun requireHandle(): Long {
        val current = handle.get()
        if (current == 0L) {
            throw ShioException(ShioException.CLOSED, "database is not open")
        }
        return current
    }

    companion object {
        const val DEFAULT_OPTIONS = """{"create":true}"""
    }
}

/** 内核 UUID 是 16 字节原始值，Kotlin 侧用标准 8-4-4-4-12 字符串表示。 */
internal object UuidCodec {

    private const val HEX = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        require(bytes.size == 16) { "record id must be 16 bytes, was ${bytes.size}" }
        val text = StringBuilder(36)
        for (index in bytes.indices) {
            if (index == 4 || index == 6 || index == 8 || index == 10) {
                text.append('-')
            }
            val value = bytes[index].toInt() and 0xFF
            text.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return text.toString()
    }

    fun decode(text: String): ByteArray {
        require(
            text.length == 36 &&
                text[8] == '-' && text[13] == '-' && text[18] == '-' && text[23] == '-',
        ) { "invalid record id: $text" }
        val bytes = ByteArray(16)
        var byteIndex = 0
        var charIndex = 0
        while (charIndex < text.length) {
            if (text[charIndex] == '-') {
                charIndex++
                continue
            }
            val high = hexValue(text[charIndex])
            val low = hexValue(text[charIndex + 1])
            bytes[byteIndex++] = ((high shl 4) or low).toByte()
            charIndex += 2
        }
        check(byteIndex == 16) { "invalid record id: $text" }
        return bytes
    }

    private fun hexValue(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> throw IllegalArgumentException("invalid hex digit: $char")
    }
}
