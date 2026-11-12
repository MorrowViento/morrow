package com.jiuxina.ying.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.core.net.toUri
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.jiuxina.ying.MainActivity
import com.jiuxina.ying.R
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.settings.SettingsRepository
import com.jiuxina.ying.data.widget.WidgetRefreshTrigger
import com.jiuxina.ying.data.widget.WidgetRow
import com.jiuxina.ying.data.widget.WidgetSnapshotBuilder
import com.jiuxina.ying.domain.model.CountdownEvent
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 小部件刷新的唯一入口。
 *
 * 数据通道按 TODO 的决策走**进程内只读快照**：广播进来 → 一次性查 Room → 拼 RemoteViews
 * → 交给 AppWidgetManager。不在这里订阅 `EventRepository.observeAll()`，
 * 因为 RemoteViews 刷完就结束，挂着的 Flow 只会在进程被回收前白占一个协程。
 *
 * 所有触发点（系统 onUpdate、数据变更、午夜闹钟、开机对账）都收敛到 [refreshAll] /
 * [refreshIds]，避免出现"某条路径忘了套配色"这种分裂。
 */
class WidgetRefresher(
    context: Context,
    private val settings: SettingsRepository,
    private val events: EventRepository,
) : WidgetRefreshTrigger {

    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val store = WidgetConfigStore(settings)

    private val overviewComponent = ComponentName(appContext, YingWidgetProvider::class.java)
    private val detailComponent = ComponentName(appContext, YingDetailWidgetProvider::class.java)

    /** 总览小部件固定 5 组行视图（XML 里写死），按样式决定显示前几行。 */
    private class RowIds(
        val container: Int,
        val title: Int,
        val meta: Int,
        val progress: Int,
        val badge: Int,
        val unit: Int,
    )

    override suspend fun refreshAll() {
        // 小部件是「顺带」的能力，任何一处出错都不该波及 App 本体：
        // 调用方多在 viewModelScope / 接收器协程里，异常没人接就会杀进程
        // （真机上曾因旧键类型错配抛 ClassCastException，表现为「保存后退回桌面」）。
        runCatching {
            val overviewIds = manager.getAppWidgetIds(overviewComponent)
            val detailIds = manager.getAppWidgetIds(detailComponent)
            if (overviewIds.isEmpty() && detailIds.isEmpty()) return
            refresh(overviewIds, isDetail = false)
            refresh(detailIds, isDetail = true)
        }.onFailure { Log.e(TAG, "刷新总览小部件失败，已忽略", it) }
    }

    /** 刷新指定的一组小部件（provider 的 onUpdate 用这个，避免每次都扫全部 id）。 */
    suspend fun refreshIds(ids: IntArray, isDetail: Boolean) {
        if (ids.isEmpty()) return
        runCatching { refresh(ids, isDetail) }
            .onFailure { Log.e(TAG, "刷新小部件失败 ids=${ids.joinToString()}", it) }
    }

    private suspend fun refresh(ids: IntArray, isDetail: Boolean) {
        if (ids.isEmpty()) return
        val all = events.loadAll()
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val systemDark = isSystemDark()
        // 只有总览需要「没配置就用默认样式」，单事件小部件必须有自己的 eventId
        val styleById = if (isDetail) emptyMap() else store.loadAll()
        val fallback = if (isDetail) null else store.defaultStyle()

        ids.forEach { widgetId ->
            val style = if (isDetail) store.load(widgetId) else (styleById[widgetId] ?: fallback ?: WidgetStyle())
            val views = if (isDetail) {
                buildDetail(widgetId, style, all, today, now, systemDark)
            } else {
                buildOverview(style, all, today, now, systemDark)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }

    // ---------------------------------------------------------------- 总览

    private fun buildOverview(
        style: WidgetStyle,
        events: List<CountdownEvent>,
        today: LocalDate,
        now: LocalDateTime,
        systemDark: Boolean,
    ): RemoteViews {
        val views = RemoteViews(appContext.packageName, R.layout.widget_overview)
        val colors = style.colors(
            systemDark = systemDark,
            backgroundDark = R.drawable.widget_bg_dark,
            backgroundLight = R.drawable.widget_bg_light,
            backgroundAccent = R.drawable.widget_bg_accent,
        )
        views.setInt(R.id.widget_root, "setBackgroundResource", colors.backgroundRes)
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent())

        val snapshot = WidgetSnapshotBuilder.overview(events, today, now, style.rows)
        views.setTextViewText(R.id.widget_header_meta, snapshot.headerMeta)
        views.setTextColor(R.id.widget_header_title, colors.textPrimary)
        views.setTextColor(R.id.widget_header_meta, colors.textSecondary)
        views.setTextViewTextSize(
            R.id.widget_header_meta,
            TypedValue.COMPLEX_UNIT_SP,
            (style.fontScale.metaSp + 1f),
        )
        views.setViewVisibility(R.id.widget_empty, if (snapshot.isEmpty) View.VISIBLE else View.GONE)
        views.setTextColor(R.id.widget_empty, colors.textSecondary)

        ROW_IDS.forEachIndexed { index, ids ->
            val row = snapshot.rows.getOrNull(index)
            if (row == null) {
                views.setViewVisibility(ids.container, View.GONE)
                return@forEachIndexed
            }
            bindRow(views, ids, row, style, colors)
        }
        return views
    }

    private fun bindRow(
        views: RemoteViews,
        ids: RowIds,
        row: WidgetRow,
        style: WidgetStyle,
        colors: WidgetColors,
    ) {
        views.setViewVisibility(ids.container, View.VISIBLE)
        views.setOnClickPendingIntent(ids.container, eventIntent(row.id))

        views.setTextViewText(ids.title, row.title)
        views.setTextColor(ids.title, colors.textPrimary)
        views.setTextViewTextSize(ids.title, TypedValue.COMPLEX_UNIT_SP, style.fontScale.titleSp)

        val meta = row.metaLine(style.showCategory, style.showDate)
        views.setTextViewText(ids.meta, meta)
        views.setTextColor(ids.meta, colors.textSecondary)
        views.setTextViewTextSize(ids.meta, TypedValue.COMPLEX_UNIT_SP, style.fontScale.metaSp)
        views.setViewVisibility(ids.meta, if (meta.isEmpty()) View.GONE else View.VISIBLE)

        views.setTextViewText(ids.badge, row.badgeText)
        views.setTextColor(ids.badge, colors.textPrimary)
        views.setTextViewTextSize(ids.badge, TypedValue.COMPLEX_UNIT_SP, style.fontScale.badgeSp)

        views.setTextViewText(ids.unit, row.badgeUnit)
        views.setTextColor(ids.unit, colors.textSecondary)
        views.setTextViewTextSize(ids.unit, TypedValue.COMPLEX_UNIT_SP, style.fontScale.metaSp)

        val showProgress = style.showProgress && row.progressPercent >= 0
        views.setViewVisibility(ids.progress, if (showProgress) View.VISIBLE else View.GONE)
        if (showProgress) {
            views.setProgressBar(ids.progress, 100, row.progressPercent, false)
            views.setColorStateList(
                ids.progress,
                "setProgressTintList",
                android.content.res.ColorStateList.valueOf(colors.accent),
            )
        }
    }

    // ---------------------------------------------------------------- 单事件

    private fun buildDetail(
        widgetId: Int,
        style: WidgetStyle,
        events: List<CountdownEvent>,
        today: LocalDate,
        now: LocalDateTime,
        systemDark: Boolean,
    ): RemoteViews {
        val views = RemoteViews(appContext.packageName, R.layout.widget_detail)
        val colors = style.colors(
            systemDark = systemDark,
            backgroundDark = R.drawable.widget_bg_dark,
            backgroundLight = R.drawable.widget_bg_light,
            backgroundAccent = R.drawable.widget_bg_accent,
        )
        views.setInt(R.id.detail_root, "setBackgroundResource", colors.backgroundRes)
        views.setOnClickPendingIntent(R.id.detail_root, configIntent(widgetId))

        views.setTextColor(R.id.detail_title, colors.textPrimary)
        views.setTextColor(R.id.detail_badge, colors.textPrimary)
        views.setTextColor(R.id.detail_unit, colors.textSecondary)
        views.setTextColor(R.id.detail_meta, colors.textSecondary)
        views.setTextColor(R.id.detail_hint, colors.textSecondary)
        views.setTextViewTextSize(R.id.detail_title, TypedValue.COMPLEX_UNIT_SP, style.fontScale.titleSp)
        views.setTextViewTextSize(R.id.detail_badge, TypedValue.COMPLEX_UNIT_SP, style.fontScale.badgeSp * 1.25f)
        views.setTextViewTextSize(R.id.detail_unit, TypedValue.COMPLEX_UNIT_SP, style.fontScale.metaSp + 2f)
        views.setTextViewTextSize(R.id.detail_meta, TypedValue.COMPLEX_UNIT_SP, style.fontScale.metaSp + 1f)

        val bound = style.eventId
        val event = bound?.let { id -> events.firstOrNull { it.id == id } }
        val row = WidgetSnapshotBuilder.single(event, today, now)

        if (row == null) {
            // 未绑定 or 事件被删：给一句引导，别让小部件成为一个空洞
            views.setTextViewText(R.id.detail_title, appContext.getString(R.string.app_name))
            views.setViewVisibility(R.id.detail_badge_group, View.GONE)
            views.setViewVisibility(R.id.detail_progress, View.GONE)
            views.setViewVisibility(R.id.detail_meta, View.GONE)
            views.setViewVisibility(R.id.detail_hint, View.VISIBLE)
            views.setTextViewText(
                R.id.detail_hint,
                appContext.getString(
                    if (bound == null) R.string.widget_detail_unbound else R.string.widget_detail_missing,
                ),
            )
            return views
        }

        views.setViewVisibility(R.id.detail_badge_group, View.VISIBLE)
        views.setViewVisibility(R.id.detail_hint, View.GONE)
        views.setOnClickPendingIntent(R.id.detail_root, eventIntent(row.id))
        views.setTextViewText(R.id.detail_title, row.title)
        views.setTextViewText(R.id.detail_badge, row.badgeText)
        views.setTextViewText(R.id.detail_unit, row.badgeUnit)
        val meta = row.metaLine(showCategory = true, showDate = style.showDate)
        views.setTextViewText(R.id.detail_meta, meta)
        views.setViewVisibility(R.id.detail_meta, if (meta.isEmpty()) View.GONE else View.VISIBLE)

        val showProgress = style.showProgress && row.progressPercent >= 0
        views.setViewVisibility(R.id.detail_progress, if (showProgress) View.VISIBLE else View.GONE)
        if (showProgress) {
            views.setProgressBar(R.id.detail_progress, 100, row.progressPercent, false)
            views.setColorStateList(
                R.id.detail_progress,
                "setProgressTintList",
                android.content.res.ColorStateList.valueOf(colors.accent),
            )
        }
        return views
    }

    // ---------------------------------------------------------------- 跳转

    /** 点事件行：直接进详情。 */
    private fun eventIntent(eventId: String): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
            // data 唯一，否则同一 requestCode 的 PendingIntent 会互相覆盖（M4 通知踩过同一个坑）
            data = "ying://widget/event/$eventId".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            data = "ying://widget/open".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 单事件小部件点空白处（未绑定时也点它）= 重新配置。
     *
     * 必须指向 `DetailWidgetConfigActivity`：总览的配置台没有「绑定事件」区，
     * 指错了就会出现「单事件小部件怎么也选不了事件」（M5 起一直是这个 bug）。
     */
    private fun configIntent(widgetId: Int): PendingIntent {
        val intent = Intent(appContext, DetailWidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = "ying://widget/config/$widgetId".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            1000 + widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun isSystemDark(): Boolean =
        appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    companion object {
        private const val TAG = "YingWidget"

        /** 与 `widget_overview.xml` 里写死的 5 组行保持一致。 */
        private const val ROW_COUNT = 5

        private val ROW_IDS = arrayOf(
            RowIds(R.id.row0, R.id.row0_title, R.id.row0_meta, R.id.row0_progress, R.id.row0_badge, R.id.row0_unit),
            RowIds(R.id.row1, R.id.row1_title, R.id.row1_meta, R.id.row1_progress, R.id.row1_badge, R.id.row1_unit),
            RowIds(R.id.row2, R.id.row2_title, R.id.row2_meta, R.id.row2_progress, R.id.row2_badge, R.id.row2_unit),
            RowIds(R.id.row3, R.id.row3_title, R.id.row3_meta, R.id.row3_progress, R.id.row3_badge, R.id.row3_unit),
            RowIds(R.id.row4, R.id.row4_title, R.id.row4_meta, R.id.row4_progress, R.id.row4_badge, R.id.row4_unit),
        )
    }
}
