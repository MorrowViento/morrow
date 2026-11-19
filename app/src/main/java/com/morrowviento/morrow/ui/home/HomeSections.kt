package com.morrowviento.morrow.ui.home

/**
 * 首页的分区组件：日历槽、大标题、排序胶囊与列表/空态。
 *
 * 只依赖传入的 state 与回调，不持有首页自己的滚动/搜索状态，所以与 HomeScreen 分开放。
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.component.PageChrome
import com.morrowviento.morrow.ui.component.PageHeaderActions
import com.morrowviento.morrow.ui.component.PageHeaderRow
import com.morrowviento.morrow.ui.component.PageTitleSlot
import com.morrowviento.morrow.ui.component.SortGlyph
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.event.HomeUiState
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.LocalFestivalSkin
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.GlassMenu
import com.morrowviento.morrow.ui.glass.GlassMotion
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary

@Composable
internal fun HomeCalendarContent(
    calendarActive: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Boolean) -> Unit,
) {
    AnimatedContent(
        targetState = calendarActive,
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            // 旧页面立即退场，不用重新铺壁纸遮挡隐藏首页。
            val enter = if (reduceMotion) EnterTransition.None else {
                fadeIn(GlassMotion.enterFade) + slideInHorizontally(GlassMotion.navigationSlide) { width ->
                    if (targetState) width / 12 else -width / 12
                }
            }
            (enter togetherWith ExitTransition.None).using(null)
        },
        label = "homeCalendarPage",
    ) { active ->
        Box(Modifier.fillMaxSize()) {
            // AnimatedContent 在新页入场完成前仍保留旧槽，旧槽不再绘制或接收触摸。
            if (active == calendarActive) content(active)
        }
    }
}

/**
 * 大标题随滚动收缩（iOS 式），副标题在收缩后隐藏。
 *
 * 标题右侧是「排序胶囊 + ⋯」：排序原本在底栏左侧，但底栏改成
 * 「首页 / ＋ / 日历」之后就没有它的位置了，放在标题区既能一直看到，
 * 也不会像之前那样把三颗按钮挤在大标题右边。
 */
