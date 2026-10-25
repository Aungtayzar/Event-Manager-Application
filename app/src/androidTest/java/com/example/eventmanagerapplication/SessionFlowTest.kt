package com.example.eventmanagerapplication

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SessionFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var eventId = 0L

    @Before fun launch() {
        context.deleteDatabase("events.db")
        EventDatabaseHelper(context).use { database ->
            val event = Event(name = "Conference", date = "18/10/2026", location = "Hall",
                organiser = "Alex", type = "Conference", status = "Upcoming", amount = 0.0)
            eventId = database.insert(event)
            database.insert(event.copy(name = "Other event"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun cleanup() {
        scenario.close()
        context.deleteDatabase("events.db")
    }

    private fun openSessions(eventName: String = "Conference") {
        onView(allOf(withId(R.id.eventSessionsButton),
            withParent(hasDescendant(withText(eventName))))).perform(scrollTo(), click())
    }

    private fun enterSession(name: String, time: String) {
        onView(withId(R.id.sessionNameInput)).perform(scrollTo(), replaceText(name), closeSoftKeyboard())
        onView(withId(R.id.sessionTimeInput)).perform(scrollTo(), replaceText(time), closeSoftKeyboard())
    }

    private fun save() {
        onView(withId(R.id.saveSessionButton)).perform(scrollTo(), click())
    }

    private fun captureScreen(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureSessionScreenshots") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
    }

    @Test fun addEditDeleteSessionsRefreshesEventCountsAndKeepsEventsIsolated() {
        openSessions()
        onView(withId(R.id.emptySessionsText)).check(matches(isDisplayed()))
        onView(withId(R.id.addSessionButton)).perform(click())
        enterSession("Opening", "09:00")
        onView(withId(R.id.sessionSpeakerInput)).perform(scrollTo(), replaceText("Alex"), closeSoftKeyboard())
        onView(withId(R.id.sessionRoomInput)).perform(scrollTo(), replaceText("Hall A"), closeSoftKeyboard())
        onView(withId(R.id.sessionDescriptionInput)).perform(scrollTo(), replaceText("Bring a notebook"), closeSoftKeyboard())
        onView(withId(R.id.sessionNameInput)).perform(scrollTo())
        captureScreen("session-editor")
        save()
        onView(withId(R.id.sessionCardName)).check(matches(withText("Opening")))
        onView(withId(R.id.sessionCardDetails)).check(matches(withText(containsString("Bring a notebook"))))
        captureScreen("session-list")
        onView(withId(R.id.editSessionButton)).perform(click())
        onView(withId(R.id.sessionSpeakerInput)).check(matches(withText("Alex")))
        enterSession("Closing", "17:00")
        save()
        onView(withId(R.id.sessionCardName)).check(matches(withText("Closing")))
        onView(withId(R.id.backToEventsButton)).perform(click())
        onView(allOf(withId(R.id.eventSessionCount), withParent(hasDescendant(withText("Conference")))))
            .check(matches(withText(context.resources.getQuantityString(R.plurals.session_count, 1, 1))))
        captureScreen("event-session-count")
        openSessions("Other event")
        onView(withId(R.id.emptySessionsText)).check(matches(isDisplayed()))
        onView(withId(R.id.backToEventsButton)).perform(click())
        openSessions()
        onView(withId(R.id.deleteSessionButton)).perform(click())
        onView(withText(R.string.cancel)).perform(click())
        onView(withId(R.id.sessionCardName)).check(matches(withText("Closing")))
        onView(withId(R.id.deleteSessionButton)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())
        onView(withId(R.id.emptySessionsText)).check(matches(isDisplayed()))
        onView(withId(R.id.sessionsCount)).check(matches(withText(context.resources.getQuantityString(R.plurals.session_count, 0, 0))))
        onView(withId(R.id.backToEventsButton)).perform(click())
        EventDatabaseHelper(context).use { assertEquals(0, it.getEvent(eventId)!!.sessionCount) }
    }

    @Test fun multipleSessionsAndEventDeletionRemoveOnlyLinkedSessions() {
        openSessions()
        listOf("Opening" to "09:00", "Networking" to "10:00").forEach { (name, time) ->
            onView(withId(R.id.addSessionButton)).perform(click())
            enterSession(name, time)
            save()
        }
        onView(withId(R.id.sessionsCount)).check(matches(withText(context.resources.getQuantityString(R.plurals.session_count, 2, 2))))
        onView(withId(R.id.backToEventsButton)).perform(click())
        onView(allOf(withId(R.id.deleteEventButton),
            withParent(withParent(hasDescendant(withText("Conference")))))).perform(click())
        onView(withId(android.R.id.message)).check(matches(withText(containsString("sessions"))))
        onView(withId(android.R.id.button1)).perform(click())
        EventDatabaseHelper(context).use {
            assertTrue(it.getSessions(eventId).isEmpty())
            assertEquals("Other event", it.getAll().single().name)
        }
    }

    @Test fun requiredFieldsAndInvalidTimeAreRejectedWithoutSaving() {
        openSessions()
        onView(withId(R.id.addSessionButton)).perform(click())
        save()
        onView(withId(R.id.sessionNameInput)).check(matches(hasErrorText(context.getString(R.string.error_session_name))))
        onView(withId(R.id.sessionTimeInput)).check(matches(hasErrorText(context.getString(R.string.error_session_time))))
        enterSession("Opening", "24:00")
        save()
        onView(withId(R.id.sessionTimeInput)).check(matches(hasErrorText(context.getString(R.string.error_session_time))))
        pressBack()
        EventDatabaseHelper(context).use { assertTrue(it.getSessions(eventId).isEmpty()) }
    }

    @Test fun editorRestoresDraftAndGeneratedIdAcrossRecreation() {
        val id = EventDatabaseHelper(context).use {
            it.insertSession(Session(eventId = eventId, name = "Opening", time = "09:00", type = "Keynote"))
        }
        ActivityScenario.launch<SessionEditorActivity>(SessionEditorActivity.intent(context, eventId, id)).use { editor ->
            enterSession("Updated opening", "09:30")
            editor.recreate()
            onView(withId(R.id.sessionNameInput)).check(matches(withText("Updated opening")))
            onView(withId(R.id.sessionTimeInput)).check(matches(withText("09:30")))
            onView(withId(R.id.sessionIdText)).check(matches(withText(context.getString(R.string.session_id, id))))
            save()
        }
        EventDatabaseHelper(context).use {
            val session = it.getSessions(eventId).single()
            assertEquals(id, session.id)
            assertEquals("Updated opening", session.name)
            assertEquals("Keynote", session.type)
        }
    }

    @Test fun duplicatePrefillsEditableFieldsAndCreatesIndependentSessionOnlyOnSave() {
        val original = EventDatabaseHelper(context).use { db ->
            val source = Session(eventId = eventId, name = "Opening", time = "09:00", type = "Keynote",
                speaker = "Alex", room = "Hall A", description = "Bring notes")
            val id = db.insertSession(source)
            db.setPreparationItem(eventId, id, 0, true)
            source.copy(id = id)
        }
        openSessions()
        onView(withId(R.id.duplicateSessionButton)).perform(scrollTo(), click())
        onView(withId(R.id.sessionNameInput)).check(matches(withText("Opening (copy)")))
        onView(withId(R.id.sessionTimeInput)).check(matches(withText("09:00")))
        onView(withId(R.id.sessionSpeakerInput)).check(matches(withText("Alex")))
        onView(withId(R.id.sessionRoomInput)).check(matches(withText("Hall A")))
        onView(withId(R.id.sessionDescriptionInput)).check(matches(withText("Bring notes")))
        onView(withId(R.id.cancelSessionButton)).perform(scrollTo(), click())
        EventDatabaseHelper(context).use { assertEquals(listOf(original), it.getSessions(eventId)) }
        ActivityScenario.launch<SessionEditorActivity>(
            SessionEditorActivity.duplicateIntent(context, eventId, original.id)
        ).use { editor ->
            enterSession("Afternoon opening", "14:00")
            editor.recreate()
            onView(withId(R.id.sessionNameInput)).check(matches(withText("Afternoon opening")))
            onView(withId(R.id.sessionTimeInput)).check(matches(withText("14:00")))
            save()
        }
        EventDatabaseHelper(context).use { db ->
            val sessions = db.getSessions(eventId)
            assertEquals(2, sessions.size)
            assertEquals(original, db.getSession(eventId, original.id))
            val duplicate = sessions.single { it.id != original.id }
            assertEquals(original.copy(id = duplicate.id, name = "Afternoon opening", time = "14:00"), duplicate)
            assertTrue(db.getPreparation(eventId, duplicate.id).isEmpty())
            assertEquals(setOf(0), db.getPreparation(eventId, original.id))
        }
    }
    @Test fun preparationAutoSavesAcrossReopenAndCanBeUnchecked() {
        openSessions()
        onView(withId(R.id.addSessionButton)).perform(click())
        enterSession("Opening", "09:00")
        save()
        onView(withId(R.id.detailSessionButton)).perform(click())
        onView(withId(R.id.preparationProgress)).check(matches(withText(context.getString(R.string.preparation_progress, 0))))
        listOf(R.id.roomPrepared, R.id.equipmentTested, R.id.speakerConfirmed, R.id.materialsReady).forEach {
            onView(withId(it)).perform(scrollTo(), click())
        }
        onView(withId(R.id.preparationProgress)).check(matches(withText(R.string.preparation_ready)))
        captureScreen("session-preparation")
        onView(withText(R.string.close)).perform(click())
        onView(withId(R.id.detailSessionButton)).perform(click())
        onView(withId(R.id.roomPrepared)).check(matches(isChecked())).perform(scrollTo(), click())
        onView(withId(R.id.preparationProgress)).check(matches(withText(context.getString(R.string.preparation_progress, 3))))
        onView(withText(R.string.close)).perform(click())
        onView(withId(R.id.detailSessionButton)).perform(click())
        onView(withId(R.id.roomPrepared)).check(matches(isNotChecked()))
        onView(withId(R.id.materialsReady)).check(matches(isChecked()))
        onView(withText(R.string.close)).perform(click())
    }
    @Test fun timePickerProvidesAValidTimeAndCancelLeavesNoSession() {
        openSessions()
        onView(withId(R.id.addSessionButton)).perform(click())
        onView(withId(R.id.chooseTimeButton)).perform(scrollTo(), click())
        onView(withId(android.R.id.button1)).perform(click())
        onView(withId(R.id.sessionTimeInput)).check { view, error ->
            if (error != null) throw error
            assertTrue(SessionValidation.validTime((view as android.widget.EditText).text.toString()))
        }
        onView(withId(R.id.cancelSessionButton)).perform(scrollTo(), click())
        onView(withId(R.id.emptySessionsText)).check(matches(isDisplayed()))
    }
}
