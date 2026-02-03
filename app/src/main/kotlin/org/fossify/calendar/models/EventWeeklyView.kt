package org.fossify.calendar.models

data class EventWeeklyView(
    val event: Event,
    val startMinute: Int,
    val endMinute: Int,
    var slot: Int = 0,
    var slotMax: Int = 1,
)
