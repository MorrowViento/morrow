package com.jiuxina.ying.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.data.category.CategoryItem
import com.jiuxina.ying.domain.model.SortMode
import com.jiuxina.ying.ui.category.CategoryActions
import com.jiuxina.ying.ui.category.CategorySheet
import com.jiuxina.ying.ui.event.EventEditSheet
import com.jiuxina.ying.ui.event.EventFormState
import com.jiuxina.ying.ui.event.HomeUiState
import com.jiuxina.ying.ui.glass.GlassBackdropHost
import com.jiuxina.ying.ui.glass.GlassCirclePanel
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.GlassTheme
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary
import java.time.LocalDate

/**
 * 首页（M2a）：事件列表 + 折叠标题 + 排序切换 + 空态。
 *
 * 玻璃控件必须放在 [GlassBackdropHost] 的 overlayContent 内，
 * 背景渐变放在 sampledContent 内作为采样源。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    categories: List<CategoryItem>,
    categoryActions: CategoryActions,
    onToggleCompleted: () -> Unit,
    onSortChange: (SortMode) -> Unit,
    onSaveEvent: (EventFormState) -> Unit,
) {
    val listState = rememberLazyListState()
    var editingForm by remember { mutableStateOf<EventFormState?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    val categoryNames = remember(categories) { categories.map { it.name } }

    GlassTheme {
        GlassBackdropHost(
            modifier = Modifier.fillMaxSize(),
            sampledContent = { BackdropGradient() },
            overlayContent = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        HomeHeader(state, listState, onSortChange)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            when {
                                state.error != null -> CenterState(
                                    title = "读取事件失败",
                                    detail = state.error,
                                )

                                state.isLoading -> CenterState("载入中…", null)

                                state.isEmpty -> EmptyState()

                                else -> EventList(state, listState, onToggleCompleted)
                            }
                        }
                    }
                    CreateButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 26.dp),
                        onClick = { editingForm = EventFormState(date = LocalDate.now()) },
                    )

                    editingForm?.let { form ->
                        EventEditSheet(
                            initial = form,
                            categories = categoryNames,
                            onManageCategories = { showCategories = true },
                            onDismiss = { editingForm = null },
                            onSave = { saved ->
                                onSaveEvent(saved)
                                editingForm = null
                            },
                        )
                    }

                    // 分类管理叠在表单之上：用户从表单的「管理分类…」进入，关闭后回到表单继续填写
                    if (showCategories) {
                        CategorySheet(
                            items = categories,
                            actions = categoryActions,
                            onDismiss = { showCategories = false },
                        )
                    }
                }
            },
        )
    }
}

/** 大标题随滚动收缩（iOS 式），副标题在收缩后隐藏。 */
@Composable
private fun HomeHeader(
    state: HomeUiState,
    listState: LazyListState,
    onSortChange: (SortMode) -> Unit,
) {
    val collapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40 }
    }
    val titleSize by animateFloatAsState(if (collapsed) 21f else 34f, label = "homeTitleSize")
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "萤",
                maxLines = 1,
                style = TextStyle(fontSize = titleSize.sp, fontWeight = FontWeight.Bold, color = primary),
            )
            AnimatedVisibility(visible = !collapsed) {
                Text(
                    text = "${state.totalCount} 个事件 · ${state.active.size} 进行中",
                    style = TextStyle(fontSize = 12.sp, color = secondary),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        SortChip(state.sortMode, onSortChange)
    }
}

/** 排序切换：点一次切下一个（搜索/筛选栏到 M2c 再补）。 */
@Composable
private fun SortChip(mode: SortMode, onSortChange: (SortMode) -> Unit) {
    val next = SortMode.entries[(mode.ordinal + 1) % SortMode.entries.size]
    GlassPanel(modifier = Modifier.clickable { onSortChange(next) }) {
        Text(
            text = "排序 · ${mode.label()}",
            style = TextStyle(fontSize = 12.sp, color = glassTextPrimary()),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun EventList(
    state: HomeUiState,
    listState: LazyListState,
    onToggleCompleted: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "active-header") {
            SectionLabel("进行中 · ${state.active.size}")
        }
        if (state.active.isEmpty()) {
            item(key = "active-empty") {
                SectionHint("暂无进行中的事件，点右下角 + 新建")
            }
        }
        items(state.active, key = { it.id }) { card ->
            EventCardView(card)
        }
        if (state.completed.isNotEmpty()) {
            item(key = "completed-header") {
                CompletedHeader(state.completed.size, state.completedExpanded, onToggleCompleted)
            }
            if (state.completedExpanded) {
                items(state.completed, key = { "done-${it.id}" }) { card ->
                    EventCardView(card, modifier = Modifier.alpha(0.55f))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = glassTextSecondary()),
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
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
private fun Chevron(expanded: Boolean, color: Color) {
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
private fun EmptyState() {
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
                    text = "点右下角 + 记录第一个期待",
                    style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                )
            }
        }
    }
}

@Composable
private fun CenterState(title: String, detail: String?) {
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

@Composable
private fun CreateButton(modifier: Modifier, onClick: () -> Unit) {
    GlassCirclePanel(modifier = modifier.size(60.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Light, color = glassTextPrimary()),
            )
        }
    }
}

/** 被采样的背景：浅色用品牌渐变，深色压暗以免玻璃高光过曝。 */
@Composable
private fun BackdropGradient() {
    val colors = if (isSystemInDarkTheme()) {
        listOf(Color(0xFF151A35), Color(0xFF2A1436), Color(0xFF3F1630))
    } else {
        listOf(Color(0xFF3B6FE0), Color(0xFF9B4BE8), Color(0xFFE84B8A))
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)))
}

private fun SortMode.label(): String = when (this) {
    SortMode.DISTANCE -> "距离"
    SortMode.TARGET_DATE -> "日期"
    SortMode.CREATED_AT -> "创建"
}
