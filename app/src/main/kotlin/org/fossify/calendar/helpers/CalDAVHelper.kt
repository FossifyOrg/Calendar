package org.fossify.calendar.helpers

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Colors
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.widget.Toast
import org.fossify.calendar.R
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventTypesDB
import org.fossify.calendar.extensions.eventsDB
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.queryCursorInlined
import org.fossify.calendar.extensions.refreshCalDAVCalendars
import org.fossify.calendar.extensions.scheduleCalDAVSync
import org.fossify.calendar.extensions.toLocalAllDayEvent
import org.fossify.calendar.extensions.toUtcAllDayEvent
import org.fossify.calendar.models.Attendee
import org.fossify.calendar.models.CalDAVCalendar
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.EventType
import org.fossify.calendar.models.Reminder
import org.fossify.calendar.objects.States.isUpdatingCalDAV
import org.fossify.commons.extensions.areDigitsOnly
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getIntValueOrNull
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.PERMISSION_READ_CALENDAR
import org.fossify.commons.helpers.PERMISSION_WRITE_CALENDAR
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import kotlin.math.max

@SuppressLint("MissingPermission")
class CalDAVHelper(val context: Context) {
    private val eventsHelper = context.eventsHelper

    fun refreshCalendars(showToasts: Boolean, scheduleNextSync: Boolean, callback: () -> Unit) {
        if (isUpdatingCalDAV) {
            return
        }

        isUpdatingCalDAV = true
        try {
            val calDAVCalendars =
                getCalDAVCalendars(context.config.caldavSyncedCalendarIds, showToasts)
            for (calendar in calDAVCalendars) {
                val localEventType =
                    eventsHelper.getEventTypeWithCalDAVCalendarId(calendar.id) ?: continue
                if (calendar.displayName != localEventType.title || calendar.color != localEventType.color) {
                    localEventType.apply {
                        title = calendar.displayName
                        caldavDisplayName = calendar.displayName
                        caldavEmail = calendar.accountName
                        color = calendar.color
                        eventsHelper.insertOrUpdateEventTypeSync(this)
                    }
                }

                fetchCalDAVCalendarEvents(calendar, localEventType.id!!, showToasts)
            }

            if (scheduleNextSync) {
                context.scheduleCalDAVSync(true)
            }

            callback()
        } finally {
            isUpdatingCalDAV = false
        }
    }

    @SuppressLint("MissingPermission")
    fun getCalDAVCalendars(ids: String, showToasts: Boolean): ArrayList<CalDAVCalendar> {
        val calendars = ArrayList<CalDAVCalendar>()
        if (
            !context.hasPermission(PERMISSION_WRITE_CALENDAR) ||
            !context.hasPermission(PERMISSION_READ_CALENDAR)
        ) {
            return calendars
        }

        val uri = Calendars.CONTENT_URI
        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.OWNER_ACCOUNT,
            Calendars.CALENDAR_COLOR,
            Calendars.CALENDAR_ACCESS_LEVEL
        )

        val selection = if (ids.trim().isNotEmpty()) "${Calendars._ID} IN ($ids)" else null
        context.queryCursor(uri, projection, selection, showErrors = showToasts) { cursor ->
            calendars.add(
                CalDAVCalendar(
                    id = cursor.getIntValue(Calendars._ID),
                    displayName = cursor.getStringValue(Calendars.CALENDAR_DISPLAY_NAME),
                    accountName = cursor.getStringValue(Calendars.ACCOUNT_NAME),
                    accountType = cursor.getStringValue(Calendars.ACCOUNT_TYPE),
                    ownerName = cursor.getStringValue(Calendars.OWNER_ACCOUNT) ?: "",
                    color = cursor.getIntValue(Calendars.CALENDAR_COLOR),
                    accessLevel = cursor.getIntValue(Calendars.CALENDAR_ACCESS_LEVEL)
                )
            )
        }

