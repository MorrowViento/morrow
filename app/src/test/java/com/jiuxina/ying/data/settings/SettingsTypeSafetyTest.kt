package com.jiuxina.ying.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jiuxina.ying.widget.WidgetConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 旧版偏好项**类型错配**的回归测试。
 *
 * 真机事故：M1 的迁移用 `putRaw` 原样保留了旧键的类型，而 Flutter 版的
 * `widget_show_progress` 等键是 Boolean；M5 的 `WidgetConfigStore` 却按 String 读，
 * DataStore 的 `Key` 只按名字比较、不校验类型，于是抛
 * `ClassCastException: Boolean cannot be cast to String`。
 * 那一次发生在 `viewModelScope` 里没人接住，直接杀进程——用户看到的是
 * 「点保存后退回桌面，重开记录还在」。
 */
class SettingsTypeSafetyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun openStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            // 必须指向尚不存在的文件：DataStore 不接受「已存在但内容为空」的旧文件
            File(temp.root, "settings.preferences_pb")
        }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** 旧键存成 Boolean 时，按字符串读不能抛异常，而要拿到可用的文本。 */
    @Test
    fun booleanValueReadAsStringDoesNotThrow() = runBlocking {
        val store = openStore()
        store.edit {
            it[booleanPreferencesKey("widget_show_progress")] = true
            it[booleanPreferencesKey(MigrationKeys.DONE_V1)] = true
        }
        val settings = SettingsRepository(store)

        // 崩点原样复现：M5 读小部件旧键、M1 判断迁移是否已完成，两者都在这里
        assertEquals("true", settings.stringOnce("widget_show_progress"))
        assertEquals("true", settings.rawString(MigrationKeys.DONE_V1).first())
    }

    /** 数字类型的旧值同样不能让设置读取炸掉。 */
    @Test
    fun numericValueReadAsStringDoesNotThrow() = runBlocking {
        val store = openStore()
        store.edit {
            it[intPreferencesKey(LegacySettingsKeys.THEME_MODE)] = 2
            it[intPreferencesKey(LegacySettingsKeys.REDUCE_MOTION)] = 1
        }
        val settings = SettingsRepository(store)

        assertEquals("2", settings.stringOnce(LegacySettingsKeys.THEME_MODE))
        // 主题取到无法识别的值 → 回落「跟随系统」，而不是崩
        assertEquals("system", settings.appSettings.first().themeMode.prefValue)
        // 数字 1 当布尔读 → true
        assertTrue(settings.appSettings.first().reduceMotion)
    }

    /** 缺键时保持原有语义：返回 null / 默认值。 */
    @Test
    fun missingKeysKeepDefaults() = runBlocking {
        val settings = SettingsRepository(openStore())

        assertEquals(null, settings.stringOnce("not_there"))
        assertEquals(null, settings.rawString("not_there").first())
        val defaults = settings.appSettings.first()
        assertEquals("system", defaults.themeMode.prefValue)
        assertFalse(defaults.reduceTransparency)
    }

    /** 崩溃点的完整复现路径：旧版布尔键存在时读默认小部件样式。 */
    @Test
    fun legacyWidgetDefaultsDoesNotCrash() = runBlocking {
        val store = openStore()
        store.edit {
            it[booleanPreferencesKey("widget_show_category")] = true
            it[booleanPreferencesKey("widget_show_date")] = true
            it[booleanPreferencesKey("widget_show_progress")] = false
            it[stringPreferencesKey("widget_style_preset")] = "minimal"
        }
        val config = WidgetConfigStore(SettingsRepository(store))

        val style = config.legacyDefaults()

        assertEquals("MINIMAL", style.preset.name)
        assertTrue("旧版显式开的分类开关应带过来", style.showCategory)
        assertTrue(style.showDate)
        assertFalse("旧版显式关的进度开关应带过来", style.showProgress)
    }

    /** 没有任何旧键时，取默认样式也不该出错。 */
    @Test
    fun legacyWidgetDefaultsWithoutLegacyKeys() = runBlocking {
        val config = WidgetConfigStore(SettingsRepository(openStore()))

        val style = config.legacyDefaults()

        assertNotNull(style)
        assertEquals("STANDARD", style.preset.name)
    }
}
