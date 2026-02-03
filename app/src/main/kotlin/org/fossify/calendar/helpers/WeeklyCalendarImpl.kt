package org.fossify.calendar.helpers

import android.content.Context
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.getFirstDayOfWeekDt
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.interfaces.WeeklyCalendar
import org.fossify.calendar.models.DayWeekly
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.EventWeeklyView
import org.joda.time.DateTime

class WeeklyCalendarImpl(val callback: WeeklyCalendar, val context: Context, val timeStepMinutes: Int = 1) {

    fun updateWeeklyCalendar(dateWithinWeek: DateTime) {
        val weekStart = context.getFirstDayOfWeekDt(dateWithinWeek);
        val end = weekStart.plusDays(context.config.weeklyViewDays)
        val toTS = end.seconds()
        context.eventsHelper.getEvents(weekStart.seconds(), toTS) { events ->
            val replaceDescription = context.config.replaceDescription
            val sortedEvents = events.filter { it.startTS < toTS }.sortedWith(
                compareBy<Event> { it.startTS }.thenBy { it.endTS }.thenBy { it.title }
                    .thenBy { if (replaceDescription) it.location else it.description }
            ).toMutableList() as ArrayList<Event>

            getDays(weekStart, sortedEvents)
        }
    }

    fun getWeek(targetDate: DateTime) {
        updateWeeklyCalendar(targetDate)
    }

    private fun getDays(weekStart: DateTime, sortedEvents: ArrayList<Event>) {
        val days = ArrayList<DayWeekly>(context.config.weeklyViewDays)
        for (i in 0 until context.config.weeklyViewDays) {
            val day = DayWeekly(weekStart.plusDays(i), ArrayList(), ArrayList())
            days.add(day)
        }

        // add events to days
        for (event in sortedEvents) {
            val eventStart = Formatter.getDateTimeFromTS(event.startTS)
            val eventEnd = Formatter.getDateTimeFromTS(event.endTS)

            if (shouldAddEventOnTopBar(event, eventStart, eventEnd)) {
                // an event spanning multiple days still only gets added to one day's top bar
                val day = days.lastOrNull { it.start <= eventStart } ?: days[0]
                day.topBarEvents.add(event)
            } else {
                // the event gets added to all days it spans
                for (day in days) {
                    val dayEnd = day.start.plusDays(1)
                    if ((eventStart < dayEnd && eventEnd > day.start)
                    || (eventStart == day.start && eventEnd == day.start)) {
                        val startMinute = divRound(eventStart.coerceAtLeast(day.start).minuteOfDay, timeStepMinutes) * timeStepMinutes
                        val endMinute = divRound(eventEnd.coerceAtMost(dayEnd).minuteOfDay, timeStepMinutes) * timeStepMinutes
                        day.dayEvents.add(
                            EventWeeklyView(
                                event,
                                startMinute,
                                endMinute.coerceAtLeast(startMinute + timeStepMinutes),
                            )
                        )
                    }
                }
            }
        }

        // make sure that events don't overlap visually even if their timing overlaps
        val currentEvents = ArrayList<Int>()
        for (day in days) {
            // prepare sweep-and-prune algorithm
            val sapPoints = ArrayList<SweepAndPrunePoint>()
            for ((i, ews) in day.dayEvents.withIndex()) {
                sapPoints.add(SweepAndPrunePoint(ews.startMinute, i, true))
                sapPoints.add(SweepAndPrunePoint(ews.endMinute, i, false))
            }
            sapPoints.sortWith(
                compareBy<SweepAndPrunePoint> { it.minutes }.thenBy { it.isStart }
            )
            var startOfCurrentBlock = 0
            var neededSlots = 0
            for ((i, sap) in sapPoints.withIndex()) {
                if (sap.isStart) {
                    if (neededSlots == 0) {
                        startOfCurrentBlock = i
                    }
                    currentEvents.add(sap.eventIndex)
                } else {
                    currentEvents.remove(sap.eventIndex)
                    if (currentEvents.isEmpty()) {
                        // no events remain in the current block
                        // slots can now be distributed
                        var slot = 0
                        val slotUsage = (0 until neededSlots).map { false }.toMutableList();
                        for (i in startOfCurrentBlock until i) {
                            // reuse sweep-and-prune points to assign slots
                            val sap = sapPoints[i]
                            if (sap.isStart) {
                                // find next free slot
                                while (slotUsage[slot]) {
                                    slot = (slot + 1) % neededSlots
                                }
                                // block slot
                                slotUsage[slot] = true
                                day.dayEvents[sap.eventIndex].slot = slot
                                day.dayEvents[sap.eventIndex].slotMax = neededSlots
                                slot = (slot + 1) % neededSlots
                            } else {
                                // free slot
                                slotUsage[day.dayEvents[sap.eventIndex].slot] = false
                            }
                        }
                        // reset needed slots for the next block
                        neededSlots = 0
                    }
                }
                // at least as many slots as concurrent events are needed
                neededSlots = neededSlots.coerceAtLeast(currentEvents.size)
            }
        }

        callback.updateWeeklyCalendar(context, days)
    }

    private class SweepAndPrunePoint (
        val minutes: Int,
        val eventIndex: Int,
        val isStart: Boolean,
    )

    /**
     * Events are shown in the top bar if
     * - they're marked as all-day
     * - they don't end on the day they started and the 'showMidnightSpanningEventsAtTop' config flag is set to true
     */
    private fun shouldAddEventOnTopBar(event: Event, startDateTime: DateTime, endDateTime: DateTime): Boolean {
        val spansMultipleDays = Formatter.getDayCodeFromDateTime(startDateTime) != Formatter.getDayCodeFromDateTime(endDateTime)
        return event.getIsAllDay() || (spansMultipleDays && context.config.showMidnightSpanningEventsAtTop)
    }
}

private fun divRound(a: Int, b: Int): Int {
    return a / b + if ((a % b) * 2 >= b) 1 else 0
}
