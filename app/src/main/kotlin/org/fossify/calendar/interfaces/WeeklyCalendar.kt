package org.fossify.calendar.interfaces

import android.content.Context
import org.fossify.calendar.models.DayWeekly

interface WeeklyCalendar {
    fun updateWeeklyCalendar(context: Context, days: ArrayList<DayWeekly>, earliestStartHour: Int, latestEndHour: Int)
}
