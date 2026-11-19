package com.morrowviento.morrow

import android.app.Application
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
import java.security.MessageDigest

/** 验证实际 main/generated assets 装配；不冒充最终 APK 或完整许可审计。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RuntimeNoticesAssetTest {
    private fun asset(name: String): ByteArray =
        RuntimeEnvironment.getApplication().assets.open("licenses/$name").use { it.readBytes() }

    private fun text(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test
    fun runtimeManifestUsesActualCoordinatesAndExplicitCoverageStatus() {
        val runtime = text(asset("runtime.txt"))
        // 坐标行必须一坐标一条：`androidx.core:core:1.18.0` 曾在解析结果里出现两次，
        // 清单跟着打了两条一模一样的记录，只数总数看不出问题，必须同时查唯一性。
        val coordinates = Regex("(?m)^([a-z][^\\r\\n]+) — ").findAll(runtime)
            .map { it.groupValues[1] }.toList()
        assertEquals(109, coordinates.size)
        assertEquals(coordinates.size, coordinates.distinct().size)
        val protobuf = runtime.lineSequence().first {
            it.startsWith("androidx.datastore:datastore-preferences-external-protobuf:1.3.0 — ")
        }
        assertTrue(protobuf.endsWith("BSD-3-Clause"))
        assertFalse(protobuf.contains("Apache-2.0"))
        val material = runtime.lineSequence().first {
            it.startsWith("com.materialkolor:material-color-utilities-android:4.1.1 — ")
        }
        assertTrue(material.contains("MIT") && material.contains("Apache-2.0"))
        assertFalse(runtime.contains("CopyrightKt.class"))
        assertFalse(runtime.contains("!/lint.jar!/"))
        assertFalse(runtime.any { it.isISOControl() && it !in "\n\r\t" })
        val gapCount = Regex("(?m)^TEXT_COVERAGE_GAPS: ([0-9]+)$").find(runtime)!!.groupValues[1].toInt()
        assertEquals(gapCount > 0, runtime.contains("NOT RELEASE READY"))
    }

    @Test
    fun generatedMorrowAssetPreservesFullRootMitAndCopyrights() {
        val morrow = text(asset("morrow.txt"))
        assertTrue(morrow.startsWith("MIT License\n"))
        assertTrue(morrow.contains("Copyright (c) 2026 Morrow contributors"))
        assertTrue(morrow.contains("Copyright (c) 2026 Gao Shuai (ying-derived portions)"))
        assertTrue(morrow.contains("Permission is hereby granted, free of charge"))
        assertTrue(morrow.contains("THE SOFTWARE IS PROVIDED \"AS IS\""))
        assertTrue(text(asset("runtime.txt")).contains(morrow))
        val ying = text(asset("ying.txt"))
        assertTrue(ying.contains("Copyright (c) 2026 Gao Shuai"))
        assertTrue(ying.contains("Permission is hereby granted, free of charge"))
    }

    @Test
    fun staticLicenseAssetsCloseEveryCoverageGap() {
        val runtime = text(asset("runtime.txt"))
        assertEquals(0, Regex("(?m)^TEXT_COVERAGE_GAPS: ([0-9]+)$").find(runtime)!!.groupValues[1].toInt())
        assertFalse(runtime.contains("NOT RELEASE READY"))

        // 三个静态文件必须真的随包，且各自带可追踪的 Source/License 头。
        val apache = text(asset("apache-2.0.txt"))
        val kolor = text(asset("materialkolor.txt"))
        val mcu = text(asset("material-color-utilities.txt"))
        listOf(apache, kolor, mcu).forEach {
            assertTrue(Regex("(?m)^Source: https://\\S+$").containsMatchIn(it))
            assertTrue(Regex("(?m)^License: \\S.*$").containsMatchIn(it))
        }
        assertTrue(apache.contains("END OF TERMS AND CONDITIONS"))
        assertTrue(apache.contains("License: Apache-2.0"))
        assertTrue(kolor.contains("Copyright (c) 2025 Jordon de Hoog"))
        assertTrue(kolor.contains("License: MIT"))
        assertTrue(mcu.contains("Copyright 2021 Google LLC"))
        assertTrue(mcu.contains("License: Apache-2.0"))
        assertFalse(kolor.contains("Copyright 2021 Google LLC"))

        // 静态补充正文必须进 runtime.txt，并按坐标保留归属来源。
        assertTrue(runtime.contains(apache))
        assertTrue(runtime.contains(kolor))
        assertTrue(runtime.contains(mcu))
        assertTrue(runtime.contains("静态补充 assets/licenses/materialkolor.txt"))
        assertTrue(runtime.contains("静态补充 assets/licenses/material-color-utilities.txt"))
        val apacheSources = runtime.lineSequence()
            .filter { it.endsWith("— 静态补充 assets/licenses/apache-2.0.txt（非归档原文）") }
        assertTrue(apacheSources.count() >= 30)
        apacheSources.forEach { line ->
            assertTrue(Regex("^- [a-z][A-Za-z0-9._-]+:[A-Za-z0-9._-]+:[0-9][^ —]* — ")
                .containsMatchIn(line))
        }
        // 缺口收敛后不允许残留“待补”提示。
        assertFalse(runtime.contains("待补 "))
    }

    @Test
    fun protobufCarriesStaticAttributionWithCopyrightLine() {
        val runtime = text(asset("runtime.txt"))
        val attribution = text(asset("protobuf-bsd-3.txt"))
        assertTrue(attribution.contains("Copyright 2008 Google Inc."))
        assertTrue(attribution.contains("Neither the name of Google Inc."))
        assertTrue(attribution.contains("License: BSD-3-Clause"))
        assertTrue(runtime.contains(attribution))
        assertTrue(runtime.contains("静态归属 assets/licenses/protobuf-bsd-3.txt（版权行与上游全文）"))
        assertTrue(runtime.contains("静态归属 1 项"))
        // 归档里的模板正文仍按原字节保留；归属补充是追加，不是替换。
        assertTrue(runtime.contains("Neither the name of the copyright holder"))
        assertTrue(runtime.contains("Neither the name of Google Inc."))
        val protobufEntry = runtime.lineSequence().first {
            it.startsWith("androidx.datastore:datastore-preferences-external-protobuf:1.3.0 — ")
        }
        assertTrue(protobufEntry.endsWith("BSD-3-Clause"))
    }

    @Test
    fun lockedGlasmioNoticeRetainsUpstreamTextAndAppleExclusions() {
        val bytes = asset("glasmio-third-party-notices.txt")
        assertEquals("297bbf9d3745103a1d04fc205fc9da50c4b9a544927393f6a15b3feef2654318", sha256(bytes))
        val notice = text(bytes)
        assertTrue(notice.contains("Apple Inc. retains copyright"))
        assertTrue(notice.contains("not covered by Glasmio's Apache-2.0 license"))
        // main 新增：Miuix 的 Display-cutout safe-area repair 也进了库，声明必须同步
        assertTrue(notice.contains("Display-cutout safe-area repair"))
        val runtime = text(asset("runtime.txt"))
        assertTrue(runtime.contains(notice))
        assertTrue(runtime.contains("030d31c1cb1b9958ddab0b6764c4e8ed82864b0b/THIRD_PARTY_NOTICES.md"))
        assertTrue(runtime.contains("glasmio-ui-0.1.4-main.aar"))
        assertTrue(runtime.contains("glasmio-icons-0.1.4-main.aar"))
    }

    @Test
    fun everyOriginalGroupHasExactBytesHashAndRetainedSources() {
        val runtime = text(asset("runtime.txt"))
        val headers = Regex("(?m)^===== ORIGINAL ([0-9a-f]{64}) ([0-9]+) bytes =====$")
            .findAll(runtime).toList()
        assertTrue(headers.isNotEmpty())
        assertEquals(headers.size, headers.map { it.groupValues[1] }.distinct().size)
        val begin = "----- BEGIN ORIGINAL -----\n"
        val end = "\n----- END ORIGINAL -----\n"
        headers.forEach { header ->
            val beginIndex = runtime.indexOf(begin, header.range.last)
            assertTrue(beginIndex > header.range.last)
            val sources = runtime.substring(header.range.last + 1, beginIndex)
                .lineSequence().filter { it.startsWith("- ") }.toList()
            assertTrue(sources.isNotEmpty())
            assertEquals(sources.sorted(), sources)
            assertEquals(sources.distinct(), sources)
            val tail = runtime.substring(beginIndex + begin.length).toByteArray(Charsets.UTF_8)
            val size = header.groupValues[2].toInt()
            val original = tail.copyOfRange(0, size)
            assertEquals(header.groupValues[1], sha256(original))
            assertEquals(end, text(tail.copyOfRange(size, size + end.toByteArray().size)))
        }
    }
}
