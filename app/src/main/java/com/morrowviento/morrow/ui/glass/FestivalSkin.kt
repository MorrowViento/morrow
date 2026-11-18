package com.morrowviento.morrow.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf
import com.morrowviento.morrow.domain.time.LunarCalendar
import java.time.LocalDate

/**
 * 节日皮肤：节日当天的称呼、一句祝语与一支点缀色。
 *
 * **为什么不像旧版那样「换一整套皮肤」**：那需要为每个节日准备壁纸、配色与图标，
 * 体积和授权都是负担，而且会盖掉用户自己挑的配色。这里改成「只加一层」——
 * 背景上一团很淡的节日色柔光 + 标题下的一行小字。用户选的配色、玻璃厚度、字体
 * 一个都不动，节日过了柔光自己消失。
 *
 * [tint] 只用于柔光与这行小字，不参与主题色推导：否则节日当天所有按钮都会变红，
 * 用户会以为自己的配色设置被改了。
 */
data class FestivalSkin(
    val name: String,
    val greeting: String,
    /** 节日主色（ARGB）。 */
    val tint: Long,
)

/**
 * 今天的节日皮肤；不是节日或该节日不设皮肤时返回 null。
 *
 * 「中元」故意不给皮肤：这类日子不适合在倒数应用里做视觉庆祝，
 * 日历格子上仍照常标注节日名，需要的人自己看得到。
 */
fun festivalSkinOf(date: LocalDate): FestivalSkin? =
    LunarCalendar.festival(date)?.let { FESTIVAL_SKINS[it] }

/** 设置页「预览」用的临时状态：哪一天的皮肤，以及皮肤本身。 */
data class FestivalPreview(val date: LocalDate, val skin: FestivalSkin)

/**
 * 从 [from]（含当天）往后第一个有皮肤的节日，最多找 [maxDays] 天。
 *
 * 一年只有八九天会生效，用户点进设置页时几乎必然不是节日——没有这个函数，
 * 那一项就只能写一句「节日当天才有」，用户既看不到也验不了，只会当成坏功能。
 * 扫一年最多 365 次农历换算（纯位运算 + 二分），点一次按钮的代价，可以接受。
 */
fun upcomingFestivalSkin(from: LocalDate, maxDays: Int = 400): FestivalPreview? {
    var date = from
    repeat(maxDays) {
        festivalSkinOf(date)?.let { return FestivalPreview(date, it) }
        date = date.plusDays(1)
    }
    return null
}

/**
 * 由外层在组合期提供，[GlassBackdrop] 与首页标题都读它。
 *
 * 用 `static` 是因为它一天只变一次（跨零点），没必要让每个读取者登记：
 * 变化时整棵树按新的一天重排一次反而更省心。
 */
val LocalFestivalSkin = staticCompositionLocalOf<FestivalSkin?> { null }

private val FESTIVAL_SKINS: Map<String, FestivalSkin> = mapOf(
    "春节" to FestivalSkin("春节", "又是一年春", 0xFFC0392B),
    "元宵" to FestivalSkin("元宵", "灯火可亲", 0xFFE08A3C),
    "端午" to FestivalSkin("端午", "端午安康", 0xFF2E7D5B),
    "七夕" to FestivalSkin("七夕", "愿有情人终成眷属", 0xFFA45C8E),
    "中秋" to FestivalSkin("中秋", "月圆人团圆", 0xFFC9A227),
    "重阳" to FestivalSkin("重阳", "登高望远", 0xFFD08A2C),
    "腊八" to FestivalSkin("腊八", "过了腊八就是年", 0xFF8A5A3C),
    "除夕" to FestivalSkin("除夕", "今宵守岁", 0xFFC0392B),
)
