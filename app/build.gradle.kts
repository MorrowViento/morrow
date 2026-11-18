import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        versionCode = 73
        versionName = "4.2.0"
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

    // 冲突的 META-INF 许可由 generateRuntimeNotices 原文收集到 assets/licenses/runtime.txt。
    // 不排 *.kotlin_module：Kotlin 反射仍需这些元数据。
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

// 构建期代码与回归均留在本脚本；不新增生产/TestKit/buildSrc 依赖。
object RuntimeNoticePolicy {
    // 4：protobuf 增加「静态归属」义务（归档正文之外恒追加带上游版权行的全文）。
    const val version = 4
    const val protobuf = "androidx.datastore:datastore-preferences-external-protobuf:1.3.0"
    const val materialKolor = "com.materialkolor:material-color-utilities-android:4.1.1"
    const val glasmioNoticeSha256 = "e7327a8f2829e07f92e4c582d80a03c439ee2c55bea46a294490288b1ea820c1"
    const val glasmioNoticeSource =
        "https://github.com/NEORUAA/Glasmio/blob/3332b385a4c1578d93366471195a882d1f0173e1/THIRD_PARTY_NOTICES.md"

    // 精确到模块和解析版本；同 group 的新模块/新版本不能自动继承许可。
    val apacheCoordinates = setOf(
        "androidx.activity:activity-compose:1.13.0",
        "androidx.activity:activity-ktx:1.13.0",
        "androidx.activity:activity:1.13.0",
        "androidx.annotation:annotation-experimental:1.5.0",
        "androidx.annotation:annotation-jvm:1.10.0",
        "androidx.arch.core:core-common:2.2.0",
        "androidx.arch.core:core-runtime:2.2.0",
        "androidx.autofill:autofill:1.0.0",
        "androidx.collection:collection-jvm:1.6.0",
        "androidx.collection:collection-ktx:1.6.0",
        "androidx.compose.animation:animation-android:1.12.1",
        "androidx.compose.animation:animation-core-android:1.12.1",
        "androidx.compose.foundation:foundation-android:1.12.1",
        "androidx.compose.foundation:foundation-layout-android:1.12.1",
        "androidx.compose.material3:material3-android:1.4.0",
        "androidx.compose.material3:material3-window-size-class-android:1.4.0",
        "androidx.compose.material:material-icons-core-android:1.7.8",
        "androidx.compose.material:material-icons-extended-android:1.7.8",
        "androidx.compose.material:material-ripple-android:1.12.1",
        "androidx.compose.runtime:runtime-android:1.12.1",
        "androidx.compose.runtime:runtime-annotation-android:1.12.1",
        "androidx.compose.runtime:runtime-retain-android:1.12.1",
        "androidx.compose.runtime:runtime-saveable-android:1.12.1",
        "androidx.compose.ui:ui-android:1.12.1",
        "androidx.compose.ui:ui-geometry-android:1.12.1",
        "androidx.compose.ui:ui-graphics-android:1.12.1",
        "androidx.compose.ui:ui-text-android:1.12.1",
        "androidx.compose.ui:ui-unit-android:1.12.1",
        "androidx.compose.ui:ui-util-android:1.12.1",
        "androidx.concurrent:concurrent-futures:1.1.0",
        "androidx.core:core-ktx:1.18.0",
        "androidx.core:core-viewtree:1.0.0",
        "androidx.core:core:1.18.0",
        "androidx.customview:customview-poolingcontainer:1.0.0",
        "androidx.datastore:datastore-android:1.3.0",
        "androidx.datastore:datastore-core-android:1.3.0",
        "androidx.datastore:datastore-core-okio-jvm:1.3.0",
        "androidx.datastore:datastore-preferences-android:1.3.0",
        "androidx.datastore:datastore-preferences-core-android:1.3.0",
        "androidx.datastore:datastore-preferences-proto:1.3.0",
        "androidx.emoji2:emoji2:1.4.0",
        "androidx.graphics:graphics-path:1.0.1",
        "androidx.interpolator:interpolator:1.0.0",
        "androidx.lifecycle:lifecycle-common-java8:2.10.0",
        "androidx.lifecycle:lifecycle-common-jvm:2.10.0",
        "androidx.lifecycle:lifecycle-livedata-core:2.10.0",
        "androidx.lifecycle:lifecycle-process:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-android:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-compose-android:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-ktx-android:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-android:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-compose-android:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.10.0",
        "androidx.navigation3:navigation3-runtime-android:1.2.0",
        "androidx.navigationevent:navigationevent-android:1.1.2",
        "androidx.navigationevent:navigationevent-compose-android:1.1.2",
        "androidx.profileinstaller:profileinstaller:1.4.0",
        "androidx.room:room-common-jvm:2.8.5",
        "androidx.room:room-ktx:2.8.5",
        "androidx.room:room-runtime-android:2.8.5",
        "androidx.savedstate:savedstate-android:1.4.0",
        "androidx.savedstate:savedstate-compose-android:1.4.0",
        "androidx.savedstate:savedstate-ktx:1.4.0",
        "androidx.sqlite:sqlite-android:2.6.2",
        "androidx.sqlite:sqlite-framework-android:2.6.2",
        "androidx.startup:startup-runtime:1.1.1",
        "androidx.tracing:tracing-android:2.0.1",
        "androidx.versionedparcelable:versionedparcelable:1.1.1",
        "androidx.window:window-core-android:1.5.0",
        "androidx.window:window:1.5.0",
        "com.google.code.gson:gson:2.11.0",
        "com.google.errorprone:error_prone_annotations:2.27.0",
        "com.google.guava:listenablefuture:1.0",
        "com.squareup.okio:okio-jvm:3.17.0",
        "dev.drewhamilton.poko:poko-annotations-jvm:0.21.1",
        "io.github.kyant0:capsule:2.1.3",
        "io.github.kyant0:shapes-android:1.2.1",
        "io.github.neoruaa.glasmio:glasmio-core:0.1.3",
        "io.github.neoruaa.glasmio:glasmio-glass:0.1.3",
        "io.github.neoruaa.glasmio:glasmio-icons:0.1.3",
        "io.github.neoruaa.glasmio:glasmio-preference:0.1.3",
        "io.github.neoruaa.glasmio:glasmio-scaffold:0.1.3",
        "io.github.neoruaa.glasmio:glasmio-ui:0.1.3",
        "org.jetbrains.compose.runtime:runtime-android:1.12.0",
        "org.jetbrains.compose.runtime:runtime-saveable-android:1.12.0",
        "org.jetbrains.compose.ui:ui-android:1.12.0",
        "org.jetbrains.compose.ui:ui-geometry-android:1.12.0",
        "org.jetbrains.compose.ui:ui-graphics-android:1.12.0",
        "org.jetbrains.compose.ui:ui-text-android:1.12.0",
        "org.jetbrains.compose.ui:ui-unit-android:1.12.0",
        "org.jetbrains.compose.ui:ui-util-android:1.12.0",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0",
        "org.jetbrains.kotlin:kotlin-stdlib:2.4.21",
        "org.jetbrains.kotlinx:atomicfu-jvm:0.28.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3",
        "org.jetbrains:annotations:23.0.0",
        "org.jspecify:jspecify:1.0.0",
        "sh.calvin.reorderable:reorderable-android:3.1.0",
        "top.yukonga.miuix.kmp:miuix-core-android:0.9.3",
        "top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3",
        "top.yukonga.miuix.kmp:miuix-shader-android:0.9.3",
        "top.yukonga.miuix.kmp:miuix-squircle-android:0.9.3",
        "top.yukonga.miuix.kmp:miuix-ui-android:0.9.3",
    )

