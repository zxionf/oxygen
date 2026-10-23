package top.zxionf.shio.kernel

/**
 * `libshio_jni.so` 的 Kotlin 声明。只做类型映射，所有语义在 [ShioDatabase]。
 *
 * 加载顺序：先 `shio_ffi`，再 `shio_jni`，避免依赖解析在类加载阶段隐式发生。
 * 传入/返回的 JSON 与 UUID 都是 UTF-8 / 原始字节，由上层决定如何解释。
 */
internal object NativeShio {

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

    external fun lastError(handle: Long): String
}
