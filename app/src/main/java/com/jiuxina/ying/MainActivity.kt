package com.jiuxina.ying

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiuxina.ying.ui.calendar.CalendarActions
import com.jiuxina.ying.ui.category.CategoryActions
import com.jiuxina.ying.ui.category.CategoryViewModel
import com.jiuxina.ying.ui.event.EventActions
import com.jiuxina.ying.ui.event.EventViewModel
import com.jiuxina.ying.ui.home.HomeScreen
import com.jiuxina.ying.ui.reminder.ReminderActions
import com.jiuxina.ying.ui.reminder.ReminderViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 应用入口。首页、日历与提醒诊断的数据都来自 [YingApp] 装配的仓库，
 * 且共用同一个 [EventViewModel]，避免多处各自订阅导致状态不同步。
 *
 * 当前进度：M2a 列表 + M2b 表单 + M2b2 分类 + M2c 详情/撤销 + M2d 日历 + M4 提醒。
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
                    app.reminderPermissions,
                    app.reminderNotifier,
                    app.reminderScheduler,
                ),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val calendar by viewModel.calendar.collectAsStateWithLifecycle()
            val categories by categoryViewModel.items.collectAsStateWithLifecycle()
            val reminders by reminderViewModel.state.collectAsStateWithLifecycle()
            val openDetail by pendingDetailId.collectAsStateWithLifecycle()

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
                    sendTest = reminderViewModel::sendTest,
                    resync = reminderViewModel::resync,
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
                onToggleCompleted = viewModel::toggleCompletedSection,
                onSortChange = viewModel::setSortMode,
                onSaveEvent = viewModel::save,
                openDetailRequest = openDetail,
                onDetailRequestConsumed = { pendingDetailId.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDetailId.value = intent.getStringExtra(EXTRA_EVENT_ID)
    }

    companion object {
        /** 通知 -> 详情页的事件 id 额外字段，与 [com.jiuxina.ying.reminder.ReminderNotifier] 对齐。 */
        const val EXTRA_EVENT_ID = "event_id"
    }
}