    // 2026-11-18：35 个缺口已全部补上静态原文（apache-2.0.txt / materialkolor.txt /
    // material-color-utilities.txt），因此本名单必须为空。任何新增缺口都会让
    // requireKnownGaps 失败并中止 generateRuntimeNotices：缺口不是发行豁免，
    // 必须补原文并在审核记录里显式说明，才允许继续打包。
    val expectedTextGaps = emptySet<String>()

    fun license(coordinate: String): String = when (coordinate) {
        protobuf -> "BSD-3-Clause"
        materialKolor -> "MIT（MaterialKolor 发布声明；MCU 源码部分另为 Apache-2.0）"
        in apacheCoordinates -> "Apache-2.0"
        else -> error("尚未审核精确运行时坐标：$coordinate；禁止按 group 自动继承许可")
    }

    fun fallbackFiles(coordinate: String): List<String> = when (coordinate) {
        materialKolor -> listOf("materialkolor.txt", "material-color-utilities.txt")
        in apacheCoordinates -> listOf("apache-2.0.txt")
        protobuf -> emptyList() // 当前版本原始 BSD 必须来自归档；消失即新增缺口并失败。
        else -> error("没有静态正文映射：$coordinate")
    }

    // 与 fallbackFiles 的区别：fallbackFiles 只在「归档里没有任何文本」时补缺口；
    // attributionFiles 是**无条件追加**的归属义务——归档有正文也要加。
    // 触发条件是归档正文缺少版权行、无法满足协议自身的保留条款：
    // protobuf 的 BSD-3 把「Google Inc.」改成「the copyright holder」，也没有 Copyright 行，
    // 原样转发只是「不改上游文件」，不构成 BSD-3 第 1 条的合规。
    fun attributionFiles(coordinate: String): List<String> = when (coordinate) {
        protobuf -> listOf("protobuf-bsd-3.txt")
        else -> emptyList()
    }

