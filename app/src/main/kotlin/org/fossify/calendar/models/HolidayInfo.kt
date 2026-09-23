package org.fossify.calendar.models

data class HolidayInfo(
    val code: String,
    val country: String,
    val public: String? = null,
    val regional: String? = null,
    val other: String? = null,
) {
    val paths: List<String>
        get() = listOfNotNull(public, regional, other).filter { it.isNotEmpty() }
}
