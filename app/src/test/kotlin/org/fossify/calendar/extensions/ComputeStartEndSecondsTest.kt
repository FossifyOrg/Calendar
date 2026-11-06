package org.fossify.calendar.extensions

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ComputeStartEndSecondsTest {
    // Tokyo (JST, +9) departure 07:00, Colombo (+0530) arrival 08:30, same date.
    @Test
    fun differentZonesProduceIndependentInstants() {
        val startWall = DateTime(2026, 8, 30, 7, 0, DateTimeZone.UTC)
        val endWall = DateTime(2026, 8, 30, 8, 30, DateTimeZone.UTC)
        val (startTS, endTS) = computeStartEndSeconds(
            startWall, "Asia/Tokyo", endWall, "Asia/Colombo"
        )
        // 07:00 JST == 22:00 UTC on 2026-08-29
        assertEquals(DateTime(2026, 8, 29, 22, 0, DateTimeZone.UTC).millis / 1000L, startTS)
        // 08:30 +0530 == 03:00 UTC on 2026-08-30
        assertEquals(DateTime(2026, 8, 30, 3, 0, DateTimeZone.UTC).millis / 1000L, endTS)
        // Flight duration is 5 hours
        assertEquals(5 * 60 * 60L, endTS - startTS)
    }

    @Test
    fun sameZoneMatchesPlainConversion() {
        val startWall = DateTime(2026, 1, 1, 9, 0, DateTimeZone.UTC)
        val endWall = DateTime(2026, 1, 1, 10, 0, DateTimeZone.UTC)
        val (startTS, endTS) = computeStartEndSeconds(
            startWall, "Europe/London", endWall, "Europe/London"
        )
        assertEquals(
            startWall.withZoneRetainFields(DateTimeZone.forID("Europe/London")).millis / 1000L,
            startTS
        )
        assertEquals(
            endWall.withZoneRetainFields(DateTimeZone.forID("Europe/London")).millis / 1000L,
            endTS
        )
    }

    // A wall-clock time inside a DST spring-forward gap must not crash the conversion.
    @Test
    fun springForwardGapTimeIsResolvedLeniently() {
        // America/New_York springs forward 2026-03-08 02:00 -> 03:00, so 02:30 does not exist.
        val gapWall = DateTime(2026, 3, 8, 2, 30, DateTimeZone.UTC)
        val endWall = DateTime(2026, 3, 8, 4, 30, DateTimeZone.UTC)
        val (startTS, _) = computeStartEndSeconds(
            gapWall, "America/New_York", endWall, "America/New_York"
        )
        // It resolves to a real instant; viewed back in New York the hour is never the
        // nonexistent 02:00 (it is shifted to the post-transition side).
        val resolvedHour =
            DateTime(startTS * 1000L, DateTimeZone.forID("America/New_York")).hourOfDay
        assertNotEquals(2, resolvedHour)
    }
}
