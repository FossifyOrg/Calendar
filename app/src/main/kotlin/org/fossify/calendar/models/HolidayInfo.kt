package org.fossify.calendar.models

data class HolidayInfo(
    val code: String,
    val country: String,
    val public: String,
    val regional: String? = null,
    val other: String? = null,
)