    fun requireKnownGaps(actual: Set<String>, expected: Set<String> = expectedTextGaps) {
        require(actual == expected) {
            "许可正文缺口变化：未声明=${(actual - expected).sorted()}；已过期=${(expected - actual).sorted()}；" +
                "补原文并显式更新审核名单，不得静默跳过"
        }
    }
}

class RuntimeNoticeCollector {
    data class Original(val bytes: ByteArray, val sources: MutableSet<String> = sortedSetOf())
    private val originals = sortedMapOf<String, Original>()
    val originalCount: Int get() = originals.size
    val sourceCount: Int get() = originals.values.sumOf { it.sources.size }

    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun text(bytes: ByteArray, source: String): String {
            require(bytes.isNotEmpty()) { "许可文本为空：$source" }
            val decoded = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (error: java.nio.charset.CharacterCodingException) {
                throw IllegalArgumentException("许可不是合法 UTF-8：$source", error)
            }
            require(decoded.isNotBlank()) { "许可文本只有空白，不能作为正文覆盖：$source" }
            require(decoded.none { it.isISOControl() && it !in "\n\r\t" }) {
                "许可文件含二进制控制字符：$source"
            }
            return decoded // 不 trim、不归一化换行/BOM，原字节不同就不合并。
        }

        fun isNotice(name: String): Boolean {
            val base = name.substringAfterLast('/').uppercase(Locale.ROOT)
            if (base in setOf("AL2.0", "LGPL2.1")) return true
            val dot = base.lastIndexOf('.')
            if (dot >= 0 && base.substring(dot + 1) !in setOf("TXT", "MD", "MARKDOWN", "RST", "HTML")) {
                return false
            }
            val stem = if (dot >= 0) base.substring(0, dot) else base
            return stem in setOf("LICENSE", "LICENCE", "NOTICE", "NOTICES", "COPYING", "COPYRIGHT") ||
                Regex("^(LICENSE|LICENCE|NOTICE|NOTICES|COPYING|COPYRIGHT)[_-][A-Z0-9][A-Z0-9._-]*$")
                    .matches(stem) || Regex("^THIRD[_-]PARTY[_-]NOTICES?$").matches(stem)
        }

        fun isRuntimeJar(name: String): Boolean =
            name == "classes.jar" || Regex("^libs/[^/]+\\.jar$").matches(name)

        fun staticText(bytes: ByteArray, source: String): String {
            val result = text(bytes, source)
            require(Regex("(?m)^Source: https://\\S+\\s*$").containsMatchIn(result) &&
                Regex("(?m)^License: \\S[^\\r\\n]*$").containsMatchIn(result)) {
                "静态许可缺 Source/License 元数据：$source"
            }
            return result
        }

