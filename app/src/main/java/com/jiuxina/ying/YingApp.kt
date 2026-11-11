package com.jiuxina.ying

import android.app.Application
import android.util.Log
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.backup.BackupRepository
import com.jiuxina.ying.data.category.CategoryRepository
import com.jiuxina.ying.data.db.YingDatabase
import com.jiuxina.ying.data.legacy.LegacyDataMigrator
import com.jiuxina.ying.data.reminder.AlarmReminderScheduler
import com.jiuxina.ying.data.reminder.ReminderPermissions
import com.jiuxina.ying.data.reminder.ReminderScheduler
import com.jiuxina.ying.data.settings.SettingsRepository
import com.jiuxina.ying.data.undo.UndoRepository
import com.jiuxina.ying.reminder.ReminderNotifier
import com.jiuxina.ying.util.StartupTrace
import com.jiuxina.ying.widget.MidnightRefreshScheduler
import com.jiuxina.ying.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用容器：手写依赖装配（单模块、依赖少，暂不引入 DI 框架，后续按需再替）。
 */
class YingApp : Application() {

    lateinit var database: YingDatabase
        private set

    lateinit var eventRepository: EventRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var categoryRepository: CategoryRepository
        private set

    lateinit var undoRepository: UndoRepository
        private set

    lateinit var reminderScheduler: ReminderScheduler
        private set

    lateinit var reminderNotifier: ReminderNotifier
        private set

    lateinit var reminderPermissions: ReminderPermissions
        private set

    /** M5：小部件刷新入口。Provider / ViewModel / 午夜闹钟共用这一个实例。 */
    lateinit var widgetRefresher: WidgetRefresher
        private set

    /** M6：备份导出 / 导入。 */
    lateinit var backupRepository: BackupRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 诊断设施必须最先装上：构造阶段的崩溃留不下证据（M7 发布前移除）。
        // 每步几十字节的同步写，不构成 ANR 风险。
        StartupTrace.install(this)

        database = YingDatabase.build(this)
        StartupTrace.mark("app", "database built")

        eventRepository = EventRepository(database.eventDao())
        settingsRepository = SettingsRepository(this)
        categoryRepository = CategoryRepository(settingsRepository, eventRepository)
        undoRepository = UndoRepository(settingsRepository, eventRepository, appScope)
        StartupTrace.mark("app", "repositories wired")

        // M4：提醒链路。渠道必须在任何通知投递前建好，否则通知会被系统静默丢弃
        reminderScheduler = AlarmReminderScheduler(this)
        reminderNotifier = ReminderNotifier(this).also { it.ensureChannel() }
        reminderPermissions = ReminderPermissions(this)
        StartupTrace.mark("app", "reminder wired")

        // M5：小部件刷新器（数据源直读 Room，不订阅 Flow——RemoteViews 刷完即弃）
        widgetRefresher = WidgetRefresher(this, settingsRepository, eventRepository)

        // M6：备份（只做数据进出，导入后的提醒对账与小部件刷新由调用方负责）
        backupRepository = BackupRepository(settingsRepository, eventRepository)
        StartupTrace.mark("app", "widget + backup wired")

        appScope.launch { warmStart() }
    }

    /**
     * 冷启动自愈。
     *
     * 每一步都单独隔离：这些全是「有则更好、失败也该照常启动」的维护动作，
     * 任何一步抛异常都不允许掀掉整个进程——协程里的未捕获异常会直达
     * `UncaughtExceptionHandler`，表现就是用户看到的「打开即闪退」。
     */
    private suspend fun warmStart() {
        StartupTrace.mark("boot", "warm start begin")

        step("migrate") {
            // M1：旧版数据迁移（幂等，失败不阻断启动）
            val report = LegacyDataMigrator(this@YingApp, database.eventDao(), settingsRepository)
                .migrateIfNeeded()
            Log.i(TAG, "迁移结果: $report")
        }

        step("undo") {
            // M2c：恢复未过期的待撤销项（窗口 6 秒，跨进程重启仍有效）
            undoRepository.restore()
        }

        step("reminders") {
            // M4：启动对账——被强制停止、清理加速或卸载重装后闹钟都可能丢失，
            // 每次冷启动重排一遍是最省事的自愈手段（sync 幂等）
            val events = eventRepository.loadAll()
            reminderScheduler.syncAll(events)
            Log.i(TAG, "已对账 ${events.size} 个事件的提醒排期")
        }

        step("widget") {
            // M5：冷启动可能是「重启后桌面小部件还在但状态过期」，顺便刷一次；
            // 跨天闹钟也在这里排上（幂等，没有小部件时排了也不会醒——接收器会自己取消）
            widgetRefresher.refreshAll()
            if (MidnightRefreshScheduler.hasAnyWidget(this@YingApp)) {
                MidnightRefreshScheduler.schedule(this@YingApp)
            }
        }

        StartupTrace.mark("boot", "warm start done")
    }

    /** 执行一个可失败的自愈步骤：异常只记录，不向外传播。 */
    private suspend fun step(name: String, block: suspend () -> Unit) {
        runCatching { block() }.onFailure { StartupTrace.record("boot", name, it) }
    }

    companion object {
        private const val TAG = "YingApp"
    }
}
