package com.morrowviento.morrow.ui.settings

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.backdrop.BackdropImage
import com.morrowviento.morrow.data.backup.BackupRepository
import com.morrowviento.morrow.data.db.MorrowDatabase
import com.morrowviento.morrow.data.reminder.ReminderPermissions
import com.morrowviento.morrow.data.reminder.ReminderScheduler
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.data.undo.UndoRepository
import com.morrowviento.morrow.data.widget.WidgetRefreshTrigger
import com.morrowviento.morrow.domain.model.CountdownEvent
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val viewModels = ViewModelStore()
    private lateinit var context: ContextWrapper
    private lateinit var database: MorrowDatabase
    private lateinit var events: EventRepository
    private lateinit var settings: SettingsRepository
    private lateinit var store: DataStore<Preferences>

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = temp.root
        }
        database = Room.inMemoryDatabaseBuilder(context, MorrowDatabase::class.java).allowMainThreadQueries().build()
        events = EventRepository(database.eventDao())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(temp.root, "vm.preferences_pb") }
        settings = SettingsRepository(store)
    }

    @After fun tearDown() = runBlocking {
        viewModels.clear()
        job.cancelAndJoin()
        database.close()
        BackdropImage.clear(context)
        Dispatchers.resetMain()
    }

    private fun source(): Uri {
        val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(40, 140, 220))
        val source = File(temp.root, "source.jpg")
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return Uri.fromFile(source)
    }

    private fun model(repository: SettingsRepository = settings, refresh: WidgetRefreshTrigger = WidgetRefreshTrigger.NONE): SettingsViewModel {
        val undo = UndoRepository(repository, events, scope)
        val reminders = object : ReminderScheduler {
            override fun sync(event: CountdownEvent) = Unit
            override fun syncAll(events: List<CountdownEvent>) = Unit
            override fun cancel(eventId: String) = Unit
            override fun snooze(event: CountdownEvent, targetAtMillis: Long, minutes: Int) = Unit
        }
        return SettingsViewModel(context, repository, BackupRepository(repository, events, undo), events,
            reminders, ReminderPermissions(context), refresh).also { viewModels.put("vm", it) }
    }

    private suspend fun finished(vm: SettingsViewModel): SettingsUiState =
        withTimeout(10_000) { vm.state.first { !it.busy && it.message != null } }

    private suspend fun prepared(vm: SettingsViewModel, uri: Uri, ratio: Float = 0.5f): PendingBackdrop {
        vm.prepareBackdrop(uri, ratio)
        return withTimeout(10_000) { vm.state.first { !it.busy && it.pendingBackdrop != null }.pendingBackdrop!! }
    }

    private suspend fun selectAndConfirm(vm: SettingsViewModel, uri: Uri) {
        val pending = prepared(vm, uri)
        vm.confirmBackdrop(pending.bitmap, com.morrowviento.morrow.data.backdrop.BackdropCrop.centered(
            pending.bitmap.width, pending.bitmap.height, pending.aspectRatio,
        ))
    }

    @Test fun preparationAndCancelDoNotPublishOrChangeSettings(): Unit = runBlocking {
        val vm = model()
        val before = settings.snapshotSettings()
        val pending = prepared(vm, source())
        assertEquals(before, settings.snapshotSettings())
        assertTrue(BackdropImage.dir(context).listFiles()!!.isEmpty())
        assertEquals(0.5f, pending.aspectRatio)
        vm.cancelBackdrop()
        withTimeout(10_000) { vm.state.first { it.pendingBackdrop == null } }
        assertFalse(pending.bitmap.isRecycled)
        assertEquals(before, settings.snapshotSettings())
        vm.confirmBackdrop(pending.bitmap, com.morrowviento.morrow.data.backdrop.BackdropCrop.centered(40, 20, 0.5f))
        withTimeout(10_000) { vm.state.first { !it.busy } }
        assertTrue(BackdropImage.dir(context).listFiles()!!.isEmpty())
    }

    @Test fun firstImportDefaultsToTintButReplacingImageRespectsDisabledTint() = runBlocking {
        val vm = model()
        val uri = source()
        selectAndConfirm(vm, uri)
        finished(vm)
        val first = settings.snapshotSettings().backdrop
        assertTrue(first.autoTint)
        assertTrue(first.hasImage)
        settings.setBackdrop(first.copy(autoTint = false))
        vm.consumeMessage()
        selectAndConfirm(vm, uri)
        finished(vm)
        val next = settings.snapshotSettings().backdrop
        assertFalse(next.autoTint)
        assertNotEquals(first.image, next.image)
        assertFalse(BackdropImage.file(context, first.image).exists())
        assertTrue(BackdropImage.file(context, next.image).isFile)
    }

    @Test fun failedSettingsCommitDeletesNewImageAndKeepsOldImage() = runBlocking {
        val uri = source()
        val old = BackdropImage.import(context, uri)
        settings.setBackdrop(com.morrowviento.morrow.data.settings.BackdropSettings(
            mode = com.morrowviento.morrow.data.settings.BackdropMode.IMAGE, image = old.name,
        ))
        val failing = SettingsRepository(object : DataStore<Preferences> by store {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("write failed")
        })
        val vm = model(failing)
        selectAndConfirm(vm, uri)
        assertTrue(finished(vm).message!!.startsWith("设置背景失败"))
        assertEquals(old.name, settings.snapshotSettings().backdrop.image)
        assertEquals(listOf(old.name), BackdropImage.dir(context).listFiles()!!.map { it.name })
    }

    @Test fun submittedInstallationReturnsToReadyAndCanBeDismissed(): Unit = runBlocking {
        val vm = model()
        val apk = File(temp.root, "ready.apk").apply { writeBytes(ByteArray(1024)) }
        // Seed the completed-download precondition; HTTP download itself has separate integration tests.
        SettingsViewModel::class.java.getDeclaredField("downloadedApk").apply { isAccessible = true }.set(vm, apk)
        vm.installUpdate()
        val submitted = withTimeout(10_000) {
            vm.state.first { !it.update.busy && it.update.stage != com.morrowviento.morrow.ui.update.UpdateStage.IDLE }
        }
        assertEquals(com.morrowviento.morrow.ui.update.UpdateStage.READY, submitted.update.stage)
        assertTrue(submitted.update.message!!.contains("系统界面确认"))
        vm.dismissUpdate()
        withTimeout(10_000) { vm.state.first { it.update.stage == com.morrowviento.morrow.ui.update.UpdateStage.IDLE } }
    }

    @Test fun glassSettingsRefreshWidgetsOnlyAfterCommittedSnapshot(): Unit = runBlocking {
        val snapshots = kotlinx.coroutines.channels.Channel<com.morrowviento.morrow.data.settings.AppSettings>(
            kotlinx.coroutines.channels.Channel.UNLIMITED)
        val vm = model(refresh = object : WidgetRefreshTrigger {
            override suspend fun refreshAll() { snapshots.send(settings.snapshotSettings()) }
        })
        for (level in com.morrowviento.morrow.data.settings.GlassFrostLevel.entries) {
            vm.setGlassFrostLevel(level)
            val committed = withTimeout(10_000) { snapshots.receive() }
            assertEquals(level.frost, committed.glassFrost, 0f)
            assertEquals(level.frost >= 0.85f, committed.usesOpaqueGlass)
        }
        vm.setReduceTransparency(false)
        assertFalse(withTimeout(10_000) { snapshots.receive() }.usesOpaqueGlass)
        vm.setReduceTransparency(true)
        assertTrue(withTimeout(10_000) { snapshots.receive() }.usesOpaqueGlass)
        snapshots.close()
    }

    @Test fun failedGlassSettingsWriteDoesNotRefreshWidgets() = runBlocking {
        val failing = SettingsRepository(object : DataStore<Preferences> by store {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("write failed")
        })
        var refreshes = 0
        val vm = model(failing, object : WidgetRefreshTrigger {
            override suspend fun refreshAll() { refreshes++ }
        })
        val before = settings.snapshotSettings()
        vm.setGlassFrostLevel(com.morrowviento.morrow.data.settings.GlassFrostLevel.HEAVY)
        assertTrue(finished(vm).message!!.startsWith("保存设置失败"))
        assertEquals(0, refreshes)
        assertEquals(before, settings.snapshotSettings())
        vm.consumeMessage()
        vm.setReduceTransparency(true)
        assertTrue(finished(vm).message!!.startsWith("保存设置失败"))
        assertEquals(0, refreshes)
        assertEquals(before, settings.snapshotSettings())
    }

    @Test fun widgetRefreshFailureCannotDeleteCommittedImage() = runBlocking {
        val vm = model(refresh = object : WidgetRefreshTrigger {
            override suspend fun refreshAll(): Unit = throw IOException("binder failed")
        })
        selectAndConfirm(vm, source())
        assertTrue(finished(vm).message!!.startsWith("背景已换"))
        val backdrop = settings.snapshotSettings().backdrop
        assertTrue(backdrop.hasImage)
        assertTrue(BackdropImage.file(context, backdrop.image).isFile)
    }
}
