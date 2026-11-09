// ying · Kotlin 重构 —— 根构建脚本
// 版本对齐 Glasmio 0.1.2 官方构建环境（决策 D6）
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
