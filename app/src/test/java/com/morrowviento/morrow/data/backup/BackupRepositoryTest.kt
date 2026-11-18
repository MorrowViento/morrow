package com.morrowviento.morrow.data.backup

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.withTransaction
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.category.CategoryEdit
import com.morrowviento.morrow.data.category.CategoryRepository
import com.morrowviento.morrow.data.db.MorrowDatabase
import com.morrowviento.morrow.data.settings.AppSettings
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.data.undo.UndoRepository
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.UndoKind
import com.morrowviento.morrow.domain.model.UndoOperation
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BackupRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val storeJob = SupervisorJob()
    private val storeScope = CoroutineScope(storeJob + Dispatchers.IO)
    private lateinit var database: MorrowDatabase
    private lateinit var events: EventRepository
    private lateinit var store: DataStore<Preferences>
    private lateinit var settings: SettingsRepository
    private val now = 1_000_000L

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MorrowDatabase::class.java)
            .allowMainThreadQueries().build()
        events = EventRepository(database.eventDao())
        store = PreferenceDataStoreFactory.create(scope = storeScope) { File(temp.root, "backup.preferences_pb") }
        settings = SettingsRepository(store)
    }

    @After fun tearDown() = runBlocking {
        storeJob.cancelAndJoin()
        database.close()
    }

    private fun event(id: String) = CountdownEvent(
        id = id, title = id, targetDate = LocalDateTime.of(2027, 1, 1, 12, 0),
        createdAt = LocalDateTime.of(2026, 1, 1, 12, 0),
    )

    private fun document(vararg items: CountdownEvent) = BackupDocument(
        exportedAt = "", appVersion = "test", events = items.map(BackupMapper::of),
        categories = listOf("生活"), settings = BackupMapper.ofSettings(AppSettings()), widgets = emptyList(),
    )

    private fun operation(item: CountdownEvent) = UndoOperation(
        id = "old", kind = UndoKind.DELETE, message = "deleted", snapshot = listOf(item),
        expiresAtMillis = now + UndoOperation.WINDOW_MILLIS, pushedAtMillis = now,
    )

    @Test fun versionOneDatabaseMigratesWithoutLosingOldStatesAndCanReopenNewFields(): Unit = runBlocking {
        val path = File(temp.root, "upgrade.db")
        val original = event("legacy").copy(isCompleted = true, isPinned = true,
            repeatType = com.morrowviento.morrow.domain.model.RepeatType.YEARLY,
            repeatMonth = 1, repeatDay = 1, note = "keep old data")
        val row = com.morrowviento.morrow.data.db.EventMapper.toEntity(original)
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE events (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, target_at INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL, icon TEXT NOT NULL, direction TEXT NOT NULL, reminders TEXT NOT NULL, is_all_day INTEGER NOT NULL, is_completed INTEGER NOT NULL, is_pinned INTEGER NOT NULL, repeat_type TEXT NOT NULL, repeat_month INTEGER, repeat_day INTEGER, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42, '03ae315c69d2ee36900cb6cec37d2823')")
            db.execSQL("INSERT INTO events VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                row.id, row.title, row.targetAt, row.category, row.note, row.icon, row.direction,
                row.reminders, 0, 1, 1, row.repeatType, row.repeatMonth, row.repeatDay, row.createdAt))
            db.version = 1
        }
        fun open() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MorrowDatabase::class.java, path.absolutePath)
            .addMigrations(
                MorrowDatabase.MIGRATION_1_2,
                MorrowDatabase.MIGRATION_2_3,
                MorrowDatabase.MIGRATION_3_4,
                MorrowDatabase.MIGRATION_4_5,
            )
            .allowMainThreadQueries().build()
        val updated = original.copy(isCompleted = false,
            kind = com.morrowviento.morrow.domain.model.EventKind.ANNIVERSARY,
            repeatType = com.morrowviento.morrow.domain.model.RepeatType.MONTHLY, repeatMonth = null,
            lastHandledOccurrence = java.time.LocalDate.of(2027, 1, 1))
        val migrated = open()
        try {
            val repository = EventRepository(migrated.eventDao())
            assertEquals(original, repository.findById(original.id))
            // 4.2.x 补丁：升级到 schema 5（标签 / 归档 / 时长 + 结束时刻），1→5 一步到位也要能过
            assertEquals(5, migrated.openHelper.writableDatabase.version)
            repository.save(updated)
        } finally { migrated.close() }
        val reopened = open()
        try { assertEquals(updated, EventRepository(reopened.eventDao()).findById(original.id)) }
        finally { reopened.close() }
    }

    @Test fun replaceRollsBackWhenAnInsertFails() = runTest {
        val undo = UndoRepository(settings, events, backgroundScope) { now }
        events.save(event("original"))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_bad BEFORE INSERT ON events WHEN NEW.id = 'bad' BEGIN SELECT RAISE(ABORT, 'test failure'); END",
        )
        val failure = runCatching {
            BackupRepository(settings, events, undo).import(document(event("good"), event("bad")), ImportMode.REPLACE, 0)
        }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(listOf("original"), events.loadAll().map { it.id })
    }

    @Test fun cancelledRoomTransactionDoesNotLeaveAnEmptyTable() = runTest {
        events.save(event("original"))
        try {
            database.withTransaction {
                events.replaceAll(listOf(event("new")))
                throw CancellationException("cancel transaction")
            }
        } catch (_: CancellationException) {
            // Room must roll back the outer transaction, including the nested replaceAll.
        }
        assertEquals(listOf("original"), events.loadAll().map { it.id })
    }

    @Test fun importInvalidatesMemoryAndPersistedUndo() = runTest {
        val undo = UndoRepository(settings, events, backgroundScope) { now }
        undo.push(operation(event("deleted")))
        BackupRepository(settings, events, undo).import(document(event("new")), ImportMode.REPLACE, 0)
        assertNull(undo.pending.value)
        assertNull(settings.stringOnce(UndoRepository.KEY_PENDING))
        assertFalse(undo.undo())
        val reopened = UndoRepository(settings, events, backgroundScope) { now }
        reopened.restore()
        assertNull(reopened.pending.value)
        assertEquals(listOf("new"), events.loadAll().map { it.id })
    }

    @Test fun mergeDoesNotRewriteOtherRowsOrAppearance() = runTest {
        val undo = UndoRepository(settings, events, backgroundScope) { now }
        val local = event("local").copy(isPinned = true, note = "keep")
        events.restore(listOf(local, event("shared")))
        settings.setReduceMotion(true)
        BackupRepository(settings, events, undo).import(document(event("shared").copy(title = "incoming")), ImportMode.MERGE, 0)
        assertEquals(local, events.findById("local"))
        assertEquals("incoming", events.findById("shared")!!.title)
        assertTrue(settings.snapshotSettings().reduceMotion)
    }

    @Test fun failureToClearPersistedUndoBlocksReplacement() = runTest {
        events.save(event("original"))
        val undo = UndoRepository(settings, events, backgroundScope) { now }
        undo.push(operation(event("deleted")))
        val failing = SettingsRepository(object : DataStore<Preferences> by store {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("storage unavailable")
        })
        val failingUndo = UndoRepository(failing, events, backgroundScope) { now }
        assertTrue(runCatching {
            BackupRepository(failing, events, failingUndo).import(document(event("new")), ImportMode.REPLACE, 0)
        }.exceptionOrNull() is IOException)
        assertEquals(listOf("original"), events.loadAll().map { it.id })
        assertNotNull(settings.stringOnce(UndoRepository.KEY_PENDING))
    }

    @Test fun lateDeleteSnapshotCannotCrossImportBoundary() = runTest {
        val undo = UndoRepository(settings, events, backgroundScope) { now }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val deleting = launch {
            events.withMutation {
                entered.complete(Unit)
                release.await()
                undo.push(operation(event("deleted")))
            }
        }
        entered.await()
        val importing = async {
            BackupRepository(settings, events, undo).import(document(event("new")), ImportMode.REPLACE, 0)
        }
        assertFalse(importing.isCompleted)
        release.complete(Unit)
        deleting.join()
        importing.await()
        assertNull(undo.pending.value)
        assertFalse(undo.undo())
        assertEquals(listOf("new"), events.loadAll().map { it.id })
    }

    @Test fun clearAllInvalidatesUndoAndRunsReconcileWithPreviousRows() = runTest {
        val undo = UndoRepository(settings, events, backgroundScope) { now }
        events.save(event("original"))
        undo.push(operation(event("deleted")))
        var previous = emptyList<CountdownEvent>()
        BackupRepository(settings, events, undo).clearAllData { previous = it }
        assertEquals(listOf("original"), previous.map { it.id })
        assertEquals(0, events.count())
        assertFalse(undo.undo())
        assertNull(settings.stringOnce(UndoRepository.KEY_PENDING))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun homeCalendarAndWidgetObserverShareOneEventSubscription() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val starts = java.util.concurrent.atomic.AtomicInteger()
        val delegate = database.eventDao()
        val observed = EventRepository(object : com.morrowviento.morrow.data.db.EventDao by delegate {
            override fun observeAll() = kotlinx.coroutines.flow.flow<List<com.morrowviento.morrow.data.db.EventEntity>> {
                starts.incrementAndGet()
                emit(emptyList())
                kotlinx.coroutines.awaitCancellation()
            }
        })
        val viewModels = androidx.lifecycle.ViewModelStore()
        try {
            val vm = eventViewModel(observed)
            viewModels.put("event", vm)
            kotlinx.coroutines.withTimeout(10_000) {
                vm.state.first { !it.isLoading }
                vm.calendarStrip.first { !it.current.isLoading }
            }
            assertEquals(1, starts.get())
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun sharedUpstreamErrorReachesBothHomeAndCalendar() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val delegate = database.eventDao()
        val observed = EventRepository(object : com.morrowviento.morrow.data.db.EventDao by delegate {
            override fun observeAll() = kotlinx.coroutines.flow.flow<List<com.morrowviento.morrow.data.db.EventEntity>> {
                throw IOException("database unavailable")
            }
        })
        val viewModels = androidx.lifecycle.ViewModelStore()
        try {
            val vm = eventViewModel(observed)
            viewModels.put("event", vm)
            kotlinx.coroutines.withTimeout(10_000) {
                assertEquals("database unavailable", vm.state.first { it.error != null }.error)
                assertEquals("database unavailable", vm.calendarStrip.first { it.current.error != null }.current.error)
            }
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

    private fun eventViewModel(observed: EventRepository): com.morrowviento.morrow.ui.event.EventViewModel {
        val undo = UndoRepository(settings, observed, storeScope)
        val reminders = object : com.morrowviento.morrow.data.reminder.ReminderScheduler {
            override fun sync(event: CountdownEvent) = Unit
            override fun syncAll(events: List<CountdownEvent>) = Unit
            override fun cancel(eventId: String) = Unit
            override fun snooze(event: CountdownEvent, targetAtMillis: Long, minutes: Int) = Unit
        }
        return com.morrowviento.morrow.ui.event.EventViewModel(observed, settings, undo, reminders)
    }

    @Test fun categoryRenameInvalidatesSnapshotsWithOldCategory() = runTest {
        val undo = UndoRepository(settings, events, backgroundScope) { now }
        events.save(event("original"))
        undo.push(operation(event("original")))
        val result = CategoryRepository(settings, events, undo).rename("生活", "日常")
        assertTrue(result is CategoryEdit.Ok)
        assertEquals("日常", events.findById("original")!!.category)
        assertFalse(undo.undo())
    }
}
