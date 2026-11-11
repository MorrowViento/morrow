package com.jiuxina.ying.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import com.jiuxina.ying.YingApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 广播接收器里跑挂起任务的统一入口。
 *
 * `goAsync()` 只把广播的生命周期延长约 10 秒，必须在 `finally` 里 `finish()`，
 * 否则系统会判超时并杀掉进程（M4 的 `ReminderReceiver` 已经踩过同一个点）。
 */
internal object WidgetRefreshRunner {

    private const val TAG = "WidgetRefresh"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun run(receiver: BroadcastReceiver, context: Context, block: suspend (YingApp) -> Unit) {
        val pending = receiver.goAsync()
        val app = context.applicationContext as? YingApp
        if (app == null) {
            pending.finish()
            return
        }
        scope.launch {
            try {
                block(app)
            } catch (t: Throwable) {
                // 小部件刷新失败不该让广播崩溃（会弹"萤 屡次停止运行"）
                Log.w(TAG, "小部件刷新失败", t)
            } finally {
                pending.finish()
            }
        }
    }

    /** 非广播上下文（如 App 冷启动、ViewModel 数据变更）直接起协程。 */
    fun launch(context: Context, block: suspend (YingApp) -> Unit) {
        val app = context.applicationContext as? YingApp ?: return
        scope.launch {
            try {
                block(app)
            } catch (t: Throwable) {
                Log.w(TAG, "小部件刷新失败", t)
            }
        }
    }
}
