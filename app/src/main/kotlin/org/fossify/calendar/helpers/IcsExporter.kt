package org.fossify.calendar.helpers

import android.content.Context
import android.provider.CalendarContract.Events
import org.fossify.calendar.R
import org.fossify.calendar.extensions.calDAVHelper
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.helpers.IcsExporter.ExportResult.EXPORT_FAIL
import org.fossify.calendar.helpers.IcsExporter.ExportResult.EXPORT_OK
import org.fossify.calendar.helpers.IcsExporter.ExportResult.EXPORT_PARTIAL
import org.fossify.calendar.models.CalDAVCalendar
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.writeLn
import org.fossify.commons.helpers.ensureBackgroundThread
import org.joda.time.DateTimeZone
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter

class IcsExporter(private val context: Context) {
    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    private val MAX_LINE_LENGTH = 75
    private var eventsExported = 0
    private var eventsFailed = 0
    private var calendars = ArrayList<CalDAVCalendar>()
    private val reminderLabel = context.getString(R.string.reminder)
    private val exportTime = Formatter.getExportedTime(System.currentTimeMillis())

    fun exportEvents(
        outputStream: OutputStream?,
        events: List<Event>,
        showExportingToast: Boolean,
        callback: (result: ExportResult) -> Unit
    ) {
        if (outputStream == null) {
            callback(EXPORT_FAIL)
            return
        }

        ensureBackgroundThread {
            calendars = context.calDAVHelper.getCalDAVCalendars("", false)
            if (showExportingToast) {
                context.toast(org.fossify.commons.R.string.exporting)
            }

            object : BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)) {
                val lineSeparator = "\r\n"

                /**
                 * Writes a line separator. The line separator string is defined by RFC 5545 in 3.1. Content Lines:
                 * Content Lines are delimited by a line break, which is a CRLF sequence (CR character followed by LF character).
                 *
                 * @see <a href="https://icalendar.org/iCalendar-RFC-5545/3-1-content-lines.html">RFC 5545 - 3.1. Content Lines</a>
                 */
                override fun newLine() {
                    write(lineSeparator)
                }
            }.use { out ->
                out.writeContentLine(BEGIN_CALENDAR)
                out.writeContentLine(CALENDAR_PRODID)
                out.writeContentLine(CALENDAR_VERSION)
                for (event in events) {
                    if (event.isTask()) {
                        writeTask(out, event)
                    } else {
                        writeEvent(out, event)
                    }
                }
                out.writeContentLine(END_CALENDAR)
            }

