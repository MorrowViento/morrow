plugins {
    alias(libs.plugins.android.application)
    // AGP 9 起内置 Kotlin 支持，不再应用 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// M7：可选的更新镜像前缀与正式签名，全部由外部注入，仓库里不放任何密钥。
// gradle.properties 里写 ying.updateMirrors=https://mirror-a/;https://mirror-b/ 即可启用镜像。
val updateMirrors: String = (project.findProperty("ying.updateMirrors") as? String).orEmpty()
val abiSplitEnabled: Boolean =
    (project.findProperty("ying.abiSplit") as? String)?.toBooleanStrictOrNull() ?: false
val releaseKeystore: String = System.getenv("YING_KEYSTORE")
    ?: (project.findProperty("ying.keystore") as? String).orEmpty()

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

        // 镜像前缀写进 BuildConfig，避免把第三方代理域名硬编码进代码（这类域名寿命短）。
        // 默认空 = 只走 GitHub API / jsDelivr / raw 三路直连。
        buildConfigField(
            "String",
            "UPDATE_MIRROR_PREFIXES",
            "\"${updateMirrors.replace("\"", "").replace("\\", "")}\"",
        )
    }

    signingConfigs {
        if (releaseKeystore.isNotBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("YING_KEYSTORE_PASSWORD").orEmpty()
                keyAlias = System.getenv("YING_KEY_ALIAS").orEmpty()
                keyPassword = System.getenv("YING_KEY_PASSWORD").orEmpty()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 没有注入 keystore 时保持未签名的 release，本地/CI 的 debug 构建完全不受影响
            if (releaseKeystore.isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // 分 ABI 打包只在显式开启时生效（-Pying.abiSplit=true）。
    // 默认关闭是为了让日常 assembleDebug 只产出一个 APK；真机安装与交付都按单包走。
    splits {
        abi {
            isEnable = abiSplitEnabled
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // M7：发布前收紧。当前基线是 0 errors / 11 warnings，warnings 不阻断构建。
        abortOnError = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Room schema 导出（迁移测试基线，M1 起纳入版本管理）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Glasmio（决策 D3：业务代码不得直接 import Glasmio，仅限 ui/glass/ 封装层）
    implementation(libs.glasmio.scaffold)
    implementation(libs.glasmio.preference)

    // 数据层（决策 D5：Room + DataStore）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // 旧数据迁移：解析 Flutter 版 JSON 结构
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
