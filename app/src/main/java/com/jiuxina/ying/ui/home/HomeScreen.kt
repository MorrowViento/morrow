package com.jiuxina.ying.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.data.category.CategoryItem
import com.jiuxina.ying.domain.model.SortMode
import com.jiuxina.ying.ui.calendar.CalendarActions
import com.jiuxina.ying.ui.calendar.CalendarScreen
import com.jiuxina.ying.ui.calendar.CalendarUiState
import com.jiuxina.ying.ui.category.CategoryActions
import com.jiuxina.ying.ui.category.CategorySheet
import com.jiuxina.ying.ui.event.EventActions
import com.jiuxina.ying.ui.event.EventDetailScreen
import com.jiuxina.ying.ui.event.EventEditSheet
import com.jiuxina.ying.ui.event.EventFormState
import com.jiuxina.ying.ui.event.HomeUiState
import com.jiuxina.ying.ui.event.UndoBar
import com.jiuxina.ying.ui.glass.GlassBackdropHost
import com.jiuxina.ying.ui.glass.GlassCirclePanel
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.GlassTheme
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary
import com.jiuxina.ying.ui.reminder.ReminderActions
import com.jiuxina.ying.ui.reminder.ReminderSheet
import com.jiuxina.ying.ui.reminder.ReminderUiState
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 首页（M2a/M2c）：事件列表 + 折叠标题 + 排序切换 + 空态 + 详情浮层 + 撤销条。
 *
 * 玻璃控件必须放在 [GlassBackdropHost] 的 overlayContent 内，
 * 背景渐变放在 sampledContent 内作为采样源。
 * 所有浮层（表单 / 详情 / 分类 / 菜单）都叠在同一个 Box 内，保证 backdrop 不被切断。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    calendar: CalendarUiState,
    calendarActions: CalendarActions,
    categories: List<CategoryItem>,
    categoryActions: CategoryActions,
    eventActions: EventActions,
    reminders: ReminderUiState,
    reminderActions: ReminderActions,
    onToggleCompleted: () -> Unit,
    onSortChange: (SortMode) -> Unit,
    onSaveEvent: (EventFormState) -> Unit,
    /** 从提醒通知点进来时要直接打开的事件；消费后由调用方清零。 */
    openDetailRequest: String? = null,
    onDetailRequestConsumed: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var editingForm by remember { mutableStateOf<EventFormState?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    val categoryNames = remember(categories) { categories.map { it.name } }

    // 通知点进来时数据可能还没加载完：先记下 id，等列表到位后 detailCard 自然会匹配上
    LaunchedEffect(openDetailRequest) {
        val id = openDetailRequest ?: return@LaunchedEffect
        detailId = id
        onDetailRequestConsumed()
    }

    // 日历盖在首页之上，返回键应先关日历而不是退出应用
    BackHandler(enabled = showCalendar) { showCalendar = false }

    // 详情页依赖最新卡片（每秒刷新会换新对象）；事件被删后自动失配 → 浮层自行关闭
    val detailCard = detailId?.let { id ->
        (state.active + state.completed).firstOrNull { it.id == id }
    }

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
                        HomeHeader(
                            state = state,
                            listState = listState,
                            onSortChange = onSortChange,
                            onCalendar = { showCalendar = true },
                            onMore = { showMoreMenu = true },
                        )
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

                                else -> EventList(
                                    state = state,
                                    listState = listState,
                                    onToggleCompleted = onToggleCompleted,
                                    onOpenDetail = { detailId = it },
                                )
                            }
                        }
                    }
                    CreateButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 26.dp),
                        onClick = { editingForm = EventFormState(date = LocalDate.now()) },
                    )

                    // 日历是整页替换（不是浮层）：盖住列表与 FAB，但与列表共用同一个 backdrop，
                    // 换成独立窗口会切断采样源让玻璃材质失效。
                    AnimatedVisibility(
                        visible = showCalendar,
                        modifier = Modifier.fillMaxSize(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        CalendarScreen(
                            state = calendar,
                            actions = calendarActions,
                            onOpenEvent = { detailId = it },
                            onDismiss = { showCalendar = false },
                        )
                    }

                    detailCard?.let { card ->
                        EventDetailScreen(
                            card = card,
                            onDismiss = { detailId = null },
                            onEdit = {
                                val id = card.id
                                detailId = null
                                scope.launch { eventActions.loadForm(id)?.let { editingForm = it } }
                            },
                            onToggleCompleted = { eventActions.toggleCompleted(card.id) },
                            onTogglePinned = { eventActions.togglePinned(card.id) },
                            onDelete = {
                                detailId = null
                                eventActions.delete(card.id)
                            },
                        )
                    }

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

                    if (showMoreMenu) {
                        MoreMenu(
                            completedCount = state.completed.size,
                            totalCount = state.totalCount,
                            onOpenReminders = {
                                showMoreMenu = false
                                showReminders = true
                            },
                            onClearCompleted = {
                                showMoreMenu = false
                                eventActions.clearCompleted()
                            },
                            onClearAll = {
                                showMoreMenu = false
                                confirmClearAll = true
                            },
                            onDismiss = { showMoreMenu = false },
                        )
                    }

                    if (confirmClearAll) {
                        ConfirmClearAll(
                            totalCount = state.totalCount,
                            onConfirm = {
                                confirmClearAll = false
                                eventActions.clearAll()
                            },
                            onDismiss = { confirmClearAll = false },
                        )
                    }

                    if (showReminders) {
                        ReminderSheet(
                            state = reminders,
                            actions = reminderActions,
                            onDismiss = { showReminders = false },
                        )
                    }

                    // 撤销条最后绘制：详情/表单浮层打开时它仍需可见，否则 6 秒窗口会被遮住白白错过
                    state.undo?.let { undo ->
                        UndoBar(
                            undo = undo,
                            onUndo = eventActions.undo,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
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
    onCalendar: () -> Unit,
    onMore: () -> Unit,
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
        Spacer(Modifier.size(6.dp))
        CalendarButton(onCalendar)
        Spacer(Modifier.size(6.dp))
        MoreButton(onMore)
    }
}

/** 日历入口。图标用 Canvas 画，避免依赖字体里是否有日历字形。 */
@Composable
private fun CalendarButton(onClick: () -> Unit) {
    val primary = glassTextPrimary()
    GlassCirclePanel(modifier = Modifier.size(32.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(13.dp)) {
                val stroke = 1.2.dp.toPx()
                val corner = 2.dp.toPx()
                val top = size.height * 0.16f
                drawRoundRect(
                    color = primary,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, size.height - top),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color = primary,
                    start = Offset(0f, size.height * 0.4f),
                    end = Offset(size.width, size.height * 0.4f),
                    strokeWidth = stroke,
                )
                // 顶部两个挂环
                drawLine(
                    color = primary,
                    start = Offset(size.width * 0.3f, 0f),
                    end = Offset(size.width * 0.3f, size.height * 0.26f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = primary,
                    start = Offset(size.width * 0.7f, 0f),
                    end = Offset(size.width * 0.7f, size.height * 0.26f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** 排序切换：点一次切下一个（完整筛选/搜索栏留待 M2d 与日历页一起做）。 */
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

/** 批量清理入口（清空已完成 / 清空全部），放在排序旁边避免标题区过挤。 */
@Composable
private fun MoreButton(onClick: () -> Unit) {
    GlassCirclePanel(modifier = Modifier.size(32.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⋯",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary()),
            )
        }
    }
}

@Composable
private fun EventList(
    state: HomeUiState,
    listState: LazyListState,
    onToggleCompleted: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 150.dp),
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
            EventCardView(card, onClick = { onOpenDetail(card.id) })
        }
        if (state.completed.isNotEmpty()) {
            item(key = "completed-header") {
                CompletedHeader(state.completed.size, state.completedExpanded, onToggleCompleted)
            }
            if (state.completedExpanded) {
                items(state.completed, key = { "done-${it.id}" }) { card ->
                    EventCardView(
                        card = card,
                        modifier = Modifier.alpha(0.55f),
                        onClick = { onOpenDetail(card.id) },
                    )
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

@Composable
private fun MoreMenu(
    completedCount: Int,
    totalCount: Int,
    onOpenReminders: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onDismiss),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                MenuRow(label = "提醒", enabled = true, onClick = onOpenReminders)
                MenuRow(
                    label = "清空已完成（$completedCount）",
                    enabled = completedCount > 0,
                    onClick = onClearCompleted,
                )
                MenuRow(
                    label = "清空全部（$totalCount）",
                    enabled = totalCount > 0,
                    danger = true,
                    onClick = onClearAll,
                )
                MenuRow(label = "取消", enabled = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun MenuRow(
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> glassTextSecondary().copy(alpha = 0.4f)
        danger -> Color(0xFFFF7A7A)
        else -> glassTextPrimary()
    }
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        style = TextStyle(fontSize = 14.sp, color = color),
    )
}

/** 清空全部是破坏性最强的操作，即使有撤销也给一次确认。 */
@Composable
private fun ConfirmClearAll(totalCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = "清空全部事件",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "将删除全部 $totalCount 个事件，6 秒内可撤销。",
                    style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "取消",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        style = TextStyle(fontSize = 13.sp, color = glassTextSecondary()),
                    )
                    Text(
                        text = "清空",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF7A7A)),
                    )
                }
            }
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
