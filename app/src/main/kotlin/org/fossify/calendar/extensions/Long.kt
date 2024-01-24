package org.fossify.calendar.extensions

import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.models.Event

fun Long.isTsOnProperDay(event: Event): Boolean {
    val dateTime = Formatter.getDateTimeFromTS(this)
    val power = 1 shl (dateTime.dayOfWeek - 1)
    return event.repeatRule and power != 0
}
