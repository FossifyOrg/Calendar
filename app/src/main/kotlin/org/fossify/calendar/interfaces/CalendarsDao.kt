package org.fossify.calendar.interfaces

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.calendar.models.CalendarEntity

@Dao
interface CalendarsDao {
    @Query("SELECT * FROM event_types ORDER BY title ASC")
    fun getCalendars(): List<CalendarEntity>

    @Query("SELECT * FROM event_types WHERE id = :id")
    fun getCalendarWithId(id: Long): CalendarEntity?

    @Query("SELECT id FROM event_types WHERE title = :title COLLATE NOCASE")
    fun getCalendarIdWithTitle(title: String): Long?

    @Query("SELECT id FROM event_types WHERE title = :title AND caldav_calendar_id = 0 COLLATE NOCASE")
    fun getLocalCalendarIdWithTitle(title: String): Long?

    @Query("SELECT id FROM event_types WHERE type = :classId")
    fun getCalendarIdWithClass(classId: Int): Long?

    @Query("SELECT id FROM event_types WHERE type = :classId AND caldav_calendar_id = 0")
    fun getLocalCalendarIdWithClass(classId: Int): Long?

    @Query("SELECT * FROM event_types WHERE caldav_calendar_id = :calendarId")
    fun getCalendarWithCalDAVCalendarId(calendarId: Int): CalendarEntity?

    @Query("DELETE FROM event_types WHERE caldav_calendar_id IN (:ids)")
    fun deleteCalendarsWithCalendarIds(ids: List<Int>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(calendar: CalendarEntity): Long

    @Delete
    fun deleteCalendars(calendars: List<CalendarEntity>)
}
