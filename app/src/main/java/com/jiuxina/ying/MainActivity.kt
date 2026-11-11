package com.jiuxina.ying

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiuxina.ying.ui.category.CategoryActions
import com.jiuxina.ying.ui.category.CategoryViewModel
import com.jiuxina.ying.ui.event.EventViewModel
import com.jiuxina.ying.ui.home.HomeScreen

/**
 * 应用入口。首页数据来自 [YingApp] 装配的仓库（Room + DataStore）。
 *
 * 当前进度：M2a 列表 + M2b 新建表单 + M2b2 分类管理已接入；卡片点击（详情页）留待 M2c。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as YingApp

        setContent {
            val viewModel: EventViewModel = viewModel(
                factory = EventViewModel.factory(app.eventRepository, app.settingsRepository),
            )
            val categoryViewModel: CategoryViewModel = viewModel(
                factory = CategoryViewModel.factory(app.categoryRepository),
            )
            val state by viewModel.state.collectAsStateWithLifecycle()
            val categories by categoryViewModel.items.collectAsStateWithLifecycle()

            // 分类操作集合只取决于 ViewModel 实例，避免每帧重组都新建 lambda 触发浮层内的状态抖动
            val categoryActions = remember(categoryViewModel) {
                CategoryActions(
                    add = categoryViewModel::add,
                    rename = categoryViewModel::rename,
                    delete = categoryViewModel::delete,
                )
            }

            HomeScreen(
                state = state,
                categories = categories,
                categoryActions = categoryActions,
                onToggleCompleted = viewModel::toggleCompletedSection,
                onSortChange = viewModel::setSortMode,
                onSaveEvent = viewModel::save,
            )
        }
    }
}
