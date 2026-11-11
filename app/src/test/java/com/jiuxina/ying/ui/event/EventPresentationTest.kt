package com.jiuxina.ying.ui.event

import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.domain.model.RepeatType
import com.jiuxina.ying.domain.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 首页展示逻辑单测：只测纯函数，不碰 Room/Compose。
 * 时间一律固定，避免用例随真实日期漂移。
 */
class EventPresentationTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 11)
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 11, 15, 30)

    private fun event(
        id: String,
        title: String,
        target: LocalDateTime,
        createdAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
        isPinned: Boolean = false,
        isCompleted: Boolean = false,
        isAllDay: Boolean = false,
        repeatType: RepeatType = RepeatType.NONE,
        repeatMonth: Int? = null,
        repeatDay: Int? = null,
    ) = CountdownEvent(
        id = id,
        title = title,
        targetDate = target,
        createdAt = createdAt,
        isPinned = isPinned,
        isCompleted = isCompleted,
        isAllDay = isAllDay,
        repeatType = repeatType,
        repeatMonth = repeatMonth,
        repeatDay = repeatDay,
    )

    private fun idsOf(events: List<CountdownEvent>, mode: SortMode): List<String> {
        val (active, _) = EventPresentation.partition(events, mode, today, now)
        return active.map { it.id }
    }

    @Test
    fun distanceSort_putsTodayFirstThenFutureThenRecentPast() {
        val events = listOf(
            event("past-5", "五天前", LocalDateTime.of(2026, 11, 6, 9, 0)),
            event("future-3", "三天后", LocalDateTime.of(2026, 11, 14, 9, 0)),
            event("today", "今天", LocalDateTime.of(2026, 11, 11, 18, 0)),
            event("future-30", "一个月后", LocalDateTime.of(2026, 12, 11, 9, 0)),
            event("past-1", "昨天", LocalDateTime.of(2026, 11, 10, 9, 0)),
        )

        assertEquals(
            listOf("today", "future-3", "future-30", "past-1", "past-5"),
            idsOf(events, SortMode.DISTANCE),
        )
    }

    @Test
    fun pinnedAlwaysOnTop_regardlessOfMode() {
        val events = listOf(
            event("near", "明天", LocalDateTime.of(2026, 11, 12, 9, 0)),
            event("pinned-far", "置顶", LocalDateTime.of(2027, 6, 1, 9, 0), isPinned = true),
        )

        listOf(SortMode.DISTANCE, SortMode.TARGET_DATE, SortMode.CREATED_AT).forEach { mode ->
            assertEquals(mode.name, listOf("pinned-far", "near"), idsOf(events, mode))
        }
    }

    @Test
    fun targetDateSort_isAscendingByResolvedTarget() {
        val events = listOf(
            event("dec", "十二月", LocalDateTime.of(2026, 12, 1, 9, 0)),
            event("oct", "十月已经过了", LocalDateTime.of(2026, 10, 1, 9, 0)),
            event("nov", "十一月", LocalDateTime.of(2026, 11, 20, 9, 0)),
        )

        assertEquals(listOf("oct", "nov", "dec"), idsOf(events, SortMode.TARGET_DATE))
    }

    @Test
    fun createdAtSort_newestFirst() {
        val events = listOf(
            event("old", "旧", LocalDateTime.of(2026, 12, 1, 9, 0), createdAt = LocalDateTime.of(2025, 5, 1, 0, 0)),
            event("new", "新", LocalDateTime.of(2026, 12, 2, 9, 0), createdAt = LocalDateTime.of(2026, 8, 1, 0, 0)),
            event("mid", "中", LocalDateTime.of(2026, 12, 3, 9, 0), createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)),
        )

        assertEquals(listOf("new", "mid", "old"), idsOf(events, SortMode.CREATED_AT))
    }

    @Test
    fun yearlyRepeat_rollsForwardToNextAnniversary() {
        val events = listOf(
            // 今年的周年已过（1 月 1 日）→ 应换算到明年，44 天的不该排在它前面
            event(
                "passed-anniversary",
                "元旦",
                LocalDateTime.of(2020, 1, 1, 0, 0),
                repeatType = RepeatType.YEARLY,
                repeatMonth = 1,
                repeatDay = 1,
            ),
            event("upcoming", "圣诞", LocalDateTime.of(2026, 12, 25, 9, 0)),
        )

        val (active, _) = EventPresentation.partition(events, SortMode.DISTANCE, today, now)
        val anniversary = active.first { it.id == "passed-anniversary" }
        assertEquals(LocalDate.of(2027, 1, 1), anniversary.target.toLocalDate())
        assertEquals(51, anniversary.dayDelta)
        assertEquals(listOf("upcoming", "passed-anniversary"), active.map { it.id })
    }

    @Test
    fun completedEvents_goToSeparateSection() {
        val events = listOf(
            event("a", "进行中", LocalDateTime.of(2026, 12, 1, 9, 0)),
            event("b", "已完成", LocalDateTime.of(2026, 12, 2, 9, 0), isCompleted = true),
        )

        val (active, completed) = EventPresentation.partition(events, SortMode.DISTANCE, today, now)
        assertEquals(listOf("a"), active.map { it.id })
        assertEquals(listOf("b"), completed.map { it.id })
    }

    @Test
    fun progress_isClampedAndZeroForRepeats() {
        val createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        val target = LocalDateTime.of(2026, 1, 11, 0, 0)

        assertEquals(0f, EventPresentation.progressOf(createdAt, target, LocalDateTime.of(2025, 12, 20, 0, 0)), 0.001f)
        assertEquals(0.5f, EventPresentation.progressOf(createdAt, target, LocalDateTime.of(2026, 1, 6, 0, 0)), 0.001f)
        assertEquals(1f, EventPresentation.progressOf(createdAt, target, LocalDateTime.of(2026, 2, 1, 0, 0)), 0.001f)

        val repeat = event(
            "r", "生日", LocalDateTime.of(2020, 5, 1, 0, 0),
            repeatType = RepeatType.YEARLY, repeatMonth = 5, repeatDay = 1,
        )
        assertEquals(0f, EventPresentation.toCard(repeat, today, now).progress, 0.001f)
    }

    @Test
    fun remainingToday_onlyForTimedEventLaterToday() {
        val laterToday = event("t", "今晚", LocalDateTime.of(2026, 11, 11, 18, 30))
        assertEquals("03:00:00", EventPresentation.toCard(laterToday, today, now).remainingToday)

        val alreadyPassed = event("p", "早上", LocalDateTime.of(2026, 11, 11, 9, 0))
        assertNull(EventPresentation.toCard(alreadyPassed, today, now).remainingToday)

        val tomorrow = event("m", "明天", LocalDateTime.of(2026, 11, 12, 9, 0))
        assertNull(EventPresentation.toCard(tomorrow, today, now).remainingToday)

        val allDay = event("d", "全天", LocalDateTime.of(2026, 11, 11, 0, 0), isAllDay = true)
        assertNull(EventPresentation.toCard(allDay, today, now).remainingToday)
    }

    @Test
    fun todayCard_hasZeroDeltaAndIsNotCountingUp() {
        val card = EventPresentation.toCard(
            event("t", "今天", LocalDateTime.of(2026, 11, 11, 18, 0)),
            today,
            now,
        )
        assertEquals(0, card.dayDelta)
        assertEquals(0, card.days)
        assertTrue(!card.isCountingUp)
    }

    @Test
    fun sortMode_mapsLegacyPrefValues() {
        assertEquals(SortMode.TARGET_DATE, SortMode.fromPref("targetDate"))
        assertEquals(SortMode.CREATED_AT, SortMode.fromPref("createdAt"))
        assertEquals(SortMode.DISTANCE, SortMode.fromPref("distance"))
        assertEquals(SortMode.DISTANCE, SortMode.fromPref(null))
        assertEquals(SortMode.DISTANCE, SortMode.fromPref("unknown"))
    }
}
