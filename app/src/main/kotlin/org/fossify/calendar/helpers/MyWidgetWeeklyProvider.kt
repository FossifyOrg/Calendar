package org.fossify.calendar.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Paint
import android.widget.RemoteViews
import org.fossify.calendar.R
import org.fossify.calendar.activities.SplashActivity
import org.fossify.calendar.extensions.*
import org.fossify.calendar.extensions.config
import org.fossify.calendar.interfaces.WeeklyCalendar
import org.fossify.calendar.models.DayWeekly
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.HIGHER_ALPHA
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.joda.time.DateTime

class MyWidgetWeeklyProvider : AppWidgetProvider() {

    private var dayColumns = ArrayList<RemoteViews>()
    private var mDays = ArrayList<DayWeekly>()
        set(value) {
            field = value
            // don't show hours in which no events occur, so existing events aren't as squished
            earliestEventStartHour = 24
            latestEventEndHour = 0
            for (day in value) {
                val startHour = day.dayEvents.minOfOrNull { it.startMinute / 60 }
                earliestEventStartHour = earliestEventStartHour.coerceAtMost(startHour ?: earliestEventStartHour)
                val endHour = day.dayEvents.maxOfOrNull { it.endMinute / 60 + if (it.endMinute % 60 > 0) 1 else 0 }
                latestEventEndHour = latestEventEndHour.coerceAtLeast(endHour ?: latestEventEndHour)
            }
            if (earliestEventStartHour > latestEventEndHour) {
                // looks like there are no events during this week, show default range
                earliestEventStartHour = 6
                latestEventEndHour = 18
            } else if (latestEventEndHour - earliestEventStartHour < 6 ) {
                // make sure that more than one hour is shown
                val hoursToAdd = 6 - latestEventEndHour + earliestEventStartHour
                earliestEventStartHour = (earliestEventStartHour - hoursToAdd / 2).coerceAtLeast(0)
                latestEventEndHour = (latestEventEndHour + hoursToAdd - (hoursToAdd / 2)).coerceAtMost(24)
            }
        }
    private var earliestEventStartHour = 0
    private var latestEventEndHour = 24

    companion object {
        private val vertical_spaces = arrayOf(
            R.layout.vertical_1,
            R.layout.vertical_2,
            R.layout.vertical_3,
            R.layout.vertical_4,
            R.layout.vertical_5,
            R.layout.vertical_6,
            R.layout.vertical_7,
            R.layout.vertical_8,
            R.layout.vertical_9,
            R.layout.vertical_10,
            R.layout.vertical_11,
            R.layout.vertical_12,
            R.layout.vertical_13,
            R.layout.vertical_14,
            R.layout.vertical_15,
            R.layout.vertical_16,
            R.layout.vertical_17,
            R.layout.vertical_18,
            R.layout.vertical_19,
            R.layout.vertical_20,
            R.layout.vertical_21,
            R.layout.vertical_22,
            R.layout.vertical_23,
            R.layout.vertical_24,
            R.layout.vertical_25,
            R.layout.vertical_26,
            R.layout.vertical_27,
            R.layout.vertical_28,
            R.layout.vertical_29,
            R.layout.vertical_30,
            R.layout.vertical_31,
            R.layout.vertical_32,
            R.layout.vertical_33,
            R.layout.vertical_34,
            R.layout.vertical_35,
            R.layout.vertical_36,
            R.layout.vertical_37,
            R.layout.vertical_38,
            R.layout.vertical_39,
            R.layout.vertical_40,
            R.layout.vertical_41,
            R.layout.vertical_42,
            R.layout.vertical_43,
            R.layout.vertical_44,
            R.layout.vertical_45,
            R.layout.vertical_46,
            R.layout.vertical_47,
            R.layout.vertical_48,
            R.layout.vertical_49,
            R.layout.vertical_50,
            R.layout.vertical_51,
            R.layout.vertical_52,
            R.layout.vertical_53,
            R.layout.vertical_54,
            R.layout.vertical_55,
            R.layout.vertical_56,
            R.layout.vertical_57,
            R.layout.vertical_58,
            R.layout.vertical_59,
            R.layout.vertical_60,
            R.layout.vertical_61,
            R.layout.vertical_62,
            R.layout.vertical_63,
            R.layout.vertical_64,
            R.layout.vertical_65,
            R.layout.vertical_66,
            R.layout.vertical_67,
            R.layout.vertical_68,
            R.layout.vertical_69,
            R.layout.vertical_70,
            R.layout.vertical_71,
            R.layout.vertical_72,
            R.layout.vertical_73,
            R.layout.vertical_74,
            R.layout.vertical_75,
            R.layout.vertical_76,
            R.layout.vertical_77,
            R.layout.vertical_78,
            R.layout.vertical_79,
            R.layout.vertical_80,
            R.layout.vertical_81,
            R.layout.vertical_82,
            R.layout.vertical_83,
            R.layout.vertical_84,
            R.layout.vertical_85,
            R.layout.vertical_86,
            R.layout.vertical_87,
            R.layout.vertical_88,
            R.layout.vertical_89,
            R.layout.vertical_90,
            R.layout.vertical_91,
            R.layout.vertical_92,
            R.layout.vertical_93,
            R.layout.vertical_94,
            R.layout.vertical_95,
            R.layout.vertical_96,
        )
        private val horizontal_spaces = arrayOf(
            R.layout.horizontal_1,
            R.layout.horizontal_2,
            R.layout.horizontal_3,
            R.layout.horizontal_4,
            R.layout.horizontal_5,
            R.layout.horizontal_6,
            R.layout.horizontal_7,
            R.layout.horizontal_8,
            R.layout.horizontal_9,
            R.layout.horizontal_10,
            R.layout.horizontal_11,
            R.layout.horizontal_12,
            R.layout.horizontal_13,
            R.layout.horizontal_14,
        )
        // 15 minute chunks
        private val timeStepMinutes = 24 * 60 / vertical_spaces.size
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        performUpdate(context)
    }

