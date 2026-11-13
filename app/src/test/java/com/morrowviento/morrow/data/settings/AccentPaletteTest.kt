package com.morrowviento.morrow.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「从背景图取色」的推导规则。
 *
 * 这些断言守的是**可读性**而不是美观：强调色必须足够饱和、够亮（否则按钮发脏），
 * 深色档的四团色斑必须整组更暗（否则夜里整页被刷白），同一个种子必须稳定
 * （否则每次重组颜色微变，界面上会出现「色斑在抖」）。
 */
class AccentPaletteTest {

    @Test
    fun `预设摊平后与枚举逐值一致`() {
        AccentColor.entries.forEach { color ->
            val palette = color.palette
            assertEquals(color.prefValue, palette.id)
            assertEquals(color.light, palette.light)
            assertEquals(color.dark, palette.dark)
            assertTrue(palette.blobsLight.contentEquals(color.blobsLight))
            // 同一套配色反复取值必须是同一个对象：GlassTheme 拿它当 key，
            // 每次新建会让整棵主题子树被判成「换了配色」而重建
            assertTrue("${color.label} 的 palette 不稳定", palette === color.palette)
        }
    }

    @Test
    fun `取色档的 id 固定为 auto 且标记为取色`() {
        val derived = AccentPalette.fromSeed(0xFF8E5A2BL)
        assertEquals(AccentPalette.AUTO_ID, derived.id)
        assertTrue(derived.isAuto)
        assertEquals("取色", derived.label)
    }

    @Test
    fun `取色结果稳定且四团色斑齐全`() {
        val first = AccentPalette.fromSeed(0xFF2E7D32L)
        val second = AccentPalette.fromSeed(0xFF2E7D32L)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(4, first.blobsLight.size)
        assertEquals(4, first.blobsDark.size)
    }

    @Test
    fun `取色得到的强调色有足够彩度与亮度`() {
        // 一张很灰、很暗的图（山林阴天）也必须推导出可用的强调色，
        // 否则「从图取色」等于把界面变成一片土灰
        listOf(0xFF4A4A45L, 0xFF1A1A1AL, 0xFFD8D8D8L, 0xFF6B5B4BL).forEach { seed ->
            val derived = AccentPalette.fromSeed(seed)
            listOf(derived.light, derived.dark).forEach { argb ->
                val (_, saturation, lightness) = argbToHsl(argb)
                assertTrue("种子 $seed 的强调色彩度太低: $saturation", saturation >= 0.50f)
                assertTrue("种子 $seed 的强调色亮度越界: $lightness", lightness in 0.40f..0.72f)
            }
        }
    }

    @Test
    fun `深色那组色斑整组比浅色暗`() {
        AccentPalette.fromSeed(0xFF3F6FB5L).let { palette ->
            palette.blobsLight.indices.forEach { index ->
                val light = palette.blobsLight[index]
                val dark = palette.blobsDark[index]
                assertTrue(
                    "第 $index 团色斑的深色版本没有变暗",
                    luminance(dark) < luminance(light),
                )
            }
        }
    }

    @Test
    fun `同一色相族的不同种子得到接近的强调色`() {
        // 两张都是「晚霞」的照片（偏橙），取出来的色相应当接近，
        // 否则用户每换一张同风格的照片，整个应用的颜色都会跳一次
        val a = AccentPalette.fromSeed(0xFFD2691EL)
        val b = AccentPalette.fromSeed(0xFFE08A3CL)
        val hueA = argbToHsl(a.light).first
        val hueB = argbToHsl(b.light).first
        val delta = kotlin.math.abs(hueA - hueB)
        assertTrue("同族种子的色相差异过大: $hueA vs $hueB", minOf(delta, 360f - delta) < 24f)
    }

    @Test
    fun `近无彩的种子回落为一支冷静的蓝`() {
        val derived = AccentPalette.fromSeed(0xFF808080L)
        val (hue, saturation, _) = argbToHsl(derived.light)
        assertTrue("灰色种子应当落到蓝色相: $hue", hue in 195f..225f)
        assertTrue(saturation >= 0.50f)
    }

    @Test
    fun `取值相等的两套配色判为相等`() {
        // equals 必须按内容比：`LongArray` 默认是引用相等，会让上层误判为「每帧换配色」
        val a = AccentPalette.fromSeed(0xFF3F6FB5L)
        val b = AccentPalette.fromSeed(0xFF3F6FB5L)
        assertEquals(a, b)
        assertNotEquals(a, AccentPalette.fromSeed(0xFFB53F6FL))
    }

    private fun luminance(argb: Long): Float {
        val (_, _, lightness) = argbToHsl(argb)
        return lightness
    }
}
