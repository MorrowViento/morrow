package com.morrowviento.morrow

import android.app.Application
import com.morrowviento.morrow.ui.settings.LicenseCatalog
import com.morrowviento.morrow.ui.settings.LicenseDocument
import com.morrowviento.morrow.ui.settings.licenseBlocks
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * 设置页「开源许可」的目录与随包文本回归。
 *
 * 只验证机械事实：目录里的每份文档确实在 assets 中、能按 UTF-8 读出、标注与实际正文一致，
 * 以及正文切块不丢内容。不冒充完整许可审计。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LicenseCatalogTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun raw(document: LicenseDocument): ByteArray =
        context.assets.open(document.assetPath).use { it.readBytes() }

    private fun text(document: LicenseDocument): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(raw(document))).toString()

    private fun document(title: String): LicenseDocument =
        LicenseCatalog.single { it.title == title }

    @Test
    fun everyDocumentIsPackagedAndDecodesAsUtf8() {
        assertTrue(LicenseCatalog.size >= 7)
        assertEquals(LicenseCatalog.size, LicenseCatalog.map { it.assetPath }.distinct().size)
        LicenseCatalog.forEach { document ->
            assertTrue("${document.assetPath} 为空", raw(document).isNotEmpty())
            assertTrue("${document.assetPath} 标题为空", document.title.isNotBlank())
            assertTrue("${document.assetPath} 许可标识为空", document.license.isNotBlank())
            val body = text(document)
            assertFalse("${document.assetPath} 含替换字符", body.contains('\uFFFD'))
            assertFalse(
                "${document.assetPath} 含二进制控制字符",
                body.any { it.isISOControl() && it !in "\n\r\t" },
            )
        }
    }

    @Test
    fun catalogLabelsMatchActualLicenseText() {
        // Glasmio 是 Apache-2.0，不是 MIT —— 旧设置页写错的就是这一条。
        val glasmio = document("Glasmio")
        assertEquals("Apache-2.0", glasmio.license)
        val glasmioText = text(glasmio)
        assertTrue(glasmioText.contains("Apache-2.0"))
        assertFalse(glasmioText.contains("MIT License"))
        assertTrue(glasmioText.contains("Apple Inc. retains copyright"))
        assertTrue(glasmioText.contains("not covered by Glasmio's Apache-2.0 license"))

        // 本应用与上游 ying 都是 MIT，且必须带完整授权段。
        listOf("朝夕（本应用）", "ying（原 Flutter 版）").forEach { title ->
            val entry = document(title)
            assertEquals("MIT", entry.license)
            val body = text(entry)
            assertTrue(body.contains("Permission is hereby granted, free of charge"))
            assertTrue(body.contains("THE SOFTWARE IS PROVIDED \"AS IS\""))
        }
        assertTrue(text(document("朝夕（本应用）")).contains("Copyright (c) 2026 Morrow contributors"))
        assertTrue(text(document("ying（原 Flutter 版）")).contains("Copyright (c) 2026 Gao Shuai"))

        // MaterialKolor 是 MIT，内嵌 MCU 是 Apache-2.0，两者不能互相顶替。
        val kolor = document("MaterialKolor")
        assertEquals("MIT", kolor.license)
        assertTrue(text(kolor).contains("Copyright (c) 2025 Jordon de Hoog"))
        val mcu = document("Material Color Utilities")
        assertEquals("Apache-2.0", mcu.license)
        assertTrue(text(mcu).contains("Copyright 2021 Google LLC"))

        val apache = document("Apache License 2.0")
        assertEquals("Apache-2.0", apache.license)
        assertTrue(text(apache).contains("END OF TERMS AND CONDITIONS"))
    }

    @Test
    fun runtimeDocumentReflectsCompleteCoverage() {
        val runtime = text(document("全部第三方组件"))
        assertEquals(0, Regex("(?m)^TEXT_COVERAGE_GAPS: ([0-9]+)$")
            .find(runtime)!!.groupValues[1].toInt())
        assertFalse(runtime.contains("NOT RELEASE READY"))
        assertFalse(runtime.contains("!/lint.jar!/"))
        assertFalse(runtime.contains("CopyrightKt.class"))
        // 静态正文与锁定第三方声明都必须出现在 runtime 清单里（ying 是上游独立许可，不参与汇总）。
        listOf("朝夕（本应用）", "Glasmio", "MaterialKolor", "Material Color Utilities", "Apache License 2.0")
            .forEach { title -> assertTrue("runtime.txt 缺少 $title 正文", runtime.contains(text(document(title)))) }
    }

    @Test
    fun blocksKeepEveryLineInOrderAndStayBounded() {
        val runtime = text(document("全部第三方组件"))
        val blocks = licenseBlocks(runtime)
        assertTrue(blocks.size > 20)
        blocks.forEach { block ->
            assertTrue(block.isNotBlank())
            // 超限只允许发生在单行超长（无换行可切）的情况下。
            assertTrue(block.length <= 2_000 || !block.contains('\n'))
        }
        val expected = runtime.lineSequence().map { it.trimEnd() }.filter { it.isNotEmpty() }.joinToString("\n")
        assertEquals(expected, blocks.joinToString("\n"))
    }

    @Test
    fun blocksHandleEmptyAndShortText() {
        assertEquals(emptyList<String>(), licenseBlocks(""))
        assertEquals(emptyList<String>(), licenseBlocks("\n\n  \n"))
        assertEquals(listOf("a\nb", "c"), licenseBlocks("a\nb\n\nc"))
        // 自定义上限：按行累积切块，仍保持原顺序。
        val long = (1..10).joinToString("\n") { "line$it" }
        val split = licenseBlocks(long, maxChars = 6)
        assertEquals(long, split.joinToString("\n"))
        assertTrue(split.all { it.length <= 6 })
    }
}
