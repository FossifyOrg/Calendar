package org.fossify.calendar.helpers

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.collection.LongSparseArray
import org.fossify.calendar.R
import org.fossify.calendar.extensions.calDAVHelper
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.cancelNotification
import org.fossify.calendar.extensions.cancelPendingIntent
import org.fossify.calendar.extensions.completedTasksDB
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsDB
import org.fossify.calendar.extensions.isTsOnProperDay
import org.fossify.calendar.extensions.isXWeeklyRepetition
import org.fossify.calendar.extensions.maybeAdjustRepeatLimitCount
import org.fossify.calendar.extensions.scheduleNextEventReminder
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.extensions.updateWidgets
import org.fossify.calendar.models.CalendarEntity
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.CHOPPED_LIST_DEFAULT_SIZE
import org.fossify.commons.helpers.ensureBackgroundThread

class EventsHelper(val context: Context) {
    private val config = context.config
    private val eventsDB = context.eventsDB
    private val calendarsDB = context.calendarsDB
    private val completedTasksDB = context.completedTasksDB

    fun getCalendars(
        activity: Activity,
        showWritableOnly: Boolean,
        callback: (calendars: ArrayList<CalendarEntity>) -> Unit
    ) {
        ensureBackgroundThread {
            var calendars = ArrayList<CalendarEntity>()
            try {
                calendars = calendarsDB.getCalendars().toMutableList() as ArrayList<CalendarEntity>
            } catch (ignored: Exception) {
            }

            if (showWritableOnly) {
                val caldavCalendars = activity.calDAVHelper.getCalDAVCalendars("", true)
                calendars = calendars.filter {
                    val calendar = it
                    it.caldavCalendarId == 0 || caldavCalendars.firstOrNull { it.id == calendar.caldavCalendarId }
                        ?.canWrite() == true
                }.toMutableList() as ArrayList<CalendarEntity>
            }

            activity.runOnUiThread {
                callback(calendars)
            }
        }
    }

    fun getCalendarsSync() = calendarsDB.getCalendars().toMutableList() as ArrayList<CalendarEntity>

    fun insertOrUpdateCalendar(
        activity: Activity,
        calendar: CalendarEntity,
        callback: ((newCalendarId: Long) -> Unit)? = null
    ) {
        ensureBackgroundThread {
            val calendarId = insertOrUpdateCalendarSync(calendar)
            activity.runOnUiThread {
                callback?.invoke(calendarId)
            }
        }
    }

    fun insertOrUpdateCalendarSync(calendar: CalendarEntity): Long {
        if (calendar.id != null && calendar.id!! > 0 && calendar.caldavCalendarId != 0) {
            context.calDAVHelper.updateCalDAVCalendar(calendar)
        }

        val newId = calendarsDB.insertOrUpdate(calendar)
        if (calendar.id == null) {
            config.addDisplayCalendar(newId.toString())

            if (config.quickFilterCalendars.isNotEmpty()) {
                config.addQuickFilterCalendar(newId.toString())
            } else {
                val calendars = getCalendarsSync()
                if (calendars.size == 2) {
                    calendars.forEach {
                        config.addQuickFilterCalendar(it.id.toString())
                    }
                }
            }
        }
        return newId
    }

    fun getCalendarIdWithTitle(title: String) = calendarsDB.getCalendarIdWithTitle(title) ?: -1L

    fun getCalendarIdWithClass(classId: Int) = calendarsDB.getCalendarIdWithClass(classId) ?: -1L

    private fun getLocalCalendarIdWithTitle(title: String) =
        calendarsDB.getLocalCalendarIdWithTitle(title) ?: -1L

    private fun getLocalCalendarIdWithClass(classId: Int) =
        calendarsDB.getLocalCalendarIdWithClass(classId) ?: -1L

    fun getCalendarWithCalDAVCalendarId(calendarId: Int) =
        calendarsDB.getCalendarWithCalDAVCalendarId(calendarId)

