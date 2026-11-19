package com.morrowviento.morrow.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 顶栏结构：三页共用同一套「首行」几何。
 *
 * 之前各页各自写 `Row(verticalAlignment = CenterVertically)`：首页是「大标题 + 副标题」两行，
 * 日历是「标题 + 副标题」两行，设置只有一行。Row 的高度被最高的子项决定，垂直居中之后
 * 同一颗 34dp 圆钮在三页的落点能差好几 dp —— 截图叠在一起就能看出按钮不重合。
 *
 * 现在的约定：**标题首行与右侧按钮各自占一个固定高度的槽**（[PageChrome.HeaderFirstLine]），
 * 槽内垂直居中。于是三页按钮的视觉中心恒等于 `HeaderTop + HeaderFirstLine / 2`，
 * 与标题有几行、字号多大都无关。
 */

/** 顶栏外层：左右/上下内边距统一，子项顶对齐（各槽自己定高）。 */
@Composable
internal fun PageHeaderRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PageChrome.HeaderHorizontal,
                end = PageChrome.HeaderHorizontal,
                top = PageChrome.HeaderTop,
                bottom = PageChrome.HeaderBottom,
            ),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/**
 * 标题首行槽：固定 [PageChrome.HeaderFirstLine] 高，内容在槽内居中。
 *
 * 多行标题只把**第一行**放进槽里，副标题跟在槽下面 —— 这样首行中心永远与按钮对齐。
 */
@Composable
internal fun PageTitleSlot(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.height(PageChrome.HeaderFirstLine),
        contentAlignment = alignment,
        content = content,
    )
}

/** 顶栏右侧按钮组：与标题首行同高的槽，按钮在槽内垂直居中，间距统一 8dp。 */
@Composable
internal fun PageHeaderActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.height(PageChrome.HeaderFirstLine),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PageChrome.HeaderActionGap),
        content = content,
    )
}
