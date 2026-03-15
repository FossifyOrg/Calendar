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

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val HOURS_PER_DAY = 24
        const val MIN_SHOWN_HOURS_PER_DAY = 6
        const val EMPTY_START_HOUR = 6
        const val EMPTY_END_HOUR = 18
    }

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

            val days = generateDaysInitial(weekStart, sortedEvents)
            fixEventOverlap(days)
            callbackWithHourRange(days)
        }
    }

    fun getWeek(targetDate: DateTime) {
        updateWeeklyCalendar(targetDate)
    }

    private fun generateDaysInitial(weekStart: DateTime, sortedEvents: ArrayList<Event>): ArrayList<DayWeekly> {
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
                addNormalEventToDays(days, event, eventStart, eventEnd)
            }
        }
        return days
    }

    private fun addNormalEventToDays(
        days: ArrayList<DayWeekly>,
        event: Event,
        eventStart: DateTime,
        eventEnd: DateTime,
    ) {
        // the event gets added to all days it spans
        for (day in days) {
            val dayEnd = day.start.plusDays(1)
            val eventIsDuringThisDay = eventStart < dayEnd && eventEnd > day.start
            val eventStartsAndEndsWithDay = eventStart == day.start && eventEnd == day.start
            if (eventIsDuringThisDay || eventStartsAndEndsWithDay) {
                val startM = eventStart.coerceAtLeast(day.start).minuteOfDay
                val endM = eventEnd.coerceAtMost(dayEnd).minuteOfDay
                // round to timeStep
                val startMinute = divRound(startM, timeStepMinutes) * timeStepMinutes
                val endMinute = divRound(endM, timeStepMinutes) * timeStepMinutes
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

    private fun fixEventOverlap(days: ArrayList<DayWeekly>) {
        // make sure that events don't overlap visually even if their timing overlaps
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
            fixEventOverlapDay(day, sapPoints)
        }
    }

    private fun fixEventOverlapDay(day: DayWeekly, sapPoints: ArrayList<SweepAndPrunePoint>) {
        // make sure that events don't overlap visually even if their timing overlaps
        var startOfCurrentBlock = 0
        var neededSlots = 0
        val currentEvents = ArrayList<Int>()
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
                    distributeEventSlots(day, neededSlots, startOfCurrentBlock, i, sapPoints)
                    // reset needed slots for the next block
                    neededSlots = 0
                }
            }
            // at least as many slots as concurrent events are needed
            neededSlots = neededSlots.coerceAtLeast(currentEvents.size)
        }
    }

    private fun distributeEventSlots(
        day: DayWeekly,
        neededSlots: Int,
        startOfCurrentBlock: Int,
        endOfCurrentBlock: Int,
        sapPoints: ArrayList<SweepAndPrunePoint>,
    ) {
        var slot = 0
        val slotUsage = (0 until neededSlots).map { false }.toMutableList();
        for (i in startOfCurrentBlock until endOfCurrentBlock) {
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
    }

    private fun callbackWithHourRange(days: ArrayList<DayWeekly>) {
        // allows hiding hours in which nothing happens
        var earliestEventStartHour = HOURS_PER_DAY
        var latestEventEndHour = 0
        for (day in days) {
            val startHour = day.dayEvents.minOfOrNull { it.startMinute / MINUTES_PER_HOUR }
            earliestEventStartHour = earliestEventStartHour.coerceAtMost(startHour ?: earliestEventStartHour)
            val endHour = day.dayEvents.maxOfOrNull {
                it.endMinute / MINUTES_PER_HOUR + if (it.endMinute % MINUTES_PER_HOUR > 0) 1 else 0
            }
            latestEventEndHour = latestEventEndHour.coerceAtLeast(endHour ?: latestEventEndHour)
        }
        if (earliestEventStartHour > latestEventEndHour) {
            // looks like there are no events during this week, show default range
            earliestEventStartHour = EMPTY_START_HOUR
            latestEventEndHour = EMPTY_END_HOUR
        } else if (latestEventEndHour - earliestEventStartHour < MIN_SHOWN_HOURS_PER_DAY ) {
            // make sure that more than one hour is shown
            val hoursToAdd = MIN_SHOWN_HOURS_PER_DAY - latestEventEndHour + earliestEventStartHour
            earliestEventStartHour = (earliestEventStartHour - hoursToAdd / 2).coerceAtLeast(0)
            latestEventEndHour = (latestEventEndHour + hoursToAdd - (hoursToAdd / 2)).coerceAtMost(HOURS_PER_DAY)
        }
        callback.updateWeeklyCalendar(context, days, earliestEventStartHour, latestEventEndHour)
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
    private fun shouldAddEventOnTopBar(
        event: Event,
        startDateTime: DateTime,
        endDateTime: DateTime,
    ): Boolean {
        val dayCodeStart = Formatter.getDayCodeFromDateTime(startDateTime)
        val dayCodeEnd = Formatter.getDayCodeFromDateTime(endDateTime)
        val spansMultipleDays = dayCodeStart != dayCodeEnd
        return event.getIsAllDay() || (spansMultipleDays && context.config.showMidnightSpanningEventsAtTop)
    }
}

private fun divRound(a: Int, b: Int): Int {
    return a / b + if ((a % b) * 2 >= b) 1 else 0
}
