package com.morrowviento.morrow.ui.event

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.db.MorrowDatabase
import com.morrowviento.morrow.data.reminder.ReminderScheduler
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.data.undo.UndoRepository
import com.morrowviento.morrow.data.widget.WidgetRefreshTrigger
import com.morrowviento.morrow.data.widget.WidgetSnapshotBuilder
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventKind
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.domain.time.RepeatRule
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EventViewModelTest {
    @get:Rule val temp = TemporaryFolder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val viewModels = ViewModelStore()
    private lateinit var database: MorrowDatabase
    private lateinit var events: EventRepository
    private lateinit var undo: UndoRepository
    private lateinit var vm: EventViewModel
    private val notifications = CopyOnWriteArrayList<String>()
    private val cancelled = CopyOnWriteArrayList<String>()
    private val synced = CopyOnWriteArrayList<CountdownEvent>()
    private val refreshed = kotlinx.coroutines.CompletableDeferred<Unit>()

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MorrowDatabase::class.java)
            .allowMainThreadQueries().build()
        events = EventRepository(database.eventDao())
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            File(temp.root, "events.preferences_pb")
        })
        undo = UndoRepository(settings, events, scope)
        val reminders = object : ReminderScheduler {
            override fun sync(event: CountdownEvent) { synced += event }
            override fun syncAll(events: List<CountdownEvent>) { synced.addAll(events) }
            override fun cancel(eventId: String) { cancelled += eventId }
            override fun snooze(event: CountdownEvent, targetAtMillis: Long, minutes: Int) = Unit
        }
        vm = EventViewModel(events, settings, undo, reminders, object : WidgetRefreshTrigger {
            override suspend fun refreshAll() { refreshed.complete(Unit) }
        }, notifications::add)
        viewModels.put("vm", vm)
        scope.launch { vm.state.collect() }
    }

    @After fun tearDown(): Unit = runBlocking {
        viewModels.clear()
        job.cancelAndJoin()
        database.close()
        Dispatchers.resetMain()
    }

    private fun monthly(id: String = "monthly") = CountdownEvent(
        id = id, title = id,
        targetDate = YearMonth.now().plusMonths(1).atEndOfMonth().atTime(12, 0),
        repeatType = RepeatType.MONTHLY, repeatDay = 31,
        kind = EventKind.ANNIVERSARY,
        createdAt = LocalDateTime.of(2020, 1, 1, 12, 0),
    )

    private suspend fun card(id: String, predicate: (EventCardModel) -> Boolean = { true }): EventCardModel =
        withTimeout(10_000) {
            vm.state.first { state -> (state.active + state.completed).any { it.id == id && predicate(it) } }
                .let { (it.active + it.completed).first { item -> item.id == id } }
        }

    @Test fun queuedDoubleAdvanceHandlesOneCycleAndUndoRestoresAnchor(): Unit = runBlocking {
        val original = monthly()
        events.save(original)
        val before = card(original.id)
        events.withMutation {
            vm.toggleEventCompleted(original.id)
            vm.toggleEventCompleted(original.id)
        }
        val advanced = RepeatRule.applyPrimaryAction(original, LocalDate.now(), before.target.toLocalDate())!!
        card(original.id) { it.target > before.target }
        events.withMutation {
            assertEquals(advanced, events.findById(original.id))
            assertEquals(listOf(original), undo.pending.value!!.snapshot)
            assertEquals(listOf(original.id), notifications.toList())
            assertEquals(listOf(original.id), cancelled.toList())
        }
        val widget = WidgetSnapshotBuilder.toRow(advanced, LocalDate.now(), LocalDateTime.now())
        assertEquals(card(original.id).target, widget.target)
        assertEquals(-1, widget.progressPercent)
        withTimeout(10_000) { refreshed.await() }
        vm.undoLast()
        card(original.id) { it.target == before.target }
        events.withMutation { assertEquals(original, events.findById(original.id)) }
        assertNull(undo.pending.value)
    }

    @Test fun editReadsLatestStateAndDeletedFormCannotResurrectEvent(): Unit = runBlocking {
        val original = monthly().copy(kind = EventKind.TODO)
        events.save(original)
        val staleForm = vm.formFor(original.id)!!
        val latest = original.copy(isPinned = true, isCompleted = true, lastHandledOccurrence = original.dateOnly)
        events.save(latest)
        vm.save(staleForm.copy(title = "edited"))
        card(original.id) { it.title == "edited" }
        events.withMutation { assertEquals(latest.copy(title = "edited"), events.findById(original.id)) }
        val deletedForm = vm.formFor(original.id)!!
        events.delete(original.id)
        events.withMutation { vm.save(deletedForm.copy(title = "must not return")) }
        events.withMutation { assertNull(events.findById(original.id)) }
    }

    @Test fun anniversaryDoesNotCompleteAndBatchClearCancelsNotifications(): Unit = runBlocking {
        val anniversary = monthly("anniversary").copy(repeatType = RepeatType.NONE, repeatDay = null)
        val completed = monthly("completed").copy(kind = EventKind.TODO, isCompleted = true)
        events.save(anniversary)
        events.save(completed)
        card(anniversary.id)
        events.withMutation { vm.toggleEventCompleted(anniversary.id) }
        events.withMutation { assertEquals(anniversary, events.findById(anniversary.id)) }
        events.withMutation { vm.clearCompleted() }
        events.withMutation {
            assertNull(events.findById(completed.id))
            assertEquals(listOf(completed.id), notifications.toList())
            assertEquals(anniversary, events.findById(anniversary.id))
        }
        events.withMutation { vm.clearAll() }
        events.withMutation {
            assertTrue(events.loadAll().isEmpty())
            assertEquals(setOf(completed.id, anniversary.id), notifications.toSet())
            assertEquals(setOf(completed.id, anniversary.id), cancelled.toSet())
        }
    }
}
