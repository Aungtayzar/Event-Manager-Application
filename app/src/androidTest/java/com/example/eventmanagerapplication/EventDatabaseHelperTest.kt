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
            amount = 25.0
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
}
