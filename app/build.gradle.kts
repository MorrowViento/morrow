plugins {
    alias(libs.plugins.android.application)
    // AGP 9 起内置 Kotlin 支持，不再应用 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jiuxina.ying"
    compileSdk = 37

    defaultConfig {
        // 与 Flutter 版保持一致，确保升级安装覆盖旧包
        applicationId = "com.jiuxina.ying"
        // 决策 D1：Glasmio 强制 Android 13（API 33）+
        minSdk = 33
        targetSdk = 37
        versionCode = 40
        versionName = "4.0.0-alpha1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }

    lint {
        // M0 阶段放宽，M7 发布前收紧为 true（对齐 Glasmio 官方工程）
        abortOnError = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Glasmio（决策 D3：业务代码不得直接 import Glasmio，仅限 ui/glass/ 封装层）
    implementation(libs.glasmio.scaffold)
    implementation(libs.glasmio.preference)

    testImplementation(libs.junit)
}
