package org.fossify.calendar.models

import org.joda.time.DateTime

data class DayWeekly(
    val start: DateTime,
    var topBarEvents: ArrayList<Event>,
    var dayEvents: ArrayList<EventWeeklyView>,
)
