package com.morrowviento.morrow.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.net.toUri
import com.morrowviento.morrow.MainActivity
import com.morrowviento.morrow.R
import com.morrowviento.morrow.MorrowApp
import com.morrowviento.morrow.data.settings.AccentColor
import com.morrowviento.morrow.data.widget.WidgetRow
import com.morrowviento.morrow.data.widget.WidgetSnapshotBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 总览小部件的集合视图数据源。
 *
 * 为什么需要它：静态 RemoteViews 不能滚动，只能塞固定几行；一旦用户把卡片拉高，
 * 下方就是空白。ListView 这类集合视图由桌面接管滚动，条目按高度自然排布。
 *
 * 工厂跑在本进程的 binder 线程上（`onDataSetChanged` 允许阻塞），所以直接读 Room 与
 * DataStore，不依赖 WidgetRefresher 传对象——进程被回收后桌面重新要数据时也能自洽。
 */
class WidgetListService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = WidgetListFactory(
        applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
        intent,
    )

    companion object {
        internal const val EXTRA_STYLE = "widget_style"
        internal const val EXTRA_DARK = "widget_dark"
        internal const val EXTRA_ACCENT = "widget_accent"
        internal const val EXTRA_FROST = "widget_frost"
        internal const val EXTRA_OPAQUE = "widget_opaque"
    }
}

internal class WidgetListFactory(
    private val context: Context,
    private val widgetId: Int,
    private val intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {

    private data class Snapshot(
        val rows: List<WidgetRow> = emptyList(),
        val style: WidgetStyle = WidgetStyle(),
        val colors: WidgetColors = WidgetStyle().colors(false, AccentColor.DEFAULT.palette),
        val frost: Float = 0f,
        val opaque: Boolean = false,
    )

    @Volatile private var snapshot = Snapshot()

    /** 读库只在 onDataSetChanged 里做一次，getViewAt 只做排版。 */
    override fun onDataSetChanged() {
        val app = context.applicationContext as? MorrowApp ?: return
        runBlocking {
            runCatching {
                val appearance = app.settingsRepository.appSettings.first()
                val style = WidgetStyleCodec.decode(intent.getStringExtra(WidgetListService.EXTRA_STYLE))
                    ?: WidgetConfigStore(app.settingsRepository).load(widgetId)
                val systemDark = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                val colors = if (intent.hasExtra(WidgetListService.EXTRA_DARK)) {
                    val dark = intent.getBooleanExtra(WidgetListService.EXTRA_DARK, false)
                    WidgetColors(WidgetAppearance.textPrimary(dark), WidgetAppearance.textSecondary(dark),
                        intent.getIntExtra(WidgetListService.EXTRA_ACCENT, 0), WidgetAppearance.separator(dark),
                        dark, style.palette == WidgetPalette.ACCENT)
                } else style.colors(appearance, systemDark)
                val events = app.eventRepository.loadAll()
                val rows = WidgetSnapshotBuilder.overview(
                    events = events,
                    today = LocalDate.now(),
                    now = LocalDateTime.now(),
                    // 集合视图自己会滚，不再按预设截断；上限只是防御超大列表拖慢 binder
                    limit = ROW_LIMIT,
                ).rows
                snapshot = Snapshot(rows, style, colors,
                    intent.getFloatExtra(WidgetListService.EXTRA_FROST, appearance.glassFrost),
                    intent.getBooleanExtra(WidgetListService.EXTRA_OPAQUE, appearance.usesOpaqueGlass))
            }.onFailure {
                // 小部件是顺带能力：读失败就让列表空着，不能把桌面进程的绑定弄崩
                snapshot = snapshot.copy(rows = emptyList())
            }
        }
        // 桌面是否真的回调了这里，是「列表不更新」问题的第一现场，
        // 用 logcat -s MorrowWidget 一眼可辨
        android.util.Log.i("MorrowWidget", "onDataSetChanged widgetId=$widgetId rows=${snapshot.rows.size}")
    }

    override fun onCreate() = Unit

    override fun onDestroy() {
        snapshot = snapshot.copy(rows = emptyList())
    }

    override fun getCount(): Int = snapshot.rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val current = snapshot
        val row = current.rows.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_row)
        val views = RemoteViews(context.packageName, R.layout.widget_row)
        val style = current.style
        val colors = current.colors
        views.setInt(R.id.widget_row_bg, "setColorFilter",
            WidgetAppearance.rowTint(colors, current.frost, current.opaque))
        views.setInt(R.id.widget_row_edge, "setColorFilter", colors.separator)

        views.setTextViewText(R.id.widget_row_title, row.title)
        views.setTextColor(R.id.widget_row_title, colors.textPrimary)
        views.setTextViewTextSize(R.id.widget_row_title, TypedValue.COMPLEX_UNIT_SP, style.fontScale.titleSp)

        val meta = row.metaLine(style.showCategory, style.showDate)
        views.setTextViewText(R.id.widget_row_meta, meta)
        views.setTextColor(R.id.widget_row_meta, colors.textSecondary)
        views.setTextViewTextSize(R.id.widget_row_meta, TypedValue.COMPLEX_UNIT_SP, style.fontScale.metaSp)
        views.setViewVisibility(R.id.widget_row_meta, if (meta.isEmpty()) View.GONE else View.VISIBLE)

        views.setTextViewText(R.id.widget_row_badge, row.badgeText)
        views.setTextColor(R.id.widget_row_badge, colors.textPrimary)
        views.setTextViewTextSize(R.id.widget_row_badge, TypedValue.COMPLEX_UNIT_SP, style.fontScale.badgeSp)

        views.setTextViewText(R.id.widget_row_unit, row.badgeUnit)
        views.setTextColor(R.id.widget_row_unit, colors.textSecondary)
        views.setTextViewTextSize(R.id.widget_row_unit, TypedValue.COMPLEX_UNIT_SP, style.fontScale.metaSp)

        val showProgress = style.showProgress && row.progressPercent >= 0
        views.setViewVisibility(R.id.widget_row_progress, if (showProgress) View.VISIBLE else View.GONE)
        if (showProgress) {
            views.setProgressBar(R.id.widget_row_progress, 100, row.progressPercent, false)
            views.setColorStateList(
                R.id.widget_row_progress,
                "setProgressTintList",
                android.content.res.ColorStateList.valueOf(colors.accent),
            )
        }

        // 点击由父级（WidgetRefresher 里的 setPendingIntentTemplate）+ 这里的 fill-in 组合而成：
        // fill-in 只能补 extras/data，不能自己带 PendingIntent
        views.setOnClickFillInIntent(
            R.id.widget_row_root,
            Intent()
                .setData("morrow://widget/event/${row.id}".toUri())
                .putExtra(MainActivity.EXTRA_EVENT_ID, row.id),
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = snapshot.rows.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        /** 与首页列表的量级对齐：再多也没人会在桌面滚到底。 */
        const val ROW_LIMIT = 50
    }
}
