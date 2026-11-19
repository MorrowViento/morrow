package com.morrowviento.morrow.ui.event

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.ui.glass.GlassBackdropHost
import com.morrowviento.morrow.ui.glass.GlassTheme
import com.morrowviento.morrow.ui.home.EventList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 「有时间 + 每天/每周」这条组合的真实渲染回归。
 *
 * 「进入列表闪退」只在这种组合下复现，所以两条腿都要走：
 * 1. [toCard] 纯函数（首页卡片的字段全在这里算出来）；
 * 2. 真正的 Compose 渲染——列表卡片与详情页各渲染一遍，
 *    否则崩溃发生在组合期（而不是算字段时）就抓不到。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RepeatTimedEventRenderTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 19)
    private val now = LocalDateTime.of(2026, 11, 19, 9, 0)

    private fun event(
        repeat: RepeatType,
        allDay: Boolean,
        weekdays: Set<Int> = emptySet(),
        interval: Int = 1,
        until: LocalDate? = null,
        endDate: LocalDate? = null,
        duration: Int = 0,
        /** 锚点在过去：让「下一次」必须由重复规则推出来。 */
        anchor: LocalDateTime = LocalDateTime.of(2026, 11, 2, 7, 30),
        /** 列表里同 key 会直接抛 IllegalArgumentException，组合测试必须每条唯一。 */
        id: String = "r-${repeat.name}-$allDay-$interval-$weekdays-${duration}-${endDate}-${until}",
    ) = CountdownEvent(
        id = id,
        title = "背单词",
        category = "学习",
        targetDate = anchor,
        createdAt = LocalDateTime.of(2026, 10, 1, 9, 0),
        isAllDay = allDay,
        repeatType = repeat,
        repeatInterval = interval,
        repeatWeekdays = weekdays,
        repeatUntil = until,
        endDate = endDate,
        durationMinutes = duration,
    )

    /** 覆盖用户能造出来的组合：每天/每周 × 全天/有时刻 × 有/无时长 × 有/无结束日。 */
    private fun matrix(): List<CountdownEvent> = listOf(
        event(RepeatType.DAILY, allDay = false),
        event(RepeatType.DAILY, allDay = false, duration = 45),
        event(RepeatType.DAILY, allDay = false, interval = 3),
        event(RepeatType.DAILY, allDay = false, until = LocalDate.of(2027, 3, 1)),
        event(RepeatType.DAILY, allDay = false, endDate = LocalDate.of(2026, 11, 25)),
        event(RepeatType.DAILY, allDay = true),
        event(RepeatType.WEEKLY, allDay = false),
        event(RepeatType.WEEKLY, allDay = false, weekdays = setOf(1, 3, 5)),
        event(RepeatType.WEEKLY, allDay = false, weekdays = setOf(7), interval = 2),
        event(RepeatType.WEEKLY, allDay = false, until = LocalDate.of(2026, 12, 31)),
        event(RepeatType.WEEKLY, allDay = false, endDate = LocalDate.of(2026, 11, 22), duration = 90),
        event(RepeatType.WEEKLY, allDay = true),
        event(RepeatType.MONTHLY, allDay = false),
        event(RepeatType.YEARLY, allDay = false),
    )

    @Test
    fun everyRepeatCombinationMapsToCardWithoutThrowing() {
        matrix().forEach { e ->
            val card = EventPresentation.toCard(e, today, now)
            // 字段真算出来了：重复文案、日期行都不能是空的
            assert(card.meta.isNotBlank()) { "meta 为空：${e.id}" }
            assert(card.repeatLabel.isNotBlank()) { "repeatLabel 为空：${e.id}" }
            assert(card.target.year in 1..9999) { "target 越界：${e.id} -> ${card.target}" }
        }
    }

    @Test
    fun dailyTimedCardRendersInList() = renderListOf(
        event(RepeatType.DAILY, allDay = false, duration = 45),
    )

    @Test
    fun weeklyTimedCardRendersInList() = renderListOf(
        event(RepeatType.WEEKLY, allDay = false, weekdays = setOf(1, 3, 5)),
    )

    @Test
    fun allRepeatCardsRenderInOneList() = renderListOf(*matrix().toTypedArray())

    @Test
    fun dailyTimedDetailRenders() = renderDetailOf(event(RepeatType.DAILY, allDay = false, duration = 45))

    @Test
    fun weeklyTimedDetailRenders() =
        renderDetailOf(event(RepeatType.WEEKLY, allDay = false, weekdays = setOf(1, 3, 5)))

    /** 表单回填 → 再保存：重复 + 有时刻这条链路不能在中途丢字段或抛异常。 */
    @Test
    fun formRoundTripKeepsRepeatAndTime() {
        matrix().forEach { e ->
            val form = EventFormState.fromEvent(e)
            val saved = form.toEvent(null)
            assert(saved != null) { "toEvent 返回 null：${e.id}" }
            saved!!
            assert(saved.repeatType == e.repeatType) { "重复类型丢失：${e.id}" }
            assert(saved.isAllDay == e.isAllDay) { "全天标记丢失：${e.id}" }
            assert(saved.repeatInterval == e.repeatInterval) { "间隔丢失：${e.id}" }
        }
    }

    /** 新建面板本身也要能画出来：用户就是在这一步加的「每天 / 每周」。 */
    @Test
    fun editSheetRendersForDailyTimedForm() = renderEditSheet(event(RepeatType.DAILY, allDay = false))

    @Test
    fun editSheetRendersForWeeklyTimedForm() =
        renderEditSheet(event(RepeatType.WEEKLY, allDay = false, weekdays = setOf(2, 4)))

    private fun renderEditSheet(event: CountdownEvent) {
        val form = EventFormState.fromEvent(event)
        compose.setContent {
            GlassTheme(darkTheme = true) {
                GlassBackdropHost(backgroundColor = Color(0xFF141414)) { _ ->
                    Box(Modifier.fillMaxSize()) {
                        EventEditSheet(
                            initial = form,
                            categories = listOf(event.category),
                            categoryAliases = emptyMap(),
                            tagSuggestions = emptyList(),
                            onManageCategories = {},
                            onDismiss = {},
                            onSave = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun cards(vararg events: CountdownEvent): List<EventCardModel> =
        events.map { EventPresentation.toCard(it, today, now) }

    private fun renderListOf(vararg events: CountdownEvent) {
        val cards = cards(*events)
        compose.setContent {
            GlassTheme(darkTheme = true) {
                GlassBackdropHost(backgroundColor = Color(0xFF141414)) { _ ->
                    Box(Modifier.fillMaxSize()) {
                        val listState = rememberLazyListState()
                        EventList(
                            state = HomeUiState(active = cards, isLoading = false),
                            listState = listState,
                            onToggleCompleted = {},
                            onOpenDetail = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun renderDetailOf(event: CountdownEvent) {
        val card = EventPresentation.toCard(event, today, now)
        compose.setContent {
            GlassTheme(darkTheme = true) {
                GlassBackdropHost(backgroundColor = Color(0xFF141414)) { _ ->
                    Box(Modifier.fillMaxSize()) {
                        EventDetailScreen(
                            card = card,
                            onDismiss = {},
                            onEdit = {},
                            onToggleCompleted = {},
                            onTogglePinned = {},
                            onDelete = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }
}
