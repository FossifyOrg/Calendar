package org.fossify.calendar.interfaces

import org.fossify.calendar.models.Event

interface WeeklyCalendar {
    fun updateWeeklyCalendar(events: ArrayList<Event>)
}
