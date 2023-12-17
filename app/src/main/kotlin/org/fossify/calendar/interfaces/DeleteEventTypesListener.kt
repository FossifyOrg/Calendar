package org.fossify.calendar.interfaces

import org.fossify.calendar.models.EventType

interface DeleteEventTypesListener {
    fun deleteEventTypes(eventTypes: ArrayList<EventType>, deleteEvents: Boolean): Boolean
}