    fun deleteCalendars(calendars: ArrayList<CalendarEntity>, deleteEvents: Boolean) {
        val typesToDelete = calendars
            .asSequence()
            .filter { it.caldavCalendarId == 0 && it.id != LOCAL_CALENDAR_ID }
            .toMutableList()
        val deleteIds = typesToDelete.map { it.id }.toMutableList()
        val deletedSet = deleteIds.map { it.toString() }.toHashSet()
        config.removeDisplayCalendars(deletedSet)

        if (deleteIds.isEmpty()) {
            return
        }

        for (calendarId in deleteIds) {
            if (deleteEvents) {
                deleteEventsAndTasksWithCalendarId(calendarId!!)
            } else {
                eventsDB.resetEventsAndTasksWithCalendarId(calendarId!!)
            }
        }

        calendarsDB.deleteCalendars(typesToDelete)

        if (getCalendarsSync().size == 1) {
            config.quickFilterCalendars = HashSet()
        }
    }

    fun insertEvent(
        event: Event,
        addToCalDAV: Boolean,
        showToasts: Boolean,
        enableCalendar: Boolean = true,
        eventOccurrenceTS: Long? = null,
        updateWidgets: Boolean = true,
        callback: ((id: Long) -> Unit)? = null
    ) {
        if (event.startTS > event.endTS) {
            callback?.invoke(0)
            return
        }

        event.id = eventsDB.insertOrUpdate(event)
        ensureCalendarVisibility(event, enableCalendar)
        if (updateWidgets) context.updateWidgets()
        context.scheduleNextEventReminder(event, showToasts)

        if (addToCalDAV && config.caldavSync && event.source != SOURCE_SIMPLE_CALENDAR && event.source != SOURCE_IMPORTED_ICS) {
            context.calDAVHelper.insertCalDAVEvent(event, eventOccurrenceTS)
        }

        callback?.invoke(event.id!!)
    }

    fun insertTask(
        task: Event,
        showToasts: Boolean,
        enableCalendar: Boolean = true,
        callback: () -> Unit
    ) {
        task.id = eventsDB.insertOrUpdate(task)
        ensureCalendarVisibility(task, enableCalendar)
        context.updateWidgets()
        context.scheduleNextEventReminder(task, showToasts)
        callback()
    }

    fun insertEvents(events: ArrayList<Event>, addToCalDAV: Boolean) {
        try {
            for (event in events) {
                if (event.startTS > event.endTS) {
                    context.toast(R.string.end_before_start, Toast.LENGTH_LONG)
                    continue
                }

                event.id = eventsDB.insertOrUpdate(event)
                ensureCalendarVisibility(event, true)
                context.scheduleNextEventReminder(event, false)
                if (addToCalDAV && event.source != SOURCE_SIMPLE_CALENDAR && event.source != SOURCE_IMPORTED_ICS && config.caldavSync) {
                    context.calDAVHelper.insertCalDAVEvent(event)
                }
            }
        } finally {
            context.updateWidgets()
        }
    }

    fun updateEvent(
        event: Event,
        updateAtCalDAV: Boolean,
        showToasts: Boolean,
        enableCalendar: Boolean = true,
        updateWidgets: Boolean = true,
        callback: (() -> Unit)? = null
    ) {
        eventsDB.insertOrUpdate(event)
        ensureCalendarVisibility(event, enableCalendar)
        if (updateWidgets) context.updateWidgets()
        context.scheduleNextEventReminder(event, showToasts)
        if (updateAtCalDAV && event.source != SOURCE_SIMPLE_CALENDAR && config.caldavSync) {
            context.calDAVHelper.updateCalDAVEvent(event)
        }
        callback?.invoke()
    }

