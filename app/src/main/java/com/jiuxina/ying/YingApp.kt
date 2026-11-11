package com.jiuxina.ying

import android.app.Application
import android.util.Log
import com.jiuxina.ying.data.EventRepository
import com.jiuxina.ying.data.category.CategoryRepository
import com.jiuxina.ying.data.db.YingDatabase
import com.jiuxina.ying.data.legacy.LegacyDataMigrator
import com.jiuxina.ying.data.settings.SettingsRepository
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = YingDatabase.build(this)
        eventRepository = EventRepository(database.eventDao())
        settingsRepository = SettingsRepository(this)
        categoryRepository = CategoryRepository(settingsRepository, eventRepository)

        // M1：启动即执行旧版数据迁移（幂等，失败不阻断启动）
        appScope.launch {
            val report = LegacyDataMigrator(this@YingApp, database.eventDao(), settingsRepository)
                .migrateIfNeeded()
            Log.i(TAG, "迁移结果: $report")
        }
    }

    companion object {
        private const val TAG = "YingApp"
    }
}
