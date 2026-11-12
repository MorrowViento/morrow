package com.morrowviento.morrow.data.reminder

import android.content.Context
import androidx.core.content.edit

/**
 * 一次排期最终落到哪条系统通道。
 *
 * 用户能观察到的差异只有两个：准不准、状态栏有没有闹钟图标。而这两件事完全由「走了哪条
 * API」决定，所以每次排期后把结果记下来，诊断页就不必让用户盯着状态栏猜。
 */
enum class SchedulePath {
    /** `setAlarmClock`：系统当作用户自己设的闹钟，Doze 与后台冻结都推迟不了，代价是挂图标。 */
    ALARM_CLOCK,

    /** `setExactAndAllowWhileIdle`：准点但不挂图标，被 ROM 冻结时仍可能排队到下次唤醒。 */
    EXACT,

    /** `setAndAllowWhileIdle`：没有精确闹钟权限时的兜底，可能晚几分钟。 */
    INEXACT,
}

/** [recordedAtMillis] 是排期发生的时刻，用来判断落点当时是否已在 12 小时窗口内。 */
data class ScheduleTrace(
    val path: SchedulePath,
    val triggerAtMillis: Long,
    val recordedAtMillis: Long,
)

/**
 * 纯字符串编解码，键盘是 `路径|落点|记录时刻`。
 *
 * 单独抽出来是为了能脱离 Android 跑单测：这段格式一旦写错，诊断页会安静地显示「没有记录」，
 * 比显示错误更难查。
 */
object ScheduleTraceCodec {

    fun encode(trace: ScheduleTrace): String =
        "${trace.path.name}|${trace.triggerAtMillis}|${trace.recordedAtMillis}"

    fun decode(raw: String?): ScheduleTrace? {
        val parts = raw?.split('|') ?: return null
        if (parts.size != 3) return null
        val path = SchedulePath.entries.firstOrNull { it.name == parts[0] } ?: return null
        val triggerAt = parts[1].toLongOrNull() ?: return null
        val recordedAt = parts[2].toLongOrNull() ?: return null
        return ScheduleTrace(path, triggerAt, recordedAt)
    }
}

/** 最近一次排期结果的落盘位置；只保留最后一条，不需要历史。 */
class ScheduleTraceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun record(
        path: SchedulePath,
        triggerAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        prefs.edit {
            putString(KEY, ScheduleTraceCodec.encode(ScheduleTrace(path, triggerAtMillis, nowMillis)))
        }
    }

    fun last(): ScheduleTrace? = ScheduleTraceCodec.decode(prefs.getString(KEY, null))

    companion object {
        private const val FILE = "reminder_schedule_trace"
        private const val KEY = "last"
    }
}
