package com.morrowviento.morrow.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.morrowviento.morrow.MorrowApp

/**
 * 总览小部件。
 *
 * 刷新时机：系统 onUpdate（添加/恢复备份/启动器重启）→ 数据变更 → 午夜闹钟。
 * 系统周期更新已在 `widget_overview_info.xml` 里关掉（`updatePeriodMillis=0`），
 * 因为它的最短间隔是 30 分钟，跨天那一刻必然对不上。
 */
class MorrowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetRefreshRunner.run(this, context) { app ->
            app.widgetRefresher.refreshIds(appWidgetIds, isDetail = false)
        }
    }

    /** 改尺寸后行高变了，重画一次让文字重新排布。 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetRefreshRunner.run(this, context) { app ->
            app.widgetRefresher.refreshIds(intArrayOf(appWidgetId), isDetail = false)
        }
    }

    /** 删除后清掉配置：Android 会回收 widgetId 并分配给新实例，不清会继承上一任的样式。 */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        clearConfig(this, context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        MidnightRefreshScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        // 最后一个总览小部件被移除；单事件小部件还在时闹钟要留给它
        if (!MidnightRefreshScheduler.hasAnyWidget(context)) {
            MidnightRefreshScheduler.cancel(context)
        }
    }
}

/** 单事件小部件：与总览共用刷新链路，只是渲染另一个布局。 */
class MorrowDetailWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetRefreshRunner.run(this, context) { app ->
            app.widgetRefresher.refreshIds(appWidgetIds, isDetail = true)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        clearConfig(this, context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        MidnightRefreshScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        if (!MidnightRefreshScheduler.hasAnyWidget(context)) {
            MidnightRefreshScheduler.cancel(context)
        }
    }
}

private fun clearConfig(receiver: AppWidgetProvider, context: Context, ids: IntArray) {
    WidgetRefreshRunner.run(receiver, context) { app: MorrowApp ->
        val store = WidgetConfigStore(app.settingsRepository)
        ids.forEach { store.remove(it) }
    }
}
