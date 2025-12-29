package org.fossify.calendar.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.fossify.calendar.R
import org.fossify.calendar.databinding.ActivityTaskBinding
import org.fossify.calendar.dialogs.DeleteEventDialog
import org.fossify.calendar.dialogs.EditRepeatingEventDialog
import org.fossify.calendar.dialogs.ReminderWarningDialog
import org.fossify.calendar.dialogs.RepeatLimitTypePickerDialog
import org.fossify.calendar.dialogs.RepeatRuleWeeklyDialog
import org.fossify.calendar.dialogs.SelectCalendarDialog
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsDB
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.getNewEventTimestampFromCode
import org.fossify.calendar.extensions.getRepetitionText
import org.fossify.calendar.extensions.getShortDaysFromBitmask
import org.fossify.calendar.extensions.isTaskCompleted
import org.fossify.calendar.extensions.isXMonthlyRepetition
import org.fossify.calendar.extensions.isXWeeklyRepetition
import org.fossify.calendar.extensions.isXYearlyRepetition
import org.fossify.calendar.extensions.notifyEvent
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.extensions.shareEvents
import org.fossify.calendar.extensions.showEventRepeatIntervalDialog
import org.fossify.calendar.extensions.updateTaskCompletion
import org.fossify.calendar.helpers.CALENDAR_ID
import org.fossify.calendar.helpers.DELETE_ALL_OCCURRENCES
import org.fossify.calendar.helpers.DELETE_FUTURE_OCCURRENCES
import org.fossify.calendar.helpers.DELETE_SELECTED_OCCURRENCE
import org.fossify.calendar.helpers.EDIT_ALL_OCCURRENCES
import org.fossify.calendar.helpers.EDIT_FUTURE_OCCURRENCES
import org.fossify.calendar.helpers.EDIT_SELECTED_OCCURRENCE
import org.fossify.calendar.helpers.EVENT_COLOR
import org.fossify.calendar.helpers.EVENT_ID
import org.fossify.calendar.helpers.EVENT_OCCURRENCE_TS
import org.fossify.calendar.helpers.FLAG_ALL_DAY
import org.fossify.calendar.helpers.FLAG_TASK_COMPLETED
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.IS_DUPLICATE_INTENT
import org.fossify.calendar.helpers.IS_NEW_EVENT
import org.fossify.calendar.helpers.IS_TASK_COMPLETED
import org.fossify.calendar.helpers.LOCAL_CALENDAR_ID
import org.fossify.calendar.helpers.NEW_EVENT_START_TS
import org.fossify.calendar.helpers.ORIGINAL_START_TS
import org.fossify.calendar.helpers.REMINDER_1_MINUTES
import org.fossify.calendar.helpers.REMINDER_2_MINUTES
import org.fossify.calendar.helpers.REMINDER_3_MINUTES
import org.fossify.calendar.helpers.REMINDER_NOTIFICATION
import org.fossify.calendar.helpers.REMINDER_OFF
import org.fossify.calendar.helpers.REPEAT_INTERVAL
import org.fossify.calendar.helpers.REPEAT_LAST_DAY
import org.fossify.calendar.helpers.REPEAT_LIMIT
import org.fossify.calendar.helpers.REPEAT_ORDER_WEEKDAY
import org.fossify.calendar.helpers.REPEAT_ORDER_WEEKDAY_USE_LAST
import org.fossify.calendar.helpers.REPEAT_RULE
import org.fossify.calendar.helpers.REPEAT_SAME_DAY
import org.fossify.calendar.helpers.START_TS
import org.fossify.calendar.helpers.TASK
import org.fossify.calendar.helpers.TYPE_TASK
import org.fossify.calendar.helpers.generateImportId
import org.fossify.calendar.models.CalendarEntity
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.Reminder
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.addBitIf
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getDatePickerDialogTheme
import org.fossify.commons.extensions.getFormattedMinutes
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTimePickerDialogTheme
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.isGone
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.removeBit
import org.fossify.commons.extensions.setFillWithStroke
import org.fossify.commons.extensions.showPickSecondsDialogHelper
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.EVERY_DAY_BIT
import org.fossify.commons.helpers.FRIDAY_BIT
import org.fossify.commons.helpers.MONDAY_BIT
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.SATURDAY_BIT
import org.fossify.commons.helpers.SAVE_DISCARD_PROMPT_INTERVAL
import org.fossify.commons.helpers.SUNDAY_BIT
import org.fossify.commons.helpers.THURSDAY_BIT
import org.fossify.commons.helpers.TUESDAY_BIT
import org.fossify.commons.helpers.WEDNESDAY_BIT
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.getJavaDayOfWeekFromISO
import org.fossify.commons.models.RadioItem
import org.joda.time.DateTime

class TaskActivity : SimpleActivity() {
    private var mCalendarId = LOCAL_CALENDAR_ID
    private lateinit var mTaskDateTime: DateTime
    private lateinit var mTask: Event

    private var mIsAllDayTask = false
    private var mReminder1Minutes = REMINDER_OFF
    private var mReminder2Minutes = REMINDER_OFF
    private var mReminder3Minutes = REMINDER_OFF
    private var mReminder1Type = REMINDER_NOTIFICATION
    private var mReminder2Type = REMINDER_NOTIFICATION
    private var mReminder3Type = REMINDER_NOTIFICATION
    private var mRepeatInterval = 0
    private var mRepeatLimit = 0L
    private var mRepeatRule = 0
    private var mTaskOccurrenceTS = 0L
    private var mOriginalStartTS = 0L
    private var mTaskCompleted = false
    private var mLastSavePromptTS = 0L
    private var mIsNewTask = true
    private var mEventColor = 0
    private var mConvertedFromOriginalAllDay = false