    fun applyOriginalStartEndTimes(event: Event, oldStartTS: Long, oldEndTS: Long) {
        val originalEvent = eventsDB.getEventOrTaskWithId(event.id!!) ?: return
        val originalStartTS = originalEvent.startTS
        val originalEndTS = originalEvent.endTS

        event.apply {
            val startTSDelta = oldStartTS - startTS
            val endTSDelta = oldEndTS - endTS
            startTS = originalStartTS - startTSDelta
            endTS = if (isTask()) startTS else originalEndTS - endTSDelta
        }
    }

    fun editSelectedOccurrence(
        event: Event,
        eventOccurrenceTS: Long,
        showToasts: Boolean,
        callback: () -> Unit
    ) {
        ensureBackgroundThread {
            val originalEvent =
                eventsDB.getEventOrTaskWithId(event.id!!) ?: return@ensureBackgroundThread
            originalEvent.addRepetitionException(Formatter.getDayCodeFromTS(eventOccurrenceTS))
            eventsDB.updateEventRepetitionExceptions(
                originalEvent.repetitionExceptions.toString(),
                originalEvent.id!!
            )
            context.scheduleNextEventReminder(originalEvent, false)

            event.apply {
                parentId = id!!
                id = null
                repeatRule = 0
                repeatInterval = 0
                repeatLimit = 0
                repetitionExceptions = emptyList()
            }
            if (event.isTask()) {
                insertTask(event, showToasts = showToasts, callback = callback)
            } else {
                insertEvent(
                    event, addToCalDAV = true,
                    showToasts = showToasts,
                    eventOccurrenceTS = eventOccurrenceTS
                ) {
                    callback()
                }
            }
        }
    }

    fun editFutureOccurrences(
        event: Event,
        eventOccurrenceTS: Long,
        showToasts: Boolean,
        callback: () -> Unit
    ) {
        ensureBackgroundThread {
            val eventId = event.id!!
            val originalEvent =
                eventsDB.getEventOrTaskWithId(event.id!!) ?: return@ensureBackgroundThread
            event.maybeAdjustRepeatLimitCount(originalEvent, eventOccurrenceTS)
            event.id = null
            addEventRepeatLimit(eventId, eventOccurrenceTS)
            if (eventOccurrenceTS == originalEvent.startTS) {
                deleteEvent(eventId, true)
            }

            if (event.isTask()) {
                insertTask(event, showToasts = showToasts, callback = callback)
            } else {
                insertEvent(event, addToCalDAV = true, showToasts = showToasts) {
                    callback()
                }
            }
        }
    }

    fun editAllOccurrences(
        event: Event,
        originalStartTS: Long,
        originalEndTS: Long = 0,
        showToasts: Boolean,
        callback: () -> Unit
    ) {
        ensureBackgroundThread {
            applyOriginalStartEndTimes(event, originalStartTS, originalEndTS)
            updateEvent(
                event,
                updateAtCalDAV = !event.isTask(),
                showToasts = showToasts,
                callback = callback
            )
        }
    }

    private fun ensureCalendarVisibility(event: Event, enableCalendar: Boolean) {
        if (enableCalendar) {
            val calendar = event.calendarId.toString()
            val displayCalendars = config.displayCalendars
            if (!displayCalendars.contains(calendar)) {
                config.displayCalendars = displayCalendars.plus(calendar)
            }
        }
    }

    fun deleteAllEvents() {
        ensureBackgroundThread {
            val eventIds = eventsDB.getEventIds().toMutableList()
            deleteEvents(eventIds, true)
        }
    }

    fun deleteEvent(id: Long, deleteFromCalDAV: Boolean, updateWidgets: Boolean = true) {
        deleteEvents(arrayListOf(id), deleteFromCalDAV, updateWidgets)
    }

