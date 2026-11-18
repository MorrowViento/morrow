package com.morrowviento.morrow.widget

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.morrowviento.morrow.MorrowApp
import com.morrowviento.morrow.R
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.db.MorrowDatabase
import com.morrowviento.morrow.data.settings.AccentColor
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.data.settings.ThemeMode
import com.morrowviento.morrow.domain.model.CountdownEvent
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetListSnapshotTest {
    @get:Rule val temp = TemporaryFolder()
    private val job = SupervisorJob()
    private lateinit var context: Context
    private lateinit var db: MorrowDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var events: EventRepository

    @Before fun setUp() = runBlocking {
        val base = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(base, MorrowDatabase::class.java).allowMainThreadQueries().build()
        events = EventRepository(db.eventDao())
        settings = SettingsRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO)) { File(temp.root, "list.preferences_pb") })
        // 只注入工厂读取的两个仓库，避免启动真实 Application 的迁移/提醒后台任务。
        val app = MorrowApp()
        MorrowApp::class.java.getDeclaredField("eventRepository").apply { isAccessible = true }.set(app, events)
        MorrowApp::class.java.getDeclaredField("settingsRepository").apply { isAccessible = true }.set(app, settings)
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = app
        }
        val now = LocalDateTime.now()
        events.save(CountdownEvent("event", "测试事件", now.plusDays(10), createdAt = now))
    }

    @After fun tearDown() = runBlocking {
        job.cancelAndJoin()
        db.close()
    }

    @Test fun rowsKeepRefreshAppearanceAfterSettingsChangeAndInflateNatively() = runBlocking {
        val style = WidgetStyle(fontScale = WidgetFontScale.LARGE, showDate = false)
        val intent = Intent().putExtra(WidgetListService.EXTRA_STYLE, WidgetStyleCodec.encode(style))
            .putExtra(WidgetListService.EXTRA_DARK, true)
            .putExtra(WidgetListService.EXTRA_ACCENT, AccentColor.ROSE.dark.toInt())
            .putExtra(WidgetListService.EXTRA_FROST, 0f)
            .putExtra(WidgetListService.EXTRA_OPAQUE, false)
        settings.setThemeMode(ThemeMode.LIGHT)
        val factory = WidgetListFactory(context, 8, intent)
        factory.onDataSetChanged()
        assertEquals(1, factory.getCount())
        val first = factory.getViewAt(0).apply(context, FrameLayout(context))
        assertEquals(WidgetStyle.WHITE, first.findViewById<TextView>(R.id.widget_row_title).currentTextColor)
        assertEquals("测试事件", first.findViewById<TextView>(R.id.widget_row_title).text.toString())
        assertNotNull(first.findViewById<ImageView>(R.id.widget_row_bg).colorFilter)
        assertNotNull(first.findViewById<ImageView>(R.id.widget_row_edge).colorFilter)
        assertTrue(first.findViewById<TextView>(R.id.widget_row_title).textSize >= 17f)
        settings.setThemeMode(ThemeMode.DARK)
        factory.onDataSetChanged()
        val next = factory.getViewAt(0).apply(context, FrameLayout(context))
        assertEquals(WidgetStyle.WHITE, next.findViewById<TextView>(R.id.widget_row_title).currentTextColor)
        assertEquals(factory.getItemId(0), factory.getItemId(0))
        assertTrue(factory.hasStableIds())
        factory.onDestroy()
        assertEquals(0, factory.getCount())
    }

    @Test fun legacyAdapterFallsBackToApplicationTheme() = runBlocking {
        settings.setThemeMode(ThemeMode.DARK)
        val factory = WidgetListFactory(context, 9, Intent())
        factory.onDataSetChanged()
        val row = factory.getViewAt(0).apply(context, FrameLayout(context))
        assertEquals(WidgetStyle.WHITE, row.findViewById<TextView>(R.id.widget_row_title).currentTextColor)
        assertNotNull(factory.getViewAt(99).apply(context, FrameLayout(context)))
    }
}
