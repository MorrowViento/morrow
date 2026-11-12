package com.jiuxina.ying.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.jiuxina.ying.R

/**
 * 「把萤的小部件放到桌面」的请求入口。
 *
 * 只做一件事：调 `AppWidgetManager.requestPinAppWidget`，由系统弹「添加到主屏幕」确认框，
 * 用户点确认就直接落在桌面上。
 *
 * 两个入口：
 * 1. 长按应用图标 → 静态快捷方式（`res/xml/shortcuts.xml`）；
 * 2. 应用内 设置 → 桌面小部件 →「添加」。
 *
 * **窗口必须真的可见**：真机上反馈「不先打开 App 就提示桌面不支持」。原因是原先用了
 * `Theme.NoDisplay`，这个 Activity 在 onCreate 里就 finish，从来没到过 RESUMED；
 * ColorOS 一类桌面会因此判定调用方不在前台，直接丢弃 pin 请求（我们只能在
 * 异常路径上给用户一句"不支持"，看起来像权限问题）。现在改成透明但可见的窗口，
 * 在 `onResume` 里发请求，之后自己结束。
 */
class WidgetPinActivity : ComponentActivity() {

    private var requested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明主题也需要一个内容视图，否则部分 ROM 认为窗口没准备好、不给前台身份
        setContentView(android.widget.FrameLayout(this))
    }

    override fun onResume() {
        super.onResume()
        if (requested) return
        requested = true
        val isDetail = intent?.action == ACTION_PIN_DETAIL ||
            intent?.getBooleanExtra(EXTRA_DETAIL, false) == true
        pin(this, isDetail)
        // 确认框属于系统/桌面，不依赖我们的窗口；留一小段时间保证请求已交付再退出
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, FINISH_DELAY_MILLIS)
    }

    companion object {
        const val EXTRA_DETAIL = "com.jiuxina.ying.extra.WIDGET_DETAIL"

        /** 必须在 `res/xml/shortcuts.xml` 里保持一致。 */
        const val ACTION_PIN_DETAIL = "com.jiuxina.ying.action.PIN_DETAIL_WIDGET"

        private const val TAG = "YingWidgetPin"
        private const val FINISH_DELAY_MILLIS = 1_200L

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
                Log.w(TAG, "desktop 不支持 requestPinAppWidget：$provider")
                toastUnsupported(context)
                return
            }
            runCatching { manager.requestPinAppWidget(provider, null, null) }
                .onFailure {
                    // 少数 ROM 声明支持却抛异常，和「不支持」对用户是同一件事
                    Log.w(TAG, "requestPinAppWidget 失败：$provider", it)
                    toastUnsupported(context)
                }
        }

        private fun toastUnsupported(context: Context) {
            Toast.makeText(context.applicationContext, R.string.widget_pin_unsupported, Toast.LENGTH_LONG)
                .show()
        }
    }
}
