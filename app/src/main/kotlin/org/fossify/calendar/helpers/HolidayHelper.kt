package org.fossify.calendar.helpers

import com.google.gson.Gson
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databases.EventsDatabase
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.cancelNotification
import org.fossify.calendar.extensions.cancelPendingIntent
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.scheduleNextEventReminder
import org.fossify.calendar.extensions.updateWidgets
import org.fossify.calendar.helpers.IcsImporter.ImportResult
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.HolidayMetadata
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.CHOPPED_LIST_DEFAULT_SIZE

class HolidayHelper(private val activity: SimpleActivity) {
    companion object {
        val lock = Any()
    }

    fun load(): HolidayMetadata = activity.assets.open("holidays/metadata.json").bufferedReader().use {
        Gson().fromJson(it, HolidayMetadata::class.java)
    }

    fun refreshIfNeeded(): ImportResult = synchronized(lock) {
        val config = activity.config
        if (config.holidayPaths.isEmpty() && !config.hasPendingHolidayUpdate) return ImportResult.IMPORT_NOTHING_NEW
        updateSelection(config.holidayPaths, config.holidayReminders)
    }

    @Suppress("TooGenericExceptionCaught")
    fun updateSelection(paths: Set<String>, reminders: ArrayList<Int>): ImportResult = synchronized(lock) {
        try {
            val metadata = load()
            val availablePaths = metadata.countries.flatMap { it.paths }.toSet()
            val selectedPaths = paths.intersect(availablePaths)
            val config = activity.config
            if (selectedPaths == config.holidayPaths && reminders == config.holidayReminders &&
                metadata.version == config.holidayDataVersion
            ) {
                return ImportResult.IMPORT_NOTHING_NEW
            }

            var calendarId = activity.calendarsDB.getLocalCalendarIdWithClass(HOLIDAY_EVENT) ?: -1L
            val incoming = ArrayList<Event>()
            for (path in selectedPaths.sorted()) {
                incoming.addAll(
                    IcsImporter(activity).parseHolidays(path, calendarId, reminders) ?: return ImportResult.IMPORT_FAIL
                )
            }
            val holidays = incoming.distinctBy(::holidayKey)
            if (!config.saveHolidaySelection(selectedPaths, reminders, "")) return ImportResult.IMPORT_FAIL
            if (calendarId == -1L && holidays.isNotEmpty()) {
                calendarId = activity.eventsHelper.createPredefinedCalendar(
                    activity.getString(R.string.holidays), R.color.default_holidays_color, HOLIDAY_EVENT
                )
            }
            val removed = replaceHolidays(calendarId, holidays)
            removed.forEach {
                activity.cancelNotification(it)
                activity.cancelPendingIntent(it)
            }
            holidays.forEach { activity.scheduleNextEventReminder(it, false) }
            activity.updateWidgets()
            val saved = config.saveHolidaySelection(selectedPaths, reminders, metadata.version)
            if (saved) ImportResult.IMPORT_OK else ImportResult.IMPORT_FAIL
        } catch (e: Exception) {
            activity.showErrorToast(e)
            ImportResult.IMPORT_FAIL
        }
    }

    private fun replaceHolidays(calendarId: Long, holidays: List<Event>): List<Long> {
        val db = EventsDatabase.getInstance(activity)
        val events = db.EventsDao()
        val removed = ArrayList<Long>()
        db.runInTransaction {
            val incomingUids = holidays.map { it.importId }.toSet()
            val owned = events.getAllEventsWithCalendarIds(listOf(calendarId)).filter {
                it.importId.startsWith(MANAGED_HOLIDAY_PREFIX) ||
                    (it.source == SOURCE_IMPORTED_ICS && it.importId in incomingUids)
            }.mapNotNull { it.id }
            removed.addAll(owned)
            removed.chunked(CHOPPED_LIST_DEFAULT_SIZE).forEach {
                events.detachEventsWithParentIds(it)
                events.deleteEvents(it)
            }
            for (holiday in holidays) {
                holiday.calendarId = calendarId
                holiday.importId = MANAGED_HOLIDAY_PREFIX + holidayKey(holiday)
                holiday.id = events.insertOrUpdate(holiday)
            }
        }
        return removed
    }

    private fun holidayKey(event: Event) =
        "${event.importId}:${event.startTS}:${event.endTS}:" +
            "${event.repeatInterval}:${event.repeatRule}:${event.repeatLimit}"
}
