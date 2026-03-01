package org.fossify.calendar.fragments

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.view.DragEvent
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.collection.LongSparseArray
import androidx.fragment.app.Fragment
import org.fossify.calendar.R
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.AllDayEventsHolderLineBinding
import org.fossify.calendar.databinding.FragmentWeekBinding
import org.fossify.calendar.databinding.WeekAllDayEventMarkerBinding
import org.fossify.calendar.databinding.WeekEventMarkerBinding
import org.fossify.calendar.databinding.WeekGridItemBinding
import org.fossify.calendar.databinding.WeekNowMarkerBinding
import org.fossify.calendar.databinding.WeeklyViewDayColumnBinding
import org.fossify.calendar.databinding.WeeklyViewDayLetterBinding
import org.fossify.calendar.dialogs.EditRepeatingEventDialog
import org.fossify.calendar.extensions.checkViewStrikeThrough
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsDB
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.getWeeklyViewItemHeight
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.extensions.shouldStrikeThrough
import org.fossify.calendar.helpers.Config
import org.fossify.calendar.helpers.EDIT_ALL_OCCURRENCES
import org.fossify.calendar.helpers.EDIT_FUTURE_OCCURRENCES
import org.fossify.calendar.helpers.EDIT_SELECTED_OCCURRENCE
import org.fossify.calendar.helpers.EVENT_ID
import org.fossify.calendar.helpers.EVENT_OCCURRENCE_TS
import org.fossify.calendar.helpers.FLAG_ALL_DAY
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.IS_TASK_COMPLETED
import org.fossify.calendar.helpers.NEW_EVENT_SET_HOUR_DURATION
import org.fossify.calendar.helpers.NEW_EVENT_START_TS
import org.fossify.calendar.helpers.TYPE_EVENT
import org.fossify.calendar.helpers.TYPE_TASK
import org.fossify.calendar.helpers.WEEK_START_TIMESTAMP
import org.fossify.calendar.helpers.WeeklyCalendarImpl
import org.fossify.calendar.helpers.getActivityToOpen
import org.fossify.calendar.helpers.isWeekend
import org.fossify.calendar.interfaces.WeekFragmentListener
import org.fossify.calendar.interfaces.WeeklyCalendar
import org.fossify.calendar.models.DayWeekly
import org.fossify.calendar.models.Event
import org.fossify.calendar.views.MyScrollView
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.onGlobalLayout
import org.fossify.commons.extensions.realScreenSize
import org.fossify.commons.extensions.removeBit
import org.fossify.commons.extensions.usableScreenSize
import org.fossify.commons.helpers.HIGHER_ALPHA
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MEDIUM_ALPHA
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isNougatPlus
import org.fossify.commons.models.RadioItem
import org.joda.time.DateTime
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WeekFragment : Fragment(), WeeklyCalendar {
    private val WEEKLY_EVENT_ID_LABEL = "event_id_label"
    private val PLUS_FADEOUT_DELAY = 5000L
    private val MIN_SCALE_FACTOR = 0.3f
    private val MAX_SCALE_FACTOR = 5f
    private val MIN_SCALE_DIFFERENCE = 0.02f
    private val SCALE_RANGE = MAX_SCALE_FACTOR - MIN_SCALE_FACTOR

    var listener: WeekFragmentListener? = null
    private var weekTimestamp = 0L
    private var weekDateTime = DateTime()
    private var rowHeight = 0f
    private var todayColumnIndex = -1
    private var primaryColor = 0
    private var lastHash = 0
    private var prevScaleSpanY = 0f
    private var scaleCenterPercent = 0f
    private var defaultRowHeight = 0f
    private var screenHeight = 0
    private var rowHeightsAtScale = 0f
    private var prevScaleFactor = 0f
    private var mWasDestroyed = false
    private var isFragmentVisible = false
    private var wasFragmentInit = false
    private var wasExtraHeightAdded = false
    private var dimPastEvents = true
    private var dimCompletedTasks = true
    private var highlightWeekends = false
    private var wasScaled = false
    private var isPrintVersion = false
    private var selectedGrid: View? = null
    private var currentTimeView: ImageView? = null
    private var fadeOutHandler = Handler()
    private var allDayHolders = ArrayList<RelativeLayout>()
    private var allDayRows = ArrayList<Int>()
    private var currDays = ArrayList<DayWeekly>()
    private var dayColumns = ArrayList<RelativeLayout>()
    private var calendarColors = LongSparseArray<Int>()
    private var currentlyDraggedView: View? = null

    private lateinit var binding: FragmentWeekBinding
    private lateinit var scrollView: MyScrollView
    private lateinit var res: Resources
    private lateinit var config: Config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        res = requireContext().resources
        config = requireContext().config
        rowHeight = requireContext().getWeeklyViewItemHeight()
        defaultRowHeight = res.getDimension(R.dimen.weekly_view_row_height)
        weekTimestamp = requireArguments().getLong(WEEK_START_TIMESTAMP)
        weekDateTime = Formatter.getDateTimeFromTS(weekTimestamp)
        dimPastEvents = config.dimPastEvents
        dimCompletedTasks = config.dimCompletedTasks
        highlightWeekends = config.highlightWeekends
        primaryColor = requireContext().getProperPrimaryColor()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val fullHeight = requireContext().getWeeklyViewItemHeight().toInt() * 24
        binding = FragmentWeekBinding.inflate(inflater, container, false).apply {
            scrollView = weekEventsScrollview
            weekHorizontalGridHolder.layoutParams.height = fullHeight
            weekEventsColumnsHolder.layoutParams.height = fullHeight

            val scaleDetector = getViewScaleDetector()
            scrollView.setOnTouchListener { _, motionEvent ->
                scaleDetector.onTouchEvent(motionEvent)
                if (motionEvent.action == MotionEvent.ACTION_UP && wasScaled) {
                    scrollView.isScrollable = true
                    wasScaled = false
                    true
                } else {
                    false
                }
            }
        }

        addDayColumns()
        scrollView.setOnScrollviewListener(object : MyScrollView.ScrollViewListener {
            override fun onScrollChanged(
                scrollView: MyScrollView,
                x: Int,
                y: Int,
                oldx: Int,
                oldy: Int
            ) {
                checkScrollLimits(y)
            }
        })

        scrollView.onGlobalLayout {
            if (fullHeight < scrollView.height) {
                scrollView.layoutParams.height =
                    fullHeight - res.getDimension(org.fossify.commons.R.dimen.one_dp).toInt()
            }

            val initialScrollY = (rowHeight * config.startWeeklyAt).toInt()
            updateScrollY(max(listener?.getCurrScrollY() ?: 0, initialScrollY))
        }

        wasFragmentInit = true
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        requireContext().eventsHelper.getCalendars(requireActivity(), false) {
            it.map { calendar ->
                calendarColors.put(calendar.id!!, calendar.color)
            }
        }

        setupDayLabels()
        updateCalendar()

        if (rowHeight != 0f && binding.root.width != 0) {
            addCurrentTimeIndicator()
        }
    }

    override fun onPause() {
        super.onPause()
        wasExtraHeightAdded = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mWasDestroyed = true
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        isFragmentVisible = menuVisible
        if (isFragmentVisible && wasFragmentInit) {
            listener?.updateHoursTopMargin(binding.weekTopHolder.height)
            checkScrollLimits(scrollView.scrollY)

            // fix some glitches like at swiping from a fully scaled out fragment with all-day events to an empty one
            val fullFragmentHeight =
                (listener?.getFullFragmentHeight() ?: 0) - binding.weekTopHolder.height
            if (scrollView.height < fullFragmentHeight) {
                config.weeklyViewItemHeightMultiplier = fullFragmentHeight / 24 / defaultRowHeight
                updateViewScale()
                listener?.updateRowHeight(rowHeight.toInt())
            }
        }
    }

    fun updateCalendar() {
        if (context != null) {
            currentlyDraggedView = null
            WeeklyCalendarImpl(this, requireContext()).updateWeeklyCalendar(weekDateTime)
        }
    }

    private fun addDayColumns() {
        binding.weekEventsColumnsHolder.removeAllViews()
        (0 until config.weeklyViewDays).forEach {
            val column = WeeklyViewDayColumnBinding.inflate(
                layoutInflater,
                binding.weekEventsColumnsHolder,
                false
            ).root
            binding.weekEventsColumnsHolder.addView(column)
            dayColumns.add(column)
        }
    }

    private fun setupDayLabels() {
        var curDay = weekDateTime
        val todayCode = Formatter.getDayCodeFromDateTime(DateTime())
        val screenWidth = context?.usableScreenSize?.x ?: return
        val dayWidth = screenWidth / config.weeklyViewDays
        val useLongerDayLabels = dayWidth > res.getDimension(R.dimen.weekly_view_min_day_label)

        binding.weekLettersHolder.removeAllViews()
        for (i in 0 until config.weeklyViewDays) {
            val dayCode = Formatter.getDayCodeFromDateTime(curDay)
            val labelIDs = if (useLongerDayLabels) {
                org.fossify.commons.R.array.week_days_short
            } else {
                org.fossify.commons.R.array.week_day_letters
            }

            val dayLetters = res.getStringArray(labelIDs).toMutableList() as ArrayList<String>
            val dayLetter = dayLetters[curDay.dayOfWeek - 1]

            val textColor = when {
                !isPrintVersion && todayCode == dayCode -> primaryColor
                highlightWeekends && isWeekend(curDay.dayOfWeek) -> config.highlightWeekendsColor
                isPrintVersion -> resources.getColor(org.fossify.commons.R.color.theme_light_text_color)
                else -> requireContext().getProperTextColor()
            }

            val label = WeeklyViewDayLetterBinding.inflate(
                layoutInflater,
                binding.weekLettersHolder,
                false
            ).root
            label.text = "$dayLetter\n${curDay.dayOfMonth}"
            label.setTextColor(textColor)
            if (todayCode == dayCode) {
                todayColumnIndex = i
            }
            label.setOnClickListener {
                (activity as MainActivity).openDayFromWeekly(Formatter.getDateTimeFromCode(dayCode))
            }

            binding.weekLettersHolder.addView(label)
            curDay = curDay.plusDays(1)
        }
    }

    private fun checkScrollLimits(y: Int) {
        if (isFragmentVisible) {
            listener?.scrollTo(y)
        }
    }

    private fun initGrid() {
        (0 until config.weeklyViewDays).mapNotNull { dayColumns.getOrNull(it) }
            .forEachIndexed { index, layout ->
                layout.removeAllViews()
                val gestureDetector = getViewGestureDetector(layout, index)

                layout.setOnTouchListener { _, motionEvent ->
                    gestureDetector.onTouchEvent(motionEvent)
                    true
                }

                layout.setOnDragListener { _, dragEvent ->
                    when (dragEvent.action) {
                        DragEvent.ACTION_DRAG_STARTED -> dragEvent.clipDescription.hasMimeType(
                            ClipDescription.MIMETYPE_TEXT_PLAIN
                        )

                        DragEvent.ACTION_DRAG_ENTERED,
                        DragEvent.ACTION_DRAG_EXITED,
                        DragEvent.ACTION_DRAG_LOCATION,
                        DragEvent.ACTION_DRAG_ENDED -> true

                        DragEvent.ACTION_DROP -> {
                            try {
                                val (eventId, originalStartTS, originalEndTS) = dragEvent.clipData.getItemAt(
                                    0
                                ).text.toString().split(";").map { it.toLong() }
                                val startHour = (dragEvent.y / rowHeight).toInt()
                                ensureBackgroundThread {
                                    val event = context?.eventsDB?.getEventOrTaskWithId(eventId)
                                    event?.let {
                                        val currentStartTime =
                                            Formatter.getDateTimeFromTS(event.startTS)
                                        val startTime = weekDateTime.plusDays(index)
                                            .withTime(
                                                startHour,
                                                currentStartTime.minuteOfHour,
                                                currentStartTime.secondOfMinute,
                                                currentStartTime.millisOfSecond
                                            ).seconds()
                                        val currentEventDuration = event.endTS - event.startTS
                                        val endTime = startTime + currentEventDuration
                                        val newEvent = event.copy(
                                            startTS = startTime,
                                            endTS = endTime,
                                            flags = event.flags.removeBit(FLAG_ALL_DAY)
                                        )
                                        if (event.repeatInterval > 0) {
                                            val activity = this.activity as SimpleActivity
                                            activity.runOnUiThread {
                                                EditRepeatingEventDialog(activity) {
                                                    activity.hideKeyboard()
                                                    when (it) {
                                                        null -> {
                                                            revertDraggedEvent()
                                                        }

                                                        EDIT_SELECTED_OCCURRENCE -> {
                                                            context?.eventsHelper?.editSelectedOccurrence(
                                                                newEvent,
                                                                originalStartTS,
                                                                false
                                                            ) {
                                                                updateCalendar()
                                                            }
                                                        }

                                                        EDIT_FUTURE_OCCURRENCES -> {
                                                            context?.eventsHelper?.editFutureOccurrences(
                                                                newEvent,
                                                                originalStartTS,
                                                                false
                                                            ) {
                                                                // we need to refresh all fragments because they can contain future occurrences
                                                                (activity as MainActivity).refreshItems()
                                                            }
                                                        }

                                                        EDIT_ALL_OCCURRENCES -> {
                                                            context?.eventsHelper?.editAllOccurrences(
                                                                newEvent,
                                                                originalStartTS,
                                                                originalEndTS,
                                                                false
                                                            ) {
                                                                (activity as MainActivity).refreshItems()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            if (event.startTS == newEvent.startTS && event.endTS == newEvent.endTS) {
                                                revertDraggedEvent()
                                            } else {
                                                context?.eventsHelper?.updateEvent(
                                                    newEvent,
                                                    updateAtCalDAV = true,
                                                    showToasts = false
                                                ) {
                                                    updateCalendar()
                                                }
                                            }
                                        }
                                    }
                                }
                                true
                            } catch (ignored: Exception) {
                                false
                            }
                        }

                        else -> false
                    }
                }
            }
    }

    private fun revertDraggedEvent() {
        activity?.runOnUiThread {
            currentlyDraggedView?.beVisible()
            currentlyDraggedView = null
        }
    }

    private fun getViewGestureDetector(view: ViewGroup, index: Int): GestureDetector {
        return GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(event: MotionEvent): Boolean {
                selectedGrid?.animation?.cancel()
                selectedGrid?.beGone()

                val hour = (event.y / rowHeight).toInt()
                selectedGrid = WeekGridItemBinding.inflate(layoutInflater).root.apply {
                    view.addView(this)
                    background = ColorDrawable(primaryColor)
                    layoutParams.width = view.width
                    layoutParams.height = rowHeight.toInt()
                    y = hour * rowHeight - hour / 2
                    applyColorFilter(primaryColor.getContrastColor())

                    setOnClickListener {
                        val timestamp =
                            weekDateTime.plusDays(index).withTime(hour, 0, 0, 0).seconds()
                        if (config.allowCreatingTasks) {
                            val items = arrayListOf(
                                RadioItem(TYPE_EVENT, getString(R.string.event)),
                                RadioItem(TYPE_TASK, getString(R.string.task))
                            )

                            RadioGroupDialog(activity!!, items) {
                                launchNewEventIntent(timestamp, it as Int == TYPE_TASK)
                            }
                        } else {
                            launchNewEventIntent(timestamp, false)
                        }
                    }

                    // do not use setStartDelay, it will trigger instantly if the device has disabled animations
                    fadeOutHandler.removeCallbacksAndMessages(null)
                    fadeOutHandler.postDelayed({
                        animate().alpha(0f).withEndAction {
                            beGone()
                        }
                    }, PLUS_FADEOUT_DELAY)
                }
                return super.onSingleTapUp(event)
            }
        })
    }

    private fun launchNewEventIntent(timestamp: Long, isTask: Boolean) {
        Intent(context, getActivityToOpen(isTask)).apply {
            putExtra(NEW_EVENT_START_TS, timestamp)
            putExtra(NEW_EVENT_SET_HOUR_DURATION, true)
            startActivity(this)
        }
    }

    private fun getViewScaleDetector(): ScaleGestureDetector {
        return ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val percent = (prevScaleSpanY - detector.currentSpanY) / screenHeight
                    prevScaleSpanY = detector.currentSpanY

                    val wantedFactor =
                        config.weeklyViewItemHeightMultiplier - (SCALE_RANGE * percent)
                    var newFactor = max(min(wantedFactor, MAX_SCALE_FACTOR), MIN_SCALE_FACTOR)
                    if (scrollView.height > defaultRowHeight * newFactor * 24) {
                        newFactor = scrollView.height / 24f / defaultRowHeight
                    }

                    if (Math.abs(newFactor - prevScaleFactor) > MIN_SCALE_DIFFERENCE) {
                        prevScaleFactor = newFactor
                        config.weeklyViewItemHeightMultiplier = newFactor
                        updateViewScale()
                        listener?.updateRowHeight(rowHeight.toInt())

                        val targetY =
                            rowHeightsAtScale * rowHeight - scaleCenterPercent * getVisibleHeight()
                        scrollView.scrollTo(0, targetY.toInt())
                    }
                    return super.onScale(detector)
                }

                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    scaleCenterPercent = detector.focusY / scrollView.height
                    rowHeightsAtScale =
                        (scrollView.scrollY + scaleCenterPercent * getVisibleHeight()) / rowHeight
                    scrollView.isScrollable = false
                    prevScaleSpanY = detector.currentSpanY
                    prevScaleFactor = config.weeklyViewItemHeightMultiplier
                    wasScaled = true
                    screenHeight = context!!.realScreenSize.y
                    return super.onScaleBegin(detector)
                }
            })
    }

    private fun getVisibleHeight(): Float {
        val fullContentHeight = rowHeight * 24
        val visibleRatio = scrollView.height / fullContentHeight
        return fullContentHeight * visibleRatio
    }

    override fun updateWeeklyCalendar(
        context: Context,
        days: ArrayList<DayWeekly>,
        earliestStartHour: Int,
        latestEndHour: Int,
    ) {
        val newHash = days.hashCode()
        if (newHash == lastHash || mWasDestroyed) {
            return
        }

        lastHash = newHash

        requireActivity().runOnUiThread {
            if (activity != null && isAdded) {
                currDays = days
                addDays(days)
            }
        }
    }

    private fun updateViewScale() {
        rowHeight = context?.getWeeklyViewItemHeight() ?: return

        val oneDp = res.getDimension(org.fossify.commons.R.dimen.one_dp).toInt()
        val fullHeight = max(rowHeight.toInt() * 24, scrollView.height + oneDp)
        scrollView.layoutParams.height = fullHeight - oneDp
        binding.weekHorizontalGridHolder.layoutParams.height = fullHeight
        binding.weekEventsColumnsHolder.layoutParams.height = fullHeight
        addDays(currDays)
    }

    private fun addDays(days: ArrayList<DayWeekly>) {
        initGrid()
        allDayHolders.clear()
        allDayRows.clear()
        binding.weekAllDayHolder.removeAllViews()

        val minuteHeight = rowHeight / 60
        val minimalHeight = res.getDimension(R.dimen.weekly_view_minimal_event_height).toInt()
        val density = res.displayMetrics.density.roundToInt()

        for ((dayOfWeek, day) in days.withIndex()) {
            for (event in day.topBarEvents) {
                addAllDayEvent(dayOfWeek, event)
            }
            for (ews in day.dayEvents) {
                val dayColumn = dayColumns[dayOfWeek]
                val event = ews.event
                WeekEventMarkerBinding.inflate(layoutInflater).apply {
                    var backgroundColor = if (event.color == 0) {
                        calendarColors.get(event.calendarId, primaryColor)
                    } else {
                        event.color
                    }
                    var textColor = backgroundColor.getContrastColor()

                    val adjustAlpha = if (event.isTask()) {
                        dimCompletedTasks && event.isTaskCompleted()
                    } else {
                        dimPastEvents && event.isPastEvent && !isPrintVersion
                    }

                    if (adjustAlpha) {
                        backgroundColor = backgroundColor.adjustAlpha(MEDIUM_ALPHA)
                        textColor = textColor.adjustAlpha(HIGHER_ALPHA)
                    }

                    root.background = ColorDrawable(backgroundColor)
                    dayColumn.addView(root)
                    root.y = ews.startMinute * minuteHeight

                    // compensate grid offset
                    root.y -= (ews.startMinute / 60) / 2

                    weekEventTaskImage.beVisibleIf(event.isTask())
                    if (event.isTask()) {
                        weekEventTaskImage.applyColorFilter(textColor)
                    }

                    weekEventLabel.apply {
                        setTextColor(textColor)
                        maxLines = if (event.isTask() || event.startTS == event.endTS) {
                            1
                        } else {
                            3
                        }

                        text = event.title
                        checkViewStrikeThrough(event.shouldStrikeThrough())
                        contentDescription = text

                        val durationMinutes = ews.endMinute - ews.startMinute
                        minHeight = minimalHeight.coerceAtLeast((durationMinutes * minuteHeight).toInt() - 1)
                    }

                    (root.layoutParams as RelativeLayout.LayoutParams).apply {
                        width = (dayColumn.width - 1) / ews.slotMax
                        root.x = (width * ews.slot).toFloat()
                        if (ews.slot > 0) {
                            root.x += density
                            width -= density
                        }
                    }

                    root.setOnClickListener {
                        Intent(context, getActivityToOpen(event.isTask())).apply {
                            putExtra(EVENT_ID, event.id!!)
                            putExtra(EVENT_OCCURRENCE_TS, event.startTS)
                            putExtra(IS_TASK_COMPLETED, event.isTaskCompleted())
                            startActivity(this)
                        }
                    }

                    root.setOnLongClickListener { view ->
                        currentlyDraggedView = view
                        val shadowBuilder = View.DragShadowBuilder(view)
                        val clipData = ClipData.newPlainText(
                            WEEKLY_EVENT_ID_LABEL,
                            "${event.id};${event.startTS};${event.endTS}"
                        )
                        if (isNougatPlus()) {
                            view.startDragAndDrop(clipData, shadowBuilder, null, 0)
                        } else {
                            view.startDrag(clipData, shadowBuilder, null, 0)
                        }
                        true
                    }

                    root.setOnDragListener(DragListener())
                }
            }
        }

        checkTopHolderHeight()
        addCurrentTimeIndicator()
    }

    private fun addTopEventLine() {
        val allDaysLine = AllDayEventsHolderLineBinding.inflate(layoutInflater).root
        binding.weekAllDayHolder.addView(allDaysLine)
        allDayHolders.add(allDaysLine)
    }

    private fun addCurrentTimeIndicator() {
        if (todayColumnIndex != -1) {
            val calendar = Calendar.getInstance()
            val minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            if (todayColumnIndex >= dayColumns.size) {
                currentTimeView?.alpha = 0f
                return
            }

            if (currentTimeView != null) {
                binding.weekEventsHolder.removeView(currentTimeView)
            }

            if (isPrintVersion) {
                return
            }

            val weeklyViewDays = config.weeklyViewDays
            currentTimeView = WeekNowMarkerBinding.inflate(layoutInflater).root.apply {
                applyColorFilter(primaryColor)
                binding.weekEventsHolder.addView(this, 0)
                val extraWidth =
                    res.getDimension(org.fossify.commons.R.dimen.activity_margin).toInt()
                val markerHeight = res.getDimension(R.dimen.weekly_view_now_height).toInt()
                val minuteHeight = rowHeight / 60
                (layoutParams as RelativeLayout.LayoutParams).apply {
                    width = (binding.root.width / weeklyViewDays) + extraWidth
                    height = markerHeight
                }

                x = if (weeklyViewDays == 1) {
                    0f
                } else {
                    (binding.root.width / weeklyViewDays * todayColumnIndex).toFloat() - extraWidth / 2f
                }

                y = minutes * minuteHeight - markerHeight / 2
            }
        }
    }

    private fun checkTopHolderHeight() {
        binding.weekTopHolder.onGlobalLayout {
            if (isFragmentVisible && activity != null && !mWasDestroyed) {
                listener?.updateHoursTopMargin(binding.weekTopHolder.height)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun addAllDayEvent(dayOfWeek: Int, event: Event) {
        WeekAllDayEventMarkerBinding.inflate(layoutInflater).apply {
            var backgroundColor = if (event.color == 0) {
                calendarColors.get(event.calendarId, primaryColor)
            } else {
                event.color
            }
            var textColor = backgroundColor.getContrastColor()

            val adjustAlpha = if (event.isTask()) {
                dimCompletedTasks && event.isTaskCompleted()
            } else {
                dimPastEvents && event.isPastEvent && !isPrintVersion
            }

            if (adjustAlpha) {
                backgroundColor = backgroundColor.adjustAlpha(LOWER_ALPHA)
                textColor = textColor.adjustAlpha(HIGHER_ALPHA)
            }

            root.background = ColorDrawable(backgroundColor)

            weekEventLabel.apply {
                setTextColor(textColor)
                maxLines = if (event.isTask()) 1 else 2
                text = event.title
                checkViewStrikeThrough(event.shouldStrikeThrough())
                contentDescription = text
            }

            weekEventTaskImage.beVisibleIf(event.isTask())
            if (event.isTask()) {
                weekEventTaskImage.applyColorFilter(textColor)
            }

            // horizontal positioning
            val dayWidth = binding.root.width / config.weeklyViewDays
            val endDateTime = Formatter.getDateTimeFromTS(event.endTS)
            var lastDay = currDays.indexOfLast { it.start < endDateTime }
            if (lastDay == -1) {
                lastDay = config.weeklyViewDays
            }
            lastDay = lastDay.coerceAtLeast(dayOfWeek)

            // vertical positioning (i.e. find a row where this event fits)
            var drawAtLine = allDayRows.indexOfFirst { it < dayOfWeek }
            if (drawAtLine < 0) {
                drawAtLine = allDayRows.size
                addTopEventLine()
                allDayRows.add(lastDay)
            } else {
                allDayRows[drawAtLine] = lastDay
            }
            allDayHolders[drawAtLine].addView(root)

            (root.layoutParams as RelativeLayout.LayoutParams).apply {
                leftMargin = dayOfWeek * dayWidth
                bottomMargin = 1
                width = (dayWidth) * (lastDay - dayOfWeek + 1)
            }

            calculateExtraHeight()

            root.setOnClickListener {
                Intent(context, getActivityToOpen(event.isTask())).apply {
                    putExtra(EVENT_ID, event.id)
                    putExtra(EVENT_OCCURRENCE_TS, event.startTS)
                    putExtra(IS_TASK_COMPLETED, event.isTaskCompleted())
                    startActivity(this)
                }
            }
        }
    }

    private fun calculateExtraHeight() {
        binding.weekTopHolder.onGlobalLayout {
            if (activity != null && !mWasDestroyed) {
                if (isFragmentVisible) {
                    listener?.updateHoursTopMargin(binding.weekTopHolder.height)
                }

                if (!wasExtraHeightAdded) {
                    wasExtraHeightAdded = true
                }
            }
        }
    }

    fun updateScrollY(y: Int) {
        if (wasFragmentInit) {
            scrollView.scrollY = y
        }
    }

    fun updateNotVisibleViewScaleLevel() {
        if (!isFragmentVisible) {
            updateViewScale()
        }
    }

    fun togglePrintMode() {
        isPrintVersion = !isPrintVersion
        updateCalendar()
        setupDayLabels()
        addDays(currDays)
    }

    inner class DragListener : View.OnDragListener {
        override fun onDrag(view: View, dragEvent: DragEvent): Boolean {
            return when (dragEvent.action) {
                DragEvent.ACTION_DRAG_STARTED -> currentlyDraggedView == view
                DragEvent.ACTION_DRAG_ENTERED -> {
                    view.beGone()
                    false
                }
                // handle ACTION_DRAG_LOCATION due to https://stackoverflow.com/a/19460338
                DragEvent.ACTION_DRAG_LOCATION -> true
                DragEvent.ACTION_DROP -> {
                    view.beVisible()
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    if (!dragEvent.result) {
                        view.beVisible()
                    }
                    true
                }

                else -> false
            }
        }
    }
}
