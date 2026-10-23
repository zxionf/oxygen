package top.zxionf.shio.kernel

/**
 * 内核 C ABI 错误码（与 hydrogen `crates/shio-ffi/include/shio.h` 一致）。
 *
 * JNI 层把非 [OK] 的返回码连同 `shio_last_error` 的文本一起抛成这个异常，
 * 调用方按 [code] 分支处理，不要解析 [message]。
 */
class ShioException @JvmOverloads constructor(
    val code: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    companion object {
        const val OK = 0
        const val INVALID_ARGUMENT = -1
        const val INVALID_NAME = -2
        const val INVALID_KEY = -3
        const val NOT_FOUND = -4
        const val CLOSED = -5
        const val READ_ONLY = -6
        const val BUSY = -7
        const val LOCKED = -8
        const val VALUE_TOO_LARGE = -9
        const val CORRUPTED = -10
        const val UNSUPPORTED_FORMAT = -11
        const val SERIALIZE = -12
        const val DESERIALIZE = -13
        const val IO = -14
        const val DATABASE = -15
        const val INTERNAL = -16
        const val SYNC_DISABLED = -17
        const val SYNC_ENABLED = -18
        const val SYNC_RESYNC_REQUIRED = -19
        const val ROTATION_REQUIRED = -20
        const val PAIRING_EXPIRED = -21
        const val PAIRING_INVALID = -22
    }
}
