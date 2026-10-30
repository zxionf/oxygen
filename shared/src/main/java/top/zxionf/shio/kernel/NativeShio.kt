package top.zxionf.shio.kernel

/**
 * `libshio_jni.so` 的 Kotlin 声明。只做类型映射，所有语义在 [ShioDatabase]。
 *
 * 加载顺序：先 `shio_ffi`，再 `shio_jni`，避免依赖解析在类加载阶段隐式发生。
 * 传入/返回的 JSON 与 UUID 都是 UTF-8 / 原始字节，由上层决定如何解释。
 */
object NativeShio {

    const val SUPPORTED_ABI_VERSION = 1

    init {
        System.loadLibrary("shio_ffi")
        System.loadLibrary("shio_jni")
        val version = abiVersion()
        if (version != SUPPORTED_ABI_VERSION) {
            throw ShioException(
                ShioException.UNSUPPORTED_FORMAT,
                "unsupported shio ABI version $version (expected $SUPPORTED_ABI_VERSION)",
            )
        }
    }

    external fun abiVersion(): Int

    /** 内核版本（编译期包版本，如 `0.2.0`）；来自 `shio_version()`，无需打开数据库。 */
    external fun kernelVersion(): String

    external fun open(path: String, password: ByteArray, optionsJson: ByteArray?): Long

    external fun close(handle: Long)

    external fun destroy(handle: Long)

    external fun insert(
        handle: Long,
        namespace: String,
        collection: String,
        json: ByteArray,
    ): ByteArray

    external fun put(
        handle: Long,
        namespace: String,
        collection: String,
        recordId: ByteArray,
        json: ByteArray,
    ): ByteArray

    external fun get(
        handle: Long,
        namespace: String,
        collection: String,
        recordId: ByteArray,
    ): ByteArray

    external fun delete(
        handle: Long,
        namespace: String,
        collection: String,
        recordId: ByteArray,
    ): Boolean

    external fun list(
        handle: Long,
        namespace: String,
        collection: String,
        after: ByteArray?,
        limit: Int,
        includeDeleted: Boolean,
    ): ByteArray

    external fun batch(
        handle: Long,
        namespace: String,
        collection: String,
        opsJson: ByteArray,
    ): ByteArray

    external fun stats(handle: Long): ByteArray

    /**
     * 改主口令：只重包裹密钥，不重写记录；旧口令此后不可用。
     * 口令以 UTF-8 字节传入，JNI 与本层都在调用后清零。
     */
    external fun changePassword(handle: Long, newPassword: ByteArray)

    /** 在线备份到 [outPath]（目标必须不存在），返回摘要 JSON。 */
    external fun backup(handle: Long, outPath: String): ByteArray

    /**
     * 从 [backupPath] 恢复到 [targetPath]（目标必须不存在或为空），返回新句柄。
     * 新句柄指向恢复后的库；调用方自己负责处理旧句柄与文件替换。
     */
    external fun restore(
        backupPath: String,
        targetPath: String,
        password: ByteArray,
        optionsJson: ByteArray?,
    ): Long

    /**
     * 启用同步，返回 `sync_info` JSON；首次建库必须紧接着调用。
     * [deviceId] 为 16 字节原始设备 ID；为 null 时内核生成随机设备 ID。
     */
    external fun syncEnable(handle: Long, deviceId: ByteArray?): ByteArray

    /** 同步摘要 JSON；未启用同步返回 `SHIO_SYNC_DISABLED`。 */
    external fun syncInfo(handle: Long): ByteArray

    external fun lastError(handle: Long): String
}
