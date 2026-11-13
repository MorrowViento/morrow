package com.morrowviento.morrow.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.glassAccent
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.home.EventCardView

/**
 * 搜索页（4.0.0-alpha24）。
 *
 * 它是首页**同一列里的另一种内容**，不是盖在首页之上的浮层：
 * 这一列登记成了底栏的玻璃采样源（`glassContentSource`），把结果列表放在里面，
 * 滚动时卡片才会从底栏背后透出来；换成独立整页浮层的话，底栏采到的仍是首页那张
 * 列表，玻璃里会折着「另一页的卡片」。
 *
 * 搜索与筛选都是纯函数（[EventSearch]）：查询串对标题 / 备注 / 分类做字面包含匹配，
 * 分类 chip 做精确匹配，两者可以叠加——先按分类缩小范围，再在结果里搜关键词。
 *
 * @param filter 当前分类筛选；`All` 时不筛分类。
 * @param onFilterChange 点 chip 回调；再点一次已选中的分类会回到 `All`（由这里处理）。
 */
@Composable
fun SearchPage(
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

    Column(modifier = modifier.fillMaxSize()) {
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
            EmptyResult(query = query, hasAnyEvent = allCards.isNotEmpty())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 与首页一致：底部留白让最后一张卡完整露出，中间的卡片能从底栏背后穿过去
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 94.dp),
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

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // 进页即聚焦、弹出键盘：搜索页的唯一目的就是输入，再让用户点一下输入框是多余动作。
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCirclePanel(
            modifier = Modifier.size(34.dp),
            onClick = onDismiss,
        ) {
            Text(
                text = "‹",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = glassTextPrimary()),
            )
        }
        Spacer(Modifier.width(10.dp))
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            focusRequester = focusRequester,
            modifier = Modifier.weight(1f),
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
private fun EmptyResult(query: String, hasAnyEvent: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