    fun deleteEvents(
        ids: MutableList<Long>,
        deleteFromCalDAV: Boolean,
        updateWidgets: Boolean = true
    ) {
        if (ids.isEmpty()) {
            return
        }

        ids.chunked(CHOPPED_LIST_DEFAULT_SIZE).forEach {
            val eventsWithImportId = eventsDB.getEventsByIdsWithImportIds(it)
            eventsDB.deleteEvents(it)

            it.forEach {
                context.cancelNotification(it)
                context.cancelPendingIntent(it)
            }

            if (deleteFromCalDAV && config.caldavSync) {
                eventsWithImportId.forEach {
                    context.calDAVHelper.deleteCalDAVEvent(it)
                }
            }

            deleteChildEvents(it as MutableList<Long>, deleteFromCalDAV, updateWidgets)
            if (updateWidgets) context.updateWidgets()
        }
    }

    private fun deleteChildEvents(
        ids: List<Long>,
        deleteFromCalDAV: Boolean,
        updateWidgets: Boolean = true
    ) {
        val childIds = eventsDB.getEventIdsWithParentIds(ids).toMutableList()
        if (childIds.isNotEmpty()) {
            deleteEvents(childIds, deleteFromCalDAV, updateWidgets)
        }
    }

    private fun deleteEventsAndTasksWithCalendarId(calendarId: Long) {
        val eventIds = eventsDB.getEventAndTasksIdsByCalendar(calendarId).toMutableList()
        deleteEvents(eventIds, true)
    }

    fun addEventRepeatLimit(eventId: Long, occurrenceTS: Long) {
        val event = eventsDB.getEventOrTaskWithId(eventId) ?: return
        val previousOccurrenceTS =
            occurrenceTS - event.repeatInterval // always update repeat limit of the occurrence preceding the one being edited
        val repeatLimitDateTime =
            Formatter.getDateTimeFromTS(previousOccurrenceTS).withTimeAtStartOfDay()
        val repeatLimitTS = if (event.getIsAllDay()) {
            repeatLimitDateTime.seconds()
        } else {
            repeatLimitDateTime.withTime(23, 59, 59, 0).seconds()
        }

        eventsDB.updateEventRepetitionLimit(repeatLimitTS, eventId)
        context.cancelNotification(eventId)
        context.cancelPendingIntent(eventId)
        if (config.caldavSync) {
            val event = eventsDB.getEventWithId(eventId)
            if (event != null && event.getCalDAVCalendarId() != 0) {
                context.calDAVHelper.updateCalDAVEvent(event)
            }
        }

        if (event.isTask()) {
            completedTasksDB.deleteTaskFutureOccurrences(eventId, occurrenceTS)
        }
    }

    fun doCalendarsContainEventsOrTasks(
        calendarIds: ArrayList<Long>,
        callback: (contain: Boolean) -> Unit
    ) {
        ensureBackgroundThread {
            val eventIds = eventsDB.getEventAndTasksIdsByCalendar(calendarIds)
            callback(eventIds.isNotEmpty())
        }
    }

    fun deleteRepeatingEventOccurrence(
        parentEventId: Long,
        occurrenceTS: Long,
        addToCalDAV: Boolean
    ) {
        ensureBackgroundThread {
            val parentEvent =
                eventsDB.getEventOrTaskWithId(parentEventId) ?: return@ensureBackgroundThread
            val occurrenceDayCode = Formatter.getDayCodeFromTS(occurrenceTS)
            parentEvent.addRepetitionException(occurrenceDayCode)
            eventsDB.updateEventRepetitionExceptions(
                parentEvent.repetitionExceptions.toString(),
                parentEventId
            )
            context.scheduleNextEventReminder(parentEvent, false)

            if (addToCalDAV && config.caldavSync) {
                context.calDAVHelper.insertEventRepeatException(parentEvent, occurrenceTS)
            }

            if (parentEvent.isTask()) {
                completedTasksDB.deleteTaskWithIdAndTs(parentEventId, occurrenceTS)
            }
        }
    }

    fun getEvents(
        fromTS: Long,
        toTS: Long,
        eventId: Long = -1L,
        applyTypeFilter: Boolean = true,
        searchQuery: String = "",
        callback: (events: ArrayList<Event>) -> Unit
    ) {
        ensureBackgroundThread {
            getEventsSync(fromTS, toTS, eventId, applyTypeFilter, searchQuery, callback)
        }
    }