    private fun performUpdate(context: Context) {
        WeeklyCalendarImpl(weeklyCalendar, context, timeStepMinutes).getWeek(DateTime.now())
    }

    private fun getComponentName(context: Context) = ComponentName(context, MyWidgetWeeklyProvider::class.java)

    private fun setupDayColumns(context: Context, views: RemoteViews) {
        val textColor = context.config.widgetTextColor
        dayColumns.clear()
        views.removeAllViews(R.id.week_events_columns_holder)
        views.removeAllViews(R.id.time_column)
        views.removeAllViews(R.id.week_events_hour_lines)
        views.removeAllViews(R.id.week_events_day_lines)
        val packageName = context.packageName
        // columns that will contain events
        (0 until context.config.weeklyViewDays).forEach {
            val column = RemoteViews(packageName, R.layout.widget_week_column)
            dayColumns.add(column)
        }
        // column on the left showing the time
        views.addView(R.id.time_column, RemoteViews(packageName, R.layout.vertical_1))
        views.addView(R.id.week_events_hour_lines, RemoteViews(packageName, R.layout.vertical_1))
        for (i in earliestEventStartHour + 1 until latestEventEndHour) {
            val hour = RemoteViews(packageName, R.layout.widget_week_hour)
            val time = DateTime().withHourOfDay(i)
            hour.setText(R.id.hour_textview, time.toString(Formatter.getHourPattern(context)))
            hour.setTextColor(R.id.hour_textview, textColor)
            views.addView(R.id.time_column, hour)
            views.addView(R.id.week_events_hour_lines, RemoteViews(packageName, R.layout.horizontal_line))
            views.addView(R.id.week_events_hour_lines, RemoteViews(packageName, R.layout.vertical_1))
        }
        views.addView(R.id.time_column, RemoteViews(packageName, R.layout.vertical_1))
    }

