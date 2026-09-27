package org.fossify.calendar.sync.tasks

import android.provider.CalendarContract
import org.fossify.calendar.extensions.toLocalAllDayEvent
import org.fossify.calendar.helpers.FLAG_ALL_DAY
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.Parser
import org.fossify.calendar.helpers.REMINDER_EMAIL
import org.fossify.calendar.helpers.REMINDER_NOTIFICATION
import org.fossify.calendar.helpers.REMINDER_OFF
import org.fossify.calendar.helpers.TYPE_TASK
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.Reminder
import org.fossify.calendar.sync.tasks.OpenTasksContract.Alarm
import org.fossify.calendar.sync.tasks.OpenTasksContract.Tasks
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat

/**
 * Converts between OpenTasks rows and [Event]s of type task.
 * Kept free of content provider calls so it can be unit tested.
 *
 * OpenTasks stores times in milliseconds; all-day values are midnight UTC. A Fossify task is a single point in time,
 * it is mapped to DUE (falling back to DTSTART when reading tasks without a due date).
 */
object OpenTasksMapper {
    private const val EXDATE_UTC_PATTERN = "yyyyMMdd'T'HHmmss'Z'"
    private const val MAX_REMINDERS = 3

    data class TaskRow(
        val id: Long,
        val title: String,
        val description: String,
        val location: String,
        val dtStart: Long?,
        val due: Long?,
        val isAllDay: Boolean,
        val timeZone: String?,
        val rrule: String?,
        val exdate: String?,
        val status: Int,
        val color: Int?,
    )

    data class AlarmRow(val minutesBefore: Int, val alarmType: Int)

    /** The values of a task as they are written to the provider. */
    data class TaskValues(
        val title: String,
        val description: String,
        val location: String,
        val isAllDay: Boolean,
        val due: Long,
        val timeZone: String?,
        val rrule: String?,
        val exdate: String?,
        val color: Int?,
    )

    /** @return null for tasks without any date, those cannot be shown in a calendar */
    fun toTask(row: TaskRow, alarms: List<AlarmRow>): Event? {
        val providerTime = row.due ?: row.dtStart ?: return null
        val reminders = toReminders(alarms) + List(MAX_REMINDERS) { Reminder(REMINDER_OFF, REMINDER_NOTIFICATION) }
        val task = Event(
            id = null,
            startTS = providerTime / 1000L,
            endTS = providerTime / 1000L,
            title = row.title,
            location = row.location,
            description = row.description,
            reminder1Minutes = reminders[0].minutes,
            reminder2Minutes = reminders[1].minutes,
            reminder3Minutes = reminders[2].minutes,
            reminder1Type = reminders[0].type,
            reminder2Type = reminders[1].type,
            reminder3Type = reminders[2].type,
            timeZone = row.timeZone ?: DateTimeZone.getDefault().id,
            flags = if (row.isAllDay) FLAG_ALL_DAY else 0,
            color = row.color ?: 0,
            type = TYPE_TASK,
            status = toEventStatus(row.status)
        )

        if (row.isAllDay) {
            task.toLocalAllDayEvent()
        }

        val rrule = row.rrule.orEmpty()
        if (rrule.isNotEmpty()) {
            val repetition = Parser().parseRepeatInterval(rrule, task.startTS)
            task.repeatInterval = repetition.repeatInterval
            task.repeatRule = repetition.repeatRule
            task.repeatLimit = repetition.repeatLimit
            task.repetitionExceptions = Parser().parseExDates(row.exdate.orEmpty())
        }

        return task
    }

    fun toValues(task: Event): TaskValues {
        val isAllDay = task.getIsAllDay()
        val rrule = Parser().getRepeatCode(task)
        return TaskValues(
            title = task.title,
            description = task.description,
            location = task.location,
            isAllDay = isAllDay,
            due = toProviderTime(task.startTS, isAllDay),
            timeZone = if (isAllDay) null else task.getTimeZoneString(),
            rrule = rrule.ifEmpty { null },
            exdate = if (rrule.isEmpty()) null else formatExDates(task),
            color = task.color.takeIf { it != 0 },
        )
    }

    fun toProviderTime(ts: Long, isAllDay: Boolean) = if (isAllDay) {
        Formatter.getShiftedUtcTS(ts) * 1000L
    } else {
        ts * 1000L
    }

    fun fromProviderTime(millis: Long, isAllDay: Boolean) = if (isAllDay) {
        Formatter.getShiftedLocalTS(millis / 1000L)
    } else {
        millis / 1000L
    }

    fun isCompleted(status: Int) = status == Tasks.STATUS_COMPLETED

    private fun toEventStatus(status: Int) = if (status == Tasks.STATUS_CANCELLED) {
        CalendarContract.Events.STATUS_CANCELED
    } else {
        CalendarContract.Events.STATUS_CONFIRMED
    }

    fun toReminders(alarms: List<AlarmRow>) = alarms.map {
        val type = if (it.alarmType == Alarm.ALARM_TYPE_EMAIL) REMINDER_EMAIL else REMINDER_NOTIFICATION
        Reminder(it.minutesBefore, type)
    }.sortedBy { it.minutes }.take(MAX_REMINDERS)

    fun toAlarms(task: Event) = task.getReminders().map {
        val type = if (it.type == REMINDER_EMAIL) Alarm.ALARM_TYPE_EMAIL else Alarm.ALARM_TYPE_MESSAGE
        AlarmRow(it.minutes, type)
    }

    // repetition exceptions are day codes in the local time zone,
    // the provider wants dates for all-day tasks and UTC date-times otherwise
    private fun formatExDates(task: Event): String? {
        if (task.repetitionExceptions.isEmpty()) {
            return null
        }

        if (task.getIsAllDay()) {
            return task.repetitionExceptions.joinToString(",")
        }

        val start = Formatter.getDateTimeFromTS(task.startTS)
        return task.repetitionExceptions.joinToString(",") { dayCode ->
            val date = Formatter.getLocalDateTimeFromCode(dayCode)
            start.withDate(date.year, date.monthOfYear, date.dayOfMonth)
                .withZone(DateTimeZone.UTC)
                .toString(DateTimeFormat.forPattern(EXDATE_UTC_PATTERN))
        }
    }
}
