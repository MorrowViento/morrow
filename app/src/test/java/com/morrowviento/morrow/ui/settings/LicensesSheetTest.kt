package com.morrowviento.morrow.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.morrowviento.morrow.ui.glass.GlassBackdropHost
import com.morrowviento.morrow.ui.glass.GlassTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页「开源许可」真实组合回归。
 *
 * 覆盖的是数据测试补不上的一环：弹层有界高度、目录行合并语义、点击后从 assets 读全文并渲染。
 * 只断言结构与可见首屏文本，不校验排版像素。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LicensesSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun showSheet() {
        compose.setContent {
            GlassTheme(darkTheme = true) {
                GlassBackdropHost(backgroundColor = Color(0xFF141414)) { _ ->
                    Box(Modifier.fillMaxSize()) {
                        LicensesSheet(onDismiss = {})
                    }
                }
            }
        }
    }

    @Test
    fun glasmioRowIsLabelledApacheAndNotMit() {
        showSheet()
        // clickable 行会合并子节点语义，因此这里能同时命中标题与许可标识。
        compose.onNode(hasText("Glasmio") and hasText("Apache-2.0")).assertExists()
        compose.onNode(hasText("Glasmio") and hasText("MIT")).assertDoesNotExist()
        // 朝夕、ying、MaterialKolor 是 MIT；Glasmio、MCU、Apache 副本是 Apache-2.0。
        compose.onAllNodesWithText("MIT").assertCountEquals(3)
        compose.onAllNodesWithText("Apache-2.0").assertCountEquals(3)
    }

    @Test
    fun tappingDocumentShowsFullTextFromAssets() {
        showSheet()
        compose.onNodeWithText("Glasmio").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Glasmio was extracted", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("返回").assertExists()
        // 全文必须来自 assets（含锁定上游的 Apple 素材排除说明），不是代码内摘要。
        assertTrue(
            compose.onAllNodesWithText("not covered by Glasmio's Apache-2.0 license", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Apple Inc. retains copyright", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun backReturnsToIndexBeforeClosingSheet() {
        showSheet()
        compose.onNodeWithText("ying（原 Flutter 版）").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Permission is hereby granted", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("开源许可").assertExists()
        compose.onNodeWithText("全部第三方组件").assertExists()
    }
}
