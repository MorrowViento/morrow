package com.jiuxina.ying.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.jiuxina.ying.MainActivity
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
 * **这里踩过两次坑，都跟「调用方算不算前台」有关：**
 *
 * 1. 最初用 `Theme.NoDisplay`，Activity 在 onCreate 里 finish，从没过 RESUMED，
 *    ColorOS 判定调用方不在前台 → 请求被丢弃，用户看到的是「桌面不支持」。
 * 2. 改成透明可见窗口、在 `onResume` 里发请求，**进程还活着时能成，冷启动仍失败**
 *    （用户反馈：不先打开应用就不行，添加过一次之后才行）。原因是 `onResume` 只表示
 *    本进程跑起来了，窗口这一帧还没画完，系统侧对「可见」的判定要等窗口完成绘制；
 *    冷启动时这个时间差被启动初始化拉长，正好落进失败窗口。
 *
 * 所以现在的顺序是：等第一帧真正要画（`OnPreDraw`）→ 再让一帧给窗口完成上报 →
 * 发请求 → 不支持/返回 false 就隔一小段时间重试（launcher 侧的 pin 能力查询也存在
 * 冷启动竞态）。全部尝试都失败才退到兜底：打开应用内「桌面小部件」页（真前台界面），
 * 那里点添加必然成功，而不是只丢一句「不支持」让用户无路可走。
 */
class WidgetPinActivity : ComponentActivity() {

    private var requested = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明主题也需要一个内容视图，否则部分 ROM 认为窗口没准备好、不给前台身份
        setContentView(FrameLayout(this))
    }

    override fun onResume() {
        super.onResume()
        if (requested) return
        requested = true
        waitFirstFrame { attemptPin(0) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /**
     * 等窗口真的画出来。
     *
     * `OnPreDraw` 只说明"这一帧要画了"，窗口完成上报（`reportDrawFinished`）在其后，
     * 所以再 post 一帧。另加一条定时兜底：极少数情况下 preDraw 不触发（被遮挡、熄屏），
     * 不能让请求永远挂着。
     */
    private fun waitFirstFrame(action: () -> Unit) {
        val decor = window.decorView
        var fired = false

        fun fire() {
            if (fired || isFinishing) return
            fired = true
            action()
        }

        decor.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (decor.viewTreeObserver.isAlive) {
                        decor.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    decor.post { fire() }
                    return true
                }
            },
        )
        handler.postDelayed({ fire() }, FIRST_FRAME_TIMEOUT_MILLIS)
    }

    /**
     * 第 [index] 次尝试。
     *
     * 两条失败原因分开记：`isRequestPinAppWidgetSupported == false` 是 launcher 侧的
     * 能力查询（冷启动时可能还没就绪），`requestPinAppWidget == false` 是请求发出后被拒
     * （多半是调用方可见性判定）。两者都值得再等一下重试。
     */
    private fun attemptPin(index: Int) {
        if (isFinishing || isDestroyed) return

        val manager = AppWidgetManager.getInstance(applicationContext)
        val isDetail = intent?.action == ACTION_PIN_DETAIL ||
            intent?.getBooleanExtra(EXTRA_DETAIL, false) == true
        val provider = ComponentName(
            applicationContext,
            if (isDetail) YingDetailWidgetProvider::class.java else YingWidgetProvider::class.java,
        )

        val supported = runCatching { manager.isRequestPinAppWidgetSupported }.getOrDefault(false)
        if (supported) {
            val accepted = runCatching { manager.requestPinAppWidget(provider, null, null) }
                .onFailure { Log.w(TAG, "第 ${index + 1} 次 requestPinAppWidget 抛异常：$provider", it) }
                .getOrDefault(false)
            if (accepted) {
                Log.i(TAG, "pin 请求已受理（第 ${index + 1} 次）：$provider")
                // 确认框属于 launcher，不依赖本窗口；留一段时间让弹框真正出现再收
                handler.postDelayed({ finish() }, FINISH_DELAY_MILLIS)
                return
            }
            Log.w(TAG, "第 ${index + 1} 次 requestPinAppWidget 返回 false：$provider")
        } else {
            Log.w(TAG, "第 ${index + 1} 次 isRequestPinAppWidgetSupported = false：$provider")
        }

        val nextDelay = RETRY_DELAYS_MILLIS.getOrNull(index)
        if (nextDelay != null) {
            handler.postDelayed({ attemptPin(index + 1) }, nextDelay)
            return
        }
        fallback()
    }

    /**
     * 兜底：不再只说「不支持」。
     *
     * 自动 pin 走不通时，把用户带到应用内「设置 → 桌面小部件」，那里的添加入口是在真前台
     * 点击的，命中率高得多；长按桌面空白处那套系统流程也还在。
     */
    private fun fallback() {
        Log.w(TAG, "多次尝试后仍未受理，退回应用内小部件页")
        Toast.makeText(applicationContext, R.string.widget_pin_fallback, Toast.LENGTH_LONG).show()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_WIDGET_SETTINGS, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }.onFailure { Log.w(TAG, "打开小部件页失败", it) }
        finish()
    }

    companion object {
        const val EXTRA_DETAIL = "com.jiuxina.ying.extra.WIDGET_DETAIL"

        /** 必须在 `res/xml/shortcuts.xml` 里保持一致。 */
        const val ACTION_PIN_DETAIL = "com.jiuxina.ying.action.PIN_DETAIL_WIDGET"

        private const val TAG = "YingWidgetPin"

        /** 请求受理后留多久再关自己：要给 launcher 足够时间把确认框弹出来。 */
        private const val FINISH_DELAY_MILLIS = 1_600L

        /** 等首帧的上限：超过就当它已经画过，直接往下走。 */
        private const val FIRST_FRAME_TIMEOUT_MILLIS = 800L

        /**
         * 重试间隔（第 n 项 = 第 n 次失败后等多久再试）。
         *
         * 总跨度约 2.6 秒，覆盖冷启动时「进程刚起 → launcher 侧 pin 能力就绪」的窗口。
         * 再长会让透明页停留过久，用户会以为卡住了。
         */
        private val RETRY_DELAYS_MILLIS = longArrayOf(250L, 500L, 800L, 1_000L)

        /** 从应用内拉起这个流程（设置页用）。 */
        fun start(context: Context, isDetail: Boolean) {
            val intent = Intent(context, WidgetPinActivity::class.java)
                .putExtra(EXTRA_DETAIL, isDetail)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}
