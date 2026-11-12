package com.jiuxina.ying.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 桌面上一个已放置的小部件实例。
 *
 * [id] 由桌面宿主分配，只在换机 / 重装前稳定；[eventTitle] 仅单事件小部件有意义。
 */
data class PlacedWidget(
    val id: Int,
    val isDetail: Boolean,
    val eventTitle: String? = null,
)

/**
 * 小部件配置台的统一入口。
 *
 * 存在的理由：**「长按小部件 → 编辑」不是应用能保证的**。它依赖桌面读
 * `android:widgetFeatures="reconfigurable"` 后是否愿意在长按菜单里给这一项，
 * 国产桌面（ColorOS / HyperOS 等）经常只给「移除」和「应用信息」。
 * 所以除了 XML 里声明可重配置，应用内也必须有一条自己的入口——
 * 设置页列出已放置的小部件，点一下走同一个配置台，行为与长按编辑完全一致。
 */
object WidgetEntries {

    /** 当前桌面上某个 provider 的全部实例 id（总览或单事件）。 */
    fun ids(context: Context, isDetail: Boolean): List<Int> {
        val manager = AppWidgetManager.getInstance(context.applicationContext)
        val component = ComponentName(
            context.applicationContext,
            if (isDetail) YingDetailWidgetProvider::class.java else YingWidgetProvider::class.java,
        )
        return manager.getAppWidgetIds(component).toList()
    }

    /**
     * 打开某个实例的配置台。
     *
     * 显式带上 `EXTRA_APPWIDGET_ID`：配置台靠它区分「新建」与「改已有的」，
     * 少了这个 id 会直接 finish（见 `BaseWidgetConfigActivity.onCreate`）。
     */
    fun openConfig(context: Context, widget: PlacedWidget) {
        val target: Class<out Activity> =
            if (widget.isDetail) DetailWidgetConfigActivity::class.java else WidgetConfigActivity::class.java
        val intent = Intent(context, target)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
