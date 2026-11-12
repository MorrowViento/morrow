package com.morrowviento.morrow.data.reminder

import com.morrowviento.morrow.domain.model.CountdownEvent

/**
 * 提醒排期对外接口。
 *
 * 抽象成接口的原因：上层（ViewModel / 接收器）不应该直接碰 AlarmManager，
 * 这样排期逻辑可以在无系统服务的环境里被替身实现，也便于日后换成 WorkManager 方案。
 *
 * 约定：所有方法都是「幂等对账」语义——`sync` 先按最新状态补齐缺失闹钟，
 * 再清掉多余的槽位，因此重复调用不会产生重复通知。
 */
interface ReminderScheduler {

    /** 按事件当前状态重排该事件的全部闹钟（含清除已删除的提醒）。 */
    fun sync(event: CountdownEvent)

    /** 全量对账：启动、重启、时区/时间变更后调用，修复被系统清理掉的闹钟。 */
    fun syncAll(events: List<CountdownEvent>)

    /** 取消某事件的全部闹钟（事件被删除时使用）。 */
    fun cancel(eventId: String)

    /** 稍后提醒：在 [minutes] 分钟后补一次通知，不影响原有排期。 */
    fun snooze(eventId: String, minutes: Int)
}
