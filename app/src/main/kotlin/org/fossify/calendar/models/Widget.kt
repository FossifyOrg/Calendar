package org.fossify.calendar.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "widgets", indices = [(Index(value = ["widget_id"], unique = true))])
data class Widget(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "widget_id") var widgetId: Int,
    @ColumnInfo(name = "period") var period: Int,
    @ColumnInfo(name = "header") var header: Boolean,
    @ColumnInfo(name = "calendars") var calendars: String? = null
) {
    fun isCalendarsConfigured(): Boolean = calendars != null

    fun getCalendarIdsAsList(): List<Long> {
        val cal = calendars ?: return emptyList()
        return if (cal.isNotEmpty()) {
            cal.split(",").mapNotNull { it.trim().toLongOrNull() }
        } else {
            emptyList()
        }
    }
}
