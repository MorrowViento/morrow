package com.jiuxina.ying

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiuxina.ying.ui.calendar.CalendarActions
import com.jiuxina.ying.ui.category.CategoryActions
import com.jiuxina.ying.ui.category.CategoryViewModel
import com.jiuxina.ying.ui.event.EventActions
import com.jiuxina.ying.ui.event.EventViewModel
import com.jiuxina.ying.ui.home.HomeScreen
import com.jiuxina.ying.ui.reminder.ReminderActions
import com.jiuxina.ying.ui.reminder.ReminderViewModel
import com.jiuxina.ying.ui.settings.SettingsActions
import com.jiuxina.ying.ui.settings.SettingsViewModel
import com.jiuxina.ying.util.StartupTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 应用入口。首页、日历与提醒诊断的数据都来自 [YingApp] 装配的仓库，
 * 且共用同一个 [EventViewModel]，避免多处各自订阅导致状态不同步。
 *
 * 当前进度：M2a 列表 + M2b 表单 + M2b2 分类 + M2c 详情/撤销 + M2d 日历 + M4 提醒 + M5 小部件 + M6 设置中心。
 */
class MainActivity : ComponentActivity() {

    /**
     * 通知点进来时要打开的事件 id。
     *
     * 用 StateFlow 而不是直接读 intent，是因为 Activity 可能是「复用已有实例」的路径
     * （launchMode=singleTop），此时新 intent 通过 [onNewIntent] 到达，
     * 只读 onCreate 的 intent 会拿到启动时那份旧数据。
     */
    private val pendingDetailId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("main", "onCreate enter")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as YingApp
        pendingDetailId.value = intent?.getStringExtra(EXTRA_EVENT_ID)

        setContent {
            val viewModel: EventViewModel = viewModel(
                factory = EventViewModel.factory(
                    app.eventRepository,
                    app.settingsRepository,
                    app.undoRepository,
                    app.reminderScheduler,
                    app.widgetRefresher,
                ),
            )
            val categoryViewModel: CategoryViewModel = viewModel(
                factory = CategoryViewModel.factory(app.categoryRepository),
            )
            val reminderViewModel: ReminderViewModel = viewModel(
                factory = ReminderViewModel.factory(
                    app.eventRepository,
                    app.settingsRepository,
                    app.reminderPermissions,
                    app.reminderNotifier,
                    app.reminderScheduler,
                ),
            )
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    this@MainActivity,
                    app.settingsRepository,
                    app.backupRepository,
                    app.eventRepository,
                    app.reminderScheduler,
                    app.reminderPermissions,
                    app.widgetRefresher,
                ),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val calendar by viewModel.calendarStrip.collectAsStateWithLifecycle()
            val categories by categoryViewModel.items.collectAsStateWithLifecycle()
            val reminders by reminderViewModel.state.collectAsStateWithLifecycle()
            val openDetail by pendingDetailId.collectAsStateWithLifecycle()
            val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

            // 回调集合只取决于 ViewModel 实例：避免每帧重组都新建 lambda
            val categoryActions = remember(categoryViewModel) {
                CategoryActions(
                    add = categoryViewModel::add,
                    rename = categoryViewModel::rename,
                    delete = categoryViewModel::delete,
                )
            }
            val eventActions = remember(viewModel) {
                EventActions(
                    delete = viewModel::deleteEvent,
                    toggleCompleted = viewModel::toggleEventCompleted,
                    togglePinned = viewModel::togglePinned,
                    clearCompleted = viewModel::clearCompleted,
                    clearAll = viewModel::clearAll,
                    undo = viewModel::undoLast,
                    loadForm = viewModel::formFor,
                )
            }
            val calendarActions = remember(viewModel) {
                CalendarActions(
                    setView = viewModel::setCalendarView,
                    shiftMonth = viewModel::shiftCalendarMonth,
                    shiftYear = viewModel::shiftCalendarYear,
                    selectDay = viewModel::selectCalendarDay,
                    openMonth = viewModel::openCalendarMonth,
                    goToToday = viewModel::calendarGoToToday,
                )
            }
            val reminderActions = remember(reminderViewModel) {
                ReminderActions(
                    refresh = reminderViewModel::refresh,
                    openNotificationSettings = reminderViewModel::openNotificationSettings,
                    openExactAlarmSettings = reminderViewModel::openExactAlarmSettings,
                    openBatterySettings = reminderViewModel::openBatterySettings,
                    sendTest = reminderViewModel::sendTest,
                    resync = reminderViewModel::resync,
                )
            }

            val settingsActions = remember(settingsViewModel) {
                SettingsActions(
                    refresh = settingsViewModel::refreshPermissions,
                    setTheme = settingsViewModel::setTheme,
                    setReduceTransparency = settingsViewModel::setReduceTransparency,
                    setReduceMotion = settingsViewModel::setReduceMotion,
                    exportTo = settingsViewModel::exportTo,
                    loadImport = settingsViewModel::loadImport,
                    confirmImport = settingsViewModel::confirmImport,
                    cancelImport = settingsViewModel::cancelImport,
                    requestClearAll = settingsViewModel::requestClearAll,
                    confirmClearAll = settingsViewModel::confirmClearAll,
                    cancelClearAll = settingsViewModel::cancelClearAll,
                    showLicenses = settingsViewModel::showLicenses,
                    hideLicenses = settingsViewModel::hideLicenses,
                    consumeMessage = settingsViewModel::consumeMessage,
                    openNotificationSettings = settingsViewModel::openNotificationSettings,
                    openExactAlarmSettings = settingsViewModel::openExactAlarmSettings,
                )
            }

            HomeScreen(
                state = state,
                calendar = calendar,
                calendarActions = calendarActions,
                categories = categories,
                categoryActions = categoryActions,
                eventActions = eventActions,
                reminders = reminders,
                reminderActions = reminderActions,
                appSettings = settingsState.settings,
                settings = settingsState,
                settingsActions = settingsActions,
                onToggleCompleted = viewModel::toggleCompletedSection,
                onSortChange = viewModel::setSortMode,
                onSaveEvent = viewModel::save,
                openDetailRequest = openDetail,
                onDetailRequestConsumed = { pendingDetailId.value = null },
            )
        }
        StartupTrace.mark("main", "setContent returned")

        // 首帧标记：能走到这里说明组合、测量、绘制全都过了。
        // 若 startup.log 停在上一行，崩溃就在首帧绘制里（渲染层），而不在业务代码。
        window.decorView.post { StartupTrace.mark("main", "first frame drawn") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDetailId.value = intent.getStringExtra(EXTRA_EVENT_ID)
    }

    /**
     * 撤销窗口只在前台走动。
     *
     * 删完事件切出去看别的（或按 Home），6 秒墙钟窗口照样流走，回来时撤销条已经消失——
     * 用户根本没机会点。所以离开前台时冻结、回来时把离开的时长补回（见 UndoRepository）。
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { (application as YingApp).undoRepository.resumeWindow() }
    }

    override fun onStop() {
        // 先冻结再交给系统：onStop 之后用户就看不到撤销条了，继续倒计时等于白烧窗口。
        // 只记内存时刻——进程若被杀，冷启动 restore 会直接给满一个窗口，不依赖这次记录
        (application as YingApp).undoRepository.pauseWindow()
        super.onStop()
    }

    companion object {
        /** 通知 -> 详情页的事件 id 额外字段，与 [com.jiuxina.ying.reminder.ReminderNotifier] 对齐。 */
        const val EXTRA_EVENT_ID = "event_id"
    }
}
