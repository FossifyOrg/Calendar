package org.fossify.calendar.models

data class HolidayMetadata(
    val version: String,
    val countries: List<HolidayInfo>
)
