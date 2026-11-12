package com.jiuxina.ying.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 左右滑动翻页的判定逻辑。
 *
 * 这里是整条手势链上唯一有分支的地方：跟手位移与手势分发都交给 Compose，
 * 但「翻页还是回弹、翻哪边」必须稳定——判错一次就是「想翻下一页却跳回上一月」。
 */
class CalendarSwipeTest {

    private val width = 1000f
    private val fling = 600f

    @Test
    fun shortSlowDrag_bouncesBack() {
        assertEquals(0, swipeDirection(dragged = 80f, velocity = 120f, width = width, flingVelocity = fling))
        assertEquals(0, swipeDirection(dragged = -80f, velocity = -120f, width = width, flingVelocity = fling))
        assertEquals(0, swipeDirection(dragged = 0f, velocity = 0f, width = width, flingVelocity = fling))
    }

    @Test
    fun longDrag_flipsPage() {
        // 向左滑（位移为负）= 看下一个月
        assertEquals(1, swipeDirection(dragged = -400f, velocity = 0f, width = width, flingVelocity = fling))
        // 向右滑 = 看上一个月
        assertEquals(-1, swipeDirection(dragged = 400f, velocity = 0f, width = width, flingVelocity = fling))
    }

    @Test
    fun quickFlick_flipsEvenWhenDistanceIsSmall() {
        assertEquals(1, swipeDirection(dragged = -40f, velocity = -1500f, width = width, flingVelocity = fling))
        assertEquals(-1, swipeDirection(dragged = 40f, velocity = 1500f, width = width, flingVelocity = fling))
    }

    @Test
    fun slowLongDrag_beatsStaleVelocity() {
        // 慢慢拖过一段后停住再松手：速度已衰减，按位移算
        assertEquals(1, swipeDirection(dragged = -300f, velocity = 30f, width = width, flingVelocity = fling))
    }

    @Test
    fun unknownWidth_neverFlipsByDistance() {
        // 布局还没测量完就松手（宽 0）：不给翻页，避免用 0 阈值误判
        assertEquals(0, swipeDirection(dragged = -500f, velocity = 0f, width = 0f, flingVelocity = fling))
    }
}
