package org.fossify.calendar.sync.tasks

import android.provider.CalendarContract
import org.fossify.calendar.helpers.DAY
import org.fossify.calendar.helpers.Parser
import org.fossify.calendar.helpers.REMINDER_EMAIL
import org.fossify.calendar.helpers.REMINDER_NOTIFICATION
import org.fossify.calendar.helpers.REMINDER_OFF
import org.fossify.calendar.helpers.WEEK
import org.fossify.calendar.sync.tasks.OpenTasksContract.Alarm
import org.fossify.calendar.sync.tasks.OpenTasksContract.Tasks
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenTasksMapperTest {
    private lateinit var originalZone: DateTimeZone

    @Before
    fun setUp() {
        originalZone = DateTimeZone.getDefault()
        // west of UTC, so all-day values would land on the previous day if they were not shifted
        DateTimeZone.setDefault(DateTimeZone.forID("America/New_York"))
    }

    @After
    fun tearDown() {
        DateTimeZone.setDefault(originalZone)
    }

    @Test
    fun timedTaskRoundTrip() {
        val due = DateTime(2026, 3, 10, 14, 30, DateTimeZone.UTC).millis
        val task = OpenTasksMapper.toTask(row(due = due, timeZone = "Europe/Berlin"), emptyList())!!

        assertEquals(due / 1000L, task.startTS)
        assertEquals(task.startTS, task.endTS)
        assertEquals("Europe/Berlin", task.timeZone)
        assertTrue(task.isTask())

        val values = OpenTasksMapper.toValues(task)
        assertEquals(due, values.due)
        assertEquals(false, values.isAllDay)
        assertEquals("Europe/Berlin", values.timeZone)
        assertNull(values.rrule)
        assertNull(values.exdate)
    }

    @Test
    fun allDayTaskIsShownOnTheSameDate() {
        val due = DateTime(2026, 3, 10, 0, 0, DateTimeZone.UTC).millis
        val task = OpenTasksMapper.toTask(row(due = due, isAllDay = true), emptyList())!!

        val localStart = DateTime(task.startTS * 1000L, DateTimeZone.getDefault())
        assertEquals(10, localStart.dayOfMonth)
        assertEquals(0, localStart.hourOfDay)
        assertTrue(task.getIsAllDay())

        val values = OpenTasksMapper.toValues(task)
        assertEquals(due, values.due)
        assertEquals(true, values.isAllDay)
        assertNull(values.timeZone)
    }

    @Test
    fun startIsUsedWhenThereIsNoDueDate() {
        val start = DateTime(2026, 3, 10, 9, 0, DateTimeZone.UTC).millis
        val task = OpenTasksMapper.toTask(row(dtStart = start), emptyList())!!
        assertEquals(start / 1000L, task.startTS)
    }

    @Test
    fun tasksWithoutDatesAreSkipped() {
        assertNull(OpenTasksMapper.toTask(row(), emptyList()))
    }

    @Test
    fun repeatingTaskWithExceptionsRoundTrip() {
        val due = DateTime(2026, 3, 10, 14, 30, DateTimeZone.getDefault()).millis
        val task = OpenTasksMapper.toTask(
            row(due = due, rrule = "FREQ=WEEKLY", exdate = "20260317T183000Z,20260324T183000Z"),
            emptyList()
        )!!

        assertEquals(WEEK, task.repeatInterval)
        assertEquals(listOf("20260317", "20260324"), task.repetitionExceptions)

        val values = OpenTasksMapper.toValues(task)
        assertEquals("FREQ=WEEKLY", values.rrule?.substringBefore(";"))
        assertEquals(task.repetitionExceptions, Parser().parseExDates(values.exdate!!))
    }

    @Test
    fun allDayExceptionsAreWrittenAsDates() {
        val due = DateTime(2026, 3, 10, 0, 0, DateTimeZone.UTC).millis
        val task = OpenTasksMapper.toTask(
            row(due = due, isAllDay = true, rrule = "FREQ=DAILY", exdate = "20260311,20260313"),
            emptyList()
        )!!

        assertEquals(DAY, task.repeatInterval)
        assertEquals(listOf("20260311", "20260313"), task.repetitionExceptions)
        assertEquals("20260311,20260313", OpenTasksMapper.toValues(task).exdate)
    }

    @Test
    fun providerTimesOfAllDayOccurrencesMatchLocalOccurrences() {
        val due = DateTime(2026, 3, 10, 0, 0, DateTimeZone.UTC).millis
        val task = OpenTasksMapper.toTask(row(due = due, isAllDay = true, rrule = "FREQ=DAILY"), emptyList())!!
        val nextOccurrence = task.startTS + DAY

        val providerTime = OpenTasksMapper.toProviderTime(nextOccurrence, isAllDay = true)
        assertEquals(DateTime(2026, 3, 11, 0, 0, DateTimeZone.UTC).millis, providerTime)
        assertEquals(nextOccurrence, OpenTasksMapper.fromProviderTime(providerTime, isAllDay = true))
    }

    @Test
    fun alarmsBecomeRemindersAndBack() {
        val alarms = listOf(
            OpenTasksMapper.AlarmRow(60, Alarm.ALARM_TYPE_MESSAGE),
            OpenTasksMapper.AlarmRow(10, Alarm.ALARM_TYPE_EMAIL),
            OpenTasksMapper.AlarmRow(1440, Alarm.ALARM_TYPE_MESSAGE),
            OpenTasksMapper.AlarmRow(5, Alarm.ALARM_TYPE_MESSAGE),
        )
        val task = OpenTasksMapper.toTask(row(due = 1_000_000_000L), alarms)!!

        assertEquals(5, task.reminder1Minutes)
        assertEquals(REMINDER_NOTIFICATION, task.reminder1Type)
        assertEquals(10, task.reminder2Minutes)
        assertEquals(REMINDER_EMAIL, task.reminder2Type)
        assertEquals(60, task.reminder3Minutes)

        val written = OpenTasksMapper.toAlarms(task)
        assertEquals(listOf(5, 10, 60), written.map { it.minutesBefore })
        assertEquals(Alarm.ALARM_TYPE_EMAIL, written[1].alarmType)
    }

    @Test
    fun noAlarmsMeansNoReminders() {
        val task = OpenTasksMapper.toTask(row(due = 1_000_000_000L), emptyList())!!
        assertEquals(REMINDER_OFF, task.reminder1Minutes)
        assertTrue(OpenTasksMapper.toAlarms(task).isEmpty())
    }

    @Test
    fun cancelledTasksAreMarkedCanceled() {
        val task = OpenTasksMapper.toTask(row(due = 1_000_000_000L, status = Tasks.STATUS_CANCELLED), emptyList())!!
        assertEquals(CalendarContract.Events.STATUS_CANCELED, task.status)
    }

    @Test
    fun colorIsOptional() {
        val uncolored = OpenTasksMapper.toTask(row(due = 1_000_000_000L), emptyList())!!
        assertEquals(0, uncolored.color)
        assertNull(OpenTasksMapper.toValues(uncolored).color)

        val colored = OpenTasksMapper.toTask(row(due = 1_000_000_000L, color = 0xFF00FF00.toInt()), emptyList())!!
        assertEquals(0xFF00FF00.toInt(), OpenTasksMapper.toValues(colored).color)
    }

    private fun row(
        due: Long? = null,
        dtStart: Long? = null,
        isAllDay: Boolean = false,
        timeZone: String? = null,
        rrule: String? = null,
        exdate: String? = null,
        status: Int = Tasks.STATUS_NEEDS_ACTION,
        color: Int? = null,
    ) = OpenTasksMapper.TaskRow(
        id = 1L,
        title = "Task",
        description = "",
        location = "",
        dtStart = dtStart,
        due = due,
        isAllDay = isAllDay,
        timeZone = timeZone,
        rrule = rrule,
        exdate = exdate,
        status = status,
        color = color,
    )
}
