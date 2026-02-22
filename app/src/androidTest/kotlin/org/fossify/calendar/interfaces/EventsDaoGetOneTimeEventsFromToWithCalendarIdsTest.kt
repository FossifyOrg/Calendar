package org.fossify.calendar.interfaces

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.calendar.databases.EventsDatabase
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.models.Event
import org.fossify.calendar.testing.expectedFailure
import org.joda.time.DateTime
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class EventsDaoGetOneTimeEventsFromToWithCalendarIdsTest {

    private lateinit var eventsDao: EventsDao
    private lateinit var db: EventsDatabase

    private val calendarId = 1L

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, EventsDatabase::class.java).build()
        eventsDao = db.EventsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun bug255_EventEndingAtMidnightShouldNotShowOnNextDay() {
        expectedFailure("https://github.com/FossifyOrg/Calendar/issues/255") {
            val startDay = DateTime(2026, 1, 1, 0, 0)
            val startEvent = startDay.plusHours(23)
            val event = Event(id = 0, startTS = startEvent.seconds(), endTS = startEvent.plusHours(1).seconds(), calendarId = calendarId)
            eventsDao.insertOrUpdate(event)

            val eventsDayOne = eventsDao.getOneTimeEventsFromToWithCalendarIds(startDay.plusDays(1).seconds(), startDay.seconds(), listOf(calendarId))
            val eventsDayTwo = eventsDao.getOneTimeEventsFromToWithCalendarIds(startDay.plusDays(2).seconds(), startDay.plusDays(1).seconds(), listOf(calendarId))

            Assert.assertEquals(1, eventsDayOne.count())
            Assert.assertEquals(emptyList<Event>(), eventsDayTwo)
        }
    }

    @Test
    @Throws(Exception::class)
    fun bug255_EventStartingAtMidnightWithoutDurationShouldOnlyShowOnSingleDay() {
        expectedFailure("https://github.com/FossifyOrg/Calendar/issues/255") {
            val startDay = DateTime(2026, 1, 10, 0, 0)
            val event = Event(id = 0, startTS = startDay.seconds(), endTS = startDay.seconds(), calendarId = calendarId)
            eventsDao.insertOrUpdate(event)

            val eventsDayOne = eventsDao.getOneTimeEventsFromToWithCalendarIds(startDay.seconds(), startDay.plusDays(-1).seconds(), listOf(calendarId))
            val eventsDayTwo = eventsDao.getOneTimeEventsFromToWithCalendarIds(startDay.plusDays(1).seconds(), startDay.seconds(), listOf(calendarId))

            Assert.assertEquals(1, eventsDayTwo.count())
            Assert.assertEquals(emptyList<Event>(), eventsDayOne)
        }
    }

    @Test
    @Throws(Exception::class)
    fun bug440_EventAtMidnightOnFirstJan1970() {
        expectedFailure("https://github.com/FossifyOrg/Calendar/issues/440") {
            eventsDao.insertOrUpdate(Event(id = 0, startTS = 0, endTS = 3600, calendarId = calendarId))

            val eventsOnFirstJan1970 = eventsDao.getOneTimeEventsFromToWithCalendarIds(86400, 0, listOf(calendarId))

            Assert.assertEquals(1, eventsOnFirstJan1970.count())
        }
    }
}
