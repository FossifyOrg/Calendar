package org.fossify.calendar.extensions

import org.fossify.calendar.helpers.MONTH
import org.fossify.calendar.helpers.WEEK
import org.fossify.calendar.helpers.YEAR

fun Int.isXWeeklyRepetition() = this != 0 && this % WEEK == 0

fun Int.isXMonthlyRepetition() = this != 0 && this % MONTH == 0

fun Int.isXYearlyRepetition() = this != 0 && this % YEAR == 0
