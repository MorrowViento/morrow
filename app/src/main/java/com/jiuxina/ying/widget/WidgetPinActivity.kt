package com.jiuxina.ying.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.jiuxina.ying.R

/**
 * 「把萤的小部件放到桌面」的请求入口。
 *
 * 只有一个动作：调 `AppWidgetManager.requestPinAppWidget`，由系统弹出「添加到主屏幕」
 * 确认框，用户点确认就直接落在桌面上。不渲染任何界面（`Theme.NoDisplay`）。
 *
 * 有两个入口：
 * 1. 长按应用图标 → 静态快捷方式（`res/xml/shortcuts.xml`）。这是用户提的
 *    「长按图标直接添加」——Android 没给「应用自定义长按小部件菜单」的接口，
 *    但长按图标的快捷方式菜单是标准能力，一定会出现；
 * 2. 应用内 设置 → 桌面小部件 → 「添加」。
 *
 * 个别桌面没实现 pin 能力（`isRequestPinAppWidgetSupported == false`）时给一句可执行的
 * 指引，而不是静默失败。
 */
class WidgetPinActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 快捷方式靠 action 区分（shortcuts.xml 的 <extra> 不支持 boolean 类型），
        // 应用内入口则直接带 extra
        val isDetail = intent?.action == ACTION_PIN_DETAIL ||
            intent?.getBooleanExtra(EXTRA_DETAIL, false) == true
        pin(this, isDetail)
        // NoDisplay 主题要求进入 onResume 前结束自己；确认框由系统持有，不受影响
        finish()
    }

    companion object {
        const val EXTRA_DETAIL = "com.jiuxina.ying.extra.WIDGET_DETAIL"

        /** 必须在 `res/xml/shortcuts.xml` 里保持一致。 */
        const val ACTION_PIN_DETAIL = "com.jiuxina.ying.action.PIN_DETAIL_WIDGET"

        /** 从应用内拉起这个流程（设置页用）。 */
        fun start(context: Context, isDetail: Boolean) {
            val intent = Intent(context, WidgetPinActivity::class.java)
                .putExtra(EXTRA_DETAIL, isDetail)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        private fun pin(context: Context, isDetail: Boolean) {
            val manager = AppWidgetManager.getInstance(context.applicationContext)
            val provider = ComponentName(
                context.applicationContext,
                if (isDetail) YingDetailWidgetProvider::class.java else YingWidgetProvider::class.java,
            )
            if (!manager.isRequestPinAppWidgetSupported) {
                toastUnsupported(context)
                return
            }
            runCatching { manager.requestPinAppWidget(provider, null, null) }
                .onFailure {
                    // 少数 ROM 声明支持却抛异常，和「不支持」对用户是同一件事
                    toastUnsupported(context)
                }
        }

        private fun toastUnsupported(context: Context) {
            // 这个 Activity 在 onCreate 里就 finish 了，用 applicationContext 才不会被回收带走
            Toast.makeText(context.applicationContext, R.string.widget_pin_unsupported, Toast.LENGTH_LONG)
                .show()
        }
    }
}
