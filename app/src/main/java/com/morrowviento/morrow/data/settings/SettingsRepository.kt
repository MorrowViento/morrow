package com.morrowviento.morrow.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.morrowviento.morrow.domain.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.morrowSettings: DataStore<Preferences> by preferencesDataStore(name = "morrow_settings")

/**
 * 设置读写（决策 D5：DataStore）。
 *
 * 设计要点：键名沿用 Flutter 版原始键名，迁移后无需二次映射；
 * 后续里程碑（M4 通知 / M5 小部件 / M6 设置中心）直接按原键名读取。
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.morrowSettings)

    // ---- 核心设置（M2 首页与 M6 设置中心使用）----

    val themeMode: Flow<String> = store.data.map { it.stringOrNull(LegacySettingsKeys.THEME_MODE) ?: ThemeModePref.system.name }

    val sortMode: Flow<String> = store.data.map { it.stringOrNull(LegacySettingsKeys.EVENT_SORT_MODE) ?: SortModePref.distance.name }

    val reduceTransparency: Flow<Boolean> = store.data.map { it.booleanOrNull(LegacySettingsKeys.REDUCE_TRANSPARENCY) ?: false }

    /**
     * 玻璃厚度（0~1 连续值）。
     *
     * 键缺失时依次回落：旧版四档字符串 → 更旧的无障碍布尔 → 默认值。
     * 所以从 alpha6（四档）、alpha7 之前（布尔）升上来的用户，看到的仍是自己原来的观感。
     */
    val glassFrost: Flow<Float> = store.data.map { it.glassFrostOrDefault() }

    val reduceMotion: Flow<Boolean> = store.data.map { it.booleanOrNull(LegacySettingsKeys.REDUCE_MOTION) ?: false }

    val onboardingCompleted: Flow<Boolean> = store.data.map { it.booleanOrNull(LegacySettingsKeys.ONBOARDING_COMPLETED) ?: false }

    suspend fun setOnboardingCompleted() {
        store.edit { it[booleanPreferencesKey(LegacySettingsKeys.ONBOARDING_COMPLETED)] = true }
    }

    // ---- M6：设置中心（四项合成一个快照，见 AppSettings 的注释）----

    val appSettings: Flow<AppSettings> = combine(
        themeMode,
        sortMode,
        glassFrost,
        reduceMotion,
    ) { theme, sort, frost, motion ->
        AppSettings(
            themeMode = ThemeMode.fromPref(theme),
            sortMode = SortMode.fromPref(sort),
            glassFrost = frost,
            // 与厚度保持同步：达到最厚档时同时带上无障碍「降低透明度」语义
            reduceTransparency = frost >= 0.85f,
            reduceMotion = motion,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        putString(LegacySettingsKeys.THEME_MODE, mode.prefValue)

    /**
     * 写玻璃厚度。
     *
     * 同时更新旧版两个键（四档字符串 + 布尔），保证「降级安装 alpha7」时观感不跳。
     * 不到最厚档就写 `standard` / `light` / `clear`，够旧版本读出一个接近的值。
     */
    suspend fun setGlassFrost(value: Float) {
        val frosted = value.coerceIn(0f, 1f)
        store.edit { prefs ->
            prefs[floatPreferencesKey(LegacySettingsKeys.GLASS_FROST)] = frosted
            prefs[stringPreferencesKey(LegacySettingsKeys.GLASS_MATERIAL)] = when {
                frosted < 0.14f -> "clear"
                frosted < 0.45f -> "light"
                frosted < 0.85f -> "standard"
                else -> "heavy"
            }
            prefs[booleanPreferencesKey(LegacySettingsKeys.REDUCE_TRANSPARENCY)] = frosted >= 0.85f
        }
    }

    suspend fun setReduceTransparency(enabled: Boolean) =
        setGlassFrost(if (enabled) 0.95f else GLASS_FROST_DEFAULT)

    suspend fun setReduceMotion(enabled: Boolean) =
        putBoolean(LegacySettingsKeys.REDUCE_MOTION, enabled)

    /**
     * 一次性读出整组设置。
     *
     * 供备份导出与设置中心首帧使用：这两处只要「现在的值」，
     * 订阅四条 Flow 反而要等四次 DataStore 读盘各自发射。
     */
    suspend fun snapshotSettings(): AppSettings {
        val prefs = store.data.first()
        val frost = prefs.glassFrostOrDefault()
        return AppSettings(
            themeMode = ThemeMode.fromPref(prefs.stringOrNull(LegacySettingsKeys.THEME_MODE)),
            sortMode = SortMode.fromPref(prefs.stringOrNull(LegacySettingsKeys.EVENT_SORT_MODE)),
            glassFrost = frost,
            reduceTransparency = frost >= 0.85f,
            reduceMotion = prefs.booleanOrNull(LegacySettingsKeys.REDUCE_MOTION) ?: false,
        )
    }

    /** 备份导入用：一次 edit 写完四个键，避免四条 Flow 各触发一次重组。 */
    suspend fun applySettings(value: AppSettings) {
        val frost = value.glassFrost.coerceIn(0f, 1f)
        store.edit { prefs ->
            prefs[stringPreferencesKey(LegacySettingsKeys.THEME_MODE)] = value.themeMode.prefValue
            prefs[stringPreferencesKey(LegacySettingsKeys.EVENT_SORT_MODE)] = value.sortMode.prefValue
            prefs[floatPreferencesKey(LegacySettingsKeys.GLASS_FROST)] = frost
            prefs[stringPreferencesKey(LegacySettingsKeys.GLASS_MATERIAL)] = when {
                frost < 0.14f -> "clear"
                frost < 0.45f -> "light"
                frost < 0.85f -> "standard"
                else -> "heavy"
            }
            prefs[booleanPreferencesKey(LegacySettingsKeys.REDUCE_TRANSPARENCY)] = frost >= 0.85f
            prefs[booleanPreferencesKey(LegacySettingsKeys.REDUCE_MOTION)] = value.reduceMotion
        }
    }

    // ---- 原始读写（供尚未建模的旧设置项透传）----

    fun rawString(key: String): Flow<String?> = store.data.map { it.stringOrNull(key) }

    /**
     * 一次性读取字符串。
     *
     * 面向小部件 / 广播接收器这类**短生命周期**调用方：它们没有可依附的
     * `repeatOnLifecycle`，订阅 Flow 只会留下一个不会被收集的协程。
     *
     * 注意：走 [stringOrNull] 而不是 `prefs[stringPreferencesKey(key)]`——后者在键里存的是
     * Boolean / Int 时会抛 `ClassCastException`（详见 [stringOrNull] 注释）。
     */
    suspend fun stringOnce(key: String): String? = store.data.first().stringOrNull(key)

    /** 一次性读出所有以 [prefix] 开头的字符串键（用于按 widgetId 批量取配置）。 */
    suspend fun stringsOnce(prefix: String): Map<String, String> =
        store.data.first().asMap().entries.mapNotNull { (key, value) ->
            (value as? String)?.let { key.name to it }
        }.filter { it.first.startsWith(prefix) }.toMap()

    suspend fun putString(key: String, value: String) {
        store.edit { it[stringPreferencesKey(key)] = value }
    }

    /**
     * 批量写字符串键。
     *
     * 备份导入会一次写入「事件之外的全部键」（分类 + 每个小部件的样式），
     * 逐个 [putString] 会触发同样多次落盘，DataStore 每次都要重写整份文件。
     */
    suspend fun putStrings(values: Map<String, String>) {
        if (values.isEmpty()) return
        store.edit { prefs ->
            values.forEach { (key, value) -> prefs[stringPreferencesKey(key)] = value }
        }
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

/**
 * 按**名字**取键值，不看键的类型。
 *
 * 为什么不能直接 `prefs[stringPreferencesKey(name)]`：DataStore 的 `Preferences.Key` 是
 * `data class Key<T>(val name: String)`，**相等性只看 name、不参与类型校验**，而内部实现是
 * `preferencesMap[key] as T?`。于是「用 String 键去读一个 Boolean 值」这类错配不会在读取时
 * 被挡住，而是在**调用方拿到结果做 String 赋值时**抛
 * `ClassCastException: java.lang.Boolean cannot be cast to java.lang.String`。
 *
 * 这个错配在真机上真实发生过：M1 的旧数据迁移用 [SettingsRepository.putRaw]
 * **原样保留类型**地把 Flutter 版偏好搬过来，而 Flutter 版的 `widget_show_progress` 等键是
 * Boolean；M5 的 [com.morrowviento.morrow.widget.WidgetConfigStore] 按 String 去读，于是每次刷新
 * 小部件都会抛异常——在 viewModelScope 里无人接住，直接杀进程（表现就是「保存后退回桌面」）。
 */
private fun Preferences.valueByName(name: String): Any? =
    asMap().entries.firstOrNull { it.key.name == name }?.value

/**
 * 类型宽容的字符串读取：值是 Boolean / 数字时转成文本，其它类型或缺失返回 null。
 *
 * 旧版键的类型由 Dart 侧决定，无法穷举；宁可读出一个语义上略有偏差的字符串（交给
 * `fromPref` 之类回落默认值），也不要抛异常。
 */
private fun Preferences.stringOrNull(name: String): String? = when (val value = valueByName(name)) {
    null -> null
    is String -> value
    is Boolean, is Int, is Long, is Float, is Double -> value.toString()
    else -> null
}

/** 类型宽容的布尔读取：兼容 `"true"` / `"1"` 这类以字符串存的旧值。 */
private fun Preferences.booleanOrNull(name: String): Boolean? = when (val value = valueByName(name)) {
    null -> null
    is Boolean -> value
    is String -> when (value.trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> null
    }
    is Number -> value.toInt() != 0
    else -> null
}

/** 类型宽容的浮点读取：旧版本可能把它写成字符串或整数。 */
private fun Preferences.floatOrNull(name: String): Float? = when (val value = valueByName(name)) {
    null -> null
    is Float -> value
    is Number -> value.toFloat()
    is String -> value.trim().toFloatOrNull()
    else -> null
}

/**
 * 玻璃厚度的三级回落：新键（0~1）→ 旧四档字符串 → 更旧的无障碍布尔 → 默认值。
 *
 * 放在一处，`appSettings` 与 `snapshotSettings` 才不会各写一套判断而慢慢走偏。
 */
private fun Preferences.glassFrostOrDefault(): Float =
    floatOrNull(LegacySettingsKeys.GLASS_FROST)?.coerceIn(0f, 1f)
        ?: legacyGlassFrost(stringOrNull(LegacySettingsKeys.GLASS_MATERIAL))
        ?: if (booleanOrNull(LegacySettingsKeys.REDUCE_TRANSPARENCY) == true) 0.92f else GLASS_FROST_DEFAULT
