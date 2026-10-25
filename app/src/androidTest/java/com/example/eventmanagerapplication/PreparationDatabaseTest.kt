package com.example.eventmanagerapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparationDatabaseTest {
    @Test fun versionFourUpgradePreservesSessionsAndStartsUnchecked() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "preparation-upgrade-test.db"
        context.deleteDatabase(name)
        try {
            var eventId = 0L
            var sessionId = 0L
            EventDatabaseHelper(context, name).use { db ->
                eventId = db.insert(Event(name = "Legacy", date = "25/10/2026", location = "Hall",
                    organiser = "Alex", type = "Conference", status = "Upcoming", amount = 0.0))
                sessionId = db.insertSession(Session(eventId = eventId, name = "Opening", time = "09:00", type = "Keynote"))
                db.writableDatabase.execSQL("DROP TABLE session_preparation")
                db.writableDatabase.version = 4
            }
            EventDatabaseHelper(context, name).use { db ->
                assertEquals("Opening", db.getSession(eventId, sessionId)!!.name)
                assertEquals("Legacy", db.getEvent(eventId)!!.name)
                assertTrue(db.getPreparation(eventId, sessionId).isEmpty())
                assertTrue(db.setPreparationItem(eventId, sessionId, 2, true))
                assertEquals(setOf(2), db.getPreparation(eventId, sessionId))
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun checklistPersistsIsIsolatedAndSurvivesSessionEdits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "preparation-test.db"
        context.deleteDatabase(name)
        try {
            var eventId = 0L
            var sessionId = 0L
            EventDatabaseHelper(context, name).use { db ->
                eventId = db.insert(Event(name = "Conference", date = "25/10/2026", location = "Hall",
                    organiser = "Alex", type = "Conference", status = "Upcoming", amount = 0.0))
                sessionId = db.insertSession(Session(eventId = eventId, name = "Opening", time = "09:00", type = "Keynote"))
                assertTrue(db.getPreparation(eventId, sessionId).isEmpty())
                assertTrue(db.setPreparationItem(eventId, sessionId, 0, true))
                assertTrue(db.setPreparationItem(eventId, sessionId, 3, true))
                assertFalse(db.setPreparationItem(eventId + 1, sessionId, 1, true))
                db.updateSession(db.getSession(eventId, sessionId)!!.copy(name = "Updated"))
            }
            EventDatabaseHelper(context, name).use { db ->
                assertEquals(setOf(0, 3), db.getPreparation(eventId, sessionId))
                assertTrue(db.getPreparation(eventId + 1, sessionId).isEmpty())
                assertTrue(db.setPreparationItem(eventId, sessionId, 0, false))
                assertEquals(setOf(3), db.getPreparation(eventId, sessionId))
                db.delete(eventId)
                assertTrue(db.getPreparation(eventId, sessionId).isEmpty())
                assertFalse(db.setPreparationItem(eventId, sessionId, 2, true))
            }
        } finally { context.deleteDatabase(name) }
    }
}

