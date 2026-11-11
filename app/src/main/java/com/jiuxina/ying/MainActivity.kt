package com.jiuxina.ying

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiuxina.ying.ui.event.EventViewModel
import com.jiuxina.ying.ui.home.HomeScreen

/**
 * 应用入口。首页数据来自 [YingApp] 装配的仓库（Room + DataStore）。
 *
 * 当前进度：M2a 列表 + M2b 新建表单已接入；卡片点击（详情页）留待 M2c。
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
            val state by viewModel.state.collectAsStateWithLifecycle()

            HomeScreen(
                state = state,
                onToggleCompleted = viewModel::toggleCompletedSection,
                onSortChange = viewModel::setSortMode,
                onSaveEvent = viewModel::save,
            )
        }
    }
}
