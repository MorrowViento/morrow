package com.jiuxina.ying.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 设置页里的一行小部件设置。
 *
 * [ids] 是这一行要一起编辑的桌面实例：桌面上可能放了**多个一模一样**的总览卡片，
 * 逐个列成「总览 / 编辑」「总览 / 编辑」既看不出区别也没法选，所以合成一行、保存时
 * 对整组生效。[id] 只是组里第一个，用于配置台回传结果。
 * [eventTitle] 仅单事件小部件有意义。
 */
data class PlacedWidget(
    val ids: List<Int>,
    val isDetail: Boolean,
    val eventTitle: String? = null,
) {
    init {
        require(ids.isNotEmpty()) { "一行小部件至少要有一个 widgetId" }
    }

    val id: Int get() = ids.first()
    val count: Int get() = ids.size
}

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
     * 把桌面实例折叠成设置页的行（纯逻辑，不碰系统 API）。
     *
     * 总览卡片之间没有任何可区分的信息，桌面上放两个就列出两行一模一样的
     * 「总览 / 编辑」，用户既分不清也选不对——所以一类只出一行，行里带着全部 id，
     * 保存时对整组生效。单事件按**绑定的事件**分组，绑同一事件的多张卡片也合成一行。
     *
     * @param detailBindings 单事件 widgetId → 绑定的事件 id（没有绑定就是 null）。
     */
    fun rows(
        overviewIds: List<Int>,
        detailBindings: Map<Int, String?>,
        eventTitleOf: (String?) -> String? = { null },
    ): List<PlacedWidget> = buildList {
        if (overviewIds.isNotEmpty()) {
            add(PlacedWidget(ids = overviewIds, isDetail = false))
        }
        detailBindings.entries
            .groupBy { it.value }
            .forEach { (eventId, entries) ->
                add(
                    PlacedWidget(
                        ids = entries.map { it.key },
                        isDetail = true,
                        eventTitle = eventTitleOf(eventId),
                    ),
                )
            }
    }

    /**
     * 添加一个新实例：打开配置台，让用户先把外观调好再落桌面。
     *
     * 不直接 `requestPinAppWidget`：桌面确认框一弹出来，页面就没法再操作了，
     * 「先调好再放上去」只能把 pin 放到配置台的最后一步。没带 `EXTRA_APPWIDGET_ID`，
     * 配置台据此进入「添加」模式（见 `BaseWidgetConfigActivity`）。
     */
    fun openAdd(context: Context, isDetail: Boolean) {
        val target: Class<out Activity> =
            if (isDetail) DetailWidgetConfigActivity::class.java else WidgetConfigActivity::class.java
        val intent = Intent(context, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * 打开某个实例的配置台。
     *
     * 显式带上 `EXTRA_APPWIDGET_ID`：配置台靠它区分「改已有的」与「新建」，
     * 带了就是改这一份，保存即生效，不会再去 pin 一个新实例。
     */
    fun openConfig(context: Context, widget: PlacedWidget) {
        val target: Class<out Activity> =
            if (widget.isDetail) DetailWidgetConfigActivity::class.java else WidgetConfigActivity::class.java
        val intent = Intent(context, target)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.id)
            .putExtra(EXTRA_WIDGET_IDS, widget.ids.toIntArray())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * 一次编辑多个实例时传的 id 列表。
     *
     * 单独的 `EXTRA_APPWIDGET_ID` 仍保留：系统 / 长按菜单拉起配置台时只会带它，
     * 配置台据此回落成「只改这一个」，两条路共用同一个 Activity。
     */
    const val EXTRA_WIDGET_IDS = "com.jiuxina.ying.widget.EXTRA_WIDGET_IDS"
}
