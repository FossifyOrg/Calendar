package org.fossify.calendar.interfaces

import org.fossify.calendar.models.CalendarEntity

interface DeleteCalendarsListener {
    fun deleteCalendars(calendars: ArrayList<CalendarEntity>, deleteEvents: Boolean): Boolean
}
