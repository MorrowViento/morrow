package com.jiuxina.ying.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.yingSettings: DataStore<Preferences> by preferencesDataStore(name = "ying_settings")

/**
 * 设置读写（决策 D5：DataStore）。
 *
 * 设计要点：键名沿用 Flutter 版原始键名，迁移后无需二次映射；
 * 后续里程碑（M4 通知 / M5 小部件 / M6 设置中心）直接按原键名读取。
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.yingSettings)

    // ---- 核心设置（M2 首页与 M6 设置中心使用）----

    val themeMode: Flow<String> = store.data.map { it[stringPreferencesKey(LegacySettingsKeys.THEME_MODE)] ?: ThemeModePref.system.name }

    val sortMode: Flow<String> = store.data.map { it[stringPreferencesKey(LegacySettingsKeys.EVENT_SORT_MODE)] ?: SortModePref.distance.name }

    val reduceTransparency: Flow<Boolean> = store.data.map { it[booleanPreferencesKey(LegacySettingsKeys.REDUCE_TRANSPARENCY)] ?: false }

    val reduceMotion: Flow<Boolean> = store.data.map { it[booleanPreferencesKey(LegacySettingsKeys.REDUCE_MOTION)] ?: false }

    val onboardingCompleted: Flow<Boolean> = store.data.map { it[booleanPreferencesKey(LegacySettingsKeys.ONBOARDING_COMPLETED)] ?: false }

    suspend fun setOnboardingCompleted() {
        store.edit { it[booleanPreferencesKey(LegacySettingsKeys.ONBOARDING_COMPLETED)] = true }
    }

    // ---- 原始读写（供尚未建模的旧设置项透传）----

    fun rawString(key: String): Flow<String?> = store.data.map { it[stringPreferencesKey(key)] }

    /**
     * 一次性读取字符串。
     *
     * 面向小部件 / 广播接收器这类**短生命周期**调用方：它们没有可依附的
     * `repeatOnLifecycle`，订阅 Flow 只会留下一个不会被收集的协程。
     */
    suspend fun stringOnce(key: String): String? = store.data.first()[stringPreferencesKey(key)]

    /** 一次性读出所有以 [prefix] 开头的字符串键（用于按 widgetId 批量取配置）。 */
    suspend fun stringsOnce(prefix: String): Map<String, String> =
        store.data.first().asMap().entries.mapNotNull { (key, value) ->
            (value as? String)?.let { key.name to it }
        }.filter { it.first.startsWith(prefix) }.toMap()

    suspend fun putString(key: String, value: String) {
        store.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        store.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun remove(key: String) {
        store.edit { prefs ->
            prefs.remove(stringPreferencesKey(key))
            prefs.remove(booleanPreferencesKey(key))
            prefs.remove(intPreferencesKey(key))
            prefs.remove(longPreferencesKey(key))
            prefs.remove(doublePreferencesKey(key))
            prefs.remove(stringSetPreferencesKey(key))
        }
    }

    /**
     * 原样写入一个旧版偏好项，按运行时类型选择 DataStore 对应类型。
     * @return 是否成功写入（未知类型返回 false）
     */
    suspend fun putRaw(key: String, value: Any?): Boolean {
        when (value) {
            null -> return false
            is String -> store.edit { it[stringPreferencesKey(key)] = value }
            is Boolean -> store.edit { it[booleanPreferencesKey(key)] = value }
            is Int -> store.edit { it[intPreferencesKey(key)] = value }
            is Long -> store.edit { it[longPreferencesKey(key)] = value }
            is Float -> store.edit { it[doublePreferencesKey(key)] = value.toDouble() }
            is Double -> store.edit { it[doublePreferencesKey(key)] = value }
            is Set<*> -> {
                val strings = value.filterIsInstance<String>().toSet()
                store.edit { it[stringSetPreferencesKey(key)] = strings }
            }
            else -> return false
        }
        return true
    }
}
