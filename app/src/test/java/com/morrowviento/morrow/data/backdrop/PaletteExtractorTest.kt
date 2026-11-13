package com.morrowviento.morrow.data.backdrop

import com.morrowviento.morrow.data.settings.argbToHsl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 背景图主色提取。
 *
 * 用**手搓的像素数组**而不是真实图片：这里的逻辑是纯函数，测的是「什么样的输入
 * 应该得到什么样的代表色」，与解码、相册权限都无关。真机上这一层出问题，
 * 表现是「取色得到的颜色很奇怪」，而那种问题只能靠这些断言定位。
 */
class PaletteExtractorTest {

    @Test
    fun `空输入返回 0 表示取不到颜色`() {
        assertEquals(0L, PaletteExtractor.dominantSeed(IntArray(0)))
    }

    @Test
    fun `全透明像素返回 0`() {
        val transparent = IntArray(64) { 0x00FF8800.toInt() }
        assertEquals(0L, PaletteExtractor.dominantSeed(transparent))
    }

    @Test
    fun `单一纯色直接得到该色`() {
        val orange = IntArray(64) { 0xFFE06A20.toInt() }
        val seed = PaletteExtractor.dominantSeed(orange)
        val (hue, _, _) = argbToHsl(seed)
        assertTrue("橙色像素应当得到橙色种子，实际色相 $hue", hue in 18f..40f)
    }

    @Test
    fun `大面积灰底上的一小块颜色会被选中`() {
        // 真实照片的典型形态：大半是天空/白墙/阴影，真正有颜色的只有一角。
        // 求平均会被灰色拉平，分桶 + 饱和度加权才能把那块颜色捞出来。
        val sky = IntArray(48 * 48) { 0xFF9AA4AE.toInt() }
        val flower = 24 * 24
        for (index in 0 until flower) sky[index] = 0xFFD81F5EL.toInt()

        val (hue, saturation, _) = argbToHsl(PaletteExtractor.dominantSeed(sky))
        assertTrue("没有得到品红种子，色相 $hue", hue > 320f || hue < 20f)
        assertTrue("彩度偏低: $saturation", saturation > 0.35f)
    }

    @Test
    fun `两张同色系的图给出接近的种子`() {
        val a = IntArray(64) { 0xFF3F7F4F.toInt() }
        val b = IntArray(64) { 0xFF4A8A58.toInt() }
        val hueA = argbToHsl(PaletteExtractor.dominantSeed(a)).first
        val hueB = argbToHsl(PaletteExtractor.dominantSeed(b)).first
        assertTrue(kotlin.math.abs(hueA - hueB) < 12f)
    }

    @Test
    fun `结果确定性`() {
        val pixels = IntArray(256) { index -> if (index % 3 == 0) 0xFF204080.toInt() else 0xFFEEDDCC.toInt() }
        assertEquals(
            PaletteExtractor.dominantSeed(pixels),
            PaletteExtractor.dominantSeed(pixels),
        )
    }
}