        fun requireGlasmioSource(bytes: ByteArray) {
            require(sha256(bytes) == RuntimeNoticePolicy.glasmioNoticeSha256) {
                "Glasmio v0.1.3 第三方声明与锁定上游原文不一致"
            }
        }
    }

    fun add(bytes: ByteArray, source: String) {
        text(bytes, source)
        val key = sha256(bytes)
        val document = originals.getOrPut(key) { Original(bytes.copyOf()) }
        require(document.bytes.contentEquals(bytes)) { "许可原文摘要冲突：$source" }
        document.sources.add(source)
    }

    fun collectArchive(bytes: ByteArray, source: String, aar: Boolean): Int {
        val hasEnd = bytes.size >= 22 && (maxOf(0, bytes.size - 65557)..bytes.size - 22).any { offset ->
            bytes[offset] == 0x50.toByte() && bytes[offset + 1] == 0x4b.toByte() &&
                bytes[offset + 2] == 5.toByte() && bytes[offset + 3] == 6.toByte() &&
                offset + 22 + (bytes[offset + 20].toInt() and 0xff) +
                ((bytes[offset + 21].toInt() and 0xff) shl 8) == bytes.size
        }
        require(hasEnd && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
            ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) ||
                (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()))) { "不是完整有效 ZIP 归档：$source" }
        var count = 0
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && isNotice(name)) {
                    add(zip.readBytes(), "$source!/$name")
                    count++
                } else if (aar && !entry.isDirectory && isRuntimeJar(name)) {
                    // AAR 的运行时入口只进一层；JAR 内再嵌的工具/资源归档不属于运行时。
                    count += collectArchive(zip.readBytes(), "$source!/$name", aar = false)
                }
                entry = zip.nextEntry
            }
        }
        return count
    }

    fun render(): String = buildString {
        originals.forEach { (sha, document) ->
            append("\n===== ORIGINAL $sha ${document.bytes.size} bytes =====\n")
            append("Sources (all retained):\n")
            document.sources.forEach { append("- $it\n") }
            append("----- BEGIN ORIGINAL -----\n")
            append(text(document.bytes, sha))
            append("\n----- END ORIGINAL -----\n")
        }
    }
}

