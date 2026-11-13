package com.morrowviento.morrow.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.data.category.CategoryItem
import com.morrowviento.morrow.ui.component.ChoiceChip
import com.morrowviento.morrow.ui.component.ChipRow
import com.morrowviento.morrow.ui.event.EventCardModel
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.glassAccent
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.home.EventCardView

/**
 * 搜索浮层（4.1.0）。
 *
 * ## 为什么从「独立页面」改成「主界面上的浮层」
 *
 * 上一版搜索是首页**同一列里的另一种内容**：点底栏的放大镜，整页横向推入一张搜索页。
 * 它有两个问题：
 *
 * 1. **上下文断了**。搜索是为了「在现在这堆事件里找一条」，整页替换之后，用户看不到
 *    原来的列表、底栏也变成了另一套高亮，感觉像跳到了另一个功能；
 * 2. **进出都是整页转场**。一次搜索要付两次页面切换的动画成本，而搜索本身是高频动作。
 *
 * 现在它是**从顶部展开的一块玻璃面板**：底下的首页仍在（透过玻璃与遮罩能看到），
 * 关掉就回到原处，滚动位置、列表状态全都还在。这也更贴近「弹出一个搜索框」的直觉。
 *
 * 结果只列匹配项，点一条直接开详情；分类 chip 仍然可叠加（先按分类缩小，再搜关键词）。
 */
@Composable
fun SearchOverlay(
    active: List<EventCardModel>,
    completed: List<EventCardModel>,
    categories: List<CategoryItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: CategoryFilter,
    onFilterChange: (CategoryFilter) -> Unit,
    onOpenEvent: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allCards = remember(active, completed) { active + completed }
    val result = remember(active, completed, query, filter) {
        EventSearch.apply(active, completed, query, filter)
    }
    val counts = remember(allCards, categories) {
        EventSearch.categoryCounts(allCards, categories.map { it.name })
    }
    val uncategorized = remember(allCards) { EventSearch.uncategorizedCount(allCards) }

    Box(modifier.fillMaxSize()) {
        // 遮罩：点一下就关。用纯黑而不是 Material Scrim 的灰，才不会把玻璃透出来的暖色压掉
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )

        GlassPanel(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // padding 必须在 size 之前：反过来玻璃会铺到屏幕左右边缘，只是内容被缩进。
                .padding(start = 10.dp, end = 10.dp, top = 6.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.86f),
            // 比卡片（26dp）更圆：它是一块「从顶上拉下来的面板」，不是列表里的一张卡
            cornerRadius = 30.dp,
            contentAlignment = Alignment.TopStart,
        ) {
            // 吞掉面板空白处的点击，否则会穿透到下面的遮罩上（点面板 = 点外面）
            Column(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                SearchHeader(query = query, onQueryChange = onQueryChange, onDismiss = onDismiss)
                ChipRow(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp)) {
                    ChoiceChip(
                        label = "全部 ${allCards.size}",
                        selected = filter == CategoryFilter.All,
                        onClick = { onFilterChange(CategoryFilter.All) },
                    )
                    counts.forEach { (name, count) ->
                        ChoiceChip(
                            label = "$name $count",
                            selected = filter == CategoryFilter.Named(name),
                            onClick = {
                                // 再点一次取消筛选：手指已经在同一颗 chip 上，回「全部」比
                                // 逼用户去点最左边那颗少一步。
                                onFilterChange(
                                    if (filter == CategoryFilter.Named(name)) {
                                        CategoryFilter.All
                                    } else {
                                        CategoryFilter.Named(name)
                                    },
                                )
                            },
                        )
                    }
                    if (uncategorized > 0) {
                        ChoiceChip(
                            label = "未分类 $uncategorized",
                            selected = filter == CategoryFilter.Uncategorized,
                            onClick = {
                                onFilterChange(
                                    if (filter == CategoryFilter.Uncategorized) {
                                        CategoryFilter.All
                                    } else {
                                        CategoryFilter.Uncategorized
                                    },
                                )
                            },
                        )
                    }
                }

                if (result.isEmpty) {
                    EmptyResult(
                        query = query,
                        hasAnyEvent = allCards.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        // weight 而不是 fillMaxWidth：Column 给子项的是「整块面板高度」，
                        // 不给 weight 的话列表会按内容高度撑开，最后几条被面板裁掉。
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        // 浮层底部不再压底栏，留 14dp 收边即可
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "search-count") {
                            Text(
                                text = "找到 ${result.count} 个事件",
                                style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                                modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 2.dp),
                            )
                        }
                        if (result.active.isNotEmpty()) {
                            item(key = "search-active") {
                                SectionLabel("进行中 · ${result.active.size}")
                            }
                            items(result.active, key = { it.id }) { card ->
                                EventCardView(card = card, onClick = { onOpenEvent(card.id) })
                            }
                        }
                        if (result.completed.isNotEmpty()) {
                            item(key = "search-completed") {
                                SectionLabel("已完成 · ${result.completed.size}")
                            }
                            items(result.completed, key = { "done-${it.id}" }) { card ->
                                EventCardView(
                                    card = card,
                                    // 与首页已完成卡片同一套压暗方式（不用 Modifier.alpha，
                                    // 否则按压放大会被离屏层裁掉）
                                    dim = 0.55f,
                                    onClick = { onOpenEvent(card.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // 展开即聚焦、弹出键盘：用户点放大镜就是为了输入，再让用户点一下输入框是多余动作。
    // 每帧只跑一次（key 是 Unit）。
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            focusRequester = focusRequester,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        // 文字按钮比左上角那个「‹」更适合浮层：它明确表示「收起这一层」，而不是「返回上一页」
        Text(
            text = "取消",
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = glassTextPrimary()),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val accent = glassAccent()
    GlassPanel(
        modifier = modifier.height(40.dp),
        capsule = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchGlyph(color = secondary, glyphSize = 15.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索标题、备注或分类",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, color = secondary.copy(alpha = 0.75f)),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = primary),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.12f))
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✕",
                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = secondary),
                    )
                }
            }
        }
    }
}

/** 放大镜：自绘，不依赖字体里有没有该字形。 */
@Composable
private fun SearchGlyph(color: Color, glyphSize: Dp) {
    Canvas(Modifier.size(glyphSize)) {
        val stroke = 1.7.dp.toPx()
        val w = size.width
        val h = size.height
        drawCircle(
            color = color,
            radius = w * 0.31f,
            center = Offset(w * 0.42f, h * 0.41f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(w * 0.64f, h * 0.63f),
            end = Offset(w * 0.92f, h * 0.90f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = glassTextSecondary()),
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyResult(query: String, hasAnyEvent: Boolean, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = when {
                !hasAnyEvent -> "还没有事件可以搜索"
                query.isBlank() -> "这个分类下没有事件"
                else -> "没有匹配「${query.trim()}」的事件"
            },
            style = TextStyle(fontSize = 13.sp, color = glassTextSecondary()),
        )
    }
}
