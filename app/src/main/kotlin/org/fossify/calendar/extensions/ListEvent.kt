package org.fossify.calendar.extensions

import org.fossify.calendar.models.ListEvent

fun ListEvent.shouldStrikeThrough() = isTaskCompleted || isAttendeeInviteDeclined
