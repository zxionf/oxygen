plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP 9 起 Kotlin 支持由 android 插件内置，不再应用 org.jetbrains.kotlin.android。
    alias(libs.plugins.kotlin.compose) apply false
}
