plugins {
    alias(libs.plugins.android.application)
    // AGP 9 起内置 Kotlin 支持，不再应用 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// M7：可选的更新镜像前缀与正式签名，全部由外部注入，仓库里不放任何密钥。
// gradle.properties 里写 morrow.updateMirrors=https://mirror-a/;https://mirror-b/ 即可启用镜像。
val updateMirrors: String = (project.findProperty("morrow.updateMirrors") as? String).orEmpty()
val abiSplitEnabled: Boolean =
    (project.findProperty("morrow.abiSplit") as? String)?.toBooleanStrictOrNull() ?: false

/**
 * 签名参数：环境变量优先，其次用户级 `~/.gradle/gradle.properties`。
 * 仓库与项目目录里永远不放密钥；CI 用 Secrets 注入环境变量，本地推荐属性文件。
 */
fun secret(envName: String, propertyName: String): String =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(propertyName) as? String).orEmpty()

val releaseKeystore: String = secret("MORROW_KEYSTORE", "morrow.keystore")
val releaseStorePassword: String = secret("MORROW_KEYSTORE_PASSWORD", "morrow.storePassword")
val releaseKeyAlias: String = secret("MORROW_KEY_ALIAS", "morrow.keyAlias")
val releaseKeyPassword: String = secret("MORROW_KEY_PASSWORD", "morrow.keyPassword")

// keystore 路径允许写 `~/...`；Gradle 不会替你展开波浪号。
val releaseKeystoreFile: File? = releaseKeystore
    .takeIf { it.isNotBlank() }
    ?.let { File(it.replaceFirst("~", System.getProperty("user.home"))) }

if (releaseKeystoreFile != null) {
    check(releaseStorePassword.isNotBlank() && releaseKeyAlias.isNotBlank() && releaseKeyPassword.isNotBlank()) {
        "已指定 morrow.keystore，但缺少密码或别名。请设置 MORROW_KEYSTORE_PASSWORD / " +
            "MORROW_KEY_ALIAS / MORROW_KEY_PASSWORD，或写入 ~/.gradle/gradle.properties " +
            "的 morrow.storePassword / morrow.keyAlias / morrow.keyPassword。详见 docs/SIGNING.md"
    }
    check(releaseKeystoreFile.exists()) {
        "找不到 keystore：${releaseKeystoreFile.absolutePath}（检查 morrow.keystore / MORROW_KEYSTORE）"
    }
}

android {
    namespace = "com.morrowviento.morrow"
    compileSdk = 37

    defaultConfig {
        // 2026-11-12 品牌重塑时改为独立包名，不再与 Flutter 版共用沙盒（旧包不能覆盖升级）
        applicationId = "com.morrowviento.morrow"
        // 决策 D1：Glasmio 强制 Android 13（API 33）+
        minSdk = 33
        targetSdk = 37
        // 版本号只增不减：应用内更新靠 versionCode 判定，发版前必须上调
        versionCode = 68
        versionName = "4.0.0"
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
        releaseKeystoreFile?.let { keystore ->
            create("release") {
                storeFile = keystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // 代码裁完之后必然留下一批没人引用的资源（图标、字体、layout）。
            // 只在开启 minify 时生效，debug 完全不受影响。
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 没有注入 keystore 时保持未签名的 release，本地/CI 的 debug 构建完全不受影响
            if (releaseKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // 调试包不开 minify，但把 PNG 压一遍没有任何调试损失，安装包小一截、写入也快
            isCrunchPngs = true
        }
    }

    // 依赖里的许可证 / 元数据文件会被打进 APK，运行时用不到
    // （不排 *.kotlin_module：Kotlin 反射靠它定位顶层声明，删了会在奇怪的地方炸）
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }

    // 分 ABI 打包只在显式开启时生效（-Pmorrow.abiSplit=true）。
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
