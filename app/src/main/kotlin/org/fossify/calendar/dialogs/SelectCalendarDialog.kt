package org.fossify.calendar.dialogs

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.activities.ManageCalendarsActivity
import org.fossify.calendar.databinding.DialogSelectCalendarBinding
import org.fossify.calendar.databinding.RadioButtonWithColorBinding
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.models.CalendarEntity
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.setFillWithStroke
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding

class SelectCalendarDialog(
    val activity: Activity,
    val currCalendar: Long,
    val showCalDAVCalendars: Boolean,
    val showNewCalendarOption: Boolean,
    val addLastUsedOneAsFirstOption: Boolean,
    val showOnlyWritable: Boolean,
    var showManageCalendars: Boolean,
    val callback: (calendar: CalendarEntity) -> Unit
) {
    companion object {
        private const val NEW_CALENDAR_ID = -2L
        private const val LAST_USED_CALENDAR_ID = -1L
    }

    private var dialog: AlertDialog? = null
    private val radioGroup: RadioGroup
    private var wasInit = false
    private var calendars = ArrayList<CalendarEntity>()

    private val binding by activity.viewBinding(DialogSelectCalendarBinding::inflate)

    init {
        radioGroup = binding.dialogRadioGroup
        binding.dialogManageCalendars.apply {
            beVisibleIf(showManageCalendars)
            setOnClickListener {
                activity.startActivity(Intent(activity, ManageCalendarsActivity::class.java))
                dialog?.dismiss()
            }
        }

        binding.dialogRadioDivider.beVisibleIf(showManageCalendars)

        activity.eventsHelper.getCalendars(activity, showOnlyWritable) { calendars ->
            this.calendars = calendars
            activity.runOnUiThread {
                if (addLastUsedOneAsFirstOption) {
                    val lastUsedCalendar = CalendarEntity(
                        LAST_USED_CALENDAR_ID,
                        activity.getString(R.string.last_used_one),
                        Color.TRANSPARENT,
                        0
                    )
                    addRadioButton(lastUsedCalendar)
                }
                this.calendars.filter { showCalDAVCalendars || it.caldavCalendarId == 0 }.forEach {
                    addRadioButton(it)
                }
                if (showNewCalendarOption) {
                    val newCalendar = CalendarEntity(
                        id = NEW_CALENDAR_ID,
                        title = activity.getString(R.string.add_new_type),
                        color = Color.TRANSPARENT,
                        caldavCalendarId = 0
                    )
                    addRadioButton(newCalendar)
                }
                wasInit = true
                activity.updateTextColors(binding.dialogRadioHolder)
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun addRadioButton(calendar: CalendarEntity) {
        val radioBinding = RadioButtonWithColorBinding.inflate(activity.layoutInflater)
        (radioBinding.dialogRadioButton).apply {
            text = calendar.getDisplayTitle()
            isChecked = calendar.id == currCalendar
            id = calendar.id!!.toInt()
        }

        if (calendar.color != Color.TRANSPARENT) {
            radioBinding.dialogRadioColor.setFillWithStroke(
                fillColor = calendar.color,
                backgroundColor = activity.getProperBackgroundColor()
            )
        }

        radioBinding.root.setOnClickListener { viewClicked(calendar) }
        radioGroup.addView(
            radioBinding.root,
            RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun viewClicked(calendar: CalendarEntity) {
        if (!wasInit) {
            return
        }

        if (calendar.id == NEW_CALENDAR_ID) {
            EditCalendarDialog(activity) {
                callback(it)
                activity.hideKeyboard()
                dialog?.dismiss()
            }
        } else {
            callback(calendar)
            dialog?.dismiss()
        }
    }
}
