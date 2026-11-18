package com.morrowviento.morrow.ui.glass

import com.morrowviento.morrow.domain.time.LunarCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 节日皮肤的覆盖与配色约束。
 *
 * 这类「按日期查表」的功能最容易出的错是**漏一款**：节日名在农历里有了，
 * 皮肤表忘了加，结果就是那个节日当天什么都没有，而且一年只暴露一次，
 * 谁也不会在开发期发现。所以这里不硬编码具体日期，而是扫一整年，
 * 拿农历判定出来的节日去比皮肤表——漏一个就红。
 */
class FestivalSkinTest {

    /** 2026 全年逐日（农历年表覆盖 1900–2100，这一年落在范围内）。 */
    private val yearDays: List<LocalDate> =
        (0..364).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) }

    @Test
    fun `农历节日都配了皮肤，只有中元有意留空`() {
        val festivals = yearDays.mapNotNull { day ->
            LunarCalendar.festival(day)?.let { it to day }
        }

        // 一年里至少能看到这九种节日（闰月不过节，同一天不会重复计）
        assertTrue("应能扫到九种农历节日", festivals.size >= 9)

        festivals.forEach { (name, day) ->
            if (name == "中元") {
                assertNull("中元不做视觉庆祝，应无皮肤", festivalSkinOf(day))
            } else {
                assertNotNull("节日「$name」缺少皮肤", festivalSkinOf(day))
                assertEquals(name, festivalSkinOf(day)!!.name)
            }
        }
    }

    @Test
    fun `非节日没有皮肤`() {
        // 2026-01-02：元旦是公历节日，农历上只是冬月十四，不该有皮肤
        assertNull(festivalSkinOf(LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun `皮肤色是不透明的纯色，由渲染层自行决定透明度`() {
        val springFestival = yearDays.first { LunarCalendar.festival(it) == "春节" }
        val skin = festivalSkinOf(springFestival)!!

        // alpha 通道必须满：柔光透明度由 GlassBackdrop 按明暗决定，
        // 表里再写一个半透明值会把两层叠成想不到的浓淡
        assertEquals(0xFF, (skin.tint ushr 24) and 0xFF)
        assertTrue(skin.greeting.isNotBlank())
    }
}
