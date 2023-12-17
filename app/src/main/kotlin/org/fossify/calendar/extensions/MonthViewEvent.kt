package org.fossify.calendar.extensions

import org.fossify.calendar.models.MonthViewEvent

fun MonthViewEvent.shouldStrikeThrough() = isTaskCompleted || isAttendeeInviteDeclined
