package com.morrowviento.morrow.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.morrowviento.morrow.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 「事件绑定属于小部件实例」的回归测试。
 *
 * 真机反馈：单事件小部件加进来时选不了事件。除了配置台指错，还有这一层——
 * `WidgetConfigStore` 把「上一次保存的样式」当默认样式，而那份样式里带着 `eventId`，
 * 于是新建的小部件一上来就绑在前一个小部件的事件上，面板上"已选"落在错的行。
 *
 * 锁两个方向：按 id 读得到绑定；按默认样式读**永远**读不到绑定。
 */
class WidgetBindingIsolationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun openStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(temp.root, "settings.preferences_pb")
        }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `按 id 读取保留自己的绑定`() = runBlocking {
        val store = WidgetConfigStore(SettingsRepository(openStore()))
        store.save(7, WidgetStyle(eventId = "event-a"))

        assertEquals("event-a", store.load(7).eventId)
    }

    @Test
    fun `默认样式不带任何绑定`() = runBlocking {
        val store = WidgetConfigStore(SettingsRepository(openStore()))
        store.save(7, WidgetStyle(eventId = "event-a"))

        assertNull("默认样式会被别的小部件实例读走，带绑定就是串事件", store.defaultStyle().eventId)
    }

    @Test
    fun `没配置过的小部件读不到别人的绑定`() = runBlocking {
        val store = WidgetConfigStore(SettingsRepository(openStore()))
        store.save(7, WidgetStyle(eventId = "event-a"))

        // 8 号是刚添加、还没配置过的小部件
        assertNull(store.load(8).eventId)
    }

    @Test
    fun `默认样式里的其他字段仍然要保留`() = runBlocking {
        val store = WidgetConfigStore(SettingsRepository(openStore()))
        store.save(7, WidgetStyle(eventId = "event-a", showProgress = false))

        val defaults = store.defaultStyle()
        assertEquals(false, defaults.showProgress)
    }
}
