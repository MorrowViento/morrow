package com.morrowviento.morrow.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.data.category.CategoryItem
import com.morrowviento.morrow.data.settings.AppSettings
import com.morrowviento.morrow.domain.model.SortMode
import com.morrowviento.morrow.ui.calendar.CalendarActions
import com.morrowviento.morrow.ui.calendar.CalendarScreen
import com.morrowviento.morrow.ui.calendar.CalendarStrip
import com.morrowviento.morrow.ui.category.CategoryActions
import com.morrowviento.morrow.ui.category.CategorySheet
import com.morrowviento.morrow.ui.component.HomeBottomBar
import com.morrowviento.morrow.ui.event.EventActions
import com.morrowviento.morrow.ui.event.EventDetailScreen
import com.morrowviento.morrow.ui.event.EventEditSheet
import com.morrowviento.morrow.ui.event.EventFormState
import com.morrowviento.morrow.ui.event.HomeUiState
import com.morrowviento.morrow.ui.event.UndoBar
import com.morrowviento.morrow.ui.glass.GlassBackdrop
import com.morrowviento.morrow.ui.glass.GlassBackdropHost
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.GlassAlert
import com.morrowviento.morrow.ui.glass.GlassAlertButton
import com.morrowviento.morrow.ui.glass.GlassAlertRole
import com.morrowviento.morrow.ui.glass.GlassMenu
import com.morrowviento.morrow.ui.glass.GlassMenuItem
import com.morrowviento.morrow.ui.glass.GlassMotion
import com.morrowviento.morrow.ui.glass.GlassOverscrollHost
import com.morrowviento.morrow.ui.glass.GlassPage
import com.morrowviento.morrow.ui.glass.GlassTheme
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.reminder.ReminderActions
import com.morrowviento.morrow.ui.reminder.ReminderSheet
import com.morrowviento.morrow.ui.reminder.ReminderUiState
import com.morrowviento.morrow.ui.settings.SettingsActions
import com.morrowviento.morrow.ui.settings.SettingsScreen
import com.morrowviento.morrow.ui.settings.SettingsUiState
import kotlinx.coroutines.launch

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
    calendar: CalendarStrip,
    calendarActions: CalendarActions,
    categories: List<CategoryItem>,
    categoryActions: CategoryActions,
    eventActions: EventActions,
    reminders: ReminderUiState,
    reminderActions: ReminderActions,
    /** 主题 / 减透明 / 减动效：决定 GlassTheme 的实际外观，所以必须在最外层参数里。 */
    appSettings: AppSettings,
    settings: SettingsUiState,
    settingsActions: SettingsActions,
    onToggleCompleted: () -> Unit,
    onSortChange: (SortMode) -> Unit,
    onSaveEvent: (EventFormState) -> Unit,
    /** 从提醒通知点进来时要直接打开的事件；消费后由调用方清零。 */
    openDetailRequest: String? = null,
    onDetailRequestConsumed: () -> Unit = {},
    /**
     * 每次从通知进入应用都自增。
     *
     * 通知被点时应用往往还活着（例如刚在提醒页发完测试通知），Activity 只是被带回前台，
     * 于是屏幕上仍留着刚才那个诊断面板——用户以为「点了通知却没进应用」。
     * 收到新值时把所有浮层收掉，让人落回首页（要看的详情由 [openDetailRequest] 另开）。
     */
    closeOverlaysRequest: Long = 0L,
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
    var showSettings by remember { mutableStateOf(false) }
    val categoryNames = remember(categories) { categories.map { it.name } }
    // 退场动画期间 state.undo 已经清空，靠这份快照把撤销条画完
    var lastUndo by remember { mutableStateOf(state.undo) }
    if (state.undo != null) lastUndo = state.undo

    // 分类改名 / 删除迁移的映射（旧名 → 新名，可链式）。表单状态在面板内部，
    // 只能把「这个名字已经变了」传进去，否则表单会拿着旧名保存，让旧分类以孤儿身份长期存在。
    // 每次打开新表单都清空，避免上一个表单里的改名影响新表单。
    var categoryAliases by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // 通知点进来时数据可能还没加载完：先记下 id，等列表到位后 detailCard 自然会匹配上
    LaunchedEffect(openDetailRequest) {
        val id = openDetailRequest ?: return@LaunchedEffect
        detailId = id
        onDetailRequestConsumed()
    }

    // 从通知进入时收起上一次留下的浮层。不清 detailId：真实提醒要靠 openDetailRequest 打开详情
    LaunchedEffect(closeOverlaysRequest) {
        if (closeOverlaysRequest <= 0L) return@LaunchedEffect
        editingForm = null
        showCategories = false
        showMoreMenu = false
        confirmClearAll = false
        showCalendar = false
        showReminders = false
        showSettings = false
    }

    // 日历盖在首页之上，返回键应先关日历而不是退出应用
    BackHandler(enabled = showCalendar) { showCalendar = false }

    // 详情页依赖最新卡片（每秒刷新会换新对象）；事件被删后自动失配 → 浮层自行关闭
    val detailCard = detailId?.let { id ->
        (state.active + state.completed).firstOrNull { it.id == id }
    }

    GlassTheme(
        darkTheme = appSettings.isDark(isSystemInDarkTheme()),
        reduceTransparency = appSettings.reduceTransparency,
        reduceMotion = appSettings.reduceMotion,
    ) {
        GlassBackdropHost(
            modifier = Modifier.fillMaxSize(),
            sampledContent = { GlassBackdrop() },
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
                            moreAction = {
                                // 「⋯」既是按钮也是菜单的锚点：GlassMenu 从它身上液态展开，
                                // 关闭时缩回按钮，不再是「遮罩 + 底部面板」那套动作表。
                                GlassMenu(
                                    expanded = showMoreMenu,
                                    onExpandedChange = { showMoreMenu = it },
                                    itemCount = 5,
                                    anchor = { onClick -> MoreButton(onClick) },
                                ) { backdrop, close ->
                                    GlassMenuItem(
                                        backdrop = backdrop,
                                        title = "设置",
                                        onClick = { close(); showSettings = true },
                                    )
                                    GlassMenuItem(
                                        backdrop = backdrop,
                                        title = "提醒",
                                        onClick = { close(); showReminders = true },
                                    )
                                    GlassMenuItem(
                                        backdrop = backdrop,
                                        title = "清空已完成（${state.completed.size}）",
                                        enabled = state.completed.isNotEmpty(),
                                        onClick = {
                                            close()
                                            eventActions.clearCompleted()
                                        },
                                    )
                                    GlassMenuItem(
                                        backdrop = backdrop,
                                        title = "清空全部（${state.totalCount}）",
                                        enabled = state.totalCount > 0,
                                        destructive = true,
                                        onClick = {
                                            close()
                                            confirmClearAll = true
                                        },
                                    )
                                    GlassMenuItem(
                                        backdrop = backdrop,
                                        title = "取消",
                                        onClick = close,
                                    )
                                }
                            },
                        )
                        // 页面级回弹：内容被拉出一段再弹回（不是 Android 默认的整块拉伸）
                        GlassOverscrollHost(
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

                    // 日历是整页替换（不是浮层）：盖住列表与 FAB，但与列表共用同一个 backdrop，
                    // 换成独立窗口会切断采样源让玻璃材质失效。它自己会重铺一层不透明底色，
                    // 不能依赖下层首页透出来。
                    GlassPage(
                        visible = showCalendar,
                        modifier = Modifier.fillMaxSize(),
                        reduceMotion = appSettings.reduceMotion,
                    ) {
                        CalendarScreen(
                            state = calendar.current,
                            canPrev = calendar.prev != null,
                            canNext = calendar.next != null,
                            actions = calendarActions,
                            onOpenEvent = { detailId = it },
                            onDismiss = { showCalendar = false },
                        )
                    }

                    // 设置中心同样是整页覆盖：自带不透明底色与返回处理（见 SettingsScreen）。
                    // 它排在日历之后绘制，因此两者同时为真时设置页在上层——实际上互斥，
                    // 日历里没有设置入口。
                    GlassPage(
                        visible = showSettings,
                        modifier = Modifier.fillMaxSize(),
                        reduceMotion = appSettings.reduceMotion,
                    ) {
                        SettingsScreen(
                            state = settings,
                            actions = settingsActions,
                            onDismiss = { showSettings = false },
                        )
                    }

                    // 常驻底栏：唯一的一条，画在首页 / 日历 / 设置之上。
                    // 切页面时它不重建也不换内容，只是「日历」格子会亮一下表示选中——
                    // 之前每个页面各放一条，进日历底栏就变成另一套按钮，看起来像换了个应用。
                    if (!showSettings) {
                        HomeBottomBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 14.dp),
                            sortLabel = state.sortMode.label(),
                            calendarActive = showCalendar,
                            onCycleSort = { onSortChange(nextSort(state.sortMode)) },
                            onCalendar = { showCalendar = !showCalendar },
                            onCreate = {
                                categoryAliases = emptyMap()
                                editingForm = if (showCalendar) {
                                    // 在日历里按「＋」按选中日新建，与日历页原有行为一致
                                    EventFormState.newEvent(
                                        date = calendar.current.selected ?: calendar.current.today,
                                    )
                                } else {
                                    EventFormState.newEvent()
                                }
                            },
                        )
                    }

                    detailCard?.let { card ->
                        EventDetailScreen(
                            card = card,
                            onDismiss = { detailId = null },
                            onEdit = {
                                val id = card.id
                                detailId = null
                                scope.launch {
                                    eventActions.loadForm(id)?.let {
                                        categoryAliases = emptyMap()
                                        editingForm = it
                                    }
                                }
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
                            categoryAliases = categoryAliases,
                            onManageCategories = { showCategories = true },
                            onDismiss = { editingForm = null },
                            onSave = { saved ->
                                onSaveEvent(saved)
                                editingForm = null
                            },
                        )
                    }

                    // 分类管理叠在表单之上：用户从表单的「管理分类…」进入，关闭后回到表单继续填写。
                    // 表单只记分类名，所以改名/删除要同步回填选中项，否则表单会拿旧名保存，
                    // 旧名又以「孤儿分类」身份回到列表里，新旧两个分类长期并存。
                    if (showCategories) {
                        CategorySheet(
                            items = categories,
                            actions = categoryActions,
                            onRenamed = { from, to ->
                                categoryAliases = categoryAliases + (from to to)
                            },
                            onRemoved = { name, target ->
                                // 删除后事件已迁到 target；没有 target 说明该分类没人用，
                                // 表单退回第一个仍存在的分类，别把已删的名字原样保存回去。
                                val next = target ?: categoryNames.firstOrNull { it != name }
                                if (next != null) categoryAliases = categoryAliases + (name to next)
                            },
                            onDismiss = { showCategories = false },
                        )
                    }

                    // 保持挂载、只翻转 visible：弹窗的缩放/淡出动画才播得完
                    GlassAlert(
                        visible = confirmClearAll,
                        title = "清空全部事件",
                        message = "将删除全部 ${state.totalCount} 个事件，6 秒内可撤销。",
                        onDismiss = { confirmClearAll = false },
                        buttons = listOf(
                            GlassAlertButton(
                                label = "取消",
                                role = GlassAlertRole.Cancel,
                                onClick = { confirmClearAll = false },
                            ),
                            GlassAlertButton(
                                label = "清空",
                                role = GlassAlertRole.Destructive,
                                onClick = {
                                    confirmClearAll = false
                                    eventActions.clearAll()
                                },
                            ),
                        ),
                    )

                    if (showReminders) {
                        ReminderSheet(
                            state = reminders,
                            actions = reminderActions,
                            onDismiss = { showReminders = false },
                        )
                    }

                    // 撤销条最后绘制：详情/表单浮层打开时它仍需可见，否则 6 秒窗口会被遮住白白错过
                    // 撤销条：出现 / 消失都从底栏上方滑入滑出（此前是硬切，眼睛追不上）
                    val undo = state.undo
                    AnimatedVisibility(
                        visible = undo != null,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(GlassMotion.navigation()) { it } + fadeIn(GlassMotion.enterFade),
                        exit = slideOutVertically(GlassMotion.navigation()) { it } + fadeOut(GlassMotion.enterFade),
                    ) {
                        // 退场那一帧 undo 已经是 null，用最后一份非空值把内容画完
                        val shown = undo ?: lastUndo
                        if (shown != null) {
                            UndoBar(
                                undo = shown,
                                onUndo = eventActions.undo,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

/**
 * 大标题随滚动收缩（iOS 式），副标题在收缩后隐藏。
 *
 * 排序、日历已经搬到 [HomeBottomBar]；标题区只留「更多」一颗，大标题右边不再挤三颗按钮。
 */
@Composable
private fun HomeHeader(
    state: HomeUiState,
    listState: LazyListState,
    moreAction: @Composable () -> Unit,
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
                text = "朝夕",
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
        moreAction()
    }
}

/** 「⋯」：菜单锚点。点击由 [GlassMenu] 的 anchor 回调注入。 */
@Composable
private fun MoreButton(onClick: () -> Unit) {
    GlassCirclePanel(
        modifier = Modifier.size(32.dp),
        onClick = onClick,
    ) {
        Text(
            text = "⋯",
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary()),
        )
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
                SectionHint("暂无进行中的事件，点下方 ＋ 新建")
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
                    text = "点下方 ＋ 记录第一个期待",
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

/** 被采样的背景由 [GlassBackdrop] 统一提供：首页与整页覆盖的日历页必须用同一份，
 * 否则两者交叠处会出现色差（玻璃采样到的颜色与页面底色不一致）。 */

private fun nextSort(mode: SortMode): SortMode =
    SortMode.entries[(mode.ordinal + 1) % SortMode.entries.size]

private fun SortMode.label(): String = when (this) {
    SortMode.DISTANCE -> "距离"
    SortMode.TARGET_DATE -> "日期"
    SortMode.CREATED_AT -> "创建"
}