val verifyRuntimeNotices = tasks.register("verifyRuntimeNotices") {
    group = "verification"
    description = "在内存验证许可筛选、运行时归档、去重、版本与正文缺口规则"
    doLast {
        var passed = 0
        fun regression(name: String, body: () -> Unit) {
            try { body() } catch (error: Exception) { throw GradleException("许可回归失败：$name", error) }
            passed++
        }
        fun rejected(body: () -> Unit) {
            check(runCatching(body).exceptionOrNull() is IllegalArgumentException)
        }
        fun archive(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, value) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                    zip.write(value)
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
        val license = "Original license\n".toByteArray()
        val notice = "Distinct NOTICE\n".toByteArray()
        regression("protobuf BSD 优先于 AndroidX") {
            check(RuntimeNoticePolicy.license(RuntimeNoticePolicy.protobuf) == "BSD-3-Clause")
            check(RuntimeNoticePolicy.license("androidx.datastore:datastore-android:1.3.0") == "Apache-2.0")
        }
        regression("MaterialKolor 不丢 MIT/MCU 的各自声明") {
            val value = RuntimeNoticePolicy.license(RuntimeNoticePolicy.materialKolor)
            check(value.startsWith("MIT") && "Apache-2.0" in value)
        }
        regression("已知 group 的新模块失败") {
            check(runCatching { RuntimeNoticePolicy.license("androidx.datastore:unreviewed:1.3.0") }
                .exceptionOrNull() is IllegalStateException)
        }
        regression("已知模块的新版本失败") {
            check(runCatching { RuntimeNoticePolicy.license("io.github.neoruaa.glasmio:glasmio-ui:0.1.4") }
                .exceptionOrNull() is IllegalStateException)
        }
        regression("严格文本名称与第三方 NOTICES") {
            listOf("LICENSE", "license.txt", "License-Apache", "NOTICE.md", "COPYING", "COPYRIGHT.txt",
                "THIRD_PARTY_NOTICES.md", "third-party-notice.txt", "AL2.0", "LGPL2.1")
                .forEach { check(RuntimeNoticeCollector.isNotice(it)) }
        }
        regression("二进制后缀及 CopyrightKt 不匹配") {
            listOf("CopyrightKt.class", "COPYRIGHT.class", "LICENSE.jar", "NOTICE.png", "LICENSE.bin",
                "NOTICES_HELPER.class").forEach { check(!RuntimeNoticeCollector.isNotice(it)) }
        }
        regression("标题前缀不能误收普通代码") {
            listOf("CopyrightKt", "LicenseManager", "NoticeView").forEach {
                check(!RuntimeNoticeCollector.isNotice(it))
            }
        }
        regression("包目录外的原始文本同样保留") {
            val collector = RuntimeNoticeCollector()
            check(collector.collectArchive(archive("com/example/LICENSE" to license), "jar", false) == 1)
        }
        regression("AAR 只递归 classes 与平铺 libs，排除 lint/工具") {
            val jar = archive("META-INF/LICENSE" to license)
            val collector = RuntimeNoticeCollector()
            val count = collector.collectArchive(archive("classes.jar" to jar, "libs/runtime.jar" to jar,
                "lint.jar" to jar, "assets/tools.jar" to jar, "libs/nested/tool.jar" to jar,
                "META-INF/NOTICE.txt" to notice), "aar", true)
            check(count == 3 && collector.originalCount == 2 && collector.sourceCount == 3)
            check("!/lint.jar!/" !in collector.render() && "tools.jar!/" !in collector.render())
        }
        regression("普通 JAR 不递归嵌套 JAR") {
            val collector = RuntimeNoticeCollector()
            check(collector.collectArchive(archive("libs/nested.jar" to archive("LICENSE" to license)),
                "jar", false) == 0)
        }
        regression("无效归档拒绝而非当空目录") {
            rejected { RuntimeNoticeCollector().collectArchive(byteArrayOf(1, 2, 3, 4), "invalid", false) }
        }
        regression("命中文本名称的二进制控制字符失败") {
            rejected { RuntimeNoticeCollector().collectArchive(archive("LICENSE.txt" to byteArrayOf(0)),
                "binary", false) }
        }
        regression("命中文本名称的非法 UTF-8 失败") {
            rejected { RuntimeNoticeCollector().add(byteArrayOf(0xc3.toByte(), 0x28), "bad UTF-8") }
        }
        regression("空文件或只有空白不能伪装成正文") {
            rejected { RuntimeNoticeCollector().add(byteArrayOf(), "empty") }
            rejected { RuntimeNoticeCollector().add(" \n\t".toByteArray(), "blank") }
        }
        regression("逐字节去重且保留全部不同来源") {
            val collector = RuntimeNoticeCollector()
            collector.add(license, "B")
            collector.add(license, "A")
            check(collector.originalCount == 1 && collector.sourceCount == 2)
            check("- A\n- B\n" in collector.render())
        }
        regression("不同 NOTICE 不合并") {
            val collector = RuntimeNoticeCollector()
            collector.add(license, "LICENSE")
            collector.add(notice, "NOTICE")
            check(collector.originalCount == 2 && collector.sourceCount == 2)
        }
        regression("换行 BOM 和末尾空白不同则各自保留") {
            val collector = RuntimeNoticeCollector()
            listOf("Original license\n", "Original license\r\n", "\uFEFFOriginal license\n",
                "Original license\n ").forEachIndexed { index, value ->
                collector.add(value.toByteArray(), "source-$index")
            }
            check(collector.originalCount == 4)
        }
        regression("输入遍历顺序不改变正文分组与来源顺序") {
            val first = RuntimeNoticeCollector()
            val second = RuntimeNoticeCollector()
            first.collectArchive(archive("LICENSE" to license, "NOTICE" to notice), "same", false)
            second.collectArchive(archive("NOTICE" to notice, "LICENSE" to license), "same", false)
            check(first.render() == second.render())
        }
        regression("Glasmio 锁定原文被修改则失败") {
            rejected { RuntimeNoticeCollector.requireGlasmioSource("replaced".toByteArray()) }
        }
        regression("新增正文缺口失败") {
            rejected { RuntimeNoticePolicy.requireKnownGaps(setOf("new"), emptySet()) }
        }
        regression("已过期缺口名单失败") {
            rejected { RuntimeNoticePolicy.requireKnownGaps(emptySet(), setOf("stale")) }
        }
        regression("准确已知缺口仅作为审计状态保留") {
            RuntimeNoticePolicy.requireKnownGaps(setOf("known"), setOf("known"))
        }
        regression("静态补充要求来源与许可元数据") {
            rejected { RuntimeNoticeCollector.staticText(license, "missing metadata") }
            check(RuntimeNoticeCollector.staticText(
                "Source: https://example.org/tag/LICENSE\nLicense: Apache-2.0\n\nOriginal\n".toByteArray(),
                "static").endsWith("Original\n"))
        }
        regression("MaterialKolor 两份静态原文均是必要输入") {
            check(RuntimeNoticePolicy.fallbackFiles(RuntimeNoticePolicy.materialKolor) ==
                listOf("materialkolor.txt", "material-color-utilities.txt"))
            check(RuntimeNoticePolicy.fallbackFiles("androidx.core:core:1.18.0") == listOf("apache-2.0.txt"))
            check(RuntimeNoticePolicy.fallbackFiles(RuntimeNoticePolicy.protobuf).isEmpty())
        }
        regression("protobuf 的归属补充与缺口补充是两条独立义务") {
            check(RuntimeNoticePolicy.attributionFiles(RuntimeNoticePolicy.protobuf) ==
                listOf("protobuf-bsd-3.txt"))
            check(RuntimeNoticePolicy.attributionFiles("androidx.core:core:1.18.0").isEmpty())
            check(RuntimeNoticePolicy.attributionFiles(RuntimeNoticePolicy.materialKolor).isEmpty())
            // 归档正文仍不允许消失：消失就是新增缺口，不能被归属补充掩盖。
            check(RuntimeNoticePolicy.fallbackFiles(RuntimeNoticePolicy.protobuf).isEmpty())
        }
        regression("protobuf 静态归属含版权行与完整上游 BSD-3") {
            val file = layout.projectDirectory.dir("src/main/assets/licenses/protobuf-bsd-3.txt").asFile
            check(file.isFile) { "缺少静态归属正文：assets/licenses/protobuf-bsd-3.txt" }
            val rendered = RuntimeNoticeCollector.staticText(file.readBytes(), "protobuf-bsd-3.txt")
            listOf(
                "Copyright 2008 Google Inc.",
                "License: BSD-3-Clause",
                "Redistribution and use in source and binary forms",
                "Neither the name of Google Inc.",
                "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS",
                "Code generated by the Protocol Buffer compiler is owned by the owner",
            ).forEach { check(it in rendered) { "protobuf-bsd-3.txt 缺少必需内容：$it" } }
            // 归档模板把主体写成 the copyright holder；归属正文必须是上游原文，不能拿模板充数。
            check("Neither the name of the copyright holder" !in rendered)
        }
        regression("缺口名单已收敛为空，缺口不再被放行") {
            check(RuntimeNoticePolicy.expectedTextGaps.isEmpty())
            check(runCatching { RuntimeNoticePolicy.requireKnownGaps(setOf("any")) }.exceptionOrNull()
                is IllegalArgumentException)
        }
        regression("四份静态正文存在、可读且带来源与许可元数据") {
            val dir = layout.projectDirectory.dir("src/main/assets/licenses").asFile
            val expectations = mapOf(
                "apache-2.0.txt" to listOf(
                    "Source: https://www.apache.org/licenses/LICENSE-2.0.txt",
                    "License: Apache-2.0",
                    "END OF TERMS AND CONDITIONS"),
                "materialkolor.txt" to listOf(
                    "Source: https://github.com/jordond/MaterialKolor/blob/4.1.1/LICENSE",
                    "License: MIT",
                    "Copyright (c) 2025 Jordon de Hoog"),
                "material-color-utilities.txt" to listOf(
                    "Source: https://github.com/material-foundation/material-color-utilities/blob/main/LICENSE",
                    "License: Apache-2.0",
                    "Copyright 2021 Google LLC"),
                "protobuf-bsd-3.txt" to listOf(
                    "Source: https://github.com/protocolbuffers/protobuf/blob/v3.25.5/LICENSE",
                    "License: BSD-3-Clause",
                    "Copyright 2008 Google Inc."),
            )
            expectations.forEach { (name, markers) ->
                val file = dir.resolve(name)
                check(file.isFile) { "缺少静态许可正文：assets/licenses/$name" }
                val rendered = RuntimeNoticeCollector.staticText(file.readBytes(), name)
                markers.forEach { check(it in rendered) { "$name 缺少必需内容：$it" } }
            }
            // 混合坐标的 MIT 与 MCU Apache 必须是两份不同正文，不能互相顶替。
            check(!dir.resolve("materialkolor.txt").readBytes()
                .contentEquals(dir.resolve("material-color-utilities.txt").readBytes()))
        }
        logger.lifecycle("许可收集器 $passed 项内存回归通过；不是应用全量单测报告")
    }
}