    private val binding by viewBinding(ActivityTaskBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        refreshMenuItems()

        if (checkAppSideloading()) {
            return
        }

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.taskNestedScrollview))
        setupMaterialScrollListener(binding.taskNestedScrollview, binding.taskAppbar)

        val intent = intent ?: return
        updateColors()
        val taskId = intent.getLongExtra(EVENT_ID, 0L)
        ensureBackgroundThread {
            val task = eventsDB.getTaskWithId(taskId)
            if (taskId != 0L && task == null) {
                hideKeyboard()
                finish()
                return@ensureBackgroundThread
            }

            val storedCalendars =
                calendarsDB.getCalendars().toMutableList() as ArrayList<CalendarEntity>
            val localCalendar =
                storedCalendars.firstOrNull { it.id == config.lastUsedLocalCalendarId }
            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    gotTask(savedInstanceState, localCalendar, task)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar()
    }

    private fun setupTopAppBar() {
        setupTopAppBar(binding.taskAppbar, NavigationIcon.Arrow)
        binding.taskToolbar.setNavigationOnClickListener {
            maybeShowUnsavedChangesDialog {
                hideKeyboard()
                finish()
            }
        }
    }

    private fun refreshMenuItems() {
        if (::mTask.isInitialized) {
            binding.taskToolbar.menu.apply {
                findItem(R.id.delete).isVisible = mTask.id != null
                findItem(R.id.share).isVisible = mTask.id != null
                findItem(R.id.duplicate).isVisible = mTask.id != null
            }
        }
    }

    private fun setupOptionsMenu() {
        binding.taskToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save -> saveCurrentTask()
                R.id.delete -> deleteTask()
                R.id.duplicate -> duplicateTask()
                R.id.share -> shareTask()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun isTaskChanged(): Boolean {
        if (!this::mTask.isInitialized) {
            return false
        }

        val newStartTS: Long = mTaskDateTime.seconds()
        val hasTimeChanged = if (mOriginalStartTS == 0L) {
            mTask.startTS != newStartTS
        } else {
            mOriginalStartTS != newStartTS
        }

        val reminders = getReminders()
        val originalReminders = mTask.getReminders()
        return binding.taskTitle.text.toString() != mTask.title ||
                binding.taskDescription.text.toString() != mTask.description ||
                reminders != originalReminders ||
                mRepeatInterval != mTask.repeatInterval ||
                mRepeatRule != mTask.repeatRule ||
                mRepeatLimit != mTask.repeatLimit ||
                mCalendarId != mTask.calendarId ||
                mEventColor != mTask.color ||
                hasTimeChanged
    }

    override fun onBackPressedCompat(): Boolean {
        maybeShowUnsavedChangesDialog {
            performDefaultBack()
        }
        return true
    }

    private fun maybeShowUnsavedChangesDialog(discard: () -> Unit) {
        if (System.currentTimeMillis() - mLastSavePromptTS > SAVE_DISCARD_PROMPT_INTERVAL && isTaskChanged()) {
            mLastSavePromptTS = System.currentTimeMillis()
            ConfirmationAdvancedDialog(
                activity = this,
                message = "",
                messageId = org.fossify.commons.R.string.save_before_closing,
                positive = org.fossify.commons.R.string.save,
                negative = org.fossify.commons.R.string.discard
            ) {
                if (it) {
                    saveCurrentTask()
                } else {
                    discard()
                }
            }
        } else {
            discard()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::mTask.isInitialized) {
            return
        }

        outState.apply {
            putSerializable(TASK, mTask)
            putLong(START_TS, mTaskDateTime.seconds())
            putLong(CALENDAR_ID, mCalendarId)

            putInt(REMINDER_1_MINUTES, mReminder1Minutes)
            putInt(REMINDER_2_MINUTES, mReminder2Minutes)
            putInt(REMINDER_3_MINUTES, mReminder3Minutes)

            putInt(REPEAT_INTERVAL, mRepeatInterval)
            putInt(REPEAT_RULE, mRepeatRule)
            putLong(REPEAT_LIMIT, mRepeatLimit)

            putLong(CALENDAR_ID, mCalendarId)
            putBoolean(IS_NEW_EVENT, mIsNewTask)
            putLong(ORIGINAL_START_TS, mOriginalStartTS)
            putInt(EVENT_COLOR, mEventColor)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        if (!savedInstanceState.containsKey(START_TS)) {
            hideKeyboard()
            finish()
            return
        }

        savedInstanceState.apply {
            mTask = getSerializable(TASK) as Event
            mTaskDateTime = Formatter.getDateTimeFromTS(getLong(START_TS))
            mCalendarId = getLong(CALENDAR_ID)

            mReminder1Minutes = getInt(REMINDER_1_MINUTES)
            mReminder2Minutes = getInt(REMINDER_2_MINUTES)
            mReminder3Minutes = getInt(REMINDER_3_MINUTES)

            mRepeatInterval = getInt(REPEAT_INTERVAL)
            mRepeatRule = getInt(REPEAT_RULE)
            mRepeatLimit = getLong(REPEAT_LIMIT)
            mCalendarId = getLong(CALENDAR_ID)
            mIsNewTask = getBoolean(IS_NEW_EVENT)
            mOriginalStartTS = getLong(ORIGINAL_START_TS)
            mEventColor = getInt(EVENT_COLOR)
        }

        updateCalendar()
        updateTexts()
        setupMarkCompleteButton()
        checkRepeatTexts(mRepeatInterval)
        checkRepeatRule()
        updateActionBarTitle()
    }

    private fun gotTask(savedInstanceState: Bundle?, localCalendar: CalendarEntity?, task: Event?) {
        if (localCalendar == null) {
            config.lastUsedLocalCalendarId = LOCAL_CALENDAR_ID
        }

        mCalendarId =
            if (config.defaultCalendarId == -1L) config.lastUsedLocalCalendarId else config.defaultCalendarId

        if (task != null) {
            mTask = task
            mTaskOccurrenceTS = intent.getLongExtra(EVENT_OCCURRENCE_TS, 0L)
            mTaskCompleted = intent.getBooleanExtra(IS_TASK_COMPLETED, false)
            if (savedInstanceState == null) {
                setupEditTask()
            }

            if (intent.getBooleanExtra(IS_DUPLICATE_INTENT, false)) {
                mTask.id = null
                binding.taskToolbar.title = getString(R.string.new_task)
            }
        } else {
            mTask = Event(null)
            config.apply {
                mReminder1Minutes =
                    if (usePreviousEventReminders && lastEventReminderMinutes1 >= -1) lastEventReminderMinutes1 else defaultReminder1
                mReminder2Minutes =
                    if (usePreviousEventReminders && lastEventReminderMinutes2 >= -1) lastEventReminderMinutes2 else defaultReminder2
                mReminder3Minutes =
                    if (usePreviousEventReminders && lastEventReminderMinutes3 >= -1) lastEventReminderMinutes3 else defaultReminder3
            }

            if (savedInstanceState == null) {
                setupNewTask()
            }
        }

        binding.apply {
            taskAllDay.setOnCheckedChangeListener { _, isChecked -> toggleAllDay(isChecked) }
            taskAllDayHolder.setOnClickListener {
                taskAllDay.toggle()
            }

            taskDate.setOnClickListener { setupDate() }
            taskTime.setOnClickListener { setupTime() }
            calendarHolder.setOnClickListener { showCalendarDialog() }
            taskRepetition.setOnClickListener { showRepeatIntervalDialog() }
            taskRepetitionRuleHolder.setOnClickListener { showRepetitionRuleDialog() }
            taskRepetitionLimitHolder.setOnClickListener { showRepetitionTypePicker() }

            taskReminder1.setOnClickListener {
                handleNotificationAvailability {
                    if (config.wasAlarmWarningShown) {
                        showReminder1Dialog()
                    } else {
                        ReminderWarningDialog(this@TaskActivity) {
                            config.wasAlarmWarningShown = true
                            showReminder1Dialog()
                        }
                    }
                }
            }

            taskReminder2.setOnClickListener { showReminder2Dialog() }
            taskReminder3.setOnClickListener { showReminder3Dialog() }
            taskColorHolder.setOnClickListener { showTaskColorDialog() }
        }

        refreshMenuItems()
        setupMarkCompleteButton()

        if (savedInstanceState == null) {
            updateCalendar()
            updateTexts()
        }
    }

    private fun setupEditTask() {
        mIsNewTask = false
        val realStart = if (mTaskOccurrenceTS == 0L) mTask.startTS else mTaskOccurrenceTS
        mOriginalStartTS = realStart
        mTaskDateTime = Formatter.getDateTimeFromTS(realStart)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        binding.taskToolbar.title = getString(R.string.edit_task)

        mCalendarId = mTask.calendarId
        mReminder1Minutes = mTask.reminder1Minutes
        mReminder2Minutes = mTask.reminder2Minutes
        mReminder3Minutes = mTask.reminder3Minutes
        mReminder1Type = mTask.reminder1Type
        mReminder2Type = mTask.reminder2Type
        mReminder3Type = mTask.reminder3Type
        mRepeatInterval = mTask.repeatInterval
        mRepeatLimit = mTask.repeatLimit
        mRepeatRule = mTask.repeatRule
        mEventColor = mTask.color

        binding.taskTitle.setText(mTask.title)
        binding.taskDescription.setText(mTask.description)
        binding.taskAllDay.isChecked = mTask.getIsAllDay()
        toggleAllDay(mTask.getIsAllDay())
        checkRepeatTexts(mRepeatInterval)
    }

    private fun setupNewTask() {
        val startTS = intent.getLongExtra(NEW_EVENT_START_TS, 0L)
        val dateTime = Formatter.getDateTimeFromTS(startTS)
        mTaskDateTime = dateTime

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.taskTitle.requestFocus()
        binding.taskToolbar.title = getString(R.string.new_task)

        mTask.apply {
            this.startTS = mTaskDateTime.seconds()
            this.endTS = mTaskDateTime.seconds()
            reminder1Minutes = mReminder1Minutes
            reminder1Type = mReminder1Type
            reminder2Minutes = mReminder2Minutes
            reminder2Type = mReminder2Type
            reminder3Minutes = mReminder3Minutes
            reminder3Type = mReminder3Type
            calendarId = mCalendarId
        }
    }

    private fun saveCurrentTask() {
        if (config.wasAlarmWarningShown || (mReminder1Minutes == REMINDER_OFF && mReminder2Minutes == REMINDER_OFF && mReminder3Minutes == REMINDER_OFF)) {
            ensureBackgroundThread {
                saveTask()
            }
        } else {
            ReminderWarningDialog(this) {
                config.wasAlarmWarningShown = true
                ensureBackgroundThread {
                    saveTask()
                }
            }
        }
    }

    private fun saveTask() {
        val newTitle = binding.taskTitle.value
        if (newTitle.isEmpty()) {
            toast(R.string.title_empty)
            runOnUiThread {
                binding.taskTitle.requestFocus()
            }
            return
        }

        val wasRepeatable = mTask.repeatInterval > 0
        val newImportId = if (mTask.id != null) {
            mTask.importId
        } else {
            generateImportId()
        }

        val reminders = getReminders()
        if (!binding.taskAllDay.isChecked) {
            if ((reminders.getOrNull(2)?.minutes ?: 0) < -1) {
                reminders.removeAt(2)
            }

            if ((reminders.getOrNull(1)?.minutes ?: 0) < -1) {
                reminders.removeAt(1)
            }

            if ((reminders.getOrNull(0)?.minutes ?: 0) < -1) {
                reminders.removeAt(0)
            }
        }

        val reminder1 = reminders.getOrNull(0) ?: Reminder(REMINDER_OFF, REMINDER_NOTIFICATION)
        val reminder2 = reminders.getOrNull(1) ?: Reminder(REMINDER_OFF, REMINDER_NOTIFICATION)
        val reminder3 = reminders.getOrNull(2) ?: Reminder(REMINDER_OFF, REMINDER_NOTIFICATION)

        config.apply {
            if (usePreviousEventReminders) {
                lastEventReminderMinutes1 = reminder1.minutes
                lastEventReminderMinutes2 = reminder2.minutes
                lastEventReminderMinutes3 = reminder3.minutes
            }
        }

        config.lastUsedLocalCalendarId = mCalendarId
        mTask.apply {
            startTS = mTaskDateTime.withSecondOfMinute(0).withMillisOfSecond(0).seconds()
            endTS = startTS
            title = newTitle
            description = binding.taskDescription.value

            // migrate completed task to the new completed tasks db
            if (!wasRepeatable && mTask.isTaskCompleted()) {
                mTask.flags = mTask.flags.removeBit(FLAG_TASK_COMPLETED)
                ensureBackgroundThread {
                    updateTaskCompletion(copy(startTS = mOriginalStartTS), true)
                }
            }
            importId = newImportId
            flags = mTask.flags.addBitIf(binding.taskAllDay.isChecked, FLAG_ALL_DAY)
            lastUpdated = System.currentTimeMillis()
            calendarId = mCalendarId
            type = TYPE_TASK

            reminder1Minutes = reminder1.minutes
            reminder1Type = mReminder1Type
            reminder2Minutes = reminder2.minutes
            reminder2Type = mReminder2Type
            reminder3Minutes = reminder3.minutes
            reminder3Type = mReminder3Type

            repeatInterval = mRepeatInterval
            repeatLimit = if (repeatInterval == 0) 0 else mRepeatLimit
            repeatRule = mRepeatRule
            color = mEventColor
        }

        if (mTask.getReminders().isNotEmpty()) {
            handleNotificationPermission { granted ->
                if (granted) {
                    ensureBackgroundThread {
                        storeTask(wasRepeatable)
                    }
                } else {
                    PermissionRequiredDialog(
                        activity = this,
                        textId = org.fossify.commons.R.string.allow_notifications_reminders,
                        positiveActionCallback = { openNotificationSettings() }
                    )
                }
            }
        } else {
            storeTask(wasRepeatable)
        }
    }

    private fun storeTask(wasRepeatable: Boolean) {
        if (mTask.id == null) {
            eventsHelper.insertTask(mTask, true) {
                hideKeyboard()

                if (DateTime.now().isAfter(mTaskDateTime.millis)) {
                    if (mTask.repeatInterval == 0 && mTask.getReminders()
                            .any { it.type == REMINDER_NOTIFICATION }
                    ) {
                        notifyEvent(mTask)
                    }
                }

                finish()
            }
        } else {
            if (mRepeatInterval > 0 && wasRepeatable) {
                runOnUiThread {
                    showEditRepeatingTaskDialog()
                }
            } else {
                hideKeyboard()
                eventsHelper.updateEvent(mTask, updateAtCalDAV = false, showToasts = true) {
                    finish()
                }
            }
        }
    }

    private fun showEditRepeatingTaskDialog() {
        EditRepeatingEventDialog(this, isTask = true) {
            hideKeyboard()
            if (it == null) {
                return@EditRepeatingEventDialog
            }
            when (it) {
                EDIT_SELECTED_OCCURRENCE -> {
                    eventsHelper.editSelectedOccurrence(mTask, mTaskOccurrenceTS, true) {
                        finish()
                    }
                }

                EDIT_FUTURE_OCCURRENCES -> {
                    eventsHelper.editFutureOccurrences(mTask, mTaskOccurrenceTS, true) {
                        finish()
                    }
                }

                EDIT_ALL_OCCURRENCES -> {
                    eventsHelper.editAllOccurrences(mTask, mOriginalStartTS, showToasts = true) {
                        finish()
                    }
                }
            }
        }
    }

    private fun shareTask() {
        shareEvents(arrayListOf(mTask.id!!))
    }

    private fun deleteTask() {
        if (mTask.id == null) {
            return
        }

        DeleteEventDialog(this, arrayListOf(mTask.id!!), mTask.repeatInterval > 0, isTask = true) {
            ensureBackgroundThread {
                when (it) {
                    DELETE_SELECTED_OCCURRENCE -> eventsHelper.deleteRepeatingEventOccurrence(
                        parentEventId = mTask.id!!,
                        occurrenceTS = mTaskOccurrenceTS,
                        addToCalDAV = false
                    )

                    DELETE_FUTURE_OCCURRENCES -> eventsHelper.addEventRepeatLimit(
                        eventId = mTask.id!!,
                        occurrenceTS = mTaskOccurrenceTS
                    )

                    DELETE_ALL_OCCURRENCES -> eventsHelper.deleteEvent(
                        id = mTask.id!!,
                        deleteFromCalDAV = false
                    )
                }

                runOnUiThread {
                    hideKeyboard()
                    finish()
                }
            }
        }
    }

    private fun duplicateTask() {
        // the activity has the singleTask launchMode to avoid some glitches, so finish it before relaunching
        hideKeyboard()
        finish()
        Intent(this, TaskActivity::class.java).apply {
            putExtra(EVENT_ID, mTask.id)
            putExtra(IS_DUPLICATE_INTENT, true)
            startActivity(this)
        }
    }

    private fun setupDate() {
        hideKeyboard()
        val datePicker = DatePickerDialog(
            this,
            getDatePickerDialogTheme(),
            dateSetListener,
            mTaskDateTime.year,
            mTaskDateTime.monthOfYear - 1,
            mTaskDateTime.dayOfMonth
        )

        datePicker.datePicker.firstDayOfWeek = getJavaDayOfWeekFromISO(config.firstDayOfWeek)
        datePicker.show()
    }

    private fun setupTime() {
        hideKeyboard()
        if (isDynamicTheme()) {
            val timeFormat = if (config.use24HourFormat) {
                TimeFormat.CLOCK_24H
            } else {
                TimeFormat.CLOCK_12H
            }

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(timeFormat)
                .setHour(mTaskDateTime.hourOfDay)
                .setMinute(mTaskDateTime.minuteOfHour)
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .build()

            timePicker.addOnPositiveButtonClickListener {
                timeSet(timePicker.hour, timePicker.minute)
            }

            timePicker.show(supportFragmentManager, "")
        } else {
            TimePickerDialog(
                this,
                getTimePickerDialogTheme(),
                timeSetListener,
                mTaskDateTime.hourOfDay,
                mTaskDateTime.minuteOfHour,
                config.use24HourFormat
            ).show()
        }
    }

    private val dateSetListener =
        DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            dateSet(year, monthOfYear, dayOfMonth)
        }

    private val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
        timeSet(hourOfDay, minute)
    }

    private fun dateSet(year: Int, month: Int, day: Int) {
        mTaskDateTime = mTaskDateTime.withDate(year, month + 1, day)
        updateDateText()
        checkRepeatRule()
    }

    private fun timeSet(hours: Int, minutes: Int) {
        mTaskDateTime = mTaskDateTime.withHourOfDay(hours).withMinuteOfHour(minutes)
        updateTimeText()
    }

    private fun updateTexts() {
        updateDateText()
        updateTimeText()
        updateReminderTexts()
        updateRepetitionText()
    }

    private fun checkRepeatRule() {
        if (mRepeatInterval.isXWeeklyRepetition()) {
            val day = mRepeatRule
            if (day == MONDAY_BIT || day == TUESDAY_BIT || day == WEDNESDAY_BIT || day == THURSDAY_BIT || day == FRIDAY_BIT || day == SATURDAY_BIT || day == SUNDAY_BIT) {
                setRepeatRule(1 shl (mTaskDateTime.dayOfWeek - 1))
            }
        } else if (mRepeatInterval.isXMonthlyRepetition() || mRepeatInterval.isXYearlyRepetition()) {
            if (mRepeatRule == REPEAT_LAST_DAY && !isLastDayOfTheMonth()) {
                mRepeatRule = REPEAT_SAME_DAY
            }
            checkRepetitionRuleText()
        }
    }

    private fun updateDateText() {
        binding.taskDate.text = Formatter.getDate(this, mTaskDateTime)
    }

    private fun updateTimeText() {
        binding.taskTime.text = Formatter.getTime(this, mTaskDateTime)
    }

    private fun toggleAllDay(isChecked: Boolean) {
        hideKeyboard()

        // One-time migration: when converting from all-day to timed for the first time,
        // set default start time to avoid unexpected time values
        if (!isChecked && mTask.getIsAllDay() && !mConvertedFromOriginalAllDay) {
            val defaultStartTS =
                getNewEventTimestampFromCode(Formatter.getDayCodeFromDateTime(mTaskDateTime))
            val defaultStartTime = Formatter.getDateTimeFromTS(defaultStartTS)

            mTaskDateTime = mTaskDateTime.withTime(
                defaultStartTime.hourOfDay,
                defaultStartTime.minuteOfHour,
                0,
                0
            )

            mConvertedFromOriginalAllDay = true
            updateTimeText()
        }

        mIsAllDayTask = isChecked
        binding.taskTime.beGoneIf(isChecked)
    }

    private fun setupMarkCompleteButton() {
        binding.toggleMarkComplete.setOnClickListener { toggleCompletion() }
        binding.toggleMarkComplete.beVisibleIf(mTask.id != null)
        updateTaskCompletedButton()
        ensureBackgroundThread {
            // the stored value might be incorrect so update it (e.g. user completed the task via notification action before editing)
            mTaskCompleted = isTaskCompleted(mTask.copy(startTS = mOriginalStartTS))
            runOnUiThread {
                updateTaskCompletedButton()
            }
        }
    }

    private fun updateTaskCompletedButton() {
        val primaryColor = getProperPrimaryColor()
        if (mTaskCompleted) {
            binding.toggleMarkComplete.background = ContextCompat.getDrawable(
                this, org.fossify.commons.R.drawable.button_background_stroke
            )
            binding.toggleMarkComplete.setText(R.string.mark_incomplete)
            binding.toggleMarkComplete.setTextColor(getProperTextColor())
        } else {
            binding.toggleMarkComplete.setTextColor(primaryColor.getContrastColor())
        }

        binding.toggleMarkComplete.background.applyColorFilter(primaryColor)
    }

    private fun toggleCompletion() {
        ensureBackgroundThread {
            val task = mTask.copy(startTS = mOriginalStartTS)
            updateTaskCompletion(task, completed = !mTaskCompleted)
            hideKeyboard()
            finish()
        }
    }

    private fun updateReminderTexts() {
        updateReminder1Text()
        updateReminder2Text()
        updateReminder3Text()
    }

    private fun updateReminder1Text() {
        binding.taskReminder1.text = getFormattedMinutes(mReminder1Minutes)
    }

    private fun updateReminder2Text() {
        binding.taskReminder2.apply {
            beGoneIf(isGone() && mReminder1Minutes == REMINDER_OFF)
            if (mReminder2Minutes == REMINDER_OFF) {
                text = resources.getString(R.string.add_another_reminder)
                alpha = 0.4f
            } else {
                text = getFormattedMinutes(mReminder2Minutes)
                alpha = 1f
            }
        }
    }

    private fun updateReminder3Text() {
        binding.taskReminder3.apply {
            beGoneIf(isGone() && (mReminder2Minutes == REMINDER_OFF || mReminder1Minutes == REMINDER_OFF))
            if (mReminder3Minutes == REMINDER_OFF) {
                text = resources.getString(R.string.add_another_reminder)
                alpha = 0.4f
            } else {
                text = getFormattedMinutes(mReminder3Minutes)
                alpha = 1f
            }
        }
    }

    private fun showReminder1Dialog() {
        showPickSecondsDialogHelper(mReminder1Minutes, showDuringDayOption = mIsAllDayTask) {
            mReminder1Minutes = if (it == -1 || it == 0) it else it / 60
            updateReminderTexts()
        }
    }

    private fun showReminder2Dialog() {
        showPickSecondsDialogHelper(mReminder2Minutes, showDuringDayOption = mIsAllDayTask) {
            mReminder2Minutes = if (it == -1 || it == 0) it else it / 60
            updateReminderTexts()
        }
    }

    private fun showReminder3Dialog() {
        showPickSecondsDialogHelper(mReminder3Minutes, showDuringDayOption = mIsAllDayTask) {
            mReminder3Minutes = if (it == -1 || it == 0) it else it / 60
            updateReminderTexts()
        }
    }

    private fun getReminders(): ArrayList<Reminder> {
        var reminders = arrayListOf(
            Reminder(mReminder1Minutes, mReminder1Type),
            Reminder(mReminder2Minutes, mReminder2Type),
            Reminder(mReminder3Minutes, mReminder3Type)
        )
        reminders = reminders.filter { it.minutes != REMINDER_OFF }.sortedBy { it.minutes }
            .toMutableList() as ArrayList<Reminder>
        return reminders
    }

    private fun showCalendarDialog() {
        hideKeyboard()
        SelectCalendarDialog(
            activity = this,
            currCalendar = mCalendarId,
            showCalDAVCalendars = false,
            showNewCalendarOption = false,
            addLastUsedOneAsFirstOption = false,
            showOnlyWritable = true,
            showManageCalendars = true
        ) {
            mCalendarId = it.id!!
            updateCalendar()
        }
    }

    private fun updateCalendar() {
        ensureBackgroundThread {
            val calendar = calendarsDB.getCalendarWithId(mCalendarId)
            if (calendar != null) {
                runOnUiThread {
                    binding.calendarTitle.text = calendar.title
                    binding.calendarSubtitle.text = getString(R.string.offline_never_synced)
                    updateTaskColorInfo(calendar.color)
                }
            }
            binding.taskColorImage.beVisibleIf(calendar != null)
            binding.taskColorHolder.beVisibleIf(calendar != null)
            binding.taskColorDivider.beVisibleIf(calendar != null)
        }
    }

    private fun showTaskColorDialog() {
        hideKeyboard()
        ensureBackgroundThread {
            val calendar = calendarsDB.getCalendarWithId(mCalendarId)!!
            val currentColor = if (mEventColor == 0) {
                calendar.color
            } else {
                mEventColor
            }

            runOnUiThread {
                ColorPickerDialog(
                    activity = this,
                    color = currentColor,
                    addDefaultColorButton = true
                ) { wasPositivePressed, newColor ->
                    if (wasPositivePressed) {
                        if (newColor != currentColor) {
                            mEventColor = newColor
                            updateTaskColorInfo(defaultColor = calendar.color)
                        }
                    }
                }
            }
        }
    }

    private fun updateTaskColorInfo(defaultColor: Int) {
        val taskColor = if (mEventColor == 0) {
            defaultColor
        } else {
            mEventColor
        }

        binding.taskColor.setFillWithStroke(taskColor, getProperBackgroundColor())
    }

    private fun updateColors() {
        binding.apply {
            updateTextColors(taskNestedScrollview)
            val textColor = getProperTextColor()
            arrayOf(
                taskTimeImage, taskReminderImage, calendarImage, taskRepetitionImage, taskColorImage
            ).forEach {
                it.applyColorFilter(textColor)
            }
        }
    }

    private fun showRepeatIntervalDialog() {
        showEventRepeatIntervalDialog(mRepeatInterval) {
            setRepeatInterval(it)
        }
    }

    private fun setRepeatInterval(interval: Int) {
        mRepeatInterval = interval
        updateRepetitionText()
        checkRepeatTexts(interval)

        when {
            mRepeatInterval.isXWeeklyRepetition() -> setRepeatRule(1 shl (mTaskDateTime.dayOfWeek - 1))
            mRepeatInterval.isXMonthlyRepetition() -> setRepeatRule(REPEAT_SAME_DAY)
            mRepeatInterval.isXYearlyRepetition() -> setRepeatRule(REPEAT_SAME_DAY)
        }
    }

    private fun checkRepeatTexts(limit: Int) {
        binding.taskRepetitionLimitHolder.beGoneIf(limit == 0)
        checkRepetitionLimitText()

        binding.taskRepetitionRuleHolder.beVisibleIf(mRepeatInterval.isXWeeklyRepetition() || mRepeatInterval.isXMonthlyRepetition() || mRepeatInterval.isXYearlyRepetition())
        checkRepetitionRuleText()
    }

    private fun showRepetitionTypePicker() {
        hideKeyboard()
        RepeatLimitTypePickerDialog(this, mRepeatLimit, mTaskDateTime.seconds()) {
            setRepeatLimit(it)
        }
    }

    private fun setRepeatLimit(limit: Long) {
        mRepeatLimit = limit
        checkRepetitionLimitText()
    }

    private fun checkRepetitionLimitText() {
        binding.taskRepetitionLimit.text = when {
            mRepeatLimit == 0L -> {
                binding.taskRepetitionLimitLabel.text = getString(R.string.repeat)
                resources.getString(R.string.forever)
            }

            mRepeatLimit > 0 -> {
                binding.taskRepetitionLimitLabel.text = getString(R.string.repeat_till)
                val repeatLimitDateTime = Formatter.getDateTimeFromTS(mRepeatLimit)
                Formatter.getFullDate(this, repeatLimitDateTime)
            }

            else -> {
                binding.taskRepetitionLimitLabel.text = getString(R.string.repeat)
                "${-mRepeatLimit} ${getString(R.string.times)}"
            }
        }
    }

    private fun showRepetitionRuleDialog() {
        hideKeyboard()
        when {
            mRepeatInterval.isXWeeklyRepetition() -> RepeatRuleWeeklyDialog(this, mRepeatRule) {
                setRepeatRule(it)
            }

            mRepeatInterval.isXMonthlyRepetition() -> {
                val items = getAvailableMonthlyRepetitionRules()
                RadioGroupDialog(this, items, mRepeatRule) {
                    setRepeatRule(it as Int)
                }
            }

            mRepeatInterval.isXYearlyRepetition() -> {
                val items = getAvailableYearlyRepetitionRules()
                RadioGroupDialog(this, items, mRepeatRule) {
                    setRepeatRule(it as Int)
                }
            }
        }
    }

    private fun getAvailableMonthlyRepetitionRules(): ArrayList<RadioItem> {
        val items = arrayListOf(
            RadioItem(
                REPEAT_SAME_DAY,
                getString(R.string.repeat_on_the_same_day_monthly)
            )
        )

        items.add(
            RadioItem(
                REPEAT_ORDER_WEEKDAY,
                getRepeatXthDayString(true, REPEAT_ORDER_WEEKDAY)
            )
        )
        if (isLastWeekDayOfMonth()) {
            items.add(
                RadioItem(
                    REPEAT_ORDER_WEEKDAY_USE_LAST,
                    getRepeatXthDayString(true, REPEAT_ORDER_WEEKDAY_USE_LAST)
                )
            )
        }

        if (isLastDayOfTheMonth()) {
            items.add(
                RadioItem(
                    REPEAT_LAST_DAY,
                    getString(R.string.repeat_on_the_last_day_monthly)
                )
            )
        }
        return items
    }

    private fun getAvailableYearlyRepetitionRules(): ArrayList<RadioItem> {
        val items = arrayListOf(
            RadioItem(
                REPEAT_SAME_DAY,
                getString(R.string.repeat_on_the_same_day_yearly)
            )
        )

        items.add(
            RadioItem(
                REPEAT_ORDER_WEEKDAY,
                getRepeatXthDayInMonthString(true, REPEAT_ORDER_WEEKDAY)
            )
        )
        if (isLastWeekDayOfMonth()) {
            items.add(
                RadioItem(
                    REPEAT_ORDER_WEEKDAY_USE_LAST,
                    getRepeatXthDayInMonthString(true, REPEAT_ORDER_WEEKDAY_USE_LAST)
                )
            )
        }

        return items
    }

    private fun isLastDayOfTheMonth() =
        mTaskDateTime.dayOfMonth == mTaskDateTime.dayOfMonth().withMaximumValue().dayOfMonth

    private fun isLastWeekDayOfMonth() =
        mTaskDateTime.monthOfYear != mTaskDateTime.plusDays(7).monthOfYear

    private fun getRepeatXthDayString(includeBase: Boolean, repeatRule: Int): String {
        val dayOfWeek = mTaskDateTime.dayOfWeek
        val base = getBaseString(dayOfWeek)
        val order = getOrderString(repeatRule)
        val dayString = getDayString(dayOfWeek)
        return if (includeBase) {
            "$base $order $dayString"
        } else {
            val everyString =
                getString(if (isMaleGender(mTaskDateTime.dayOfWeek)) R.string.every_m else R.string.every_f)
            "$everyString $order $dayString"
        }
    }

    private fun getBaseString(day: Int): String {
        return getString(
            if (isMaleGender(day)) {
                R.string.repeat_every_m
            } else {
                R.string.repeat_every_f
            }
        )
    }

    private fun isMaleGender(day: Int) = day == 1 || day == 2 || day == 4 || day == 5

    private fun getOrderString(repeatRule: Int): String {
        val dayOfMonth = mTaskDateTime.dayOfMonth
        var order = (dayOfMonth - 1) / 7 + 1
        if (isLastWeekDayOfMonth() && repeatRule == REPEAT_ORDER_WEEKDAY_USE_LAST) {
            order = -1
        }

        val isMale = isMaleGender(mTaskDateTime.dayOfWeek)
        return getString(
            when (order) {
                1 -> if (isMale) R.string.first_m else R.string.first_f
                2 -> if (isMale) R.string.second_m else R.string.second_f
                3 -> if (isMale) R.string.third_m else R.string.third_f
                4 -> if (isMale) R.string.fourth_m else R.string.fourth_f
                5 -> if (isMale) R.string.fifth_m else R.string.fifth_f
                else -> if (isMale) R.string.last_m else R.string.last_f
            }
        )
    }

    private fun getDayString(day: Int): String {
        return getString(
            when (day) {
                1 -> R.string.monday_alt
                2 -> R.string.tuesday_alt
                3 -> R.string.wednesday_alt
                4 -> R.string.thursday_alt
                5 -> R.string.friday_alt
                6 -> R.string.saturday_alt
                else -> R.string.sunday_alt
            }
        )
    }

    private fun getRepeatXthDayInMonthString(includeBase: Boolean, repeatRule: Int): String {
        val weekDayString = getRepeatXthDayString(includeBase, repeatRule)
        val monthString =
            resources.getStringArray(org.fossify.commons.R.array.in_months)[mTaskDateTime.monthOfYear - 1]
        return "$weekDayString $monthString"
    }

    private fun setRepeatRule(rule: Int) {
        mRepeatRule = rule
        checkRepetitionRuleText()
        if (rule == 0) {
            setRepeatInterval(0)
        }
    }

    private fun checkRepetitionRuleText() {
        when {
            mRepeatInterval.isXWeeklyRepetition() -> {
                binding.taskRepetitionRule.text = if (mRepeatRule == EVERY_DAY_BIT) {
                    getString(org.fossify.commons.R.string.every_day)
                } else {
                    getShortDaysFromBitmask(mRepeatRule)
                }
            }

            mRepeatInterval.isXMonthlyRepetition() -> {
                val repeatString =
                    if (mRepeatRule == REPEAT_ORDER_WEEKDAY_USE_LAST || mRepeatRule == REPEAT_ORDER_WEEKDAY)
                        R.string.repeat else R.string.repeat_on

                binding.taskRepetitionRuleLabel.text = getString(repeatString)
                binding.taskRepetitionRule.text = getMonthlyRepetitionRuleText()
            }

            mRepeatInterval.isXYearlyRepetition() -> {
                val repeatString =
                    if (mRepeatRule == REPEAT_ORDER_WEEKDAY_USE_LAST || mRepeatRule == REPEAT_ORDER_WEEKDAY)
                        R.string.repeat else R.string.repeat_on

                binding.taskRepetitionRuleLabel.text = getString(repeatString)
                binding.taskRepetitionRule.text = getYearlyRepetitionRuleText()
            }
        }
    }

    private fun getMonthlyRepetitionRuleText() = when (mRepeatRule) {
        REPEAT_SAME_DAY -> getString(R.string.the_same_day)
        REPEAT_LAST_DAY -> getString(R.string.the_last_day)
        else -> getRepeatXthDayString(false, mRepeatRule)
    }

    private fun getYearlyRepetitionRuleText() = when (mRepeatRule) {
        REPEAT_SAME_DAY -> getString(R.string.the_same_day)
        else -> getRepeatXthDayInMonthString(false, mRepeatRule)
    }

    private fun updateRepetitionText() {
        binding.taskRepetition.text = getRepetitionText(mRepeatInterval)
    }

    private fun updateActionBarTitle() {
        binding.taskToolbar.title = if (mIsNewTask) {
            getString(R.string.new_task)
        } else {
            getString(R.string.edit_task)
        }
    }
}
