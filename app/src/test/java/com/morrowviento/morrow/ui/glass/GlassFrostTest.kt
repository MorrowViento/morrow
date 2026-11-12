package com.morrowviento.morrow.ui.glass

import com.morrowviento.morrow.data.settings.GLASS_FROST_DEFAULT
import com.morrowviento.morrow.data.settings.GlassFrostLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 玻璃厚度映射的回归测试。
 *
 * 真机反馈过两次「调厚度只看到边缘发光变了」：
 *
 * 1. 第一次的根因是 `RegularMedium` 与 `RegularLarge` 的五个构造参数完全相同；
 * 2. 第二次的根因是 `RegularMedium` 的 `fills` 在深色下是三层 #1A1A1A（其中一层
 *    还是 `Lighten`），整块面板被压成均匀实色，同时又叠了峰值 0.72 的色纱。
 *
 * 所以现在**不再按厚度切材质枚举**（统一用 Clear 这一档「面接近直通」的材质），
 * 厚度只映射到两个连续光学参数：模糊半径 [frostRadiusOf] 与奶白色纱 [veilAlphaOf]。
 * 这里断言的就是这两条曲线：单调、相邻档可分、且任何一档都不会把透明吃掉。
 */
class GlassFrostTest {

    @Test
    fun `模糊半径随厚度单调递增，两端有明确差异`() {
        val clear = frostRadiusOf(0f).value
        val heavy = frostRadiusOf(1f).value
        assertTrue("通透档应几乎不糊，只剩边缘折射", clear <= 1f)
        assertTrue("厚重档应明显糊开", heavy >= 22f)

        var previous = -1f
        for (step in 0..20) {
            val radius = frostRadiusOf(step / 20f).value
            assertTrue("半径必须单调不减", radius >= previous)
            previous = radius
        }
    }

    @Test
    fun `色纱在通透档完全关闭，在厚重档也留得住透明`() {
        assertEquals(0f, veilAlphaOf(0f), 0.0001f)
        assertEquals(0f, veilAlphaOf(-1f), 0.0001f)
        // 最厚一档只是"奶白"，绝不是实色板：以前这里是 0.72，等于四成不透明的实色
        assertTrue("厚重档的色纱必须压在 0.3 以下", veilAlphaOf(1f) <= 0.3f)
        assertTrue("厚重档也得看得见一点奶白", veilAlphaOf(1f) >= 0.18f)
        // 曲线下凸：前段极轻、后段才明显
        assertTrue(veilAlphaOf(0.5f) < veilAlphaOf(1f) * 0.55f)
    }

    @Test
    fun `默认值落在标准档，且默认档仍然透`() {
        val veil = veilAlphaOf(GLASS_FROST_DEFAULT)
        assertTrue("默认档只该有一层薄奶白", veil in 0.05f..0.16f)
        assertEquals(GlassFrostLevel.STANDARD, GlassFrostLevel.nearest(GLASS_FROST_DEFAULT))
        // 三档的模糊半径（0.5 / 9 / 16 / 24）必须真的把背景糊开，否则"标准"和"通透"没差别
        assertTrue(frostRadiusOf(GLASS_FROST_DEFAULT).value in 13f..20f)
    }

    @Test
    fun `四个档位的观感两两可分`() {
        val levels = GlassFrostLevel.entries
        // 相邻档的模糊半径至少差 5dp：肉眼能分出来，而不是「点了没反应」
        levels.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "${a.label} → ${b.label} 的模糊半径差太小",
                frostRadiusOf(b.frost).value - frostRadiusOf(a.frost).value >= 5f,
            )
        }
        // 通透档必须真的通透
        assertEquals(0f, veilAlphaOf(GlassFrostLevel.CLEAR.frost), 0.0001f)
        // 厚重档压得住最花的背景，但依旧不是纯色块
        assertTrue(veilAlphaOf(GlassFrostLevel.HEAVY.frost) in 0.18f..0.30f)
    }

    @Test
    fun `连续值取最近档位，旧值与中间值都归得回去`() {
        assertEquals(GlassFrostLevel.CLEAR, GlassFrostLevel.nearest(0f))
        assertEquals(GlassFrostLevel.LIGHT, GlassFrostLevel.nearest(0.28f))
        assertEquals(GlassFrostLevel.STANDARD, GlassFrostLevel.nearest(0.55f))
        assertEquals(GlassFrostLevel.HEAVY, GlassFrostLevel.nearest(0.92f))
        // 读回来只是「显示成哪一格」，不改写已存值
        assertEquals(GlassFrostLevel.LIGHT, GlassFrostLevel.nearest(0.37f))
    }
}