        return calendars
    }

    fun updateCalDAVCalendar(eventType: EventType) {
        val uri =
            ContentUris.withAppendedId(Calendars.CONTENT_URI, eventType.caldavCalendarId.toLong())
        val values = ContentValues().apply {
            val colorKey = getCalDAVColorKey(eventType)
            if (colorKey != null) {
                put(Calendars.CALENDAR_COLOR_KEY, getCalDAVColorKey(eventType))
            } else {
                put(Calendars.CALENDAR_COLOR, eventType.color)
                put(Calendars.CALENDAR_COLOR_KEY, "")
            }
            put(Calendars.CALENDAR_DISPLAY_NAME, eventType.title)
        }

        try {
            context.contentResolver.update(uri, values, null, null)
            context.eventTypesDB.insertOrUpdate(eventType)
        } catch (_: IllegalArgumentException) {
        } catch (e: SecurityException) {
            context.showErrorToast(e)
        }
    }

    private fun getCalDAVColorKey(eventType: EventType): String? {
        val colors = getAvailableCalDAVCalendarColors(eventType)
        return colors[eventType.color]
    }

    @SuppressLint("MissingPermission")
    fun getAvailableCalDAVCalendarColors(
        eventType: EventType,
        colorType: Int = Colors.TYPE_CALENDAR,
    ): Map<Int, String> {
        val colors = mutableMapOf<Int, String>()
        val uri = Colors.CONTENT_URI
        val projection = arrayOf(Colors.COLOR, Colors.COLOR_KEY)
        val selection = "${Colors.COLOR_TYPE} = ? AND ${Colors.ACCOUNT_NAME} = ?"
        val selectionArgs = arrayOf(colorType.toString(), eventType.caldavEmail)

        context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
            val colorKey = cursor.getStringValue(Colors.COLOR_KEY)
            val color = cursor.getIntValue(Colors.COLOR)
            colors[color] = colorKey
        }
        return colors.toSortedMap(HsvColorComparator())
    }

    @SuppressLint("MissingPermission")
    private fun fetchCalDAVCalendarEvents(
        calendar: CalDAVCalendar,
        eventTypeId: Long,
        showToasts: Boolean,
    ) {
        val calendarId = calendar.id

        val importIdsMap = HashMap<String, Event>()
        val fetchedEventIds = ArrayList<String>()

        var errorFetchingLocalEvents = false
        val existingEvents = try {
            context.eventsDB.getEventsFromCalDAVCalendar("$CALDAV-$calendarId")
        } catch (e: Exception) {
            errorFetchingLocalEvents = true
            ArrayList()
        }

        existingEvents.forEach {
            importIdsMap[it.importId] = it
        }

        val uri = Events.CONTENT_URI
        val projection = arrayOf(
            Events._ID,
            Events.TITLE,
            Events.DESCRIPTION,
            Events.DTSTART,
            Events.DTEND,
            Events.DURATION,
            Events.EXDATE,
            Events.ALL_DAY,
            Events.RRULE,
            Events.ORIGINAL_ID,
            Events.ORIGINAL_INSTANCE_TIME,
            Events.EVENT_LOCATION,
            Events.EVENT_TIMEZONE,
            Events.CALENDAR_TIME_ZONE,
            Events.DELETED,
            Events.AVAILABILITY,
            Events.ACCESS_LEVEL,
            Events.STATUS,
            Events.EVENT_COLOR
        )

        val selection = "${Events.CALENDAR_ID} = $calendarId"
        context.queryCursorInlined(uri, projection, selection, showErrors = showToasts) { cursor ->
            val deleted = cursor.getIntValue(Events.DELETED)
            if (deleted == 1) {
                return@queryCursorInlined
            }

            val id = cursor.getLongValue(Events._ID)
            val title = cursor.getStringValue(Events.TITLE) ?: ""

            if (errorFetchingLocalEvents) {
                context.toast(
                    context.getString(R.string.fetching_event_failed, "\"$title\""),
                    Toast.LENGTH_LONG
                )
                return
            }

            val description = cursor.getStringValue(Events.DESCRIPTION) ?: ""
            val startTS = cursor.getLongValue(Events.DTSTART) / 1000L
            var endTS = cursor.getLongValue(Events.DTEND) / 1000L
            val allDay = cursor.getIntValue(Events.ALL_DAY)
            val rrule = cursor.getStringValue(Events.RRULE) ?: ""
            val location = cursor.getStringValue(Events.EVENT_LOCATION) ?: ""
            val originalId = cursor.getStringValue(Events.ORIGINAL_ID)
            val originalInstanceTime = cursor.getLongValue(Events.ORIGINAL_INSTANCE_TIME)
            val reminders = getCalDAVEventReminders(id)
            val attendees = getCalDAVEventAttendees(id, calendar)
            val accessLevel = cursor.getIntValue(Events.ACCESS_LEVEL)
            val availability = cursor.getIntValue(Events.AVAILABILITY)
            val status = cursor.getIntValue(Events.STATUS)
            val color = cursor.getIntValueOrNull(Events.EVENT_COLOR) ?: 0

            if (endTS == 0L) {
                val duration = cursor.getStringValue(Events.DURATION) ?: ""
                endTS = startTS + Parser().parseDurationSeconds(duration)
            }

            val reminder1 = reminders.getOrNull(0)
            val reminder2 = reminders.getOrNull(1)
            val reminder3 = reminders.getOrNull(2)
            val importId = getCalDAVEventImportId(calendarId, id)
            val eventTimeZone = cursor.getStringValue(Events.EVENT_TIMEZONE)
                ?: cursor.getStringValue(Events.CALENDAR_TIME_ZONE) ?: DateTimeZone.getDefault().id

            val source = "$CALDAV-$calendarId"
            val repeatRule = Parser().parseRepeatInterval(rrule, startTS)
            val event = Event(
                id = null,
                startTS = startTS,
                endTS = endTS,
                title = title,
                location = location,
                description = description,
                reminder1Minutes = reminder1?.minutes ?: REMINDER_OFF,
                reminder2Minutes = reminder2?.minutes ?: REMINDER_OFF,
                reminder3Minutes = reminder3?.minutes ?: REMINDER_OFF,
                reminder1Type = reminder1?.type ?: REMINDER_NOTIFICATION,
                reminder2Type = reminder2?.type ?: REMINDER_NOTIFICATION,
                reminder3Type = reminder3?.type ?: REMINDER_NOTIFICATION,
                repeatInterval = repeatRule.repeatInterval,
                repeatRule = repeatRule.repeatRule,
                repeatLimit = repeatRule.repeatLimit,
                repetitionExceptions = ArrayList(),
                attendees = attendees,
                importId = importId,
                timeZone = eventTimeZone,
                flags = allDay,
                eventType = eventTypeId,
                source = source,
                accessLevel = accessLevel,
                availability = availability,
                color = color,
                status = status
            )

            if (event.getIsAllDay()) {
                event.toLocalAllDayEvent()
            }

            fetchedEventIds.add(importId)

            // if the event is an exception from another events repeat rule, find the original parent event
            if (originalInstanceTime != 0L) {
                val parentImportId = "$source-$originalId"
                val parentEvent = context.eventsDB.getEventWithImportId(parentImportId)
                val originalDayCode = Formatter.getDayCodeFromTS(originalInstanceTime / 1000L)
                if (parentEvent != null) {
                    // add this event to the parent event's list of exceptions
                    if (!parentEvent.repetitionExceptions.contains(originalDayCode)) {
                        parentEvent.addRepetitionException(originalDayCode)
                        eventsHelper.insertEvent(
                            event = parentEvent,
                            addToCalDAV = false,
                            showToasts = false,
                            enableEventType = false
                        )
                    }

                    // store the event in the local db only if it is an occurrence that has been modified and not deleted
                    if (status != Events.STATUS_CANCELED && title.isNotEmpty()) {
                        val storedEventId = context.eventsDB.getEventIdWithImportId(importId)
                        if (storedEventId != null) {
                            event.id = storedEventId
                        }
                        event.parentId = parentEvent.id!!
                        eventsHelper.insertEvent(
                            event = event,
                            addToCalDAV = false,
                            showToasts = false,
                            enableEventType = false
                        )
                    } else {
                        // delete the deleted exception event from local db
                        val storedEventId = context.eventsDB.getEventIdWithImportId(importId)
                        if (storedEventId != null) {
                            eventsHelper.deleteEvent(storedEventId, true)
                        }
                    }

                    return@queryCursorInlined
                }
            }

            // some calendars add repeatable event exceptions with using the "exdate" field, not by creating a child event that is an exception
            // exdate can be stored as "20190216T230000Z", but also as "Europe/Madrid;20201208T000000Z"
            val exdate = cursor.getStringValue(Events.EXDATE) ?: ""
            if (exdate.length > 8) {
                val lines = exdate.split("\n")
                for (line in lines) {
                    val dates = line.split(",", ";")
                    dates.filter { it.isNotEmpty() && it[0].isDigit() }.forEach {
                        if (it.endsWith("Z")) {
                            // convert for example "20190216T230000Z" to "20190217000000" in Slovakia in a weird way
                            val formatter = DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss'Z'")
                            val offset =
                                DateTimeZone.getDefault().getOffset(System.currentTimeMillis())
                            val dt = formatter.parseDateTime(it).plusMillis(offset)
                            val dayCode = Formatter.getDayCodeFromDateTime(dt)
                            event.addRepetitionException(dayCode)
                        } else {
                            val potentialTS = it.substring(0, 8)
                            if (potentialTS.areDigitsOnly()) {
                                event.addRepetitionException(potentialTS)
                            }
                        }
                    }
                }
            }

            if (importIdsMap.containsKey(event.importId)) {
                val existingEvent = importIdsMap[importId]
                val originalEventId = existingEvent!!.id

                existingEvent.apply {
                    this.id = null
                    lastUpdated = 0L
                    repetitionExceptions = ArrayList()
                }

                if (existingEvent.hashCode() != event.hashCode() && title.isNotEmpty()) {
                    event.id = originalEventId
                    eventsHelper.updateEvent(
                        event = event,
                        updateAtCalDAV = false,
                        showToasts = false,
                        enableEventType = false
                    )
                }
            } else {
                if (title.isNotEmpty()) {
                    importIdsMap[event.importId] = event
                    eventsHelper.insertEvent(
                        event = event,
                        addToCalDAV = false,
                        showToasts = false,
                        enableEventType = false
                    )
                }
            }
        }

        val eventIdsToDelete = ArrayList<Long>()
        importIdsMap.keys.filter { !fetchedEventIds.contains(it) }.forEach {
            val caldavEventId = it
            existingEvents.forEach { event ->
                if (event.importId == caldavEventId) {
                    eventIdsToDelete.add(event.id!!)
                }
            }
        }

        eventsHelper.deleteEvents(eventIdsToDelete.toMutableList(), false)
    }

    @SuppressLint("MissingPermission")
    fun insertCalDAVEvent(event: Event) {
        val uri = Events.CONTENT_URI
        val values = fillEventContentValues(event)
        val newUri = context.contentResolver.insert(uri, values)

        val calendarId = event.getCalDAVCalendarId()
        val eventRemoteID = java.lang.Long.parseLong(newUri!!.lastPathSegment!!)
        event.importId = getCalDAVEventImportId(calendarId, eventRemoteID)

        setupCalDAVEventReminders(event)
        setupCalDAVEventAttendees(event)
        setupCalDAVEventImportId(event)
        refreshCalDAVCalendar(event)
    }

    fun updateCalDAVEvent(event: Event) {
        val uri = Events.CONTENT_URI
        val values = fillEventContentValues(event)
        val eventRemoteID = event.getCalDAVEventId()
        event.importId = getCalDAVEventImportId(event.getCalDAVCalendarId(), eventRemoteID)

        val newUri = ContentUris.withAppendedId(uri, eventRemoteID)
        context.contentResolver.update(newUri, values, null, null)

        setupCalDAVEventReminders(event)
        setupCalDAVEventAttendees(event)
        setupCalDAVEventImportId(event)
        refreshCalDAVCalendar(event)
    }

    private fun setupCalDAVEventReminders(event: Event) {
        clearEventReminders(event)
        event.getReminders().forEach {
            val contentValues = ContentValues().apply {
                put(Reminders.MINUTES, it.minutes)
                put(
                    Reminders.METHOD,
                    if (it.type == REMINDER_EMAIL) Reminders.METHOD_EMAIL else Reminders.METHOD_ALERT
                )
                put(Reminders.EVENT_ID, event.getCalDAVEventId())
            }

            try {
                context.contentResolver.insert(Reminders.CONTENT_URI, contentValues)
            } catch (e: Exception) {
                context.toast(org.fossify.commons.R.string.unknown_error_occurred)
            }
        }
    }

    private fun setupCalDAVEventAttendees(event: Event) {
        clearEventAttendees(event)
        event.attendees.forEach {
            val contentValues = ContentValues().apply {
                put(Attendees.ATTENDEE_NAME, it.name)
                put(Attendees.ATTENDEE_EMAIL, it.email)
                put(Attendees.ATTENDEE_STATUS, it.status)
                put(Attendees.ATTENDEE_RELATIONSHIP, it.relationship)
                put(Attendees.EVENT_ID, event.getCalDAVEventId())
            }

            try {
                context.contentResolver.insert(Attendees.CONTENT_URI, contentValues)
            } catch (e: Exception) {
                context.toast(org.fossify.commons.R.string.unknown_error_occurred)
            }
        }
    }

    private fun setupCalDAVEventImportId(event: Event) {
        context.eventsDB.updateEventImportIdAndSource(
            importId = event.importId,
            source = "$CALDAV-${event.getCalDAVCalendarId()}",
            id = event.id!!
        )
    }

    private fun fillEventContentValues(event: Event): ContentValues {
        val calendarId = event.getCalDAVCalendarId()
        return ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, event.title)
            put(Events.DESCRIPTION, event.description)
            put(Events.EVENT_LOCATION, event.location)
            put(Events.STATUS, event.status)
            put(Events.ACCESS_LEVEL, event.accessLevel)
            put(Events.AVAILABILITY, event.availability)

            if (event.color == 0) {
                put(Events.EVENT_COLOR_KEY, "")
            } else {
                val eventType = eventsHelper.getEventTypeWithCalDAVCalendarId(calendarId)!!
                val colors = getAvailableCalDAVCalendarColors(eventType, Colors.TYPE_EVENT)
                put(Events.EVENT_COLOR_KEY, colors[event.color])
            }

            val repeatRule = Parser().getRepeatCode(event)
            if (repeatRule.isEmpty()) {
                putNull(Events.RRULE)
            } else {
                put(Events.RRULE, repeatRule)
            }

            if (event.getIsAllDay()) {
                event.toUtcAllDayEvent()
                put(Events.ALL_DAY, 1)
            } else {
                put(Events.ALL_DAY, 0)
            }

            val parentEventId = event.parentId
            if (parentEventId != 0L) {
                val parentEvent = context.eventsDB.getEventWithId(parentEventId) ?: return@apply
                val isParentAllDay = parentEvent.getIsAllDay()
                // original instance time must be in UTC when the parent is an all-day event
                val originalInstanceTS = if (isParentAllDay && !event.getIsAllDay()) {
                    Formatter.getShiftedUtcTS(event.startTS)
                } else {
                    event.startTS
                }
                put(Events.ORIGINAL_ID, parentEvent.getCalDAVEventId())
                put(Events.ORIGINAL_INSTANCE_TIME, originalInstanceTS * 1000L)
                if (isParentAllDay) {
                    put(Events.ORIGINAL_ALL_DAY, 1)
                } else {
                    put(Events.ORIGINAL_ALL_DAY, 0)
                }
            }

            put(Events.DTSTART, event.startTS * 1000L)
            put(Events.EVENT_TIMEZONE, event.getTimeZoneString())
            if (event.repeatInterval > 0) {
                put(Events.DURATION, getDurationCode(event))
                putNull(Events.DTEND)
            } else {
                put(Events.DTEND, event.endTS * 1000L)
                putNull(Events.DURATION)
            }
        }
    }

    private fun clearEventReminders(event: Event) {
        val selection = "${Reminders.EVENT_ID} = ?"
        val selectionArgs = arrayOf(event.getCalDAVEventId().toString())
        context.contentResolver.delete(Reminders.CONTENT_URI, selection, selectionArgs)
    }

    private fun clearEventAttendees(event: Event) {
        val selection = "${Attendees.EVENT_ID} = ?"
        val selectionArgs = arrayOf(event.getCalDAVEventId().toString())
        context.contentResolver.delete(Attendees.CONTENT_URI, selection, selectionArgs)
    }

    private fun getDurationCode(event: Event): String {
        return if (event.getIsAllDay()) {
            val dur = max(1, (event.endTS - event.startTS) / DAY)
            "P${dur}D"
        } else {
            Parser().getDurationCode((event.endTS - event.startTS) / 60L)
        }
    }

    fun deleteCalDAVCalendarEvents(calendarId: Long) {
        val eventIds =
            context.eventsDB.getCalDAVCalendarEvents("$CALDAV-$calendarId").toMutableList()
        eventsHelper.deleteEvents(eventIds, false)
    }

    fun deleteCalDAVEvent(event: Event) {
        val uri = Events.CONTENT_URI
        val contentUri = ContentUris.withAppendedId(uri, event.getCalDAVEventId())
        try {
            context.contentResolver.delete(contentUri, null, null)
        } catch (ignored: Exception) {
        }
        refreshCalDAVCalendar(event)
    }

    fun insertEventRepeatException(parentEvent: Event, occurrenceTS: Long) {
        val uri = Events.CONTENT_URI
        val values = fillEventRepeatExceptionValues(parentEvent, occurrenceTS)
        try {
            context.contentResolver.insert(uri, values)
            refreshCalDAVCalendar(parentEvent)
        } catch (e: Exception) {
            context.showErrorToast(e)
        }
    }

    private fun fillEventRepeatExceptionValues(
        parentEvent: Event,
        occurrenceTS: Long,
    ): ContentValues {
        val isAllDay = parentEvent.getIsAllDay()
        val startMillis = if (isAllDay) {
            // original instance time must be in UTC since the parent event is an all-day event thus everything is handled in UTC
            Formatter.getShiftedUtcTS(occurrenceTS) * 1000L
        } else {
            occurrenceTS * 1000L
        }
        val durationMillis = (parentEvent.endTS - parentEvent.startTS) * 1000L
        return ContentValues().apply {
            put(Events.CALENDAR_ID, parentEvent.getCalDAVCalendarId())
            put(Events.DTSTART, startMillis)
            put(Events.DTEND, startMillis + durationMillis)
            put(Events.EVENT_TIMEZONE, parentEvent.getTimeZoneString())
            put(Events.ORIGINAL_ID, parentEvent.getCalDAVEventId())
            put(Events.ORIGINAL_INSTANCE_TIME, startMillis)
            put(Events.STATUS, Events.STATUS_CANCELED)
            if (isAllDay) {
                put(Events.ORIGINAL_ALL_DAY, 1)
            } else {
                put(Events.ORIGINAL_ALL_DAY, 0)
            }
        }
    }

    private fun getCalDAVEventReminders(eventId: Long): List<Reminder> {
        val reminders = ArrayList<Reminder>()
        val uri = Reminders.CONTENT_URI
        val projection = arrayOf(
            Reminders.MINUTES,
            Reminders.METHOD
        )
        val selection = "${Reminders.EVENT_ID} = $eventId"

        context.queryCursor(uri, projection, selection) { cursor ->
            val minutes = cursor.getIntValue(Reminders.MINUTES)
            val method = cursor.getIntValue(Reminders.METHOD)
            if (method == Reminders.METHOD_ALERT || method == Reminders.METHOD_EMAIL) {
                val type =
                    if (method == Reminders.METHOD_EMAIL) REMINDER_EMAIL else REMINDER_NOTIFICATION
                val reminder = Reminder(minutes, type)
                reminders.add(reminder)
            }
        }

        return reminders.sortedBy { it.minutes }
    }

    private fun getCalDAVEventAttendees(eventId: Long, calendar: CalDAVCalendar): List<Attendee> {
        val attendees = ArrayList<Attendee>()
        val uri = Attendees.CONTENT_URI
        val projection = arrayOf(
            Attendees.ATTENDEE_NAME,
            Attendees.ATTENDEE_EMAIL,
            Attendees.ATTENDEE_STATUS,
            Attendees.ATTENDEE_RELATIONSHIP
        )
        val selection = "${Attendees.EVENT_ID} = $eventId"
        context.queryCursor(uri, projection, selection) { cursor ->
            val email = cursor.getStringValue(Attendees.ATTENDEE_EMAIL) ?: ""
            val attendee =
                Attendee(
                    contactId = 0,
                    name = cursor.getStringValue(Attendees.ATTENDEE_NAME) ?: "",
                    email = email,
                    status = cursor.getIntValue(Attendees.ATTENDEE_STATUS),
                    photoUri = "",
                    isMe = email == calendar.ownerName,
                    relationship = cursor.getIntValue(Attendees.ATTENDEE_RELATIONSHIP)
                )
            attendees.add(attendee)
        }

        return attendees
    }

    private fun getCalDAVEventImportId(calendarId: Int, eventId: Long): String {
        return "$CALDAV-$calendarId-$eventId"
    }

    private fun refreshCalDAVCalendar(event: Event) {
        context.refreshCalDAVCalendars(event.getCalDAVCalendarId().toString(), false)
    }
}
