package com.morrowviento.morrow.ui.calendar

import android.app.Application
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.morrowviento.morrow.ui.glass.GlassBackdropHost
import com.morrowviento.morrow.ui.glass.GlassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarAnimationTest {
    @get:Rule val compose = createComposeRule()
    private val initial = CalendarUiState(month = YearMonth.of(2026, 11), isLoading = false, monthEventCount = 2)
    private val state = mutableStateOf(initial)

    private fun start(reduceMotion: Boolean = false) {
        compose.setContent {
            val transition = updateTransition(state.value, label = "testCalendarSnapshot")
            Column(Modifier.size(300.dp, 400.dp).testTag("viewport")) {
                CalendarAnimatedContent(transition, Modifier.height(40.dp), slide = false, reduceMotion = reduceMotion) { snapshot ->
                    Text("label-${snapshot.view}-${snapshot.month}-${snapshot.monthEventCount}")
                }
                CalendarAnimatedContent(transition, Modifier.weight(1f), reduceMotion = reduceMotion) { snapshot ->
                    Box(Modifier.fillMaxSize().testTag("body")) {
                        Text("body-${snapshot.view}-${snapshot.month}-${snapshot.monthEventCount}")
                    }
                }
            }
        }
        compose.onNodeWithText("body-MONTH-2026-11-2").assertExists()
        compose.mainClock.autoAdvance = false
    }

    private fun navigate(to: CalendarUiState, millis: Long = 64) {
        compose.runOnIdle { state.value = to; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    @Test
    fun outgoingTitleAndPageKeepTheirOldSnapshotWhileNewPageSlidesIn() {
        start()
        navigate(initial.copy(month = initial.month.plusMonths(1), monthEventCount = 7))
        compose.onNodeWithText("label-MONTH-2026-11-2").assertExists()
        compose.onNodeWithText("label-MONTH-2026-12-7").assertExists()
        compose.onNodeWithText("body-MONTH-2026-11-2").assertExists()
        compose.onNodeWithText("body-MONTH-2026-12-7").assertExists()
        val oldX = compose.onNodeWithText("body-MONTH-2026-11-2").fetchSemanticsNode().positionInRoot.x
        val newX = compose.onNodeWithText("body-MONTH-2026-12-7").fetchSemanticsNode().positionInRoot.x
        assertTrue("下一周期从右侧进入", newX > 0f)
        assertTrue("退出周期向左离开", oldX < 0f)
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("body-MONTH-2026-11-2").assertDoesNotExist()
        compose.onNodeWithText("label-MONTH-2026-11-2").assertDoesNotExist()
        compose.onAllNodesWithTag("body").assertCountEquals(1)
    }

    @Test
    fun rapidNavigationAndReversalFinishWithOnlyTheLatestSnapshot() {
        start()
        navigate(initial.copy(month = initial.month.plusMonths(1)), 48)
        navigate(initial.copy(month = initial.month.plusMonths(2)), 48)
        navigate(initial.copy(monthEventCount = 9), 48)
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("body-MONTH-2026-11-9").assertExists()
        compose.onNodeWithText("label-MONTH-2026-11-9").assertExists()
        compose.onNodeWithText("body-MONTH-2026-12-2").assertDoesNotExist()
        compose.onNodeWithText("body-MONTH-2027-01-2").assertDoesNotExist()
        compose.onAllNodesWithTag("body").assertCountEquals(1)
        assertEquals(0f, compose.onNodeWithText("body-MONTH-2026-11-9").fetchSemanticsNode().positionInRoot.x, 0.6f)
    }

    @Test
    fun selectionAndDataRefreshDoNotCreateAnotherPageOrTranslateNumbers() {
        start()
        navigate(initial.copy(selected = initial.month.atDay(20), monthEventCount = 12))
        compose.onAllNodesWithTag("body").assertCountEquals(1)
        compose.onNodeWithText("body-MONTH-2026-11-12").assertExists()
        compose.onNodeWithText("body-MONTH-2026-11-2").assertDoesNotExist()
        assertEquals(0f, compose.onNodeWithText("body-MONTH-2026-11-12").fetchSemanticsNode().positionInRoot.x, 0.6f)
    }

    @Test
    fun modeCrossfadeKeepsTheViewportFixedAndDoesNotSlide() {
        start()
        val before = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        navigate(initial.copy(view = CalendarView.YEAR))
        val during = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(before.height, during.height, 0.6f)
        assertEquals(before.width, during.width, 0.6f)
        assertEquals(0f, compose.onNodeWithText("body-YEAR-2026-11-2").fetchSemanticsNode().positionInRoot.x, 0.6f)
        compose.mainClock.advanceTimeBy(1000)
        navigate(initial.copy(view = CalendarView.LIST))
        compose.mainClock.advanceTimeBy(1000)
        compose.onAllNodesWithTag("body").assertCountEquals(1)
        compose.onNodeWithText("body-LIST-2026-11-2").assertExists()
    }

    @Test
    fun previousPeriodSlidesFromTheLeft() {
        start()
        navigate(initial.copy(month = initial.month.minusMonths(1)))
        assertTrue(compose.onNodeWithText("body-MONTH-2026-10-2").fetchSemanticsNode().positionInRoot.x < 0f)
        assertTrue(compose.onNodeWithText("body-MONTH-2026-11-2").fetchSemanticsNode().positionInRoot.x > 0f)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun actualCalendarDigitsKeepTheirBoundsWithLargeFontAndBoundaryGesturesDoNotNavigate() {
        val today = initial.month.atDay(17)
        val page = mutableStateOf(CalendarPresentation.build(emptyList(), CalendarNav(month = initial.month, selected = today), today))
        var shifts = 0
        val actions = CalendarActions(
            setView = {}, shiftMonth = { shifts++ }, shiftYear = { shifts++ },
            selectDay = { date -> page.value = page.value.copy(selected = date, cells = page.value.cells.map { it.copy(isSelected = it.date == date) }) },
            openMonth = {}, goToToday = {},
        )
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                GlassTheme(darkTheme = false, reduceMotion = true) {
                    GlassBackdropHost(backgroundColor = Color.White) {
                        CalendarScreen(page.value, actions, onOpenEvent = {}, onDismiss = {}, canPrev = false, canNext = false)
                    }
                }
            }
        }
        val before = compose.onNodeWithText("15", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("15", useUnmergedTree = true).performClick()
        val after = compose.onNodeWithText("15", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 0.6f)
        assertEquals(before.height, after.height, 0.6f)
        assertEquals(before.width, after.width, 0.6f)
        assertEquals(initial.month.atDay(15), page.value.selected)
        compose.onNodeWithContentDescription("上一月").assertIsNotEnabled()
        compose.onNodeWithContentDescription("下一月").assertIsNotEnabled()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onRoot().performTouchInput { swipeRight() }
        assertEquals(0, shifts)
    }

    @Test
    fun reducedMotionReplacesSnapshotWithoutLeavingAnExitingPage() {
        start(reduceMotion = true)
        navigate(initial.copy(month = initial.month.minusMonths(1)))
        compose.onNodeWithText("body-MONTH-2026-10-2").assertExists()
        compose.onNodeWithText("label-MONTH-2026-10-2").assertExists()
        compose.onNodeWithText("body-MONTH-2026-11-2").assertDoesNotExist()
        compose.onAllNodesWithTag("body").assertCountEquals(1)
    }
}