    private fun updateDays(context: Context, views: RemoteViews, days: ArrayList<DayWeekly>) {
        mDays = days
        views.removeAllViews(R.id.week_all_day_holder)

        val config = context.config
        val packageName = context.packageName
        val allDayEventRows = ArrayList<RemoteViews>()
        val allDayEventNextStart = ArrayList<Int>()

        setupDayColumns(context, views)

        // add events to the view
        for ((dayOfWeek, day) in days.withIndex()) {
            for (event in day.topBarEvents) {
                addAllDayEvent(context, packageName, dayOfWeek, event, allDayEventRows, allDayEventNextStart)
            }
            val dayColumn = dayColumns[dayOfWeek]
            var subColumnStartMinute = earliestEventStartHour * 60
            val subColumns = ArrayList<RemoteViews>()
            val subColumnLastMinutes = ArrayList<Int>()
            subColumns.add(dayColumn)
            subColumnLastMinutes.add(subColumnStartMinute)
            for (ews in day.dayEvents) {
                if (ews.slotMax != subColumns.size) {
                    // the number of subColumns has to be changed
                    fillEmptyEventColumns(packageName, dayColumn, ews.startMinute, subColumns, subColumnStartMinute, subColumnLastMinutes)
                    subColumns.clear()
                    subColumnLastMinutes.clear()
                    subColumnStartMinute = ews.startMinute
                    if (ews.slotMax == 1) {
                        // it would be pointless to add a row containing only one subColumn
                        // so instead pretend as if dayColumn was a subColumn
                        subColumns.add(dayColumn)
                    } else {
                        // subColumns will be shown side-by-side for events with overlapping timing
                        (0 until ews.slotMax).forEach { _ ->
                            val column = RemoteViews(packageName, R.layout.widget_week_column)
                            subColumns.add(column)
                        }
                    }
                    (0 until ews.slotMax).forEach { _ ->
                        subColumnLastMinutes.add(ews.startMinute)
                    }
                }
                fillEmptySpace(packageName, subColumns[ews.slot], ews.startMinute - subColumnLastMinutes[ews.slot])
                subColumnLastMinutes[ews.slot] = ews.endMinute
                val eventView = eventView(context, packageName, ews.event)
                val height = (ews.endMinute - ews.startMinute) / timeStepMinutes
                val container = RemoteViews(packageName, vertical_spaces[height - 1])
                container.addView(R.id.space_vertical, eventView)
                subColumns[ews.slot].addView(R.id.week_column, container)
            }
            fillEmptyEventColumns(packageName, dayColumn, latestEventEndHour * 60, subColumns, subColumnStartMinute, subColumnLastMinutes)
            // add vertical grid line
            views.addView(R.id.week_events_day_lines, RemoteViews(packageName, R.layout.vertical_line))
            views.addView(R.id.week_events_day_lines, RemoteViews(packageName, R.layout.horizontal_1))
            views.addView(R.id.week_events_columns_holder, dayColumn)
        }
        // add rows containing all-day events to the view
        for ((i, row) in allDayEventRows.withIndex()) {
            if (allDayEventNextStart[i] < config.weeklyViewDays) {
                val space = RemoteViews(packageName, horizontal_spaces[config.weeklyViewDays - allDayEventNextStart[i] - 1])
                allDayEventRows[i].addView(R.id.week_all_day_row, space)
            }
            views.addView(R.id.week_all_day_holder, row)
        }
    }

