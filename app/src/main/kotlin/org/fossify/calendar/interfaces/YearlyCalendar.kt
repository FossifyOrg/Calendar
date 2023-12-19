package org.fossify.calendar.interfaces

import android.util.SparseArray
import org.fossify.calendar.models.DayYearly

interface YearlyCalendar {
    fun updateYearlyCalendar(events: SparseArray<ArrayList<DayYearly>>, hashCode: Int)
}
