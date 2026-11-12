package com.morrowviento.morrow.ui.glass

import com.morrowviento.morrow.data.settings.GLASS_FROST_DEFAULT
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
    fun `默认值落在轻薄磨砂区间`() {
        val veil = veilAlphaOf(GLASS_FROST_DEFAULT)
        assertTrue("默认应能看出是玻璃，又不能太实", veil in 0.15f..0.35f)
        assertEquals(NavigationGlassMaterial.RegularSmall, materialOf(GLASS_FROST_DEFAULT))
    }
}