    fun getEventsSync(
        fromTS: Long,
        toTS: Long,
        eventId: Long = -1L,
        applyTypeFilter: Boolean,
        searchQuery: String = "",
        callback: (events: ArrayList<Event>) -> Unit
    ) {
        val birthDayEventId = getLocalBirthdaysCalendarId(createIfNotExists = false)
        val anniversaryEventId = getAnniversariesCalendarId(createIfNotExists = false)

        var events = ArrayList<Event>()
        if (applyTypeFilter) {
            val displayCalendars = context.config.displayCalendars
            if (displayCalendars.isEmpty()) {
                callback(ArrayList())
                return
            } else {
                try {
                    val typesList = context.config.getDisplayCalendarsAsList()

                    if (searchQuery.isEmpty()) {
                        events.addAll(
                            eventsDB.getOneTimeEventsFromToWithCalendarIds(
                                toTS,
                                fromTS,
                                typesList
                            ).toMutableList() as ArrayList<Event>
                        )
                    } else {
                        events.addAll(
                            eventsDB.getOneTimeEventsFromToWithTypesForSearch(
                                toTS,
                                fromTS,
                                typesList,
                                "%$searchQuery%"
                            ).toMutableList() as ArrayList<Event>
                        )
                    }
                } catch (e: Exception) {
                }
            }
        } else {
            events.addAll(eventsDB.getTasksFromTo(fromTS, toTS, ArrayList()))

            events.addAll(
                if (eventId == -1L) {
                    eventsDB.getOneTimeEventsOrTasksFromTo(toTS, fromTS)
                        .toMutableList() as ArrayList<Event>
                } else {
                    eventsDB.getOneTimeEventFromToWithId(eventId, toTS, fromTS)
                        .toMutableList() as ArrayList<Event>
                }
            )
        }

        events.addAll(getRepeatableEventsFor(fromTS, toTS, eventId, applyTypeFilter, searchQuery))

        events = events
            .asSequence()
            .distinct()
            .filterNot { it.repetitionExceptions.contains(Formatter.getDayCodeFromTS(it.startTS)) }
            .toMutableList() as ArrayList<Event>

        val calendarColors = getCalendarColors()

        events.forEach {
            if (it.isTask()) {
                updateIsTaskCompleted(it)
            }

            it.updateIsPastEvent()
            val originalEvent = eventsDB.getEventWithId(it.id!!)
            if (originalEvent != null &&
                (birthDayEventId != -1L && it.calendarId == birthDayEventId) or
                (anniversaryEventId != -1L && it.calendarId == anniversaryEventId)
            ) {
                val eventStartDate = Formatter.getDateFromTS(it.startTS)
                val originalEventStartDate = Formatter.getDateFromTS(originalEvent.startTS)
                if (it.hasMissingYear().not()) {
                    val years = (eventStartDate.year - originalEventStartDate.year).coerceAtLeast(0)
                    if (years > 0) {
                        it.title = "${it.title} ($years)"
                    }
                }
            }

            if (it.color == 0) {
                it.color = calendarColors.get(it.calendarId) ?: context.getProperPrimaryColor()
            }
        }

        callback(events)
    }

    fun createPredefinedCalendar(
        title: String, @ColorRes colorResId: Int, type: Int, caldav: Boolean = false
    ): Long {
        val calendar = CalendarEntity(
            id = null,
            title = title,
            color = context.resources.getColor(colorResId),
            type = type
        )

        // check if the event type already exists but without the type (e.g. BIRTHDAY_EVENT) so as to avoid duplication
        val originalCalendarId = if (caldav) {
            getCalendarIdWithTitle(title)
        } else {
            getLocalCalendarIdWithTitle(title)
        }
        if (originalCalendarId != -1L) {
            calendar.id = originalCalendarId
        }

        return insertOrUpdateCalendarSync(calendar)
    }

