package com.example.eventmanagerapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionValidationTest {
    @Test fun acceptsExactTwentyFourHourTimesIncludingMidnight() {
        listOf("00:00", "09:05", "12:30", "23:59").forEach {
            assertTrue(it, SessionValidation.validTime(it))
        }
    }

    @Test fun rejectsMissingMalformedAndOutOfRangeTimes() {
        listOf("", " ", "9:05", "24:00", "12:60", "-1:00", "09:00 AM", "09:00:00").forEach {
            assertFalse(it, SessionValidation.validTime(it))
        }
    }
}