@Composable
internal fun HomeHeader(
    state: HomeUiState,
    listState: LazyListState,
    sortLabel: String,
    onCycleSort: () -> Unit,
    moreAction: @Composable () -> Unit,
    /** 当前是否在设置页点了「预览节日」——祝语前面加标记，点一下结束。 */
    festivalPreviewing: Boolean = false,
    onEndFestivalPreview: () -> Unit = {},
) {
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40 }
    }
    val titleSize by animateFloatAsState(if (collapsed) 21f else 34f, label = "homeTitleSize")
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()

    // 顶栏首行固定槽高：标题第一行与右侧按钮同高居中，三页按钮因此落在同一条基线上。
    PageHeaderRow {
        Column(modifier = Modifier.weight(1f)) {
            PageTitleSlot {
                Text(
                    text = "朝夕",
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = titleSize.sp,
                        // 显式行高：34sp 的默认行高会超过首行槽，把字形裁掉一截
                        lineHeight = (titleSize * 1.15f).sp,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                    ),
                )
            }
            // 节日只在展开态出现：折叠后标题那一行已经很窄，再挤一句祝语会把
            // 「朝夕」压成省略号
            val festival = LocalFestivalSkin.current
            AnimatedVisibility(visible = !collapsed && festival != null) {
                if (festival != null) {
                    Text(
                        // 预览时加「预览」前缀并允许点击结束：否则用户会以为今天真是节日，
                        // 而且没有别的出口能退出预览
                        text = if (festivalPreviewing) {
                            "预览 · ${festival.greeting}"
                        } else {
                            "${festival.name} · ${festival.greeting}"
                        },
                        maxLines = 1,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            // 用节日色本身而不是强调色：强调色可能被用户设成同一支蓝，
                            // 那这行小字就失去了「今天是节日」的信号
                            color = Color(festival.tint),
                        ),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .then(
                                if (festivalPreviewing) {
                                    Modifier.clickable(onClick = onEndFestivalPreview)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
            AnimatedVisibility(visible = !collapsed) {
                Text(
                    text = "${state.totalCount} 个事件 · ${state.active.size} 进行中",
                    style = TextStyle(fontSize = 12.sp, color = secondary),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // 右侧按钮组：与标题首行同高，内部垂直居中。
        PageHeaderActions {
            SortChip(label = sortLabel, onClick = onCycleSort)
            moreAction()
        }
    }
}

/**
 * 排序胶囊：图标 + 当前排序名，点一下轮换（距离 → 目标日期 → 创建时间）。
 *
 * 用玻璃胶囊而不是纯文字按钮：它和「⋯」同处一行，材质一致才不会像两套控件。
 */
@Composable
internal fun SortChip(label: String, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    GlassPanel(
        modifier = Modifier.height(PageChrome.ChipHeight),
        capsule = true,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortGlyph(primary, glyphSize = 13.dp)
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = primary),
            )
        }
    }
}

/** 「⋯」：菜单锚点。点击由 [GlassMenu] 的 anchor 回调注入。 */
@Composable
internal fun MoreButton(onClick: () -> Unit) {
    GlassCirclePanel(
        modifier = Modifier.size(PageChrome.CircleButtonSize),
        onClick = onClick,
    ) {
        Text(
            text = "⋯",
            style = TextStyle(fontSize = PageChrome.CircleGlyphSize, fontWeight = FontWeight.Bold, color = glassTextPrimary()),
        )
    }
}

@Composable
internal fun EventList(
    state: HomeUiState,
    listState: LazyListState,
    onToggleCompleted: () -> Unit,
    onOpenDetail: (String) -> Unit,
    searching: Boolean = false,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // 底部留白刚好让最后一条完整露出（底栏槽 70dp + 10dp 边距 = 槽顶在 80dp），
        // 又让滚动中的卡片能从底栏背后穿过去——不穿过去，玻璃就没有东西可折，
        // 看起来就是一块实色板
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 94.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 纪念日单独一段：它和待办读法不同（「已经多少天 / 还有多久到」vs「要做的事」），
        // 混排时用户得逐条读标题才知道这张卡在讲什么。为空时整段不出现，不留空标题。
        if (state.anniversaries.isNotEmpty()) {
            item(key = "anniversary-header") {
                SectionLabel("纪念日 · ${state.anniversaries.size}")
            }
            items(state.anniversaries, key = { "anni-${it.id}" }) { card ->
                EventCardView(card, onClick = { onOpenDetail(card.id) })
            }
        }
        item(key = "active-header") {
            SectionLabel("待办 · ${state.active.size}")
        }
        if (state.active.isEmpty()) {
            item(key = "active-empty") {
                SectionHint(
                    if (searching) {
                        "没有匹配的进行中事件"
                    } else if (state.anniversaries.isNotEmpty()) {
                        "没有待办，只有纪念日"
                    } else {
                        "暂无进行中的事件，点下方 ＋ 新建"
                    },
                )
            }
        }
        items(state.active, key = { it.id }) { card ->
            EventCardView(card, onClick = { onOpenDetail(card.id) })
        }
        if (state.completed.isNotEmpty()) {
            item(key = "completed-header") {
                if (searching) SectionLabel("已完成 · ${state.completed.size}")
                else CompletedHeader(state.completed.size, state.completedExpanded, onToggleCompleted)
            }
            if (state.completedExpanded) {
                items(state.completed, key = { "done-${it.id}" }) { card ->
                    EventCardView(
                        card = card,
                        // 用内容 dim 而不是 `Modifier.alpha`：alpha 会给每张卡开一块
                        // 离屏层，按压时玻璃的放大/折射一超出自身边界就被这层裁掉，
                        // 只有已完成的卡片会「一按就缺一角」。
                        dim = 0.55f,
                        onClick = { onOpenDetail(card.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = glassTextSecondary()),
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
internal fun SectionHint(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
internal fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .padding(start = 6.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已完成 · $count",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = secondary),
        )
        Spacer(Modifier.size(6.dp))
        Chevron(expanded = expanded, color = secondary)
    }
}

@Composable
internal fun Chevron(expanded: Boolean, color: Color) {
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Canvas(
        Modifier
            .size(11.dp)
            .rotate(angle),
    ) {
        val stroke = 1.6.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.35f),
            end = Offset(size.width * 0.5f, size.height * 0.68f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.68f),
            end = Offset(size.width * 0.85f, size.height * 0.35f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
internal fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassPanel(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
            ) {
                Text(
                    text = "还没有倒数日",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点下方 ＋ 记录第一个期待",
                    style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                )
            }
        }
    }
}

@Composable
internal fun CenterState(title: String, detail: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = TextStyle(fontSize = 14.sp, color = glassTextPrimary()))
        detail?.let {
            Spacer(Modifier.height(4.dp))
            Text(text = it, style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()))
        }
    }
}