    /**
     * create a RemoteViews displaying the given event
     * for all-day events in the top-bar and normal events in the dayColumns
     */
    private fun eventView(context: Context, packageName: String, event: Event): RemoteViews {
        val config = context.config
        val eventView = RemoteViews(packageName, R.layout.widget_week_event_marker)
        var backgroundColor = event.color
        var textColor = backgroundColor.getContrastColor()

        val adjustAlpha = if (event.isTask()) {
            config.dimCompletedTasks && event.isTaskCompleted()
        } else {
            config.dimPastEvents && event.isPastEvent
        }

        if (adjustAlpha) {
            backgroundColor = backgroundColor.adjustAlpha(MEDIUM_ALPHA)
            textColor = textColor.adjustAlpha(HIGHER_ALPHA)
        }

        eventView.setInt(R.id.week_event_background, "setColorFilter", backgroundColor)

        eventView.setVisibleIf(R.id.week_event_task_image, event.isTask())
        if (event.isTask()) {
            eventView.applyColorFilter(R.id.week_event_task_image, textColor)
        }

        // week_event_label
        eventView.setTextColor(R.id.week_event_label, textColor)
        val maxLines = if (event.isTask() || event.startTS == event.endTS) {
            1
        } else {
            3
        }
        eventView.setInt(R.id.week_event_label, "setMaxLines", maxLines)
        eventView.setText(R.id.week_event_label, event.title)
        if (event.shouldStrikeThrough()) {
            eventView.setInt(R.id.week_event_label, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG)
        }
        eventView.setContentDescription(R.id.week_event_label, event.title)

        val intent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
        intent.putExtra(EVENT_ID, event.id)
        intent.putExtra(EVENT_OCCURRENCE_TS, event.startTS)
        val pendingIntent = PendingIntent.getActivity(
            context,
            event.id!!.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        eventView.setOnClickPendingIntent(R.id.week_event_holder, pendingIntent)
        return eventView
    }

    private fun addAllDayEvent(
        context: Context,
        packageName: String,
        dayOfWeek: Int,
        event: Event,
        allDayEventRows: ArrayList<RemoteViews>,
        allDayEventNextStart: ArrayList<Int>,
    ) {
        val endDateTime = Formatter.getDateTimeFromTS(event.endTS)
        var eventEndsOnDay = mDays.indexOfLast { endDateTime > it.start }
        if (eventEndsOnDay < 0) {
            eventEndsOnDay = mDays.size - 1
        }
        var rowIndex = allDayEventNextStart.indexOfFirst { it <= dayOfWeek }
        if (rowIndex < 0) {
            rowIndex = allDayEventRows.size
            val row = RemoteViews(packageName, R.layout.widget_week_all_day_row)
            allDayEventRows.add(row)
            allDayEventNextStart.add(0)
        }
        if (allDayEventNextStart[rowIndex] < dayOfWeek) {
            val space = RemoteViews(packageName, horizontal_spaces[dayOfWeek - allDayEventNextStart[rowIndex] - 1])
            allDayEventRows[rowIndex].addView(R.id.week_all_day_row, space)
        }
        allDayEventNextStart[rowIndex] = eventEndsOnDay + 1
        val container = RemoteViews(packageName, horizontal_spaces[eventEndsOnDay - dayOfWeek])
        val eventView = eventView(context, packageName, event)
        container.addView(R.id.space_horizontal, eventView)
        allDayEventRows[rowIndex].addView(R.id.week_all_day_row, container)
    }

    private val weeklyCalendar = object : WeeklyCalendar {
        override fun updateWeeklyCalendar(context: Context, days: ArrayList<DayWeekly>) {
            val textColor = context.config.widgetTextColor
            val resources = context.resources

            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach {
                val views = RemoteViews(context.packageName, R.layout.fragment_week_widget)

                views.applyColorFilter(R.id.widget_week_background, context.config.widgetBgColor)

                updateDayLabels(context, views, resources, textColor)
                updateDays(context, views, days)

                try {
                    appWidgetManager.updateAppWidget(it, views)
                } catch (ignored: RuntimeException) {
                }
            }
        }
    }

    private fun updateDayLabels(context: Context, views: RemoteViews, resources: Resources, textColor: Int) {
        val config = context.config
        val smallerFontSize = context.getWidgetFontSize()
        val packageName = context.packageName
        var curDay = context.getFirstDayOfWeekDt(DateTime())
        val dayLetters = resources.getStringArray(org.fossify.commons.R.array.week_days_short)
            .toMutableList() as ArrayList<String>

        views.removeAllViews(R.id.week_letters_holder)
        (0 until config.weeklyViewDays).forEach { _ ->
            val dayLetter = dayLetters[curDay.dayOfWeek - 1]

            val newRemoteView = RemoteViews(packageName, R.layout.widget_week_day_letter).apply {
                setText(R.id.week_day_label, dayLetter)
                setTextColor(R.id.week_day_label, textColor)
                setTextSize(R.id.week_day_label, smallerFontSize)
            }
            val dayCode = Formatter.getDayCodeFromDateTime(curDay)
            (context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)).apply {
                putExtra(DAY_CODE, dayCode)
                putExtra(VIEW_TO_OPEN, DAILY_VIEW)
                val pendingIntent = PendingIntent.getActivity(context, Integer.parseInt(dayCode), this, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                newRemoteView.setOnClickPendingIntent(R.id.week_day_label, pendingIntent)
            }
            views.addView(R.id.week_letters_holder, newRemoteView)
            curDay = curDay.plusDays(1)
        }
    }

    private fun fillEmptySpace(packageName: String, column: RemoteViews, minutes: Int) {
        val height = minutes / timeStepMinutes
        if (height <= 0)
            return
        val space = RemoteViews(packageName, vertical_spaces[height - 1])
        column.addView(R.id.week_column, space)
    }

    private fun fillEmptyEventColumns(
        packageName: String,
        dayColumn: RemoteViews,
        fillToMinute: Int,
        subColumns: ArrayList<RemoteViews>,
        subColumnStartMinute: Int,
        subColumnLastMinutes: ArrayList<Int>,
    ) {
        val lastMinute = subColumnLastMinutes.max()
        if (subColumns.size > 1) {
            val height = (lastMinute - subColumnStartMinute) / timeStepMinutes
            val subColumnRow = RemoteViews(packageName, vertical_spaces[height - 1])
            for ((i, column) in subColumns.withIndex()) {
                fillEmptySpace(packageName, column, lastMinute - subColumnLastMinutes[i])
                subColumnRow.addView(R.id.space_vertical, column)
            }
            dayColumn.addView(R.id.week_column, subColumnRow)
        }
        fillEmptySpace(packageName, dayColumn, fillToMinute - lastMinute)
    }
}
