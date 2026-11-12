package com.morrowviento.morrow.ui.glass

import com.morrowviento.morrow.data.settings.GLASS_FROST_DEFAULT
import com.morrowviento.morrow.data.settings.GlassFrostLevel
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 玻璃厚度映射的回归测试。
 *
 * 真机反馈「通透与厚重看不出区别」，根因不在我们的代码而在库的枚举：
 * `RegularMedium` 与 `RegularLarge` 的五个构造参数完全相同。所以这里断言的是
 * **映射不再落到那两个等价档位**，以及厚度确实单调地改变了模糊半径与色纱。
 */
class GlassFrostTest {

    @Test
    fun `厚度映射到三档材质，且从不使用与 RegularMedium 等价的 RegularLarge`() {
        val materials = (0..10).map { materialOf(it / 10f) }
        assertTrue(materials.none { it == NavigationGlassMaterial.RegularLarge })
        assertEquals(NavigationGlassMaterial.Clear, materialOf(0f))
        assertEquals(NavigationGlassMaterial.RegularSmall, materialOf(0.4f))
        assertEquals(NavigationGlassMaterial.RegularMedium, materialOf(1f))
    }

    @Test
    fun `模糊半径随厚度单调递增，两端有明确差异`() {
        val clear = frostRadiusOf(0f).value
        val heavy = frostRadiusOf(1f).value
        assertTrue("通透档应几乎不糊", clear in 1f..3f)
        assertTrue("厚重档应明显糊开", heavy >= 20f)

        var previous = -1f
        for (step in 0..20) {
            val radius = frostRadiusOf(step / 20f).value
            assertTrue("半径必须单调不减", radius >= previous)
            previous = radius
        }
    }

    @Test
    fun `色纱在通透档完全关闭，在厚重档接近实心`() {
        assertEquals(0f, veilAlphaOf(0f), 0.0001f)
        assertEquals(0f, veilAlphaOf(-1f), 0.0001f)
        // 拖到最厚时能压住背景，但不是纯色块
        assertTrue(veilAlphaOf(1f) in 0.7f..0.85f)
        // 曲线下凸：前半段细腻、后半段才快速变实
        assertTrue(veilAlphaOf(0.5f) < veilAlphaOf(1f) * 0.6f)
    }

    @Test
    fun `默认值落在标准档`() {
        val veil = veilAlphaOf(GLASS_FROST_DEFAULT)
        assertTrue("默认应能看出是玻璃，又不能太实", veil in 0.3f..0.5f)
        assertEquals(GlassFrostLevel.STANDARD, GlassFrostLevel.nearest(GLASS_FROST_DEFAULT))
        assertEquals(NavigationGlassMaterial.RegularMedium, materialOf(GLASS_FROST_DEFAULT))
    }

    @Test
    fun `四个档位的观感两两可分`() {
        val levels = GlassFrostLevel.entries
        // 相邻档的模糊半径至少差 4dp：肉眼能分出来，而不是「点了没反应」
        levels.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "${a.label} → ${b.label} 的模糊半径差太小",
                frostRadiusOf(b.frost).value - frostRadiusOf(a.frost).value >= 4f,
            )
            assertTrue(
                "${a.label} → ${b.label} 的色纱差太小",
                veilAlphaOf(b.frost) - veilAlphaOf(a.frost) >= 0.12f,
            )
        }
        // 通透档必须真的通透
        assertEquals(0f, veilAlphaOf(GlassFrostLevel.CLEAR.frost), 0.0001f)
        // 厚重档压得住背景，但不是纯色块
        assertTrue(veilAlphaOf(GlassFrostLevel.HEAVY.frost) in 0.55f..0.8f)
    }

    @Test
    fun `连续值取最近档位，旧值与滑块中间值都归得回去`() {
        assertEquals(GlassFrostLevel.CLEAR, GlassFrostLevel.nearest(0f))
        assertEquals(GlassFrostLevel.LIGHT, GlassFrostLevel.nearest(0.28f))
        assertEquals(GlassFrostLevel.STANDARD, GlassFrostLevel.nearest(0.55f))
        assertEquals(GlassFrostLevel.HEAVY, GlassFrostLevel.nearest(0.92f))
        // 滑块曾经能拖到任意位置，读回来只是「显示成哪一格」，不改写已存值
        assertEquals(GlassFrostLevel.LIGHT, GlassFrostLevel.nearest(0.37f))
    }
}
