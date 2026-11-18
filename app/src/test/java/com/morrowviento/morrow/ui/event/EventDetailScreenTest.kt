package com.morrowviento.morrow.ui.event

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.ui.glass.GlassBackdropHost
import com.morrowviento.morrow.ui.glass.GlassTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 详情页对「有时长 / 持续 / 归档」事件卡片的真实组合回归。
 *
 * 覆盖数据测试补不上的一环：详情页用 [EventPresentation.toCard] 的真实产物渲染，
 * 且长时长的卡片也要能渲染出「时长」行而不崩溃（对应「添加有时长的事件后点列表闪退」）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun cardWithDuration(minutes: Int): EventCardModel {
        val event = CountdownEvent(
            id = "d1",
            title = "开会",
            category = "工作",
            note = "",
            targetDate = LocalDateTime.of(2026, 11, 18, 10, 0),
            createdAt = LocalDateTime.of(2026, 11, 1, 9, 0),
            isAllDay = false,
            durationMinutes = minutes,
        )
        val today = LocalDate.of(2026, 11, 18)
        return EventPresentation.toCard(event, today, LocalDateTime.of(2026, 11, 18, 9, 0))
    }

    private fun showDetail(card: EventCardModel) {
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
    }

    @Test
    fun durationCardRendersDurationRowWithoutCrash() {
        showDetail(cardWithDuration(60))
        compose.onNodeWithText("时长").assertExists()
        compose.onNodeWithText("1 小时").assertExists()
    }

    @Test
    fun halfHourDurationCardRendersMinutesLabel() {
        showDetail(cardWithDuration(30))
        compose.onNodeWithText("30 分钟").assertExists()
    }
}
