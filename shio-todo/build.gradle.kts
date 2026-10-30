plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 待办事项提醒应用（占位）。
// 目前只保证可编译、可安装、可启动；真正的待办模型、提醒调度与数据层留待后续变更。
// 暂不依赖 :shared —— shared 里带 libshio_ffi.so（每 ABI 约 3.5 MB），
// 等本模块真正需要内核能力时再决定是否拆出独立的 UI 共享模块。
android {
    namespace = "top.zxionf.shio.todo"
    compileSdk = 37

    defaultConfig {
        applicationId = "top.zxionf.shio.todo"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // 占位阶段不做混淆；正式发版前再按实际依赖配置。
            isMinifyEnabled = false
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
