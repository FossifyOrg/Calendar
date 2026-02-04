package org.fossify.calendar.activities

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import org.fossify.calendar.databinding.DayMonthlyNumberViewBinding
import org.fossify.calendar.databinding.TopNavigationBinding
import org.fossify.calendar.databinding.WidgetConfigWeeklyBinding
import org.fossify.calendar.extensions.addDayEvents
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.isWeekendIndex
import org.fossify.calendar.helpers.MyWidgetWeeklyProvider
import org.fossify.calendar.helpers.WeeklyCalendarImpl
import org.fossify.calendar.helpers.isWeekend
import org.fossify.calendar.interfaces.WeeklyCalendar
import org.fossify.calendar.models.DayWeekly
import org.fossify.commons.R
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.IS_CUSTOMIZING_COLORS
import org.fossify.commons.helpers.LOWER_ALPHA
import org.joda.time.DateTime

class WidgetWeeklyConfigureActivity : SimpleActivity(), WeeklyCalendar {
    private var mDays: List<DayWeekly>? = null

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
        /*val weekendsTextColor = config.highlightWeekendsColor
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
        }*/
    }

    private fun updateBackgroundColor() {
        mBgColor = mBgColorWithoutTransparency.adjustAlpha(mBgAlpha)
        binding.configCalendar.widgetWeekBackground.applyColorFilter(mBgColor)
        binding.configBgColor.setFillWithStroke(mBgColor, mBgColor)
        binding.configSave.backgroundTintList = ColorStateList.valueOf(getProperPrimaryColor())
    }

    private fun updateDays() {
        // TODO
    }

    override fun updateWeeklyCalendar(context: Context, days: ArrayList<DayWeekly>) {
        runOnUiThread {
            mDays = days
            updateDays()
        }
    }

}
