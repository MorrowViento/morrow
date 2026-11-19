package com.morrowviento.morrow.ui.event

import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.domain.model.SortMode
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

    private fun idsOf(events: List<CountdownEvent>, mode: SortMode): List<String> =
        EventPresentation.partition(events, mode, today, now).active.map { it.id }

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

        val active = EventPresentation.partition(events, SortMode.DISTANCE, today, now).active
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

        val partition = EventPresentation.partition(events, SortMode.DISTANCE, today, now)
        assertEquals(listOf("a"), partition.active.map { it.id })
        assertEquals(listOf("b"), partition.completed.map { it.id })
        // 归档的一批不在前两段里：它是第三条状态线
        assertEquals(emptyList<String>(), partition.archived.map { it.id })
    }

    @Test
    fun archivedEvents_stayOutOfBothActiveAndCompleted() {
        val archivedAt = LocalDateTime.of(2026, 11, 11, 10, 0)
        val events = listOf(
            event("a", "进行中", LocalDateTime.of(2026, 12, 1, 9, 0)),
            event("b", "完成并归档", LocalDateTime.of(2026, 12, 2, 9, 0), isCompleted = true)
                .copy(archivedAt = archivedAt),
            event("c", "放弃", LocalDateTime.of(2026, 12, 3, 9, 0)),
        ).let { list -> list.map { if (it.id == "c") it.copy(archivedAt = archivedAt) else it } }

        val partition = EventPresentation.partition(events, SortMode.DISTANCE, today, now)

        // 归档既不算进行中也算不上「已完成」的成绩：它只出现在归档段
        assertEquals(listOf("a"), partition.active.map { it.id })
        assertEquals(emptyList<String>(), partition.completed.map { it.id })
        assertEquals(listOf("b", "c"), partition.archived.map { it.id }.sorted())
        assertEquals(true, partition.archived.first { it.id == "b" }.isCompleted)
        assertEquals("今天 10:00", partition.archived.first { it.id == "c" }.archivedLabel)
    }

    @Test
    fun progress_isClampedAndRepeatsUseCycleProgress() {
        val createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        val target = LocalDateTime.of(2026, 1, 11, 0, 0)

        assertEquals(0f, EventPresentation.progressOf(createdAt, target, LocalDateTime.of(2025, 12, 20, 0, 0)), 0.001f)
        assertEquals(0.5f, EventPresentation.progressOf(createdAt, target, LocalDateTime.of(2026, 1, 6, 0, 0)), 0.001f)
        assertEquals(1f, EventPresentation.progressOf(createdAt, target, LocalDateTime.of(2026, 2, 1, 0, 0)), 0.001f)

        val repeat = event(
            "r", "生日", LocalDateTime.of(2020, 5, 1, 0, 0),
            repeatType = RepeatType.YEARLY, repeatMonth = 5, repeatDay = 1,
        )
        // 4.5.0 起重复事件不再「一律 0」：环画的是本周期（上次 → 下次）走了多少。
        // 2026-05-01 到 2027-05-01 共 365 天，今天 2026-11-11 已过 194 天 ≈ 0.53
        val cycle = EventPresentation.toCard(repeat, today, now).progress
        assertEquals(194f / 365f, cycle, 0.01f)
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

    @Test
    fun explicitCountdownStaysCountdownAfterTarget() {
        val past = event("past", "过去", now.minusDays(2))
        for (direction in com.morrowviento.morrow.domain.model.CountDirection.entries) {
            val card = EventPresentation.toCard(past.copy(direction = direction), today, now)
            assertEquals(direction.name, direction != com.morrowviento.morrow.domain.model.CountDirection.COUNTDOWN, card.isCountingUp)
        }
    }

    @Test
    fun clockRefreshAdvancesQuantizedProgressAndReusesUnchangedCards() {
        val changing = EventPresentation.toCard(event("a", "进度", now.plusDays(1), createdAt = now.minusDays(1), isAllDay = true), today, now)
        val stable = EventPresentation.toCard(event("b", "重复", now.plusDays(1), repeatType = RepeatType.YEARLY), today, now)
        val cards = listOf(changing, stable)
        org.junit.Assert.assertSame(cards, EventPresentation.refreshClock(cards, today, now.plusSeconds(1)))
        val next = EventPresentation.refreshClock(cards, today, now.plusHours(1))
        assertTrue(next[0].progress > changing.progress)
        org.junit.Assert.assertSame(stable, next[1])
        assertEquals(cards.map { it.id }, next.map { it.id })
    }

    @Test
    fun clockRefreshRemovesTodaySecondsAtTarget() {
        val card = EventPresentation.toCard(event("t", "今天", now.plusSeconds(2)), today, now)
        assertEquals("00:00:02", card.remainingToday)
        val next = EventPresentation.refreshClock(listOf(card), today, now.plusSeconds(2)).single()
        assertNull(next.remainingToday)
        assertEquals(1f, next.progress, 0f)
    }

    // ---- 每秒刷新相关的纯函数（2026-11-13 性能梳理时新增）----

    @Test
    fun remainingToday_padsToTwoDigits() {
        val target = LocalDateTime.of(2026, 11, 11, 9, 5, 3)
        val early = LocalDateTime.of(2026, 11, 11, 8, 0, 0)
        assertEquals("01:05:03", EventPresentation.remainingTodayOf(target, false, today, early))

        val evening = LocalDateTime.of(2026, 11, 11, 18, 30, 0)
        val lateTarget = LocalDateTime.of(2026, 11, 11, 21, 5, 3)
        assertEquals("02:35:03", EventPresentation.remainingTodayOf(lateTarget, false, today, evening))
    }

    @Test
    fun remainingToday_isNullWhenAllDayOrOtherDayOrPassed() {
        val target = LocalDateTime.of(2026, 11, 11, 9, 0)
        assertNull(EventPresentation.remainingTodayOf(target, true, today, now))
        assertNull(
            EventPresentation.remainingTodayOf(
                LocalDateTime.of(2026, 11, 12, 9, 0), false, today, now,
            ),
        )
        assertNull(EventPresentation.remainingTodayOf(target, false, today, LocalDateTime.of(2026, 11, 11, 9, 0)))
    }

    @Test
    fun progress_isQuantizedSoOneSecondDoesNotChangeIt() {
        val created = LocalDateTime.of(2026, 1, 1, 0, 0)
        val target = LocalDateTime.of(2026, 12, 31, 0, 0)
        val a = EventPresentation.progressOf(created, target, LocalDateTime.of(2026, 6, 1, 12, 0, 0))
        val b = EventPresentation.progressOf(created, target, LocalDateTime.of(2026, 6, 1, 12, 0, 1))
        // 364 天跨度下，1 秒的变化远小于 1/256 的一格——量化后必须相等，
        // 否则 HomeUiState 每秒都不相等，整页跟着每秒重组
        assertEquals(a, b, 0f)
        assertTrue(a in 0f..1f)
    }

    @Test
    fun progress_isClampedAtBothEnds() {
        val target = LocalDateTime.of(2026, 12, 31, 0, 0)
        assertEquals(1f, EventPresentation.progressOf(LocalDateTime.of(2026, 1, 1, 0, 0), target, LocalDateTime.of(2027, 1, 2, 0, 0)), 0f)
        assertEquals(0f, EventPresentation.progressOf(LocalDateTime.of(2026, 6, 1, 0, 0), target, LocalDateTime.of(2026, 1, 1, 0, 0)), 0f)
    }

    @Test
    fun meta_precomputedWithoutStringFormat() {
        val card = EventPresentation.toCard(
            event("m1", "年会", LocalDateTime.of(2026, 12, 3, 9, 5), isAllDay = false),
            today,
            now,
        )
        assertEquals("生活 · 12月3日 · 09:05", card.meta)

        val yearly = EventPresentation.toCard(
            event(
                "m2", "生日", LocalDateTime.of(2026, 12, 3, 9, 5),
                repeatType = RepeatType.YEARLY, repeatMonth = 12, repeatDay = 3,
            ),
            today,
            now,
        )
        assertEquals("生活 · 每年 12月3日 · 09:05", yearly.meta)

        val allDay = EventPresentation.toCard(
            event("m3", "假期", LocalDateTime.of(2026, 12, 3, 0, 0), isAllDay = true),
            today,
            now,
        )
        assertEquals("生活 · 12月3日 · 全天", allDay.meta)
    }

    @Test
    fun partition_separatesAnniversariesFromTodos() {
        val todo = event("t1", "交房租", LocalDateTime.of(2026, 12, 1, 9, 0))
        val anniversary = event("a1", "在一起", LocalDateTime.of(2026, 5, 1, 9, 0))
            .copy(kind = com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY)
        val parts = EventPresentation.partition(listOf(todo, anniversary), SortMode.DISTANCE, today, now)

        // 纪念日不进「待办」段：两段读法不同，混排会让人以为纪念日也是一件要做的事
        assertEquals(listOf("t1"), parts.active.map { it.id })
        assertEquals(listOf("a1"), parts.anniversaries.map { it.id })
    }

    @Test
    fun partition_anniversariesStillExcludeArchivedAndCompleted() {
        val done = event("a1", "在一起", LocalDateTime.of(2026, 5, 1, 9, 0))
            .copy(kind = com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY, isCompleted = true)
        val archived = event("a2", "毕业", LocalDateTime.of(2026, 6, 1, 9, 0))
            .copy(
                kind = com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY,
                archivedAt = LocalDateTime.of(2026, 10, 1, 12, 0),
            )
        val parts = EventPresentation.partition(listOf(done, archived), SortMode.DISTANCE, today, now)
        assertTrue(parts.anniversaries.isEmpty())
        assertEquals(listOf("a1"), parts.completed.map { it.id })
        assertEquals(listOf("a2"), parts.archived.map { it.id })
    }

    @Test
    fun card_carriesMilestonesOnlyForAnniversaries() {
        val anniversary = event("a1", "在一起", LocalDateTime.of(2026, 5, 1, 9, 0)).copy(
            kind = com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY,
            milestones = listOf(100, 300),
            createdAt = LocalDateTime.of(2026, 1, 1, 9, 0),
        )
        val card = EventPresentation.toCard(anniversary, today, now)
        assertEquals(listOf(100, 300), card.milestones)
        // 起始日 = targetDate（2026-05-01），到 2026-11-11 是 194 天
        assertEquals(194L, card.elapsedDays)

        // 同一条数据换成待办：卡片上不该出现里程碑（表单里也看不到那一区）
        val todo = anniversary.copy(kind = com.morrowviento.morrow.domain.model.EventKind.TODO)
        assertTrue(EventPresentation.toCard(todo, today, now).milestones.isEmpty())
        assertNull(EventPresentation.toCard(todo, today, now).elapsedDays)
    }

    @Test
    fun cardById_findsAnniversaryCard() {
        // 回归：4.6.0 的详情浮层只查 active + completed，纪念日拆段之后
        // 点纪念日卡片拿不到 card，浮层整块不出现（点了没反应）
        val list = listOf(
            event("t1", "交房租", LocalDateTime.of(2026, 11, 20, 9, 0)),
            event("a1", "在一起", LocalDateTime.of(2026, 5, 1, 9, 0))
                .copy(kind = com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY),
            event("c1", "已办", LocalDateTime.of(2026, 11, 1, 9, 0), isCompleted = true),
        )
        val parts = EventPresentation.partition(list, SortMode.DISTANCE, today, now)
        val state = HomeUiState(
            anniversaries = parts.anniversaries,
            active = parts.active,
            completed = parts.completed,
            isLoading = false,
        )

        assertEquals("a1", state.cardById("a1")?.id)
        assertEquals("t1", state.cardById("t1")?.id)
        assertEquals("c1", state.cardById("c1")?.id)
        assertNull(state.cardById("不存在"))
        // 三段合并的顺序就是首页阅读顺序
        assertEquals(listOf("a1", "t1", "c1"), state.visibleCards().map { it.id })
    }
}
