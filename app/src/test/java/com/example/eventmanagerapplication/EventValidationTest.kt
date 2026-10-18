package com.example.eventmanagerapplication

import org.junit.Assert.*
import org.junit.Test

class EventValidationTest {
    @Test fun acceptsRealDatesOnlyInExactFormat() {
        assertTrue(EventValidation.validDate("29/02/2028"))
        listOf("29/02/2027", "31/04/2026", "1/01/2026", "2026-01-01", "01/01/2026x", "").forEach {
            assertFalse(it, EventValidation.validDate(it))
        }
    }
    @Test fun optionalContactsMayBeBlank() {
        assertTrue(EventValidation.validEmail(""))
        assertTrue(EventValidation.validPhone(""))
    }
    @Test fun checksProvidedContactFormats() {
        assertTrue(EventValidation.validEmail("alex@example.com"))
        assertFalse(EventValidation.validEmail("alex@"))
        assertFalse(EventValidation.validEmail("a b@example.com"))
        assertTrue(EventValidation.validPhone("+95 9 123-456-789"))
        listOf("abc", "123", "++959123456789", "1234567890123456").forEach {
            assertFalse(it, EventValidation.validPhone(it))
        }
    }
}
