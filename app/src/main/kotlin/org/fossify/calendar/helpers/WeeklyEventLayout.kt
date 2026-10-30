package org.fossify.calendar.helpers

import org.fossify.calendar.models.EventWeeklyView
import kotlin.math.min

/**
 * Lays out overlapping events of a single day the way desktop calendars do: the earliest event (or the longest one,
 * if they start at the same time) keeps the full width and every later overlapping event is drawn on top of it,
 * indented to the right.
 */
object WeeklyEventLayout {
    const val CLOSE_START_THRESHOLD_MINUTES = 30

    // a nested event covers this part of the width of the event below it
    private const val NESTED_INDENT_RATIO = 0.25f

    // an event starting at about the same time would hide the title of the event below it, so it covers only half of it
    private const val CLOSE_START_INDENT_RATIO = 0.5f

    // keep deeply nested events wide enough to be visible and tappable
    private const val MAX_INDENT = 0.8f

    /**
     * @param closeStartThreshold events starting less than this many minutes after the event below them
     * are indented more
     */
    fun arrange(events: Collection<EventWeeklyView>, closeStartThreshold: Int) {
        val sorted = events.sortedWith(
            compareBy<EventWeeklyView> { it.range.lower }.thenByDescending { it.range.upper }
        )

        val placed = ArrayList<EventWeeklyView>()
        for ((layer, event) in sorted.withIndex()) {
            // nest over the topmost event still visible when this one starts, events that already ended don't matter
            val parent = placed
                .filter { it.range.upper > event.range.lower }
                .maxByOrNull { it.indent }

            event.indent = if (parent == null) {
                0f
            } else {
                val startsClose = event.range.lower - parent.range.lower < closeStartThreshold
                val ratio = if (startsClose) CLOSE_START_INDENT_RATIO else NESTED_INDENT_RATIO
                min(parent.indent + (1 - parent.indent) * ratio, MAX_INDENT)
            }
            event.layer = layer
            placed.add(event)
        }
    }
}
