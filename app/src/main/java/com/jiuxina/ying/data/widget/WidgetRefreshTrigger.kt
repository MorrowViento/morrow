package com.jiuxina.ying.data.widget

/**
 * 触发小部件刷新。
 *
 * 抽成接口是为了让 `EventViewModel` 不依赖 `widget/` 包（那里全是 Android 系统组件）：
 * ViewModel 只声明"数据变了，谁关心谁去刷"，具体怎么拼 RemoteViews 由 widget 层负责。
 * 与 M4 的 `ReminderScheduler` 完全同构。
 */
interface WidgetRefreshTrigger {
    suspend fun refreshAll()

    /** 没有小部件时不做任何事（内部自己判空，调用方无需判断）。 */
    companion object {
        val NONE: WidgetRefreshTrigger = object : WidgetRefreshTrigger {
            override suspend fun refreshAll() = Unit
        }
    }
}
