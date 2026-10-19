package com.example.eventmanagerapplication

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "session-test.db"
    private lateinit var database: EventDatabaseHelper

    @Before fun openDatabase() {
        context.deleteDatabase(databaseName)
        database = EventDatabaseHelper(context, databaseName)
    }

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun event(name: String) = Event(name = name, date = "18/10/2026",
        location = "Hall", organiser = "Alex", type = "Conference", status = "Upcoming", amount = 0.0)

    @Test fun multipleSessionsPersistWithGeneratedIdsAndEditAllFields() {
        val eventId = database.insert(event("Conference"))
        val first = Session(eventId = eventId, name = "Opening", time = "09:00", type = "Keynote",
            speaker = "Alex", room = "Hall A", description = "Welcome everyone")
        val firstId = database.insertSession(first)
        val secondId = database.insertSession(Session(eventId = eventId, name = "Workshop",
            time = "10:00", type = "Workshop"))
        assertTrue(firstId > 0 && secondId > firstId)
        database.close()
        database = EventDatabaseHelper(context, databaseName)
        assertEquals(first.copy(id = firstId), database.getSessions(eventId).first())
        assertEquals(first.copy(id = firstId), database.getSession(eventId, firstId))
        val edited = first.copy(id = firstId, name = "Closing", time = "17:00",
            speaker = "Taylor", room = "Hall B", type = "Panel Discussion", description = "Q&A")
        assertEquals(1, database.updateSession(edited))
        assertEquals(listOf(secondId, firstId), database.getSessions(eventId).map { it.id })
        assertEquals(edited, database.getSessions(eventId).last())
        assertEquals(1, database.deleteSession(eventId, firstId))
        assertNull(database.getSession(eventId, firstId))
        assertEquals(listOf(secondId), database.getSessions(eventId).map { it.id })
        assertEquals(1, database.getEvent(eventId)!!.sessionCount)
    }

    @Test fun eventListsAreIsolatedCountsRefreshAndDeletingEventCascadesAfterReopen() {
        val firstEvent = database.insert(event("First event"))
        val secondEvent = database.insert(event("Second event"))
        assertEquals(0, database.getEvent(firstEvent)!!.sessionCount)
        val firstId = database.insertSession(Session(eventId = firstEvent, name = "Intro", time = "09:00", type = "Presentation"))
        database.insertSession(Session(eventId = firstEvent, name = "Meet", time = "10:00", type = "Networking"))
        val otherId = database.insertSession(Session(eventId = secondEvent, name = "Panel", time = "11:00", type = "Panel Discussion"))
        assertEquals(mapOf(firstEvent to 2, secondEvent to 1), database.getAll().associate { it.id to it.sessionCount })
        assertEquals(0, database.deleteSession(secondEvent, firstId))
        assertNull(database.getSession(secondEvent, firstId))
        assertEquals(0, database.updateSession(Session(id = firstId, eventId = secondEvent, name = "Wrong event", time = "12:00", type = "Others")))
        database.close()
        database = EventDatabaseHelper(context, databaseName)
        assertEquals(1, database.delete(firstEvent))
        assertTrue(database.getSessions(firstEvent).isEmpty())
        assertEquals(listOf(otherId), database.getSessions(secondEvent).map { it.id })
        assertEquals(1, database.getAll().single().sessionCount)
    }

    @Test fun rejectsOrphansAndInvalidRequiredFields() {
        val eventId = database.insert(event("Conference"))
        val valid = Session(eventId = eventId, name = "Intro", time = "00:00", type = "Others")
        try {
            database.insertSession(valid.copy(eventId = eventId + 100))
            fail("A session must have an existing parent event")
        } catch (_: SQLiteConstraintException) { }
        listOf(valid.copy(name = "  "), valid.copy(time = "24:00"), valid.copy(time = ""),
            valid.copy(type = "Unknown")).forEach { invalid ->
            try {
                database.insertSession(invalid)
                fail("Invalid required fields should be rejected")
            } catch (_: IllegalArgumentException) { }
        }
        assertTrue(database.getSessions(eventId).isEmpty())
    }

    @Test fun versionTwoUpgradePreservesEventsAndSupportsSessionCascade() {
        database.close()
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, 0, null).use { db ->
            db.execSQL("""CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL, date TEXT NOT NULL, location TEXT NOT NULL, organiser TEXT NOT NULL,
                type TEXT NOT NULL, status TEXT NOT NULL, amount REAL NOT NULL,
                description TEXT NOT NULL DEFAULT '', contact_email TEXT NOT NULL DEFAULT '',
                contact_phone TEXT NOT NULL DEFAULT '')""")
            db.execSQL("INSERT INTO events VALUES (7, 'Legacy', '18/10/2026', 'Hall', 'Alex', 'Conference', 'Upcoming', 0, 'Keep me', 'alex@example.com', '')")
            db.version = 2
        }
        database = EventDatabaseHelper(context, databaseName)
        val legacy = database.getAll().single()
        assertEquals(7L, legacy.id)
        assertEquals("Keep me", legacy.description)
        assertEquals("alex@example.com", legacy.contactEmail)
        assertEquals(0, legacy.sessionCount)
        database.insertSession(Session(eventId = legacy.id, name = "New session", time = "09:00", type = "Workshop"))
        assertEquals(1, database.getEvent(legacy.id)!!.sessionCount)
        database.delete(legacy.id)
        assertTrue(database.getSessions(legacy.id).isEmpty())
    }
}
