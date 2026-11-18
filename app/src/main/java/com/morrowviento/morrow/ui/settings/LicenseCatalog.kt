package com.morrowviento.morrow.ui.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 设置页可离线阅读的一份许可文档。
 *
 * [assetPath] 相对 `assets/`，必须真实随包；`runtime.txt` 与 `morrow.txt` 由
 * `generateRuntimeNotices` 在构建期生成，其余为源控静态全文。
 */
data class LicenseDocument(
    val title: String,
    /** 简短许可标识，显示在标题行右侧。 */
    val license: String,
    val summary: String,
    val assetPath: String,
)

/**
 * 许可目录。
 *
 * 顺序即阅读顺序：先是本应用与上游项目，再是直接影响界面观感的 Glasmio / 主题色库，
 * 最后是带版权归属的协议副本与逐组件清单。
 */
val LicenseCatalog: List<LicenseDocument> = listOf(
    LicenseDocument(
        title = "朝夕（本应用）",
        license = "MIT",
        summary = "本项目自有代码的许可与版权声明",
        assetPath = "licenses/morrow.txt",
    ),
    LicenseDocument(
        title = "ying（原 Flutter 版）",
        license = "MIT",
        summary = "上游项目 jiuxina/ying 的完整许可，Copyright (c) 2026 Gao Shuai",
        assetPath = "licenses/ying.txt",
    ),
    LicenseDocument(
        title = "Glasmio",
        license = "Apache-2.0",
        summary = "Liquid Glass 材质与组件；含第三方声明与 Apple 素材排除项",
        assetPath = "licenses/glasmio-third-party-notices.txt",
    ),
    LicenseDocument(
        title = "MaterialKolor",
        license = "MIT",
        summary = "主题调色板；4.1.1 发布层的完整许可",
        assetPath = "licenses/materialkolor.txt",
    ),
    LicenseDocument(
        title = "Material Color Utilities",
        license = "Apache-2.0",
        summary = "MaterialKolor 内嵌的上游代码，Copyright 2021 Google LLC",
        assetPath = "licenses/material-color-utilities.txt",
    ),
    LicenseDocument(
        title = "Protocol Buffers",
        license = "BSD-3-Clause",
        summary = "datastore 外置 protobuf 的版权归属；归档正文缺版权行，此处补上游全文",
        assetPath = "licenses/protobuf-bsd-3.txt",
    ),
    LicenseDocument(
        title = "Apache License 2.0",
        license = "Apache-2.0",
        summary = "适用于未随包携带独立正文的 Apache-2.0 组件",
        assetPath = "licenses/apache-2.0.txt",
    ),
    LicenseDocument(
        title = "全部第三方组件",
        license = "逐条清单",
        summary = "release 运行时每个坐标、版本、原文与来源",
        assetPath = "licenses/runtime.txt",
    ),
)

/** 单块上限：许可文本按空行分段，过长的段再按行切，避免一次布局整份大文件。 */
internal const val LicenseBlockChars = 2_000

/**
 * 把许可全文切成适合逐块懒加载渲染的文本块。
 *
 * 只做展示切分：按空行分段（许可正文本身就是段落结构），单段超过 [maxChars] 时再按行累积切开。
 * 不合并、不重排、不删字符（只去掉行尾空白），所以读起来仍是原文顺序。
 * 空文本返回空列表，调用方据此显示「空文件」而不是误判为读取失败。
 */
internal fun licenseBlocks(text: String, maxChars: Int = LicenseBlockChars): List<String> {
    require(maxChars > 0) { "maxChars 必须为正数" }
    val blocks = mutableListOf<String>()
    val buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotEmpty()) {
            blocks += buffer.toString()
            buffer.clear()
        }
    }
    text.lineSequence().forEach { raw ->
        val line = raw.trimEnd()
        if (line.isEmpty()) {
            flush()
            return@forEach
        }
        if (buffer.length + line.length + 1 > maxChars) flush()
        if (buffer.isNotEmpty()) buffer.append('\n')
        buffer.append(line)
    }
    flush()
    return blocks
}

/** 读取 assets 内的许可全文；I/O 与解码都在后台线程。 */
internal suspend fun readLicenseText(context: Context, assetPath: String): String =
    withContext(Dispatchers.IO) {
        context.assets.open(assetPath).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }
