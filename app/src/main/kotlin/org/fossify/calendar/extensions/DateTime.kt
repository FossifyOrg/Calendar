package org.fossify.calendar.extensions

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

fun DateTime.seconds() = millis / 1000L

// Interpret the start wall-clock in startZone and the end wall-clock in endZone,
// returning the two absolute instants (epoch seconds). The DateTimes' own zones are
// ignored; only their wall-clock fields are used.
fun computeStartEndSeconds(
    start: DateTime,
    startZone: String,
    end: DateTime,
    endZone: String,
): Pair<Long, Long> {
    val startTS = retainFieldsInZoneSeconds(start, DateTimeZone.forID(startZone))
    val endTS = retainFieldsInZoneSeconds(end, DateTimeZone.forID(endZone))
    return startTS to endTS
}

// Reinterpret a DateTime's wall-clock fields in [zone] and return the epoch seconds.
// Unlike DateTime.withZoneRetainFields(), this tolerates a wall-clock time that falls in a
// DST spring-forward gap (which would otherwise throw IllegalInstantException and crash the
// save) by resolving it leniently to the instant just after the transition.
private fun retainFieldsInZoneSeconds(dateTime: DateTime, zone: DateTimeZone): Long {
    // The wall-clock fields read as if they were UTC; UTC has no gaps so this never throws.
    val localMillis = dateTime.withZoneRetainFields(DateTimeZone.UTC).millis
    return zone.convertLocalToUTC(localMillis, false) / 1000L
}
