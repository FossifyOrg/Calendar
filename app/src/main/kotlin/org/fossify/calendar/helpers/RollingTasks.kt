package org.fossify.calendar.helpers

import org.fossify.calendar.extensions.seconds
import org.joda.time.DateTime
import org.joda.time.IllegalInstantException

// A rolling task keeps its stored date. If that date is before today, show it on today instead.
fun getRollingTaskStartTS(startTS: Long, now: DateTime = DateTime.now()): Long {
    val start = DateTime(startTS * 1000L, now.zone)
    if (!start.toLocalDate().isBefore(now.toLocalDate())) {
        return startTS
    }

    return try {
        start.withDate(now.year, now.monthOfYear, now.dayOfMonth).seconds()
    } catch (e: IllegalInstantException) {
        // The time of day does not exist today (daylight saving gap).
        now.withTimeAtStartOfDay().seconds()
    }
}
