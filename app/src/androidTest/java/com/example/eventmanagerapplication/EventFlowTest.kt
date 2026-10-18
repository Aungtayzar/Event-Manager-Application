package com.example.eventmanagerapplication

import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventFlowTest {
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launch() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("events.db")
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun cleanup() {
        scenario.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("events.db")
    }

    private fun fillForm(optional: Boolean = false) {
        onView(withId(R.id.nav_add)).perform(click())
        scenario.onActivity { activity ->
            mapOf(R.id.eventNameInput to "Workshop", R.id.eventDateInput to "18/10/2026",
                R.id.eventLocationInput to "City Hall", R.id.eventOrganiserInput to "Alex",
                R.id.eventAmountInput to "25").forEach { (id, value) ->
                activity.findViewById<EditText>(id).setText(value)
            }
            if (optional) {
                activity.findViewById<EditText>(R.id.eventDescriptionInput).setText("Bring a notebook")
                activity.findViewById<EditText>(R.id.contactEmailInput).setText("alex@example.com")
                activity.findViewById<EditText>(R.id.contactPhoneInput).setText("+95 912345678")
            }
        }
    }

    private fun review() { onView(withId(R.id.addEventButton)).perform(scrollTo(), click()) }
    private fun confirm() { onView(withText(R.string.confirm_save)).perform(click()) }

    @Test fun bottomNavigationShowsListAndPreservesDraft() {
        onView(withId(R.id.emptyEventsText)).check(matches(isDisplayed()))
        fillForm()
        onView(withId(R.id.nav_events)).perform(click())
        onView(withId(R.id.emptyEventsText)).check(matches(isDisplayed()))
        onView(withId(R.id.nav_add)).perform(click())
        onView(withId(R.id.eventNameInput)).check(matches(withText("Workshop")))
        scenario.recreate()
        onView(withId(R.id.eventNameInput)).check(matches(withText("Workshop")))
    }

    @Test fun datePickerFillsAnExactFormattedDate() {
        onView(withId(R.id.nav_add)).perform(click())
        onView(withId(R.id.chooseDateButton)).perform(scrollTo(), click())
        onView(withId(android.R.id.button1)).perform(click())
        scenario.onActivity {
            org.junit.Assert.assertTrue(EventValidation.validDate(
                it.findViewById<EditText>(R.id.eventDateInput).text.toString()))
        }
    }

    @Test fun emptyRequiredFieldsAreRejected() {
        onView(withId(R.id.nav_add)).perform(click())
        review()
        scenario.onActivity { activity ->
            listOf(R.id.eventNameInput, R.id.eventDateInput, R.id.eventLocationInput,
                R.id.eventOrganiserInput, R.id.eventAmountInput).forEach {
                org.junit.Assert.assertNotNull(activity.findViewById<EditText>(it).error)
            }
        }
    }

    @Test fun invalidDateAndContactsAreRejected() {
        fillForm()
        scenario.onActivity {
            it.findViewById<EditText>(R.id.eventDateInput).setText("31/02/2026")
            it.findViewById<EditText>(R.id.contactEmailInput).setText("invalid")
            it.findViewById<EditText>(R.id.contactPhoneInput).setText("abc")
        }
        review()
        scenario.onActivity { activity ->
            listOf(R.id.eventDateInput, R.id.contactEmailInput, R.id.contactPhoneInput).forEach {
                org.junit.Assert.assertNotNull(activity.findViewById<EditText>(it).error)
            }
        }
    }

    @Test fun reviewBackConfirmDetailsAndEditPersistAllFields() {
        fillForm(optional = true)
        review()
        onView(withId(android.R.id.message)).check(matches(withText(containsString("Bring a notebook"))))
        onView(withText(R.string.back_to_edit)).perform(click())
        scenario.onActivity {
            org.junit.Assert.assertTrue(EventDatabaseHelper(it).use { db -> db.getAll().isEmpty() })
        }
        review()
        confirm()
        onView(withId(R.id.detailEventButton)).perform(click())
        onView(withId(android.R.id.message)).check(matches(withText(containsString("alex@example.com"))))
        onView(withText(R.string.edit)).perform(click())
        scenario.onActivity {
            org.junit.Assert.assertEquals("+95 912345678", it.findViewById<EditText>(R.id.contactPhoneInput).text.toString())
            it.findViewById<EditText>(R.id.eventNameInput).setText("Updated workshop")
        }
        scenario.recreate()
        review()
        confirm()
        onView(allOf(withId(R.id.eventCardName), withText("Updated workshop"))).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.editEventButton), withParent(withParent(hasDescendant(withText("Updated workshop")))))).perform(click())
        onView(withId(R.id.cancelEditButton)).perform(scrollTo(), click())
        onView(withId(R.id.nav_events)).perform(click())
        onView(allOf(withId(R.id.eventCardName), withText("Updated workshop"))).check(matches(isDisplayed()))
    }

    @Test fun blankOptionalFieldsSaveDuplicateIsBlockedAndDeleteIsConfirmed() {
        fillForm()
        review()
        confirm()
        fillForm()
        review()
        scenario.onActivity {
            org.junit.Assert.assertEquals(it.getString(R.string.error_duplicate), it.findViewById<EditText>(R.id.eventNameInput).error.toString())
        }
        onView(withId(R.id.nav_events)).perform(click())
        onView(withId(R.id.deleteEventButton)).perform(click())
        onView(withText(R.string.cancel)).perform(click())
        onView(withId(R.id.eventCardName)).check(matches(withText("Workshop")))
        onView(withId(R.id.deleteEventButton)).perform(click())
        onView(withText(R.string.delete)).perform(click())
        onView(withId(R.id.emptyEventsText)).check(matches(isDisplayed()))
    }
}
