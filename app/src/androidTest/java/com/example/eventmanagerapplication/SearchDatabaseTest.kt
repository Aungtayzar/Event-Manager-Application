package com.example.eventmanagerapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "search-test.db"
    private lateinit var database: EventDatabaseHelper
    private var workshopId = 0L
    private var conferenceId = 0L
    private var otherDateId = 0L

    @Before fun setUp() {
        context.deleteDatabase(databaseName)
        database = EventDatabaseHelper(context, databaseName)
        val event = Event(name = "Design workshop", date = "19/10/2026", location = "Hall A",
            organiser = "Alex Morgan", type = "Workshop", status = "Upcoming", amount = 25.0,
            description = "Hands-on prototyping", contactEmail = "alex@example.com")
        workshopId = database.insert(event)
        conferenceId = database.insert(event.copy(name = "Technology conference", organiser = "Sam Lee",
            description = "Design systems and accessibility"))
        otherDateId = database.insert(event.copy(name = "Design meetup", date = "20/10/2026",
            description = "Community gathering"))
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun findsAllPartialNameAndDescriptionMatchesIgnoringCaseAndOuterSpaces() {
        database.close()
        database = EventDatabaseHelper(context, databaseName)
        assertEquals(listOf(otherDateId, conferenceId, workshopId), database.searchEvents("  dES  ").map { it.id })
        val result = database.searchEvents("PROTO").single()
        assertEquals(workshopId, result.id)
        assertEquals("alex@example.com", result.contactEmail)
        assertTrue(database.searchEvents("no matching event").isEmpty())
    }

    @Test fun combinesDateAndOrganiserWithBothNameAndDescriptionMatches() {
        assertEquals(listOf(conferenceId, workshopId), database.searchEvents(date = "19/10/2026").map { it.id })
        assertEquals(listOf(otherDateId, workshopId), database.searchEvents(organiser = " mOR ").map { it.id })
        assertEquals(workshopId, database.searchEvents("design", "19/10/2026", "alex").single().id)
        assertEquals(conferenceId, database.searchEvents("design", "19/10/2026", "sam").single().id)
        assertTrue(database.searchEvents("design", "20/10/2026", "sam").isEmpty())
        assertTrue(database.searchEvents(date = "19/10/2025").isEmpty())
    }

    @Test fun treatsSqlAndWildcardCharactersAsLiteralSearchText() {
        val special = database.getEvent(workshopId)!!.copy(name = "100%_ O'Brien \\ workshop", organiser = "O'Brien")
        database.update(special)
        listOf("%_", "O'Brien", "\\").forEach {
            assertEquals(workshopId, database.searchEvents(it).single().id)
        }
        assertEquals(workshopId, database.searchEvents(organiser = "O'Brien").single().id)
        assertTrue(database.searchEvents("' OR 1=1 --").isEmpty())
        assertTrue(database.searchEvents(organiser = "%").isEmpty())
    }

    @Test fun searchesSessionNamesAndDescriptionsOnlyWithinSelectedEvent() {
        val session = Session(eventId = workshopId, name = "Opening keynote", time = "10:00",
            type = "Keynote", description = "Welcome and goals", speaker = "Alex", room = "A")
        val openingId = database.insertSession(session)
        val designId = database.insertSession(session.copy(name = "Hands-on lab", time = "09:00",
            description = "Opening design exercises"))
        database.insertSession(session.copy(eventId = conferenceId))
        assertEquals(listOf(designId, openingId), database.searchSessions(workshopId, "  oPEN ").map { it.id })
        assertEquals("A", database.searchSessions(workshopId, "key").single().room)
        assertTrue(database.searchSessions(otherDateId, "Opening").isEmpty())
        assertTrue(database.searchSessions(workshopId, "missing").isEmpty())
        assertTrue(database.searchSessions(workshopId, "' OR 1=1 --").isEmpty())
        assertTrue(database.searchSessions(workshopId, "%").isEmpty())
        assertEquals(database.getSessions(workshopId), database.searchSessions(workshopId, "  "))
    }

    @Test fun blankFiltersReturnAllEventsInExistingOrderWithSessionCounts() {
        database.insertSession(Session(eventId = workshopId, name = "Lab", time = "09:00", type = "Workshop"))
        assertEquals(database.getAll(), database.searchEvents("  ", " ", "  "))
        assertEquals(1, database.searchEvents("work").single().sessionCount)
    }
}
