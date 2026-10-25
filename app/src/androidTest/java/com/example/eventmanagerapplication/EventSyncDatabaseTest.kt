package com.example.eventmanagerapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventSyncDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "sync-test.db"
    private lateinit var database: EventDatabaseHelper
    private val event = Event(name = "Workshop", date = "20/10/2026", location = "Hall",
        organiser = "Alex", type = "Workshop", status = "Upcoming", amount = 25.0,
        description = "မြန်မာ", contactEmail = "alex@example.com", contactPhone = "+95 912345678")

    @Before fun open() {
        context.deleteDatabase(databaseName)
        database = EventDatabaseHelper(context, databaseName)
    }

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun snapshotContainsAllEventsAndSessionsRegardlessOfSearch() {
        val firstId = database.insert(event)
        database.insert(event.copy(name = "Other event"))
        val sessionId = database.insertSession(Session(eventId = firstId, name = "Opening",
            time = "09:00", type = "Presentation", speaker = "Alex", room = "A", description = "Agenda"))
        assertEquals(1, database.searchEvents("Other").size)
        val snapshot = database.uploadSnapshot()
        val json = snapshot.toJson()
        assertEquals(2, json.getJSONArray("events").length())
        val uploaded = snapshot.events.single { it.event.id == firstId }
        assertEquals(sessionId, uploaded.sessions.single().id)
        val first = json.getJSONArray("events").let { events ->
            (0 until events.length()).map { events.getJSONObject(it) }.single { it.getLong("id") == firstId }
        }
        assertEquals("မြန်မာ", first.getString("description"))
        assertEquals("alex@example.com", first.getString("contactEmail"))
        assertEquals(firstId, first.getJSONArray("sessions").getJSONObject(0).getLong("eventId"))
        assertFalse(first.has("sessionCount"))
    }

    @Test fun acknowledgingAnOlderSnapshotLeavesNewChangesPending() {
        val id = database.insert(event)
        val uploading = database.uploadSnapshot()
        database.update(event.copy(id = id, description = "Changed during upload"))
        database.markUploaded(uploading.revision)
        val state = database.syncState()
        assertTrue(state.needsUpload)
        assertEquals(uploading.revision, state.uploadedRevision)
        database.markUploaded(state.revision)
        assertFalse(database.syncState().needsUpload)
        database.markUploaded(uploading.revision)
        assertFalse(database.syncState().needsUpload)
    }

    @Test fun sessionEditsAndDeletionsAdvanceRevisionAndEmptySnapshotIsRetained() {
        val id = database.insert(event)
        val before = database.syncState().revision
        val session = Session(eventId = id, name = "Opening", time = "09:00", type = "Presentation")
        val sessionId = database.insertSession(session)
        val afterInsert = database.syncState().revision
        assertTrue(afterInsert > before)
        database.updateSession(session.copy(id = sessionId, name = "Updated"))
        assertTrue(database.syncState().revision > afterInsert)
        database.deleteSession(id, sessionId)
        assertTrue(database.uploadSnapshot().events.single().sessions.isEmpty())
        database.delete(id)
        assertTrue(database.uploadSnapshot().events.isEmpty())
        assertTrue(database.syncState().needsUpload)
    }

    @Test fun identityRevisionAndAutomaticSyncSurviveReopening() {
        database.enableSync()
        database.insert(event)
        val before = database.syncState()
        database.close()
        database = EventDatabaseHelper(context, databaseName)
        assertEquals(before, database.syncState())
        assertTrue(before.enabled)
        assertTrue(before.deviceId.matches(Regex("[A-Za-z0-9_-]{16,64}")))
    }

    @Test fun versionThreeUpgradeKeepsExistingEventsAndCreatesPendingSnapshot() {
        database.close()
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, 0, null).use { db ->
            db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, date TEXT NOT NULL, location TEXT NOT NULL, organiser TEXT NOT NULL, type TEXT NOT NULL, status TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL DEFAULT '', contact_email TEXT NOT NULL DEFAULT '', contact_phone TEXT NOT NULL DEFAULT '')")
            db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, event_id INTEGER NOT NULL REFERENCES events(id) ON DELETE CASCADE, name TEXT NOT NULL, time TEXT NOT NULL, type TEXT NOT NULL, speaker TEXT NOT NULL DEFAULT '', room TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT '')")
            db.execSQL("INSERT INTO events (name,date,location,organiser,type,status,amount) VALUES ('Legacy','20/10/2026','Hall','Alex','Workshop','Upcoming',0)")
            db.version = 3
        }
        database = EventDatabaseHelper(context, databaseName)
        assertEquals("Legacy", database.uploadSnapshot().events.single().event.name)
        assertTrue(database.syncState().needsUpload)
        assertFalse(database.syncState().enabled)
    }
}