            callback(
                when {
                    eventsExported == 0 -> EXPORT_FAIL
                    eventsFailed > 0 -> EXPORT_PARTIAL
                    else -> EXPORT_OK
                }
            )
        }
    }

    private fun fillReminders(event: Event, out: BufferedWriter, reminderLabel: String) {
        event.getReminders().forEach {
            val reminder = it
            out.apply {
                writeContentLine(BEGIN_ALARM)
                writeContentLine("$DESCRIPTION_EXPORT$reminderLabel")
                if (reminder.type == REMINDER_NOTIFICATION) {
                    writeContentLine("$ACTION$DISPLAY")
                } else {
                    writeContentLine("$ACTION$EMAIL")
                    val attendee =
                        calendars.firstOrNull { it.id == event.getCalDAVCalendarId() }?.accountName
                    if (attendee != null) {
                        writeContentLine("$ATTENDEE$MAILTO$attendee")
                    }
                }

                val sign = if (reminder.minutes < -1) "" else "-"
                writeContentLine("$TRIGGER:$sign${Parser().getDurationCode(Math.abs(reminder.minutes.toLong()))}")
                writeContentLine(END_ALARM)
            }
        }
    }

    private fun fillIgnoredOccurrences(event: Event, out: BufferedWriter) {
        event.repetitionExceptions.forEach {
            out.writeContentLine("$EXDATE:$it")
        }
    }

    private fun foldContentLine(line: String): Sequence<String> = sequence {
        var index = 0
        var isFirstLine = true

        while (index < line.length) {
            var end = index + MAX_LINE_LENGTH
            // Take the prepended space into account.
            if (!isFirstLine) end--
            if (end > line.length) {
                end = line.length
            } else {
                // Avoid splitting surrogate pairs
                if (Character.isHighSurrogate(line[end - 1])) {
                    end--
                }
            }

            val substring = line.substring(index, end)
            if (isFirstLine) {
                yield(substring)
            } else {
                yield(" $substring")
            }

            isFirstLine = false
            index = end
        }
    }

    private fun writeEvent(writer: BufferedWriter, event: Event) {
        val calendarColors = context.eventsHelper.getCalendarColors()
        with(writer) {
            writeContentLine(BEGIN_EVENT)
            event.title.let { if (it.isNotEmpty()) writeTextProperty(SUMMARY, it) }
            event.importId.let { if (it.isNotEmpty()) writeContentLine("$UID$it") }
            writeContentLine("$CATEGORY_COLOR${context.calendarsDB.getCalendarWithId(event.calendarId)?.color}")
            if (event.color != 0 && event.color != calendarColors[event.calendarId]) {
                val color = CssColors.findClosestCssColor(event.color)
                if (color != null) {
                    writeContentLine("$COLOR${color}")
                }
                writeContentLine("$FOSSIFY_COLOR${event.color}")
            }
            writeContentLine("$CATEGORIES${context.calendarsDB.getCalendarWithId(event.calendarId)?.title}")
            writeContentLine("$LAST_MODIFIED:${Formatter.getExportedTime(event.lastUpdated)}")
            writeContentLine("$TRANSP${if (event.availability == Events.AVAILABILITY_FREE) TRANSPARENT else OPAQUE}")
            event.location.let { if (it.isNotEmpty()) writeContentLine("$LOCATION:$it") }

            if (event.getIsAllDay()) {
                val tz = try {
                    DateTimeZone.forID(event.timeZone)
                } catch (ignored: IllegalArgumentException) {
                    DateTimeZone.getDefault()
                }
                writeContentLine("$DTSTART;$VALUE=$DATE:${Formatter.getDayCodeFromTS(event.startTS, tz)}")
                writeContentLine(
                    "$DTEND;$VALUE=$DATE:${
                        Formatter.getDayCodeFromTS(
                            event.endTS + TWELVE_HOURS,
                            tz
                        )
                    }"
                )
            } else {
                writeContentLine("$DTSTART:${Formatter.getExportedTime(event.startTS * 1000L)}")
                writeContentLine("$DTEND:${Formatter.getExportedTime(event.endTS * 1000L)}")
            }
            writeContentLine("$MISSING_YEAR${if (event.hasMissingYear()) 1 else 0}")

            writeContentLine("$DTSTAMP$exportTime")
            writeContentLine("$CLASS:${getAccessLevelStringFromEventAccessLevel(event.accessLevel)}")
            writeContentLine("$STATUS${getStatusStringFromEventStatus(event.status)}")
            Parser().getRepeatCode(event).let { if (it.isNotEmpty()) writeContentLine("$RRULE$it") }

            writeTextProperty(DESCRIPTION, event.description)
            fillReminders(event, writer, reminderLabel)
            fillIgnoredOccurrences(event, writer)

            eventsExported++
            writeContentLine(END_EVENT)
        }
    }

    private fun writeTask(writer: BufferedWriter, task: Event) {
        val calendarColors = context.eventsHelper.getCalendarColors()
        with(writer) {
            writeContentLine(BEGIN_TASK)
            task.title.let { if (it.isNotEmpty()) writeTextProperty(SUMMARY, it) }
            task.importId.let { if (it.isNotEmpty()) writeContentLine("$UID$it") }
            writeContentLine("$CATEGORY_COLOR${context.calendarsDB.getCalendarWithId(task.calendarId)?.color}")
            if (task.color != 0 && task.color != calendarColors[task.calendarId]) {
                val color = CssColors.findClosestCssColor(task.color)
                if (color != null) {
                    writeContentLine("$COLOR${color}")
                }
                writeContentLine("$FOSSIFY_COLOR${task.color}")
            }
            writeContentLine("$CATEGORIES${context.calendarsDB.getCalendarWithId(task.calendarId)?.title}")
            writeContentLine("$LAST_MODIFIED:${Formatter.getExportedTime(task.lastUpdated)}")
            task.location.let { if (it.isNotEmpty()) writeContentLine("$LOCATION:$it") }

            if (task.getIsAllDay()) {
                writeContentLine("$DTSTART;$VALUE=$DATE:${Formatter.getDayCodeFromTS(task.startTS)}")
            } else {
                writeContentLine("$DTSTART:${Formatter.getExportedTime(task.startTS * 1000L)}")
            }

            writeContentLine("$DTSTAMP$exportTime")
            if (task.isTaskCompleted()) {
                writeContentLine("$STATUS$COMPLETED")
            }
            Parser().getRepeatCode(task).let { if (it.isNotEmpty()) writeContentLine("$RRULE$it") }

            writeTextProperty(DESCRIPTION, task.description)
            fillReminders(task, writer, reminderLabel)
            fillIgnoredOccurrences(task, writer)

            eventsExported++
            writeContentLine(END_TASK)
        }
    }

    private fun BufferedWriter.writeContentLine(line: String) {
        for (segment in foldContentLine(line)) {
            this.writeLn(segment)
        }
    }

    private fun BufferedWriter.writeTextProperty(name: String, value: String) {
        val normalizedValue = value
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val escapedValue = normalizedValue
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(";", "\\;")
            .replace(",", "\\,")

        writeContentLine("$name:$escapedValue")
    }
}
