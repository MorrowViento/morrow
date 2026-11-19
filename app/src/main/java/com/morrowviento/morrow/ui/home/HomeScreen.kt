package com.morrowviento.morrow.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
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
import com.morrowviento.morrow.ui.search.CategoryFilter
import com.morrowviento.morrow.ui.search.SearchBar
import com.morrowviento.morrow.ui.search.EventSearch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.morrowviento.morrow.ui.glass.GlassBackdrop
import com.morrowviento.morrow.ui.glass.GlassBackdropHost
import com.morrowviento.morrow.ui.glass.GlassAlert
import com.morrowviento.morrow.ui.glass.GlassAlertButton
import com.morrowviento.morrow.ui.glass.GlassAlertRole
import com.morrowviento.morrow.ui.glass.GlassMenu
import com.morrowviento.morrow.ui.glass.GlassMenuItem
import com.morrowviento.morrow.ui.glass.GlassMotion
import com.morrowviento.morrow.ui.glass.GlassOverscrollHost
import com.morrowviento.morrow.ui.glass.GlassPage
import com.morrowviento.morrow.ui.glass.GlassTheme
import com.morrowviento.morrow.ui.glass.LocalBackdropSettings
import com.morrowviento.morrow.ui.glass.LocalFestivalSkin
import com.morrowviento.morrow.ui.glass.festivalSkinOf
import com.morrowviento.morrow.ui.glass.glassContentSource
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
    /** 结束设置页里的节日预览（首页标题下那行「预览 · 祝语」点了会调它）。 */
    onEndFestivalPreview: () -> Unit = {},
    /** 长按图标的「新建」请求；null 表示没有待处理的请求。 */
    newEventRequest: NewEventRequest? = null,
) {
    var backdropSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
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
    var showArchive by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }

    // 标签建议：全库已用过的标签，去重后给编辑面板「点一下就有」。
    // 包含归档段——归档事件上的标签同样是可复用的词汇。
    val tagSuggestions = remember(state.active, state.completed, state.archived) {
        buildList {
            (state.active + state.completed + state.archived).forEach { addAll(it.tags) }
        }.distinctBy { it.lowercase() }.take(24)
    }

    // 搜索复用首页卡片；分别保存两种列表的滚动位置，过滤不改动普通首页位置。
    val searchListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable(stateSaver = CategoryFilterSaver) {
        mutableStateOf<CategoryFilter>(CategoryFilter.All)
    }
    val categoryNames = remember(categories) { categories.map { it.name } }
    val searchResult = remember(state.active, state.completed, searchQuery, searchFilter, showSearch) {
        if (showSearch) EventSearch.apply(state.active, state.completed, searchQuery, searchFilter) else null
    }
    val shownState = searchResult?.let {
        state.copy(active = it.active, completed = it.completed, completedExpanded = true)
    } ?: state
    val shownListState = if (showSearch) searchListState else listState
    LaunchedEffect(showSearch) {
        if (!showSearch) { keyboard?.hide(); focusManager.clearFocus() }
    }
    LaunchedEffect(searchQuery, searchFilter) {
        if (showSearch) searchListState.scrollToItem(0)
    }
    // 切换排序时把列表带回顶部。
    //
    // LazyColumn 只记住「第几个 item + 滚过多少像素」，不记「哪一条事件」。排序一换，
    // 同一个索引底下换成了另一条事件——用户明明没滚动，画面却整块换了内容，
    // 看起来就是「点一下排序，页面自己跳了、条目上下乱窜」。回到顶部后，
    // 读到的一定是新排序下的第一条，位置是可预期的。
    // 首次组合不触发：只在排序**真的变了**之后滚。
    var lastSortMode by remember { mutableStateOf(state.sortMode) }
    LaunchedEffect(state.sortMode) {
        if (lastSortMode == state.sortMode) return@LaunchedEffect
        lastSortMode = state.sortMode
        for (target in listOf(listState, searchListState)) {
            if (target.firstVisibleItemIndex > 0 || target.firstVisibleItemScrollOffset > 0) {
                target.animateScrollToItem(0)
            }
        }
    }
    // 退场动画期间 state.undo 已经清空，靠这份快照把撤销条画完。
    // 不能在组合里直接赋值（`if (state.undo != null) lastUndo = ...`）：
    // 组合期写状态会让 Compose 再跑一轮组合，撤销条每出现一次就多重组一帧。
    var lastUndo by remember { mutableStateOf(state.undo) }
    LaunchedEffect(state.undo) {
        if (state.undo != null) lastUndo = state.undo
    }

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

    // 长按图标选「新建」：直接开表单。带序号，所以连按两次同一个快捷方式也会各开一次。
    LaunchedEffect(newEventRequest) {
        val request = newEventRequest ?: return@LaunchedEffect
        editingForm = EventFormState.newEvent().let { base ->
            if (request.allDay) base.withAllDay(true) else base
        }
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
        showSearch = false
        showArchive = false
        showCalculator = false
    }

    // 日历盖在首页之上，返回键应先关日历而不是退出应用
    BackHandler(enabled = showCalendar) { showCalendar = false }

    // 搜索是首页的一种内容形态，返回键先退出搜索、回到列表
    BackHandler(enabled = showSearch) { showSearch = false }

    // 详情页依赖最新卡片（每秒刷新会换新对象）；事件被删后自动失配 → 浮层自行关闭。
    // 查找必须覆盖纪念日段：4.6.0 起纪念日不在 active 里，只查 active + completed
    // 会让「点纪念日卡片」什么都不发生（detailCard 为 null，浮层不出现）。
    val detailCard = remember(detailId, state.anniversaries, state.active, state.completed) {
        detailId?.let(state::cardById)
    }

    GlassTheme(
        darkTheme = appSettings.isDark(isSystemInDarkTheme()),
        reduceTransparency = appSettings.usesOpaqueGlass,
        reduceMotion = appSettings.reduceMotion,
        frost = appSettings.glassFrost,
        // 实际生效的配色：手选的七套之一，或由背景图主色推导出来的那套
        accent = appSettings.resolvedAccent,
        font = appSettings.font,
    ) {
        // 节日皮肤：按今天算一次，跨零点由 calendar 的日切换带上新值。
        // 关掉开关就等于没有节日，背景与标题都不动（见 `LocalFestivalSkin`）。
        // 设置页的「预览」优先：它是显式点出来的，即使开关关着也应当能看见效果。
        val today = calendar.current.today
        val festivalPreview = settings.festivalPreview
        val festivalSkin = remember(today, appSettings.festivalSkin, festivalPreview) {
            festivalPreview?.skin ?: if (appSettings.festivalSkin) festivalSkinOf(today) else null
        }
        CompositionLocalProvider(
            LocalBackdropSettings provides appSettings.backdrop,
            LocalFestivalSkin provides festivalSkin,
        ) {
            GlassBackdropHost(
                modifier = Modifier.fillMaxSize().onSizeChanged { backdropSize = it },
                sampledContent = { GlassBackdrop() },
                overlayContent = { contentBackdrop ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                    // 唯一活动页登记为底栏采样源；浮层和底栏留在槽外，避免自我采样。
                    HomeCalendarContent(
                        calendarActive = showCalendar,
                        reduceMotion = appSettings.reduceMotion,
                        modifier = Modifier.fillMaxSize().imePadding().glassContentSource(contentBackdrop),
                    ) { calendarActive ->
                    // 设置页打开时首页整块让位：设置页不再自绘整页底色（见 SettingsScreen），
                    // 若首页仍在下面组合，它的标题与卡片就会从壁纸上透出来变成「两页重叠」。
                    // 注意这里必须是「首页」和「日历」各自判断：早先写成
                    // `if (!calendarActive && !showSettings) 首页 else 日历`，
                    // 设置页打开时条件为假会掉进 else，把日历画在设置页底下透出来。
                    when {
                        showSettings -> Unit
                        calendarActive -> CalendarScreen(
                            state = calendar.current,
                            // 不读惰性邻页，避免组合期同步构建日历。
                            canPrev = calendar.canPrev,
                            canNext = calendar.canNext,
                            actions = calendarActions,
                            onOpenEvent = { detailId = it },
                            onDismiss = { showCalendar = false },
                            // 时间块里拖出来的区间直接进新建表单：日期、开始时刻、时长都带好，
                            // 用户只需要补标题——「先划时间再想内容」比「先开表单再滚时间」顺
                            onCreateRange = { date, startMinute, endMinute ->
                                editingForm = EventFormState.newEvent(date).copy(
                                    time = java.time.LocalTime.of(
                                        (startMinute / 60).coerceAtMost(23),
                                        startMinute % 60,
                                    ),
                                    durationMinutes = (endMinute - startMinute)
                                        .coerceIn(15, 24 * 60),
                                )
                            },
                            onResizeEvent = { id, startMinute, endMinute ->
                                eventActions.resizeDuration(id, startMinute, endMinute)
                            },
                        )

                        else -> Column(Modifier.fillMaxSize()) {
                            HomeHeader(
                                state = state,
                                listState = shownListState,
                                sortLabel = state.sortMode.label(),
                                onCycleSort = { onSortChange(nextSort(state.sortMode)) },
                                festivalPreviewing = festivalPreview != null,
                                onEndFestivalPreview = onEndFestivalPreview,
                                moreAction = {
                                    // 「⋯」既是按钮也是菜单的锚点：GlassMenu 从它身上液态展开，
                                    // 关闭时缩回按钮，不再是「遮罩 + 底部面板」那套动作表。
                                    GlassMenu(
                                        expanded = showMoreMenu,
                                        onExpandedChange = { showMoreMenu = it },
                                        itemCount = 7,
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
                                            title = "日期计算器",
                                            onClick = { close(); showCalculator = true },
                                        )
                                        GlassMenuItem(
                                            backdrop = backdrop,
                                            title = "归档箱（${state.archivedCount}）",
                                            onClick = { close(); showArchive = true },
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
                            if (showSearch) {
                                SearchBar(
                                    active = state.active, completed = state.completed, categories = categories,
                                    query = searchQuery, onQueryChange = { searchQuery = it },
                                    filter = searchFilter, onFilterChange = { searchFilter = it },
                                    onDismiss = { showSearch = false },
                                )
                                SectionLabel("找到 ${shownState.totalCount} 个事件")
                            }
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

                                    showSearch && shownState.isEmpty -> CenterState(
                                        if (state.isEmpty) "还没有事件可以搜索" else "没有匹配的事件", null,
                                    )

                                    state.isEmpty -> EmptyState()

                                    else -> EventList(
                                        state = shownState,
                                        listState = shownListState,
                                        onToggleCompleted = if (showSearch) ({}) else onToggleCompleted,
                                        onOpenDetail = {
                                            keyboard?.hide(); focusManager.clearFocus(); detailId = it
                                        },
                                        searching = showSearch,
                                    )
                                }
                            }
                        }
                    }
                    }

                    // 设置中心同样是整页覆盖，但底色来自全窗口宿主而不是自己再画一遍：
                    // 在 `safeDrawingPadding()` 里重绘壁纸会让渐变原点/照片裁剪与状态栏
                    // 那一截对不上，顶上出现一道裂缝（日历页已按同一办法修过）。
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
                            onDismiss = { settingsActions.cancelBackdrop(); showSettings = false },
                            backgroundAspectRatio = if (backdropSize.height > 0) {
                                backdropSize.width.toFloat() / backdropSize.height
                            } else 1f,
                        )
                    }

                    // 常驻底栏：唯一的一条，画在首页 / 日历 / 设置之上。
                    // 切页面时它不重建也不换内容，只是选中格会滑动过去——
                    // 之前每个页面各放一条，进日历底栏就变成另一套按钮，看起来像换了个应用。
                    if (!showSettings) {
                        HomeBottomBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp)
                                // 底栏槽 60dp（胶囊 52dp 居中，点击珠子 60dp 上下各凸 4dp），
                                // 底下再留 10dp，槽顶距屏幕底 70dp
                                .padding(bottom = 10.dp),
                            backdrop = contentBackdrop,
                            calendarActive = showCalendar,
                            searchActive = showSearch,
                            // 再点一次「搜索」回到列表：底栏那格是开关，不是只能进不能出。
                            onSearch = {
                                showCalendar = false
                                showSearch = !showSearch
                            },
                            onHome = {
                                showCalendar = false
                                showSearch = false
                            },
                            onCalendar = {
                                showSearch = false
                                showCalendar = true
                            },
                            onCreate = {
                                categoryAliases = emptyMap()
                                // 日历里按「＋」按选中日新建；搜索里新建用默认日期，
                                // 否则会把用户上一次在日历里选的那天偷偷带进来
                                editingForm = if (showCalendar) {
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
                            onToggleChecklist = { itemId -> eventActions.toggleChecklist(card.id, itemId) },
                            onArchive = {
                                detailId = null
                                if (card.isArchived) eventActions.unarchive(card.id) else eventActions.archive(card.id)
                            },
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
                            tagSuggestions = tagSuggestions,
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

                    if (showArchive) {
                        com.morrowviento.morrow.ui.archive.ArchiveSheet(
                            cards = state.archived,
                            actions = eventActions,
                            onDismiss = { showArchive = false },
                        )
                    }

                    if (showCalculator) {
                        com.morrowviento.morrow.ui.tools.DateCalculatorSheet(
                            onDismiss = { showCalculator = false },
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
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 106.dp),
                            )
                        }
                    }
                    }
                },
            )
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

