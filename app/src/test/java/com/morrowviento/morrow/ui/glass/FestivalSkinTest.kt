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

    @Test
    fun `预览能从任意一天找到下一个节日，且当天就是节日时返回当天`() {
        // 2026-11-18 不是节日（农历十月初十）
        val today = LocalDate.of(2026, 11, 18)
        assertNull(festivalSkinOf(today))

        val next = upcomingFestivalSkin(today)
        assertNotNull("非节日也必须能找到下一个节日供预览", next)
        val (date, skin) = next!!
        assertTrue("下一个节日应在一年之内", date.isAfter(today) && date.isBefore(today.plusDays(401)))
        assertEquals(skin, festivalSkinOf(date))

        // 节日当天点预览：看到的就是今天这一款
        val springFestival = yearDays.first { LunarCalendar.festival(it) == "春节" }
        assertEquals(springFestival, upcomingFestivalSkin(springFestival)!!.date)
    }

    @Test
    fun `预览逐个轮换能覆盖全部八款，且不会卡在同一个节日`() {
        var cursor = LocalDate.of(2026, 1, 1)
        val seen = LinkedHashSet<String>()
        repeat(8) {
            val next = upcomingFestivalSkin(cursor) ?: return@repeat
            seen += next.skin.name
            // 下一次从这一天的次日往后找，模拟设置页「换一个」
            cursor = next.date.plusDays(1)
        }
        assertEquals(FESTIVAL_SKIN_NAMES, seen)
    }

    private companion object {
        /** 八款有皮肤的节日（中元有意留空）。 */
        val FESTIVAL_SKIN_NAMES = setOf("春节", "元宵", "端午", "七夕", "中秋", "重阳", "腊八", "除夕")
    }
}
