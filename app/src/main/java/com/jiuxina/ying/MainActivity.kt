package com.jiuxina.ying

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

/**
 * 应用入口。首页与日历的数据都来自 [YingApp] 装配的仓库（Room + DataStore），
 * 且共用同一个 [EventViewModel]，避免两处各自订阅导致状态不同步。
 *
 * 当前进度：M2a 列表 + M2b 表单 + M2b2 分类 + M2c 详情/撤销 + M2d 日历三视图。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as YingApp

        setContent {
            val viewModel: EventViewModel = viewModel(
                factory = EventViewModel.factory(
                    app.eventRepository,
                    app.settingsRepository,
                    app.undoRepository,
                ),
            )
            val categoryViewModel: CategoryViewModel = viewModel(
                factory = CategoryViewModel.factory(app.categoryRepository),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val calendar by viewModel.calendar.collectAsStateWithLifecycle()
            val categories by categoryViewModel.items.collectAsStateWithLifecycle()

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

            HomeScreen(
                state = state,
                calendar = calendar,
                calendarActions = calendarActions,
                categories = categories,
                categoryActions = categoryActions,
                eventActions = eventActions,
                onToggleCompleted = viewModel::toggleCompletedSection,
                onSortChange = viewModel::setSortMode,
                onSaveEvent = viewModel::save,
            )
        }
    }
}
