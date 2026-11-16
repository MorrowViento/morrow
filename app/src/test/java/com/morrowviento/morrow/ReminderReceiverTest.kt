package com.morrowviento.morrow

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.morrowviento.morrow.data.EventRepository
import com.morrowviento.morrow.data.db.MorrowDatabase
import com.morrowviento.morrow.data.reminder.ReminderScheduler
import com.morrowviento.morrow.data.settings.SettingsRepository
import com.morrowviento.morrow.data.undo.UndoRepository
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventKind
import com.morrowviento.morrow.domain.model.RepeatType
import com.morrowviento.morrow.reminder.ReminderNotifier
import com.morrowviento.morrow.reminder.ReminderReceiver
import com.morrowviento.morrow.widget.WidgetRefresher
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ReminderReceiverTest {
    @get:Rule val temp = TemporaryFolder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val app = MorrowApp()
    private val receiver = ReminderReceiver()
    private lateinit var database: MorrowDatabase
    private lateinit var events: EventRepository
    private lateinit var undo: UndoRepository
    private lateinit var notifier: ReminderNotifier
    private lateinit var manager: NotificationManager
    private val synced = mutableListOf<CountdownEvent>()
    private val cancelled = mutableListOf<String>()
    private val snoozed = mutableListOf<Pair<CountdownEvent, Long>>()

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        database = Room.inMemoryDatabaseBuilder(context, MorrowDatabase::class.java).allowMainThreadQueries().build()
        events = EventRepository(database.eventDao())
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            File(temp.root, "receiver.preferences_pb")
        })
        undo = UndoRepository(settings, events, scope)
        notifier = ReminderNotifier(context).also { it.ensureChannel() }
        manager = context.getSystemService(NotificationManager::class.java)
        val scheduler = object : ReminderScheduler {
            override fun sync(event: CountdownEvent) { synced += event }
            override fun syncAll(events: List<CountdownEvent>) { synced.addAll(events) }
            override fun cancel(eventId: String) { cancelled += eventId }
            override fun snooze(event: CountdownEvent, targetAtMillis: Long, minutes: Int) {
                assertEquals(10, minutes)
                snoozed += event to targetAtMillis
            }
        }
        // Only wire the receiver dependencies; do not launch unrelated application startup jobs.
        mapOf("eventRepository" to events, "settingsRepository" to settings, "undoRepository" to undo,
            "reminderNotifier" to notifier, "reminderScheduler" to scheduler,
            "widgetRefresher" to WidgetRefresher(context, settings, events)).forEach { (name, value) ->
            MorrowApp::class.java.getDeclaredField(name).apply { isAccessible = true }.set(app, value)
        }
    }

    @After fun tearDown(): Unit = runBlocking {
        job.cancelAndJoin()
        database.close()
        manager.cancelAll()
    }

    private fun event() = CountdownEvent(
        id = "legacy-reminder", title = "current title", kind = EventKind.TODO,
        targetDate = LocalDate.now().plusDays(1).atTime(12, 0), repeatType = RepeatType.MONTHLY,
        createdAt = LocalDate.of(2020, 1, 1).atStartOfDay(),
    )

    private fun target(event: CountdownEvent) = event.reminderAnchor.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun intent(event: CountdownEvent) = Intent().putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
        .putExtra(ReminderReceiver.EXTRA_TARGET, target(event))
        .putExtra(ReminderReceiver.EXTRA_TITLE, event.title)
        .putExtra(ReminderReceiver.EXTRA_IS_ALL_DAY, event.isAllDay)
        .putExtra(ReminderReceiver.EXTRA_PRIMARY_ACTION, event.primaryAction.name)

    // Exercise the actual suspend handlers with Room, DataStore and NotificationManager.
    // This is not an assertion about Android's goAsync timeout or system alarm delivery.
    private suspend fun handle(name: String, event: CountdownEvent, intent: Intent): Unit =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val method = ReminderReceiver::class.java.declaredMethods.single { it.name == name }.apply { isAccessible = true }
            if (name == "fire") method.invoke(receiver, app, event.id, intent, System.currentTimeMillis(), continuation)
            else method.invoke(receiver, app, event.id, intent, continuation)
        }

    private fun notification(event: CountdownEvent): Notification? =
        shadowOf(manager).getNotification(event.id.hashCode() and 0x7fffffff)

    @Test fun legacyFireWithoutActionKeepsValidNotificationAndUsesCurrentContent(): Unit = runBlocking {
        val event = event()
        events.save(event)
        val legacy = intent(event).putExtra(ReminderReceiver.EXTRA_TITLE, "outdated title")
        legacy.removeExtra(ReminderReceiver.EXTRA_PRIMARY_ACTION)
        handle("fire", event, legacy)
        val shown = notification(event)!!
        assertEquals(event.title, shown.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("进入下一月", shown.actions.first().title.toString())
        assertEquals(listOf(event), synced)
        assertTrue(cancelled.isEmpty())
        assertEquals(event, events.findById(event.id))
    }

    @Test fun legacyFireForHandledCycleCannotLeaveOldNotification(): Unit = runBlocking {
        val original = event()
        val handled = original.copy(lastHandledOccurrence = original.dateOnly)
        events.save(handled)
        val legacy = intent(original)
        legacy.removeExtra(ReminderReceiver.EXTRA_PRIMARY_ACTION)
        handle("fire", original, legacy)
        assertNull(notification(original))
        assertEquals(listOf(handled), synced)
    }

    @Test fun repeatedCompleteIntentAdvancesOnceAndRetainsUndoSnapshot(): Unit = runBlocking {
        val original = event()
        events.save(original)
        handle("complete", original, intent(original))
        handle("complete", original, intent(original))
        val updated = events.findById(original.id)!!
        assertEquals(original.targetDate, updated.targetDate)
        assertEquals(original.dateOnly, updated.lastHandledOccurrence)
        assertFalse(updated.isCompleted)
        assertEquals(listOf(original), undo.pending.value!!.snapshot)
        assertEquals(listOf(original.id), cancelled)
        assertEquals(listOf(updated), synced)
    }

    @Test fun staleSnoozeCannotCancelCurrentNotificationAndValidSnoozeKeepsCycle(): Unit = runBlocking {
        val original = event()
        val handled = original.copy(lastHandledOccurrence = original.dateOnly)
        events.save(handled)
        notifier.show(handled, 0, target(original), System.currentTimeMillis())
        handle("snooze", original, intent(original))
        assertNotNull(notification(original))
        assertTrue(snoozed.isEmpty())
        events.save(original)
        handle("snooze", original, intent(original))
        assertNull(notification(original))
        assertEquals(listOf(original to target(original)), snoozed)
    }
}
