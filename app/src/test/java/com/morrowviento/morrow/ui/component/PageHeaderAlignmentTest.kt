package com.morrowviento.morrow.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.morrowviento.morrow.ui.glass.GlassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 顶栏对齐回归：三页的按钮必须落在同一高度。
 *
 * 用三种真实存在过的标题形态搭出「设置 / 日历 / 首页」的头部：
 * 单行标题、两行小标题、34sp 大标题 + 副标题。断言按钮中心距**各自头部顶端**的偏移完全相等 ——
 * 之前各页按内容高度垂直居中，这个偏移能差出 1.5dp（日历）到 7dp（首页），
 * 截图上叠加就能看到按钮不重合。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PageHeaderAlignmentTest {

    @get:Rule
    val compose = createComposeRule()

    private val tolerance = 0.5f

    @Test
    fun actionCenterSitsOnTitleFirstLineInAllThreeHeaderShapes() {
        compose.setContent {
            GlassTheme(darkTheme = true) {
                Column {
                    // 设置页：一行标题 + 一颗返回钮
                    TaggedHeader("h1") {
                        PageTitleSlot { Text("设置") }
                        PageHeaderActions { Dot("b1") }
                    }
                    // 日历页：标题第一行 + 副标题，左右各一组按钮
                    TaggedHeader("h2") {
                        PageHeaderActions { Dot("ghost") }
                        Column(Modifier.weight(1f)) {
                            PageTitleSlot { Text("2026年11月") }
                            Text("本月 21 次事件")
                        }
                        PageHeaderActions { Dot("b2") }
                    }
                    // 首页：34sp 大标题 + 副标题 + 右侧两个按钮
                    TaggedHeader("h3") {
                        Column(Modifier.weight(1f)) {
                            PageTitleSlot { Text("朝夕") }
                            Text("4 个事件 · 3 进行中")
                        }
                        PageHeaderActions { Dot("b3") }
                    }
                }
            }
        }

        val first = buttonOffsetFromHeader("h1", "b1")
        val second = buttonOffsetFromHeader("h2", "b2")
        val third = buttonOffsetFromHeader("h3", "b3")

        assertEquals("设置页与日历页按钮高度不一致", first, second, tolerance)
        assertEquals("设置页与首页按钮高度不一致", first, third, tolerance)
        // 约定值：HeaderTop(12) + HeaderFirstLine(46) / 2
        assertEquals(12f + 46f / 2f, first, tolerance)
    }

    /** 按钮中心的绝对 y 减去所属 header 顶端 y，得到「距头部顶端」的偏移。 */
    private fun buttonOffsetFromHeader(headerTag: String, buttonTag: String): Float {
        val header = compose.onNodeWithTag(headerTag).getUnclippedBoundsInRoot()
        val button = compose.onNodeWithTag(buttonTag).getUnclippedBoundsInRoot()
        val buttonCenter = (button.top.value + button.bottom.value) / 2f
        return buttonCenter - header.top.value
    }

    @Composable
    private fun TaggedHeader(tag: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
        androidx.compose.foundation.layout.Box(Modifier.testTag(tag)) {
            PageHeaderRow(content = content)
        }
    }

    @Composable
    private fun Dot(tag: String) {
        Box(Modifier.size(34.dp).testTag(tag))
    }
}
