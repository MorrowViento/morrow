// 朝夕 · Kotlin 重构 —— 根构建脚本
// 版本对齐 Glasmio 0.1.2 官方构建环境（决策 D6）
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 内置 Kotlin，无需 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose) apply false
}
