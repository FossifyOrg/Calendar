package org.fossify.calendar.models

import org.junit.Assert.assertEquals
import org.junit.Test

class EventEndTimeZoneTest {
    private fun event(tz: String, endTz: String) =
        Event(id = null, startTS = 0, endTS = 0, timeZone = tz, endTimeZone = endTz)

    @Test
    fun emptyEndZoneFallsBackToStartZone() {
        assertEquals("Europe/London", event("Europe/London", "").getEndTimeZoneString())
    }

    @Test
    fun invalidEndZoneFallsBackToStartZone() {
        assertEquals("Europe/London", event("Europe/London", "Not/AZone").getEndTimeZoneString())
    }

    @Test
    fun validEndZoneIsReturned() {
        assertEquals(
            "America/Los_Angeles",
            event("Europe/London", "America/Los_Angeles").getEndTimeZoneString()
        )
    }

    // Aliases such as US/Eastern are accepted by ICS/CalDAV import (DateTimeZone.getAvailableIDs),
    // so the read path must keep them rather than silently dropping back to the start zone.
    @Test
    fun aliasEndZoneIsAccepted() {
        assertEquals(
            "US/Eastern",
            event("America/Chicago", "US/Eastern").getEndTimeZoneString()
        )
    }
}
