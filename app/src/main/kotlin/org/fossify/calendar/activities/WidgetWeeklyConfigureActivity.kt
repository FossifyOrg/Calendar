package org.fossify.calendar.activities

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.core.view.children
import org.fossify.calendar.databinding.Horizontal1Binding
import org.fossify.calendar.databinding.HorizontalLineBinding
import org.fossify.calendar.databinding.Vertical1Binding
import org.fossify.calendar.databinding.VerticalLineBinding
import org.fossify.calendar.databinding.WidgetConfigWeeklyBinding
import org.fossify.calendar.databinding.WidgetWeekColumnBinding
import org.fossify.calendar.databinding.WidgetWeekDayLetterBinding
import org.fossify.calendar.databinding.WidgetWeekHourBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.getFirstDayOfWeekDt
import org.fossify.calendar.extensions.getWidgetFontSize
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.MyWidgetWeeklyProvider
import org.fossify.calendar.helpers.WeeklyCalendarImpl
import org.fossify.calendar.helpers.isWeekend
import org.fossify.calendar.interfaces.WeeklyCalendar
import org.fossify.calendar.models.DayWeekly
import org.fossify.commons.R
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.IS_CUSTOMIZING_COLORS
import org.joda.time.DateTime

class WidgetWeeklyConfigureActivity : SimpleActivity(), WeeklyCalendar {
    private var mDays: List<DayWeekly> = ArrayList()
    private var earliestEventStartHour = 0
    private var latestEventEndHour = WeeklyCalendarImpl.HOURS_PER_DAY

    private var mBgAlpha = 0f
    private var mWidgetId = 0
    private var mBgColorWithoutTransparency = 0
    private var mBgColor = 0
    private var mTextColor = 0

    private val binding by viewBinding(WidgetConfigWeeklyBinding::inflate)

    public override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(binding.root)
        setupEdgeToEdge(padTopSystem = listOf(binding.configHolder), padBottomSystem = listOf(binding.root))
        initVariables()

