package org.fossify.calendar.helpers

import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsDB
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.helpers.IcsImporter.ImportResult
import org.fossify.calendar.models.CalendarEntity
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.getSharedPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HolidayManagementTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var helper: HolidayHelper
    private lateinit var originalEventIds: Set<Long>
    private lateinit var originalPreferences: Map<String, *>
    private var calendarId = 0L
    private val reminders = arrayListOf(60, REMINDER_OFF, REMINDER_OFF)
    private val us = "holidays/US/public.ics"
    private val gb = "holidays/GB/public.ics"

    @Before
    fun setUp() {
        originalEventIds = context.eventsDB.getEventIds().toSet()
        val calendars = context.calendarsDB.getCalendars()
        val config = context.config
        assertTrue(
            "Run holiday management tests on a clean, disposable app installation.",
            originalEventIds.isEmpty() && calendars.all { it.id == LOCAL_CALENDAR_ID } &&
                    config.holidayPaths.isEmpty() && !config.caldavSync &&
                    !config.addBirthdaysAutomatically && !config.addAnniversariesAutomatically
        )
        originalPreferences = context.getSharedPrefs().all.filterKeys { it.startsWith("holiday_") }
        context.config.saveHolidaySelection(emptySet(), reminders, "")
        calendarId =
            context.calendarsDB.insertOrUpdate(CalendarEntity(null, "Holiday management test", 0, type = HOLIDAY_EVENT))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
        helper = HolidayHelper(activity)
    }

    @After
    fun tearDown() {
        if (!::helper.isInitialized) return
        helper.updateSelection(emptySet(), reminders)
        context.eventsDB.deleteEvents(context.eventsDB.getEventIds().filter { it !in originalEventIds })
        context.calendarsDB.deleteCalendars(context.calendarsDB.getCalendars().filter { it.id != LOCAL_CALENDAR_ID })
        val prefs = context.getSharedPrefs()
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith("holiday_") }.forEach { remove(it) }
            originalPreferences.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }.commit()
        scenario.close()
    }

    @Test
    fun selectionsShareHolidaysAndRememberTypesAndReminders() {
        val other = "holidays/US/other.ics"
        assertEquals(ImportResult.IMPORT_OK, select(us, gb, other))
        assertEquals(setOf(us, gb, other), context.config.holidayPaths)
        assertEquals(1, managed().count { it.title == "New Year's Day" })
        assertTrue(managed().all { it.reminder1Minutes == 60 })
        val ids = managed().map { it.id }
        assertEquals(ImportResult.IMPORT_NOTHING_NEW, select(other, gb, us))
        assertEquals(ids, managed().map { it.id })

        assertEquals(ImportResult.IMPORT_OK, select(gb))
        assertEquals(1, managed().count { it.title == "New Year's Day" })
        assertTrue(managed().all { it.reminder1Minutes == 60 })
        assertEquals(reminders, context.config.holidayReminders)
        assertEquals(ImportResult.IMPORT_OK, select())
        assertTrue(managed().isEmpty())
        assertTrue(context.config.holidayPaths.isEmpty())
    }

    @Test
    fun changedDataReplacesEventsAndDropsRetiredSelections() {
        select(us)
        val expected = managed().map { it.copy(id = null) }
        val edited = managed().first().copy(title = "Outdated", startTS = 0, source = SOURCE_SIMPLE_CALENDAR)
        context.eventsDB.insertOrUpdate(edited)
        context.eventsDB.insertOrUpdate(edited.copy(id = null, importId = "${MANAGED_HOLIDAY_PREFIX}withdrawn"))
        context.config.saveHolidaySelection(setOf(us, "holidays/retired/public.ics"), reminders, "old-version")

        val refreshed = CountDownLatch(1)
        val prefs = context.getSharedPrefs()
        val version = helper.load().version
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HOLIDAY_DATA_VERSION && context.config.holidayDataVersion == version) refreshed.countDown()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            scenario.recreate()
            scenario.onActivity {
                activity = it
                helper = HolidayHelper(it)
            }
            assertTrue("Startup did not refresh the holiday data", refreshed.await(30, TimeUnit.SECONDS))
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
        assertEquals(expected, managed().map { it.copy(id = null) })
        assertEquals(setOf(us), context.config.holidayPaths)
        assertEquals(helper.load().version, context.config.holidayDataVersion)
        val before = managed()
        assertEquals(ImportResult.IMPORT_NOTHING_NEW, helper.refreshIfNeeded())
        assertEquals(before, managed())
    }

    @Test
    fun onlyMatchingLegacyHolidaysAreAdoptedAndOrdinaryEventsArePreserved() {
        val legacy = IcsImporter(activity).parseHolidays(us, calendarId, reminders)!!.first()
        legacy.id = context.eventsDB.insertOrUpdate(legacy)
        val ordinary = legacy.copy(id = null, calendarId = LOCAL_CALENDAR_ID)
        ordinary.id = context.eventsDB.insertOrUpdate(ordinary)
        val untracked = legacy.copy(id = null, importId = "unrelated-import")
        untracked.id = context.eventsDB.insertOrUpdate(untracked)

        select(us)
        assertNull(context.eventsDB.getEventWithId(legacy.id!!))
        assertEquals(1, managed().count { it.title == legacy.title && it.startTS == legacy.startTS })
        val moved = managed().first().copy(calendarId = LOCAL_CALENDAR_ID)
        context.eventsDB.insertOrUpdate(moved)
        select()
        for (event in listOf(ordinary, untracked, moved)) {
            assertEquals(event, context.eventsDB.getEventWithId(event.id!!))
        }

        for (refresh in listOf(false, true)) {
            select(us)
            val parent = managed().first { it.repeatInterval > 0 }
            val occurrence = parent.copy(
                title = "Personal occasion",
                startTS = parent.startTS + YEAR,
                endTS = parent.endTS + YEAR,
                calendarId = LOCAL_CALENDAR_ID,
                source = SOURCE_SIMPLE_CALENDAR
            )
            val saved = CountDownLatch(1)
            context.eventsHelper.editSelectedOccurrence(
                occurrence,
                occurrence.startTS,
                showToasts = false
            ) { saved.countDown() }
            assertTrue(saved.await(30, TimeUnit.SECONDS))
            assertEquals(parent.id, occurrence.parentId)
            if (refresh) {
                context.config.saveHolidaySelection(setOf(us), reminders, "old-version")
                assertEquals(ImportResult.IMPORT_OK, helper.refreshIfNeeded())
            } else {
                assertEquals(ImportResult.IMPORT_OK, select())
            }
            assertEquals(occurrence.copy(parentId = 0), context.eventsDB.getEventWithId(occurrence.id!!))
        }
    }

    @Test
    fun deletingTheHolidayCalendarDisablesAutomaticRefresh() {
        for (deleteEvents in listOf(true, false)) {
            select(us)
            calendarId = context.calendarsDB.getLocalCalendarIdWithClass(HOLIDAY_EVENT)!!
            val before = managed()
            val calendar = context.calendarsDB.getCalendarWithId(calendarId)!!
            context.eventsHelper.deleteCalendars(arrayListOf(calendar), deleteEvents)
            assertTrue(context.config.holidayPaths.isEmpty())
            assertEquals(ImportResult.IMPORT_OK, helper.refreshIfNeeded())
            assertEquals(ImportResult.IMPORT_NOTHING_NEW, helper.refreshIfNeeded())
            assertNull(context.calendarsDB.getCalendarWithId(calendarId))
            assertTrue(managed().isEmpty())
            for (event in before) {
                val expected = if (deleteEvents) null else event.copy(calendarId = LOCAL_CALENDAR_ID)
                assertEquals(expected, context.eventsDB.getEventWithId(event.id!!))
            }
        }
    }

    @Test
    fun bundledFilesParseWithoutWritesAndInvalidReplacementIsRejected() {
        val before = context.eventsDB.getAllEvents()
        for (path in helper.load().countries.flatMap { it.paths }) {
            assertNotNull(path, IcsImporter(activity).parseHolidays(path, calendarId, reminders))
        }
        val fixture = File.createTempFile("invalid-holiday", ".ics", context.cacheDir)
        try {
            fixture.writeText("BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:incomplete\nDTSTART;VALUE=DATE:20270101\nSUMMARY:Test\n")
            assertNull(IcsImporter(activity).parseHolidays(fixture.path, calendarId, reminders, false))
        } finally {
            fixture.delete()
        }
        assertEquals(before, context.eventsDB.getAllEvents())
    }

    private fun select(vararg paths: String) = helper.updateSelection(paths.toSet(), reminders)

    private fun managed(): List<Event> = context.eventsDB.getAllEventsWithCalendarIds(listOf(calendarId))
        .filter { it.importId.startsWith(MANAGED_HOLIDAY_PREFIX) }
}
