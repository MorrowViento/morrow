package com.morrowviento.morrow.ui.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import com.morrowviento.morrow.widget.WidgetPreview
import com.morrowviento.morrow.widget.WidgetStyle
import com.morrowviento.morrow.data.settings.AppSettings
import com.morrowviento.morrow.data.settings.ThemeMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.morrowviento.morrow.ui.component.ChoiceChip
import com.morrowviento.morrow.ui.home.HomeCalendarContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassLayoutRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun chipTextStaysCenteredWhenSelectionAndFontScaleChange() {
        val selected = mutableStateOf(false)
        val scale = mutableStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale.value)) {
                ChoiceChip("每年重复", selected.value, Modifier.testTag("chip")) { }
            }
        }
        for (fontScale in listOf(1f, 1.5f, 2f)) {
            for (isSelected in listOf(false, true)) {
                compose.runOnIdle { scale.value = fontScale; selected.value = isSelected }
                val chip = compose.onNodeWithTag("chip").fetchSemanticsNode().boundsInRoot
                val text = compose.onNodeWithText("每年重复", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals("文字行盒必须在胶囊里垂直居中", chip.center.y, text.center.y, 0.6f)
                assertTrue(chip.height >= 44f)
                assertTrue(text.top >= chip.top && text.bottom <= chip.bottom)
                if (isSelected) compose.onNodeWithTag("chip").assertIsSelected()
            }
        }
    }

    @Test
    fun wideAndWrappedChipRetainsStartAlignmentWithoutClipping() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                ChoiceChip("一个较长的分类名称需要完整显示", true, Modifier.width(180.dp).testTag("chip")) { }
            }
        }
        val chip = compose.onNodeWithTag("chip").fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText("一个较长的分类名称需要完整显示", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(chip.center.y, text.center.y, 0.6f)
        assertTrue("全宽分类仍从起始侧排布", text.left - chip.left < 40f)
        assertTrue(text.top >= chip.top && text.bottom <= chip.bottom)
    }

    @Test
    fun calendarSwitchRemovesHiddenHomeDuringEntranceAndReturnsCleanly() {
        val calendar = mutableStateOf(false)
        compose.setContent {
            HomeCalendarContent(calendar.value, reduceMotion = false, modifier = Modifier.fillMaxSize()) { active ->
                Box(Modifier.fillMaxSize()) { Text(if (active) "calendar-content" else "home-content") }
            }
        }
        compose.onNodeWithText("home-content").assertExists()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { calendar.value = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.onNodeWithText("calendar-content").assertExists(compose.onRoot(useUnmergedTree = true).printToString())
        compose.onNodeWithText("home-content").assertDoesNotExist()
        compose.runOnIdle { calendar.value = false; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("home-content").assertExists()
        compose.onNodeWithText("calendar-content").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-mdpi")
    fun widgetPreviewUsesMeasuredSizeAndKeepsThemeAndLargeTextConsistent() {
        val settings = mutableStateOf(AppSettings(themeMode = ThemeMode.LIGHT, glassFrost = 0.34f))
        val scale = mutableStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale.value)) {
                Box(Modifier.width(300.dp).testTag("preview")) {
                    WidgetPreview(WidgetStyle(), isDetail = true, settings = settings.value)
                }
            }
        }
        fun backgroundRed(): Float {
            val image = compose.onNodeWithTag("preview").captureToImage()
            return image.toPixelMap()[image.width / 2, image.height - 30].red
        }
        compose.waitUntil(10_000) { backgroundRed() > 0.7f }
        val initial = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
        assertEquals(300f, initial.width, 0.6f)
        assertEquals(170f, initial.height, 0.6f)
        compose.runOnIdle {
            scale.value = 2f
            settings.value = settings.value.copy(themeMode = ThemeMode.DARK)
        }
        compose.waitUntil(10_000) { backgroundRed() < 0.3f }
        val next = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("春节回家").fetchSemanticsNode().boundsInRoot
        assertEquals(300f, next.width, 0.6f)
        assertEquals(340f, next.height, 0.6f)
        assertTrue(title.top >= next.top && title.bottom <= next.bottom)
    }

    @Test
    fun reducedMotionHasOnlyTheActivePage() {
        val calendar = mutableStateOf(false)
        compose.setContent {
            HomeCalendarContent(calendar.value, reduceMotion = true) { active ->
                Text(if (active) "calendar-content" else "home-content")
            }
        }
        compose.runOnIdle { calendar.value = true; Snapshot.sendApplyNotifications() }
        compose.onNodeWithText("calendar-content").assertExists()
        compose.onNodeWithText("home-content").assertDoesNotExist()
    }
}
