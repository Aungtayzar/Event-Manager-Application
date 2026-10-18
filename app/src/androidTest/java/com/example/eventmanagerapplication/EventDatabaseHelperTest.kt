package com.example.eventmanagerapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventDatabaseHelperTest {
    @Test
    fun savesUpdatesAndDeletesAcrossDatabaseReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("events-test.db")
        val original = Event(
            name = "Workshop",
            date = "10/10/2026",
            location = "Hall",
            organiser = "Alex",
            type = "Workshop",
            status = "Upcoming",
            amount = 25.0,
            description = "Hands-on session",
            contactEmail = "alex@example.com",
            contactPhone = "+95 912345678"
        )
        val firstDatabase = EventDatabaseHelper(context, "events-test.db")
        val id = firstDatabase.insert(original)
        firstDatabase.close()

        val secondDatabase = EventDatabaseHelper(context, "events-test.db")
        assertEquals(original.copy(id = id), secondDatabase.getAll().single())
        assertEquals(1, secondDatabase.update(original.copy(id = id, status = "Completed")))
        assertEquals("Completed", secondDatabase.getAll().single().status)
        assertEquals(1, secondDatabase.delete(id))
        assertTrue(secondDatabase.getAll().isEmpty())
        secondDatabase.close()
        context.deleteDatabase("events-test.db")
    }

    @Test
    fun duplicatesAreRejectedButUnchangedEditAndDifferentDateAreAllowed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "duplicates-test.db"
        context.deleteDatabase(name)
        EventDatabaseHelper(context, name).use { db ->
            val event = Event(name = "Workshop", date = "10/10/2026", location = "Hall",
                organiser = "Alex", type = "Workshop", status = "Upcoming", amount = 0.0)
            val id = db.insert(event)
            assertTrue(db.hasDuplicate(" workshop ", event.date))
            assertTrue(!db.hasDuplicate(event.name, event.date, id))
            assertEquals(1, db.update(event.copy(id = id, description = "Updated")))
            try {
                db.insert(event.copy(name = " WORKSHOP "))
                org.junit.Assert.fail("Duplicate insert should fail")
            } catch (_: IllegalArgumentException) { }
            val otherId = db.insert(event.copy(date = "11/10/2026"))
            try {
                db.update(event.copy(id = otherId))
                org.junit.Assert.fail("Duplicate update should fail")
            } catch (_: IllegalArgumentException) { }
            assertEquals(2, db.getAll().size)
        }
        context.deleteDatabase(name)
    }

    @Test
    fun versionOneUpgradePreservesEventsAndDefaultsOptionalFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { db ->
            db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, date TEXT NOT NULL, location TEXT NOT NULL, organiser TEXT NOT NULL, type TEXT NOT NULL, status TEXT NOT NULL, amount REAL NOT NULL)")
            db.execSQL("INSERT INTO events VALUES (1, 'Legacy', '10/10/2026', 'Hall', 'Alex', 'Workshop', 'Upcoming', 0)")
            db.version = 1
        }
        EventDatabaseHelper(context, name).use { db ->
            val event = db.getAll().single()
            assertEquals("Legacy", event.name)
            assertEquals("", event.description)
            assertEquals("", event.contactEmail)
            assertEquals("", event.contactPhone)
            assertEquals(1, db.update(event.copy(description = "Added later")))
        }
        context.deleteDatabase(name)
    }
}
