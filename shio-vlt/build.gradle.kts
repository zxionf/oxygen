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
        versionCode = 6
        versionName = "0.2.0"
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
    // 内核包装层（JNI 与 ShioDatabase 等封装）统一放在 shared。
    implementation(project(":shared"))

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
