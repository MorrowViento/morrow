package com.morrowviento.morrow.widget

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
import com.morrowviento.morrow.MainActivity
import com.morrowviento.morrow.R
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.data.widget.WidgetRefreshTrigger
import com.morrowviento.morrow.data.widget.WidgetSnapshotBuilder
import com.morrowviento.morrow.domain.model.CountdownEvent
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

    private val overviewComponent = ComponentName(appContext, MorrowWidgetProvider::class.java)
    private val detailComponent = ComponentName(appContext, MorrowDetailWidgetProvider::class.java)

    override suspend fun refreshAll() {
        // 小部件是「顺带」的能力，任何一处出错都不该波及 App 本体：
        // 调用方多在 viewModelScope / 接收器协程里，异常没人接就会杀进程
        // （真机上曾因旧键类型错配抛 ClassCastException，表现为「保存后退回桌面」）。
        runCatching {
            val overviewIds = manager.getAppWidgetIds(overviewComponent)
            val detailIds = manager.getAppWidgetIds(detailComponent)
            if (overviewIds.isEmpty() && detailIds.isEmpty()) return
            Log.i(TAG, "refreshAll：总览 ${overviewIds.size} 个、单事件 ${detailIds.size} 个")
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
        // 新加的小部件还没有专属配置：先看有没有「先设置、再落桌面」留下的待认领样式。
        // 必须在读样式之前做——认领会落盘，后面的 load 才拿得到用户刚调好的那套外观
        ids.forEach { widgetId -> store.adoptPending(widgetId, isDetail) }

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
                buildOverview(widgetId, style, all, today, now, systemDark)
            }
            manager.updateAppWidget(widgetId, views)
            if (!isDetail) {
                // 集合视图的内容不在 RemoteViews 里，改完必须单独通知一次数据变化，
                // 否则拖大 / 增删事件之后列表还是旧的（甚至是空的）
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
            }
            Log.i(TAG, "已刷新 widgetId=$widgetId isDetail=$isDetail 事件数=${all.size}")
        }
    }

    // ---------------------------------------------------------------- 总览

    private fun buildOverview(
        widgetId: Int,
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
        views.setOnClickPendingIntent(R.id.widget_header, openAppIntent())

        // 行数不再由预设决定，也不再截断到 5：集合视图按卡片高度排版，内容超出可上下滑动
        val snapshot = WidgetSnapshotBuilder.overview(events, today, now, OVERVIEW_ROW_LIMIT)
        views.setTextViewText(R.id.widget_header_meta, snapshot.headerMeta)
        views.setTextColor(R.id.widget_header_title, colors.textPrimary)
        views.setTextColor(R.id.widget_header_meta, colors.textSecondary)
        views.setTextViewTextSize(
            R.id.widget_header_meta,
            TypedValue.COMPLEX_UNIT_SP,
            (style.fontScale.metaSp + 1f),
        )
        views.setViewVisibility(R.id.widget_empty, if (snapshot.isEmpty) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_list, if (snapshot.isEmpty) View.GONE else View.VISIBLE)
        views.setTextColor(R.id.widget_empty, colors.textSecondary)
        views.setOnClickPendingIntent(R.id.widget_empty, openAppIntent())

        // data URI 带上刷新序号：部分 ROM 桌面对 notifyAppWidgetViewDataChanged 的处理
        // 不可靠（通知被吞、不回调 onDataSetChanged），列表就一直停在旧数据。
        // 每次让 Intent 真正变化，桌面就必须重新绑定数据源，等价于强制走一遍 onDataSetChanged。
        // extras 不参与 Intent 的相等性比较，只有 data 能起到这个作用。
        val adapter = Intent(appContext, WidgetListService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .setData("morrow://widget/list/$widgetId?v=${adapterSeq.incrementAndGet()}".toUri())
        views.setRemoteAdapter(widgetId, R.id.widget_list, adapter)
        // 条目点击 = 模板（目标 Activity）+ fill-in（事件 id/data），模板必须是 MUTABLE 才能被填
        views.setPendingIntentTemplate(R.id.widget_list, listTemplateIntent())
        return views
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
            data = "morrow://widget/event/$eventId".toUri()
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
            data = "morrow://widget/open".toUri()
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
     * 集合视图条目的点击模板。
     *
     * 集合视图的每个条目不能各自带 PendingIntent（条目由桌面按需创建，那时没有权限），
     * 只能「模板定目标 Activity + 条目 fill-in 补 data/extras」。
     * 因此模板**必须可被填充**（`FLAG_MUTABLE`），用 IMMUTABLE 的话 fill-in 会被丢弃，
     * 点哪条都只打开首页。
     */
    private fun listTemplateIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            2_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
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
            data = "morrow://widget/config/$widgetId".toUri()
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
        private const val TAG = "MorrowWidget"

        /** 集合视图 adapter 的刷新序号：让每次 setRemoteAdapter 的 data URI 都不同。 */
        private val adapterSeq = java.util.concurrent.atomic.AtomicInteger(0)

        /**
         * 集合视图的条数上限。
         *
         * 之所以还有上限：列表由桌面排版，滚多少条都能滚，但一次塞几百条会拖慢
         * binder 传输与首帧。50 与首页列表的量级一致，实际没人会在桌面滚到底。
         */
        private const val OVERVIEW_ROW_LIMIT = 50
    }
}
