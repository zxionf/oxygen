package top.zxionf.shio.kernel

import top.zxionf.shio.BuildConfig

/**
 * 内核与运行环境的只读信息，供「设置 → 关于」展示。
 *
 * - 内核版本：运行时 `shio_version()`（编译期包版本，如 `0.2.0`），无需打开数据库；
 * - ABI 版本：运行时 `shio_abi_version()`，与构建约定的 [expectedAbiVersion] 对比；
 * - 内核提交：构建时由 Gradle 注入（`SHIO_KERNEL_COMMIT`），标注产物来源，用于溯源；
 * - native 库缺失或加载失败时各项为 null，界面显示「不可用」而不是崩溃。
 */
object KernelInfo {

    /** 内核仓库名，用于「内核提交」一行。 */
    const val KERNEL_NAME = "hydrogen"

    /** 构建时约定支持的 ABI 版本（与 [NativeShio.SUPPORTED_ABI_VERSION] 同步）。 */
    val expectedAbiVersion: Int = BuildConfig.SHIO_ABI_VERSION

    /** 入库的 `libshio_ffi.so` 对应的内核提交。 */
    val kernelCommit: String = BuildConfig.SHIO_KERNEL_COMMIT

    /** 提交短哈希（12 位），release 界面用。 */
    val shortCommit: String
        get() = kernelCommit.take(12)

    /** 运行时 `shio_abi_version()`；加载失败或版本不匹配为 null。 */
    val abiVersion: Int? by lazy {
        runCatching { NativeShio.abiVersion() }.getOrNull()
    }

    /** 运行时 `shio_version()`（如 `0.2.0`）；native 不可用时为 null。 */
    val kernelVersion: String? by lazy {
        runCatching { NativeShio.kernelVersion() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** 运行时 ABI 版本是否与构建约定一致。 */
    val abiVersionMatches: Boolean
        get() = abiVersion == expectedAbiVersion
}
