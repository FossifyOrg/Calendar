package org.fossify.calendar.helpers

import android.content.Context
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.interfaces.WeeklyCalendar
import org.fossify.calendar.models.Event
import org.fossify.commons.helpers.DAY_SECONDS
import org.fossify.commons.helpers.WEEK_SECONDS

class WeeklyCalendarImpl(val callback: WeeklyCalendar, val context: Context) {
    var mEvents = ArrayList<Event>()

    fun updateWeeklyCalendar(weekStartTS: Long) {
        val endTS = weekStartTS + 2 * WEEK_SECONDS
        context.eventsHelper.getEvents(weekStartTS - DAY_SECONDS, endTS) {
            mEvents = it
            callback.updateWeeklyCalendar(it)
        }
    }
}
