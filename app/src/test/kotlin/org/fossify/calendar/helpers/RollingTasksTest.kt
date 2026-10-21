package org.fossify.calendar.helpers

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class RollingTasksTest {
    private val zone = DateTimeZone.forID("Europe/Berlin")
    private val now = DateTime(2026, 10, 19, 15, 30, zone)

    private fun ts(dateTime: DateTime) = dateTime.millis / 1000L

    @Test
    fun pastTaskMovesToTodayAndKeepsTimeOfDay() {
        val start = DateTime(2026, 10, 12, 9, 0, zone)
        assertEquals(ts(DateTime(2026, 10, 19, 9, 0, zone)), getRollingTaskStartTS(ts(start), now))
    }

    @Test
    fun todayTaskStaysUnchanged() {
        val start = DateTime(2026, 10, 19, 23, 0, zone)
        assertEquals(ts(start), getRollingTaskStartTS(ts(start), now))
    }

    @Test
    fun futureTaskStaysUnchanged() {
        val start = DateTime(2026, 10, 25, 9, 0, zone)
        assertEquals(ts(start), getRollingTaskStartTS(ts(start), now))
    }
}
