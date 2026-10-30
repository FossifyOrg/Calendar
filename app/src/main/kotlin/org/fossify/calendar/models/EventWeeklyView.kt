package org.fossify.calendar.models

import android.util.Range

data class EventWeeklyView(
    val range: Range<Int>,
    // the left offset as a fraction of the day column width, events nested over earlier ones are indented
    var indent: Float = 0f,
    // the drawing order within the day, events nested over earlier ones are drawn on top of them
    var layer: Int = 0,
)