    fun getLocalBirthdaysCalendarId(createIfNotExists: Boolean = true): Long {
        var calendarId = getLocalCalendarIdWithClass(BIRTHDAY_EVENT)
        if (calendarId == -1L && createIfNotExists) {
            val birthdays = context.getString(R.string.birthdays)
            calendarId =
                createPredefinedCalendar(birthdays, R.color.default_birthdays_color, BIRTHDAY_EVENT)
        }
        return calendarId
    }

    fun getAnniversariesCalendarId(createIfNotExists: Boolean = true): Long {
        var calendarId = getLocalCalendarIdWithClass(ANNIVERSARY_EVENT)
        if (calendarId == -1L && createIfNotExists) {
            val anniversaries = context.getString(R.string.anniversaries)
            calendarId = createPredefinedCalendar(
                anniversaries,
                R.color.default_anniversaries_color,
                ANNIVERSARY_EVENT
            )
        }
        return calendarId
    }

    fun getRepeatableEventsFor(
        fromTS: Long,
        toTS: Long,
        eventId: Long = -1L,
        applyTypeFilter: Boolean = false,
        searchQuery: String = ""
    ): List<Event> {
        val events = if (applyTypeFilter) {
            val displayCalendars = context.config.displayCalendars
            if (displayCalendars.isEmpty()) {
                return ArrayList()
            } else if (searchQuery.isEmpty()) {
                eventsDB.getRepeatableEventsOrTasksWithCalendarIds(
                    toTS,
                    context.config.getDisplayCalendarsAsList()
                ).toMutableList() as ArrayList<Event>
            } else {
                eventsDB.getRepeatableEventsOrTasksWithTypesForSearch(
                    toTS,
                    context.config.getDisplayCalendarsAsList(),
                    "%$searchQuery%"
                )
                    .toMutableList() as ArrayList<Event>
            }
        } else {
            if (eventId == -1L) {
                eventsDB.getRepeatableEventsOrTasksWithCalendarIds(toTS)
                    .toMutableList() as ArrayList<Event>
            } else {
                eventsDB.getRepeatableEventsOrTasksWithId(eventId, toTS)
                    .toMutableList() as ArrayList<Event>
            }
        }

        val startTimes = LongSparseArray<Long>()
        val newEvents = ArrayList<Event>()
        events.forEach {
            startTimes.put(it.id!!, it.startTS)
            if (it.repeatLimit >= 0) {
                newEvents.addAll(getEventsRepeatingTillDateOrForever(fromTS, toTS, startTimes, it))
            } else {
                newEvents.addAll(getEventsRepeatingXTimes(fromTS, toTS, startTimes, it))
            }
        }

        return newEvents
    }

    private fun getEventsRepeatingXTimes(
        fromTS: Long,
        toTS: Long,
        startTimes: LongSparseArray<Long>,
        event: Event
    ): ArrayList<Event> {
        val original = event.copy()
        val events = ArrayList<Event>()
        while (event.repeatLimit < 0 && event.startTS <= toTS) {
            if (event.repeatInterval.isXWeeklyRepetition()) {
                if (event.startTS.isTsOnProperDay(event)) {
                    if (event.isOnProperWeek(startTimes)) {
                        if (event.endTS >= fromTS) {
                            event.copy().apply {
                                updateIsPastEvent()
                                color = event.color
                                events.add(this)
                            }
                        }
                        event.repeatLimit++
                    }
                }
            } else {
                if (event.endTS >= fromTS) {
                    event.copy().apply {
                        updateIsPastEvent()
                        color = event.color
                        events.add(this)
                    }
                } else if (event.getIsAllDay()) {
                    val dayCode = Formatter.getDayCodeFromTS(fromTS)
                    val endDayCode = Formatter.getDayCodeFromTS(event.endTS)
                    if (dayCode == endDayCode) {
                        event.copy().apply {
                            updateIsPastEvent()
                            color = event.color
                            events.add(this)
                        }
                    }
                }
                event.repeatLimit++
            }
            event.addIntervalTime(original)
        }
        return events
    }

