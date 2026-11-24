package org.fossify.calendar.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseTimeZoneIdTest {
    @Test
    fun extractsTzidFromPropertyParameters() {
        assertEquals(
            "America/New_York",
            parseTimeZoneId(";TZID=America/New_York:20260830T070000")
        )
    }

    @Test
    fun returnsNullWhenNoTzid() {
        assertNull(parseTimeZoneId(":20260830T070000Z"))
    }

    @Test
    fun returnsNullForUnknownZone() {
        assertNull(parseTimeZoneId(";TZID=Not/AZone:20260830T070000"))
    }

    @Test
    fun extractsQuotedTzid() {
        assertEquals(
            "America/New_York",
            parseTimeZoneId(";TZID=\"America/New_York\":20260830T070000")
        )
    }

    @Test
    fun extractsTzidAfterAnotherParameter() {
        assertEquals(
            "America/New_York",
            parseTimeZoneId(";VALUE=DATE-TIME;TZID=America/New_York:20260830T070000")
        )
    }
}