        val isCustomizingColors = intent.extras?.getBoolean(IS_CUSTOMIZING_COLORS) ?: false
        mWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !isCustomizingColors) {
            finish()
        }

        val primaryColor = getProperPrimaryColor()
        binding.apply {
            configSave.setOnClickListener { saveConfig() }
            configBgColor.setOnClickListener { pickBackgroundColor() }
            configTextColor.setOnClickListener { pickTextColor() }
            configBgSeekbar.setColors(mTextColor, primaryColor, primaryColor)
        }
        setupDayLabels()
        setupDayColumns()
    }

    private fun initVariables() {
        mBgColor = config.widgetBgColor
        mBgAlpha = Color.alpha(mBgColor) / 255f

        mBgColorWithoutTransparency = Color.rgb(Color.red(mBgColor), Color.green(mBgColor), Color.blue(mBgColor))
        binding.configBgSeekbar.apply {
            progress = (mBgAlpha * 100).toInt()

            onSeekBarChangeListener { progress ->
                mBgAlpha = progress / 100f
                updateBackgroundColor()
            }
        }
        updateBackgroundColor()

        mTextColor = config.widgetTextColor
        if (mTextColor == resources.getColor(R.color.default_widget_text_color) && isDynamicTheme()) {
            mTextColor = resources.getColor(R.color.you_primary_color, theme)
        }

        updateTextColor()

        WeeklyCalendarImpl(this, this).updateWeeklyCalendar(DateTime())
    }

    private fun saveConfig() {
        storeWidgetColors()
        requestWidgetUpdate()

        Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
            setResult(RESULT_OK, this)
        }
        finish()
    }

    private fun storeWidgetColors() {
        config.apply {
            widgetBgColor = mBgColor
            widgetTextColor = mTextColor
        }
    }

    private fun setupDayLabels() {
        val dayLetters = resources.getStringArray(org.fossify.commons.R.array.week_days_short)
            .toMutableList() as ArrayList<String>
        binding.configCalendar.weekLettersHolder.apply {
            removeAllViews()
            val smallerFontSize = context.getWidgetFontSize()
            var curDay = context.getFirstDayOfWeekDt(DateTime())
            for (i in 0 until config.weeklyViewDays) {
                val dayLetter = dayLetters[curDay.dayOfWeek - 1]

                val newView = WidgetWeekDayLetterBinding.inflate(
                    layoutInflater,
                    binding.configCalendar.weekLettersHolder,
                    false
                ).root
                newView.text = dayLetter
                newView.setTextColor(mTextColor)
                newView.textSize = smallerFontSize
                addView(newView)
                curDay = curDay.plusDays(1)
            }
        }
    }

    private fun setupDayColumns() {
        binding.configCalendar.weekEventsDayLines.removeAllViews()
        // columns that will contain events
        binding.configCalendar.weekEventsColumnsHolder.apply {
            removeAllViews()
            for (i in 0 until context.config.weeklyViewDays) {
                addView(WidgetWeekColumnBinding.inflate(layoutInflater,
                    binding.configCalendar.weekEventsColumnsHolder, false).root)
            }
        }
        // column on the left showing the time
        binding.configCalendar.timeColumn.apply {
            removeAllViews()
            addView(Vertical1Binding.inflate(layoutInflater,
                binding.configCalendar.timeColumn, false).root)
            for (i in earliestEventStartHour + 1 until latestEventEndHour) {
                val time = DateTime().withHourOfDay(i)
                addView(WidgetWeekHourBinding.inflate(layoutInflater,
                    binding.configCalendar.timeColumn, false).root.apply {
                    text = time.toString(Formatter.getHourPattern(context))
                    setTextColor(mTextColor)
                })
            }
            addView(Vertical1Binding.inflate(layoutInflater, binding.configCalendar.timeColumn, false).root)
        }
        binding.configCalendar.weekEventsHourLines.apply {
            removeAllViews()
            addView(Vertical1Binding.inflate(layoutInflater, binding.configCalendar.weekEventsHourLines, false).root)
            for (i in earliestEventStartHour + 1 until latestEventEndHour) {
                addView(HorizontalLineBinding.inflate(layoutInflater,
                    binding.configCalendar.weekEventsHourLines, false).root)
                addView(Vertical1Binding.inflate(layoutInflater,
                    binding.configCalendar.weekEventsHourLines, false).root)
            }
        }
    }

    private fun pickBackgroundColor() {
        ColorPickerDialog(this, mBgColorWithoutTransparency) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mBgColorWithoutTransparency = color
                updateBackgroundColor()
            }
        }
    }

    private fun pickTextColor() {
        ColorPickerDialog(this, mTextColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mTextColor = color
                updateTextColor()
                updateDays()
            }
        }
    }

    private fun requestWidgetUpdate() {
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetWeeklyProvider::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(mWidgetId))
            sendBroadcast(this)
        }
    }

    private fun updateTextColor() {
        binding.configTextColor.setFillWithStroke(mTextColor, mTextColor)
        binding.configSave.setTextColor(getProperPrimaryColor().getContrastColor())
        val weekendsTextColor = config.highlightWeekendsColor
        for ((i, view) in binding.configCalendar.weekLettersHolder.children.withIndex()) {
            if (view is TextView) {
                val textColor = if (config.highlightWeekends && isWeekend(mDays!![i].start.dayOfWeek)) {
                    weekendsTextColor
                } else {
                    mTextColor
                }
                view.setTextColor(textColor)
            }
        }
        for (view in binding.configCalendar.timeColumn.children) {
            if (view is TextView) {
                view.setTextColor(mTextColor)
            }
        }
    }

    private fun updateBackgroundColor() {
        mBgColor = mBgColorWithoutTransparency.adjustAlpha(mBgAlpha)
        binding.configCalendar.widgetWeekBackground.applyColorFilter(mBgColor)
        binding.configBgColor.setFillWithStroke(mBgColor, mBgColor)
        binding.configSave.backgroundTintList = ColorStateList.valueOf(getProperPrimaryColor())
    }

    private fun updateDays() {
        mDays.forEach {
            binding.configCalendar.weekEventsDayLines.apply {
                addView(VerticalLineBinding.inflate(layoutInflater,
                    binding.configCalendar.weekEventsDayLines, false).root)
                addView(Horizontal1Binding.inflate(layoutInflater,
                    binding.configCalendar.weekEventsDayLines, false).root)
            }
        }
    }

    override fun updateWeeklyCalendar(
        context: Context,
        days: ArrayList<DayWeekly>,
        earliestStartHour: Int,
        latestEndHour: Int,
    ) {
        runOnUiThread {
            mDays = days
            earliestEventStartHour = earliestStartHour
            latestEventEndHour = latestEndHour
            setupDayLabels()
            setupDayColumns()
            updateDays()
        }
    }

}