    private fun getEventsRepeatingTillDateOrForever(
        fromTS: Long,
        toTS: Long,
        startTimes: LongSparseArray<Long>,
        event: Event
    ): ArrayList<Event> {
        val original = event.copy()
        val events = ArrayList<Event>()
        while (event.startTS <= toTS && (event.repeatLimit == 0L || event.repeatLimit >= event.startTS)) {
            if (event.endTS >= fromTS) {
                if (event.repeatInterval.isXWeeklyRepetition()) {
                    if (event.startTS.isTsOnProperDay(event)) {
                        if (event.isOnProperWeek(startTimes)) {
                            event.copy().apply {
                                updateIsPastEvent()
                                color = event.color
                                events.add(this)
                            }
                        }
                    }
                } else {
                    event.copy().apply {
                        updateIsPastEvent()
                        color = event.color
                        events.add(this)
                    }
                }
            }

            if (event.getIsAllDay()) {
                if (event.repeatInterval.isXWeeklyRepetition()) {
                    if (event.endTS >= toTS && event.startTS.isTsOnProperDay(event)) {
                        if (event.isOnProperWeek(startTimes)) {
                            event.copy().apply {
                                updateIsPastEvent()
                                color = event.color
                                events.add(this)
                            }
                        }
                    }
                } else {
                    val dayCode = Formatter.getDayCodeFromTS(fromTS)
                    val endDayCode = Formatter.getDayCodeFromTS(event.endTS)
                    if (dayCode == endDayCode) {
                        event.copy().apply {
                            updateIsPastEvent()
                            color = event.color
                            events.add(this)
                        }
                    }
                }
            }
            event.addIntervalTime(original)
        }
        return events
    }

    fun updateIsTaskCompleted(event: Event) {
        val task = completedTasksDB.getTaskWithIdAndTs(event.id!!, startTs = event.startTS)
        event.flags = task?.flags ?: event.flags
    }

    fun getRunningEventsOrTasks(): List<Event> {
        val ts = getNowSeconds()
        val events =
            eventsDB.getOneTimeEventsOrTasksFromTo(ts, ts).toMutableList() as ArrayList<Event>
        events.addAll(getRepeatableEventsFor(ts, ts))
        events.forEach {
            if (it.isTask()) updateIsTaskCompleted(it)
        }
        return events
    }

    fun getEventsToExport(
        calendars: List<Long>,
        exportEvents: Boolean,
        exportTasks: Boolean,
        exportPastEntries: Boolean
    ): MutableList<Event> {
        val currTS = getNowSeconds()
        var events = mutableListOf<Event>()
        val tasks = mutableListOf<Event>()
        if (exportPastEntries) {
            if (exportEvents) {
                events.addAll(eventsDB.getAllEventsWithCalendarIds(calendars))
            }
            if (exportTasks) {
                tasks.addAll(eventsDB.getAllTasksWithCalendarIds(calendars))
            }
        } else {
            if (exportEvents) {
                events.addAll(eventsDB.getAllFutureEventsWithCalendarIds(currTS, calendars))
            }
            if (exportTasks) {
                tasks.addAll(eventsDB.getAllFutureTasksWithCalendarIds(currTS, calendars))
            }
        }

        tasks.forEach {
            updateIsTaskCompleted(it)
        }
        events.addAll(tasks)

        events = events.distinctBy { it.id } as ArrayList<Event>
        return events
    }

    fun getCalendarColors(): LongSparseArray<Int> {
        val calendarColors = LongSparseArray<Int>()
        context.calendarsDB.getCalendars().forEach {
            calendarColors.put(it.id!!, it.color)
        }

        return calendarColors
    }
}
