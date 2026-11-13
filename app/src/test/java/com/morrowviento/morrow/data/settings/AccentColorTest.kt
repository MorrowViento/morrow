package com.morrowviento.morrow.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题色是纯数据 + 纯函数，可以完全脱离 Android 断言。
 *
 * 重点守两件事：① 认不出的偏好值**不能**把用户带到一个随机颜色上，必须回落默认蓝；
 * ② 默认色必须等于库自己的默认强调色——否则「不选主题色」的老用户升级后整个应用会
 * 悄悄换一个色。
 */
class AccentColorTest {

    @Test
    fun `缺失或无法识别的偏好回落默认色`() {
        assertEquals(AccentColor.DEFAULT, AccentColor.fromPref(null))
        assertEquals(AccentColor.DEFAULT, AccentColor.fromPref(""))
        assertEquals(AccentColor.DEFAULT, AccentColor.fromPref("no-such-color"))
        assertEquals(AccentColor.DEFAULT, AccentColor.fromPref("VIOLET"))
    }

    @Test
    fun `按 prefValue 精确还原`() {
        AccentColor.entries.forEach { color ->
            assertEquals(color, AccentColor.fromPref(color.prefValue))
        }
    }

    @Test
    fun `prefValue 不重复`() {
        val values = AccentColor.entries.map { it.prefValue }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun `默认色是库自己的默认强调色`() {
        // glasmioColorScheme 的默认 accent：浅色 #0088FF、深色 #0091FF。
        // 一旦这条断言挂了，说明有人改了默认色，老用户升级后颜色会跟着变。
        assertEquals(0xFF0088FF, AccentColor.DEFAULT.light)
        assertEquals(0xFF0091FF, AccentColor.DEFAULT.dark)
    }

    @Test
    fun `明暗两套值不同且都是不透明 ARGB`() {
        AccentColor.entries.forEach { color ->
            assertNotEquals(color.label, color.light, color.dark)
            listOf(color.light, color.dark).forEach { argb ->
                assertTrue("${color.label} 的 ARGB 越界: $argb", argb in 0xFF000000..0xFFFFFFFF)
            }
        }
    }

    @Test
    fun `argb 按明暗取对应值`() {
        val color = AccentColor.ROSE
        assertEquals(color.light, color.argb(isDark = false))
        assertEquals(color.dark, color.argb(isDark = true))
    }

    @Test
    fun `每套配色都有四团壁纸色斑`() {
        AccentColor.entries.forEach { color ->
            // 顺序是「暖 / 冷 / 天青 / 深」，GlassBackdrop 按下标取；
            // 少一个就会越界，多一个则永远用不到。
            assertEquals("${color.label} 浅色色斑数量", 4, color.blobsLight.size)
            assertEquals("${color.label} 深色色斑数量", 4, color.blobsDark.size)
        }
    }

    @Test
    fun `壁纸色斑都是不透明 ARGB`() {
        AccentColor.entries.forEach { color ->
            (color.blobsLight.toList() + color.blobsDark.toList()).forEach { argb ->
                assertTrue("${color.label} 的色斑越界: $argb", argb in 0xFF000000..0xFFFFFFFF)
            }
        }
    }

    @Test
    fun `blobs 按明暗取对应的那一组`() {
        val color = AccentColor.JADE
        assertTrue(color.blobs(isDark = false).contentEquals(color.blobsLight))
        assertTrue(color.blobs(isDark = true).contentEquals(color.blobsDark))
        // 深色那组必须整组更暗，否则夜里会把页面刷亮
        color.blobsLight.indices.forEach { i ->
            assertTrue(
                "${color.label} 第 $i 团色斑的深色版本没有变暗",
                color.blobsDark[i] < color.blobsLight[i],
            )
        }
    }

    @Test
    fun `默认配色的色斑保持原有那幅丝缎壁纸`() {
        // 老用户升级后背景必须逐像素不变——和强调色的默认值同理。
        assertTrue(
            AccentColor.DEFAULT.blobsLight.contentEquals(
                longArrayOf(0xFFF7DDB8, 0xFFAFC0D6, 0xFF9FB9D8, 0xFF7E8B9E),
            ),
        )
        assertTrue(
            AccentColor.DEFAULT.blobsDark.contentEquals(
                longArrayOf(0xFF4E3820, 0xFF25333F, 0xFF18293C, 0xFF28323E),
            ),
        )
    }
}
