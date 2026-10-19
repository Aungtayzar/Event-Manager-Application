package com.example.eventmanagerapplication

import android.graphics.Bitmap
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SearchFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var eventId = 0L

    @Before fun launch() {
        context.deleteDatabase("events.db")
        EventDatabaseHelper(context).use { database ->
            val event = Event(name = "Design workshop", date = "19/10/2026", location = "Hall A",
                organiser = "Alex Morgan", type = "Workshop", status = "Upcoming", amount = 25.0,
                description = "Hands-on prototyping", contactEmail = "alex@example.com", contactPhone = "+95 912345678")
            eventId = database.insert(event)
            val otherId = database.insert(event.copy(name = "Technology conference", organiser = "Sam Lee",
                date = "20/10/2026", description = "Design systems"))
            database.insertSession(Session(eventId = eventId, name = "Opening keynote", time = "09:00",
                type = "Keynote", speaker = "Alex", room = "Hall A", description = "Welcome and goals"))
            database.insertSession(Session(eventId = eventId, name = "Hands-on lab", time = "10:00",
                type = "Workshop", description = "Practical exercises"))
            database.insertSession(Session(eventId = otherId, name = "Private session", time = "09:00",
                type = "Keynote", description = "Other event only"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun cleanup() {
        scenario.close()
        context.deleteDatabase("events.db")
    }

    private fun search(id: Int, query: String) {
        onView(allOf(isAssignableFrom(EditText::class.java), isDescendantOfA(withId(id))))
            .perform(replaceText(query), closeSoftKeyboard())
    }

    private fun captureScreen(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureSearchScreenshots") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
    }

    @Test fun eventSearchUpdatesWhileTypingAndSelectedResultShowsFullDetails() {
        search(R.id.eventSearchInput, "dEs")
        onView(withText("Technology conference")).check(matches(isDisplayed()))
        search(R.id.eventSearchInput, "pRoTo")
        onView(withId(R.id.eventCardName)).check(matches(withText("Design workshop")))
        onView(withText("Technology conference")).check(doesNotExist())
        captureScreen("event-search")
        onView(withId(R.id.eventCardName)).perform(click())
        captureScreen("event-search-details")
        onView(withId(android.R.id.message)).check(matches(withText(containsString("alex@example.com"))))
        onView(withId(android.R.id.message)).check(matches(withText(containsString("Hands-on prototyping"))))
        onView(withText(R.string.close)).perform(click())
        search(R.id.eventSearchInput, "absent")
        onView(withId(R.id.emptyEventsText)).check(matches(withText(R.string.no_matching_events)))
        onView(withId(R.id.clearEventSearchButton)).perform(click())
        onView(withText("Technology conference")).check(matches(isDisplayed()))
    }

    @Test fun advancedFiltersCombineWithTextAndSurviveRecreationAndReturningFromSessions() {
        search(R.id.eventSearchInput, "des")
        onView(withId(R.id.advancedSearchButton)).perform(click())
        captureScreen("advanced-search")
        onView(withId(R.id.searchDateInput)).perform(replaceText("19/10/2026"), closeSoftKeyboard())
        onView(withId(R.id.searchOrganiserInput)).perform(replaceText("MOR"), closeSoftKeyboard())
        onView(withText(R.string.apply_filters)).perform(click())
        onView(withId(R.id.eventCardName)).check(matches(withText("Design workshop")))
        scenario.recreate()
        onView(withId(R.id.eventCardName)).check(matches(withText("Design workshop")))
        onView(withId(R.id.eventFilterSummary)).check(matches(withText(containsString("19/10/2026"))))
        onView(withId(R.id.eventSessionsButton)).perform(click())
        onView(withId(R.id.backToEventsButton)).perform(click())
        onView(withId(R.id.eventCardName)).check(matches(withText("Design workshop")))
        onView(withId(R.id.clearEventSearchButton)).perform(click())
        onView(withText("Technology conference")).check(matches(isDisplayed()))
    }

    @Test fun advancedSearchRejectsInvalidDatesAndCancelPreservesAppliedFilters() {
        onView(withId(R.id.advancedSearchButton)).perform(click())
        onView(withId(R.id.searchDateInput)).perform(replaceText("31/02/2026"), closeSoftKeyboard())
        onView(withText(R.string.apply_filters)).perform(click())
        onView(withId(R.id.searchDateInput)).check(matches(hasErrorText(context.getString(R.string.error_date_format))))
        onView(withId(R.id.searchDateInput)).perform(replaceText("20/10/2026"), closeSoftKeyboard())
        onView(withText(R.string.apply_filters)).perform(click())
        onView(withId(R.id.eventCardName)).check(matches(withText("Technology conference")))
        onView(withId(R.id.advancedSearchButton)).perform(click())
        onView(withId(R.id.searchDateInput)).perform(replaceText("19/10/2026"), closeSoftKeyboard())
        onView(withText(R.string.cancel)).perform(click())
        onView(withId(R.id.eventCardName)).check(matches(withText("Technology conference")))
        onView(withId(R.id.advancedSearchButton)).perform(click())
        onView(withText(R.string.clear_filters)).perform(click())
        onView(withId(R.id.eventSearchCount)).check(matches(withText(
            context.resources.getQuantityString(R.plurals.event_count, 2, 2))))
        search(R.id.eventSearchInput, "proto")
        onView(withId(R.id.eventCardName)).check(matches(allOf(withText("Design workshop"), isDisplayed())))
    }

    @Test fun sessionsSearchNamesDescriptionsAndDetailsWithoutLeakingOtherEvents() {
        ActivityScenario.launch<SessionsActivity>(SessionsActivity.intent(context, eventId)).use { sessions ->
            search(R.id.sessionSearchInput, "oPe")
            onView(withId(R.id.sessionCardName)).check(matches(withText("Opening keynote")))
            captureScreen("session-search")
            onView(withId(R.id.sessionCardName)).perform(click())
            onView(withId(android.R.id.message)).check(matches(withText(containsString("Welcome and goals"))))
            onView(withId(android.R.id.message)).check(matches(withText(containsString("Hall A"))))
            onView(withText(R.string.close)).perform(click())
            search(R.id.sessionSearchInput, "Practical")
            sessions.recreate()
            onView(withId(R.id.sessionCardName)).check(matches(withText("Hands-on lab")))
            search(R.id.sessionSearchInput, "Other event only")
            onView(withId(R.id.emptySessionsText)).check(matches(withText(R.string.no_matching_sessions)))
            search(R.id.sessionSearchInput, "")
            onView(withText("Opening keynote")).check(matches(isDisplayed()))
            onView(withText("Private session")).check(doesNotExist())
        }
    }
}