/**
 * 分类筛选的持久化：编码成一个短字符串，避免给密封接口加 `Parcelable`。
 *
 * `""` = 全部，`"\u0000"` = 未分类，其余按分类名还原。用 NUL 当哨兵是因为它
 * 不可能出现在分类名里（分类名会被 `CategoryRules` 规范化掉控制字符）。
 */
private val CategoryFilterSaver: Saver<CategoryFilter, String> = Saver(
    save = { filter ->
        when (filter) {
            CategoryFilter.All -> ""
            CategoryFilter.Uncategorized -> "\u0000"
            is CategoryFilter.Named -> filter.name
        }
    },
    restore = { raw ->
        when (raw) {
            "" -> CategoryFilter.All
            "\u0000" -> CategoryFilter.Uncategorized
            else -> CategoryFilter.Named(raw)
        }
    },
)

/**
 * 外部（图标快捷方式）发起的「新建」请求。
 *
 * [serial] 每次请求自增：用 Boolean 的话，连续两次选同一个快捷方式时值没变，
 * `LaunchedEffect` 不会重启，第二次就像没点到。
 */
@androidx.compose.runtime.Immutable
data class NewEventRequest(
    val serial: Long,
    /** true = 待办（全天）；false = 事件（沿用当前时刻）。 */
    val allDay: Boolean,
)
