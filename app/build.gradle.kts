import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 内置 Kotlin 支持；Compose 编译器插件仍单独应用。
    alias(libs.plugins.kotlin.compose)
}

// 发布签名：从仓库根的 keystore.properties 读取（该文件已 gitignore，不入库）。
// 没配置的机器（如别人 clone 后构建 debug）不会因此失败，release 退回未签名产物。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val releaseKeystorePath = keystoreProperties.getProperty("storeFile")?.takeIf { it.isNotBlank() }
val hasReleaseKeystore = releaseKeystorePath != null && rootProject.file(releaseKeystorePath).exists()

android {
    namespace = "top.zxionf.shio"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "top.zxionf.shio"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "0.1.5"

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

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // 配了 keystore.properties 就签名，否则保持未签名（产物名带 unsigned）。
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // FakeVaultStore 用 BuildConfig.DEBUG 区分演示数据：release 不携带样例条目。
        buildConfig = true
    }

    packaging {
        // .so 直接按原样打包，不做压缩/重排，保证 16 KB 对齐不被破坏。
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // JVM 单测里 `org.json` 只有 stub（调用会报 not mocked），引入真实实现便于验证编解码。
    testImplementation(libs.org.json)
}
