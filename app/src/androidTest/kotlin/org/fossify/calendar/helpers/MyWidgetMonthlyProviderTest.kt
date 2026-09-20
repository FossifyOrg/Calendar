package org.fossify.calendar.helpers

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.joda.time.DateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.fossify.calendar.R
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.models.DayMonthly
import org.fossify.calendar.models.Event
import org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE
import org.fossify.commons.helpers.FONT_SIZE_LARGE
import org.fossify.commons.helpers.FONT_SIZE_MEDIUM
import org.fossify.commons.helpers.FONT_SIZE_SMALL
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MyWidgetMonthlyProviderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val monthStart = DateTime(2026, 8, 31, 0, 0)
    private val updateDays = MyWidgetMonthlyProvider::class.java.getDeclaredMethod(
        "updateDays", Context::class.java, RemoteViews::class.java, List::class.java
    ).apply { isAccessible = true }

    @Test
    fun laterWeeksDoNotInheritEarlierOverlaps() = instrumentation.runOnMainSync {
        val events = listOf(
            event(1, 0, 10, "First"), event(2, 10, 19, "Second"), event(3, 19, 27, "Third"),
            event(4, 27, 34, "Fourth"), event(5, 34, 40, "Fifth")
        )
        val root = render(events, heightDp = 300)
        for ((dayIndex, title) in listOf(14 to "Second", 21 to "Third", 28 to "Fourth", 35 to "Fifth")) {
            assertEquals(2, day(root, dayIndex).childCount)
            assertEquals(title, label(root, dayIndex, 0).text.toString())
            assertTrue(slot(root, dayIndex, 0).height > 0)
        }
    }

    @Test
    fun shorterEventsFillAvailableSlots() = instrumentation.runOnMainSync {
        val root = render(listOf(event(1, 0, 3, "Conference"), event(2, 3, 5, "Trip"), event(3, 4, 4, "Call")))
        assertEquals(3, day(root, 4).childCount)
        assertEquals("Call", label(root, 4, 0).text.toString())
        assertEquals("", label(root, 4, 1).text.toString())
        assertEquals(slot(root, 3, 1).top, slot(root, 4, 1).top)
    }

    @Test
    fun spacersAndBarsAlignAtEveryFontSize() = instrumentation.runOnMainSync {
        val events = listOf(event(1, 0, 3, "Conference"), event(2, 3, 5, "Trip"))
        val fontSizes = listOf(FONT_SIZE_SMALL, FONT_SIZE_MEDIUM, FONT_SIZE_LARGE, FONT_SIZE_EXTRA_LARGE)
        for (grid in listOf(false, true)) {
            for (fontScale in listOf(1f, 1.5f)) {
                for (fontSize in fontSizes) {
                    val root = render(events, fontSize, grid, fontScale = fontScale)
                    val spacer = slot(root, 4, 0)
                    assertEquals(View.INVISIBLE, spacer.findViewById<View>(R.id.day_monthly_event_background).visibility)
                    assertEquals(slot(root, 3, 0).height, spacer.height)
                    assertEquals(slot(root, 3, 1).top, slot(root, 4, 1).top)
                    assertEquals(slot(root, 3, 1).height, slot(root, 4, 1).height)
                    assertTrue(slot(root, 3, 1).height > 0)
                }
            }
        }
    }

    @Test
    fun tallerScriptsDoNotChangeSegmentHeightsOrClipTitles() = instrumentation.runOnMainSync {
        val firstWeekEvents = listOf(event(3, 0, 3, "Conference"), event(4, 3, 5, "Trip"))
        val firstWeek = render(firstWeekEvents, heightDp = 420)
        for (title in listOf("🧳 Family holiday", "家族旅行", "العطلة الصيفية", "परिवार की छुट्टी")) {
            val root = render(listOf(event(1, 0, 3, title), event(2, 3, 5, "Trip")))
            val height = slot(root, 0, 0).height
            assertEquals(height, slot(root, 1, 0).height)
            assertEquals(height, slot(root, 4, 0).height)
            assertEquals(height, slot(root, 4, 1).height)
            val titleView = label(root, 0, 0)
            assertEquals(1, titleView.maxLines)
            assertTrue("Title's first line must fit its row: $title", titleView.layout.getLineBottom(0) <= titleView.height)

            val withLaterEvent = render(firstWeekEvents + event(5, 28, 31, title), heightDp = 420)
            for (row in 0..1) {
                val original = slot(firstWeek, 3, row)
                val actual = slot(withLaterEvent, 3, row)
                assertEquals("A later week's title must not resize earlier rows: $title", original.height, actual.height)
                assertEquals(original.top, actual.top)
                assertTrue(actual.bottom <= day(withLaterEvent, 3).height)
                val label = label(withLaterEvent, 3, row)
                assertTrue(label.layout.getLineBottom(0) <= label.height)
            }
        }
    }

    @Test
    fun startAndEndCornersFollowLayoutDirection() = instrumentation.runOnMainSync {
        for (rtl in listOf(false, true)) {
            val root = render(listOf(event(1, 0, 3, "Conference")), rtl = rtl)
            assertEquals(rtl, day(root, 0).left > day(root, 3).left)
            val startCorners = corners(root, 0)
            val endCorners = corners(root, 3)
            assertEquals(!rtl, startCorners[0] > 0f)
            assertEquals(rtl, startCorners[2] > 0f)
            assertEquals(rtl, endCorners[0] > 0f)
            assertEquals(!rtl, endCorners[2] > 0f)
        }
    }

    @Test
    fun titlesRestartAtWeekAndVisibleMonthBoundaries() = instrumentation.runOnMainSync {
        val root = render(listOf(event(1, -2, 2, "Ongoing"), event(2, 5, 9, "Vacation")))
        assertEquals("Ongoing", label(root, 0, 0).text.toString())
        assertEquals("", label(root, 1, 0).text.toString())
        assertEquals("Vacation", label(root, 5, 0).text.toString())
        assertEquals("", label(root, 6, 0).text.toString())
        assertEquals("Vacation", label(root, 7, 0).text.toString())
        assertEquals("", label(root, 8, 0).text.toString())
    }

    @Test
    fun updateCanBeBuiltOnTheEventLoaderThread() {
        assertNull(Looper.myLooper())
        val context = widgetContext(FONT_SIZE_MEDIUM, false, 1f)
        val views = RemoteViews(context.packageName, R.layout.fragment_month_widget)
        val events = listOf(event(1, 0, 3, "Trip"), event(2, 2, 4, "Conference"))
        updateDays.invoke(MyWidgetMonthlyProvider(), context, views, days(events))
    }

    private fun event(id: Long, firstDay: Int, lastDay: Int, title: String) = Event(
        id = id,
        startTS = monthStart.plusDays(firstDay).seconds(),
        endTS = monthStart.plusDays(lastDay + 1).minusSeconds(1).seconds(),
        title = title,
        flags = FLAG_ALL_DAY,
        color = Color.BLUE
    )

    private fun days(events: List<Event>) = (0 until 42).map { i ->
        val date = monthStart.plusDays(i)
        DayMonthly(
            date.dayOfMonth, date.monthOfYear == 9, false, date.toString("yyyyMMdd"), date.weekOfWeekyear,
            ArrayList(events.filter { it.startTS < date.plusDays(1).seconds() && it.endTS >= date.seconds() }),
            i, i % 7 >= 5
        )
    }

    private fun widgetContext(fontSize: Int, rtl: Boolean, fontScale: Float): Context {
        val configuration = Configuration(instrumentation.targetContext.resources.configuration).apply {
            this.fontScale = fontScale
            val locale = Locale.forLanguageTag(if (rtl) "ar" else "en")
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val base = instrumentation.targetContext.createConfigurationContext(configuration)
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                instrumentation.context.getSharedPreferences(name, mode)
        }.apply {
            config.fontSize = fontSize
            config.showWeekNumbers = false
            config.highlightWeekends = false
            config.dimPastEvents = false
        }
    }

    private fun render(
        events: List<Event>,
        fontSize: Int = FONT_SIZE_MEDIUM,
        grid: Boolean = false,
        rtl: Boolean = false,
        heightDp: Int = 900,
        fontScale: Float = 1f
    ): ViewGroup {
        val context = widgetContext(fontSize, rtl, fontScale)
        val views = RemoteViews(context.packageName,
            if (grid) R.layout.fragment_month_widget_grid else R.layout.fragment_month_widget)
        updateDays.invoke(MyWidgetMonthlyProvider(), context, views, days(events))
        val root = views.apply(context, FrameLayout(context)) as ViewGroup
        root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val density = context.resources.displayMetrics.density
        val width = (380 * density).toInt()
        val height = (heightDp * density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        return root
    }

    private fun day(root: View, index: Int): ViewGroup = root.findViewById(
        instrumentation.targetContext.resources.getIdentifier("day_$index", "id", instrumentation.targetContext.packageName)
    )

    private fun slot(root: View, dayIndex: Int, slotIndex: Int): View = day(root, dayIndex).getChildAt(slotIndex + 1)

    private fun label(root: View, dayIndex: Int, slotIndex: Int): TextView =
        slot(root, dayIndex, slotIndex).findViewById(R.id.day_monthly_event_id)

    private fun corners(root: View, dayIndex: Int): FloatArray =
        (slot(root, dayIndex, 0).findViewById<ImageView>(R.id.day_monthly_event_background).drawable as GradientDrawable)
            .cornerRadii!!
}