// 只读 releaseRuntimeClasspath；运行时归档路径/坐标、策略和静态原文都声明为输入。
val runtimeNoticesDir = layout.buildDirectory.dir("generated/runtimeNotices")
val releaseRuntime = provider { configurations.getByName("releaseRuntimeClasspath") }
val rootLicense = rootProject.layout.projectDirectory.file("LICENSE")
val licenseSourceDir = layout.projectDirectory.dir("src/main/assets/licenses")
val glasmioNoticeFile = licenseSourceDir.file("glasmio-third-party-notices.txt")
val generateRuntimeNotices = tasks.register("generateRuntimeNotices") {
    dependsOn(verifyRuntimeNotices)
    inputs.files(releaseRuntime).withPropertyName("releaseRuntimeArtifacts")
    inputs.property("releaseRuntimeCoordinates", provider {
        releaseRuntime.get().resolvedConfiguration.resolvedArtifacts.map {
            "${it.moduleVersion.id}|${it.file.name}|${it.classifier.orEmpty()}|${it.extension}"
        }.sorted()
    })
    inputs.file(layout.projectDirectory.file("build.gradle.kts")).withPropertyName("noticesGenerator")
    inputs.property("noticesPolicyVersion", RuntimeNoticePolicy.version)
    inputs.file(rootLicense).withPropertyName("rootMitLicense")
    inputs.dir(licenseSourceDir).withPropertyName("staticNoticeSources")
    outputs.dir(runtimeNoticesDir)
    doLast {
        val artifacts = releaseRuntime.get().resolvedConfiguration.resolvedArtifacts.sortedWith(
            compareBy({ it.moduleVersion.id.toString() }, { it.file.name }, { it.classifier.orEmpty() },
                { it.extension }, { RuntimeNoticeCollector.sha256(it.file.readBytes()) }))
            // 同一坐标 / 同一文件偶尔会在解析结果里出现两次（`androidx.core:core:1.18.0` 实测如此）。
            // 不去重的话清单会连着打印两条完全相同的记录，artifact 计数也就比坐标数虚高一个。
            .distinctBy { listOf(it.moduleVersion.id.toString(), it.file.name, it.classifier.orEmpty()) }
        val collector = RuntimeNoticeCollector()
        val rootBytes = rootLicense.asFile.readBytes()
        collector.add(rootBytes, "朝夕根 LICENSE — MIT；APK 副本 assets/licenses/morrow.txt")
        val glasmioBytes = glasmioNoticeFile.asFile.readBytes()
        RuntimeNoticeCollector.requireGlasmioSource(glasmioBytes)
        collector.add(glasmioBytes, "Glasmio v0.1.3 锁定第三方声明；${RuntimeNoticePolicy.glasmioNoticeSource}")
        val gaps = sortedSetOf<String>()
        var archiveTexts = 0
        val manifest = buildString {
            append("朝夕发行运行时依赖及原始随包声明\n\n")
            append("按实际解析的发行 artifact 生成；不表示 R8 保留所有类，不包含测试或 lint 工具依赖。\n")
            append("协议标签来自精确坐标审核，不替代正文；正文按原字节分组，不删除来源/不同 NOTICE。\n")
            append("Glasmio Apple 原图不受 Apache 覆盖；最终待上传 APK 必须另验不分发。\n\n")
            artifacts.forEach { artifact ->
                val coordinate = artifact.moduleVersion.id.toString()
                val license = RuntimeNoticePolicy.license(coordinate)
                require(artifact.file.extension in setOf("aar", "jar")) { "未支持的运行时归档：$coordinate" }
                val bytes = artifact.file.readBytes()
                val sha = RuntimeNoticeCollector.sha256(bytes)
                val origin = "$coordinate!/${artifact.file.name} [SHA-256 $sha]"
                val count = collector.collectArchive(bytes, origin, aar = artifact.file.extension == "aar")
                archiveTexts += count
                val needed = if (count == 0) RuntimeNoticePolicy.fallbackFiles(coordinate) else emptyList()
                val supplements = needed.filter { licenseSourceDir.file(it).asFile.isFile }
                val supplementDigests = supplements.map { name ->
                    val extra = licenseSourceDir.file(name).asFile.readBytes()
                    RuntimeNoticeCollector.staticText(extra, "$coordinate static $name")
                    collector.add(extra, "$coordinate — 静态补充 assets/licenses/$name（非归档原文）")
                    "$name SHA-256 ${RuntimeNoticeCollector.sha256(extra)}"
                }
                if (count == 0 && (needed.isEmpty() || supplements.size != needed.size)) gaps.add(coordinate)
                // 归属补充：不看归档有没有正文，坐标命中就必须存在且随包，缺失直接失败而不是记缺口。
                val attributionNames = RuntimeNoticePolicy.attributionFiles(coordinate)
                val attributionDigests = attributionNames.map { name ->
                    val file = licenseSourceDir.file(name).asFile
                    require(file.isFile) { "缺少静态归属正文：assets/licenses/$name（$coordinate）" }
                    val extra = file.readBytes()
                    RuntimeNoticeCollector.staticText(extra, "$coordinate attribution $name")
                    collector.add(extra, "$coordinate — 静态归属 assets/licenses/$name（版权行与上游全文）")
                    "$name SHA-256 ${RuntimeNoticeCollector.sha256(extra)}"
                }
                append("$coordinate — $license\n")
                append("  artifact: ${artifact.file.name}; SHA-256 $sha\n")
                append("  归档原文 $count 项；静态补充 ${supplements.size}/${needed.size} 项；" +
                    "静态归属 ${attributionDigests.size} 项\n")
                supplementDigests.forEach { append("  静态正文: $it\n") }
                attributionDigests.forEach { append("  归属正文: $it\n") }
                if (attributionDigests.isNotEmpty() && count > 0) {
                    append("  归属说明：归档正文是**无版权行**的 BSD-3 模板（条款写作 the copyright holder），" +
                        "单靠它不满足 BSD-3 第 1 条「保留上述版权声明」；\n")
                    append("  版权行与上游全文由上述静态归属副本提供，归档原文仍按原字节保留，两者不互相替代。\n")
                }
                if (count == 0 && supplements.isNotEmpty()) {
                    append("  归属说明：该归档不随包 LICENSE/NOTICE 文本；协议正文与版权声明由上述静态副本提供。\n")
                    if (license.startsWith("MIT")) {
                        append("  MIT 要求随分发保留版权声明与许可声明，二者均在静态副本中。\n")
                        if ("Apache-2.0" in license) {
                            append("  内嵌 MCU 源码部分的 Apache-2.0 副本同样由上述静态副本提供。\n")
                        }
                    } else {
                        append("  Apache-2.0 §4(a) 副本义务由静态副本满足；归档内无 NOTICE，§4(d) 无内容可转发。\n")
                    }
                }
            }
        }
        RuntimeNoticePolicy.requireKnownGaps(gaps)
        val status = buildString {
            append("\nTEXT_COVERAGE_GAPS: ${gaps.size}\n")
            if (gaps.isNotEmpty()) {
                append("NOT RELEASE READY：以下精确坐标仍缺完整许可正文；已知缺口不是发行豁免。\n")
                gaps.forEach { append("- $it；待补 ${RuntimeNoticePolicy.fallbackFiles(it).joinToString()}\n") }
            }
            append("完整性检查仅检查材料覆盖，不替代源码版权/NOTICE 归属审核或最终 APK 检查。\n")
        }
        val directory = runtimeNoticesDir.get().dir("licenses").asFile
        check(directory.mkdirs() || directory.isDirectory)
        directory.resolve("runtime.txt").writeText(manifest + status + collector.render(), Charsets.UTF_8)
        directory.resolve("morrow.txt").writeBytes(rootBytes)
        logger.lifecycle("许可清单：${artifacts.size} artifact / ${artifacts.map { it.moduleVersion.id }.distinct().size} 坐标；" +
            "$archiveTexts 份归档原文，${collector.originalCount} 个原字节组，${collector.sourceCount} 个来源；" +
            "正文缺口 ${gaps.size}（不是发行完成）")
    }
}
val verifyRuntimeNoticeCompleteness = tasks.register("verifyRuntimeNoticeCompleteness") {
    group = "verification"
    description = "发行前要求所有运行时坐标的正文覆盖完整；已知缺口也不能放行"
    dependsOn(generateRuntimeNotices)
    doLast {
        val content = runtimeNoticesDir.get().file("licenses/runtime.txt").asFile.readText(Charsets.UTF_8)
        require("TEXT_COVERAGE_GAPS: 0\n" in content && "NOT RELEASE READY" !in content) {
            "运行时许可正文尚未补全，禁止发行；先处理 runtime.txt 的 TEXT_COVERAGE_GAPS"
        }
    }
}
android.sourceSets.getByName("main").assets.directories.add(runtimeNoticesDir.get().asFile.absolutePath)
tasks.named("preBuild").configure { dependsOn(generateRuntimeNotices) }
tasks.named("check").configure { dependsOn(verifyRuntimeNotices) }

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
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
