plugins {
    // AGP 9 内置 Kotlin 支持，library 模块无需额外应用 kotlin 插件。
    alias(libs.plugins.android.library)
}

// 内核包装层：JNI 薄层（cpp/）、随库分发的 libshio_ffi.so（jniLibs/）与 Kotlin 侧封装。
// 通用 UI 目前只占位（src/main/java/top/zxionf/shio/shared/ui），暂不引入 Compose 依赖。
android {
    // 与 Kotlin 包名（top.zxionf.shio.kernel）解耦：kernel 类保持原包名，
    // 这样消费方（shio-vlt）无需改动任何 import；R 类落在 shared 自己的命名空间。
    namespace = "top.zxionf.shio.shared"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 29

        // 内核溯源信息：入库的 jniLibs/libshio_ffi.so 来自该提交，ABI 版本与 NativeShio 一致。
        // 由 scripts/sync-shio.sh 在同步产物时自动改写，不要手改。
        buildConfigField(
            "String",
            "SHIO_KERNEL_COMMIT",
            "\"5f3e8e324cda0bf772e21ad7bcd2036435ebcdd4\"",
        )
        buildConfigField("int", "SHIO_ABI_VERSION", "1")

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                // 静态链接 C++ 运行时，避免额外打包 libc++_shared.so。
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // 允许使用系统 PATH 中 >= 3.22.1 的 CMake（当前为 4.4.4），
            // 不再依赖 SDK 内的 cmake 包。
            version = "3.22.1+"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // KernelInfo 读取 SHIO_KERNEL_COMMIT / SHIO_ABI_VERSION。
        buildConfig = true
    }

    packaging {
        // .so 按原样打包，不做压缩/重排，保证 16 KB 对齐不被破坏。
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // ShioDatabase 的挂起函数跑在 Dispatchers.IO 上。
    implementation(libs.kotlinx.coroutines.core)

    // 占位阶段无其他依赖；日后搬入通用主题/组件时在此加入 Compose。
}
