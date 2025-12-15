package org.fossify.calendar.dialogs

import android.app.Activity
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.databinding.DialogCalendarBinding
import org.fossify.calendar.extensions.calDAVHelper
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.helpers.OTHER_EVENT
import org.fossify.calendar.models.CalendarEntity
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.setFillWithStroke
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread

class EditCalendarDialog(
    val activity: Activity,
    var calendar: CalendarEntity? = null,
    val callback: (calendar: CalendarEntity) -> Unit
) {
    private var isNewEvent = calendar == null
    private val binding by activity.viewBinding(DialogCalendarBinding::inflate)

    init {
        if (calendar == null) {
            calendar = CalendarEntity(null, "", activity.getProperPrimaryColor())
        }

        binding.apply {
            setupColor(typeColor)
            typeTitle.setText(calendar!!.title)
            typeColor.setOnClickListener {
                if (calendar?.caldavCalendarId == 0) {
                    ColorPickerDialog(
                        activity = activity,
                        color = calendar!!.color
                    ) { wasPositivePressed, color ->
                        if (wasPositivePressed) {
                            calendar!!.color = color
                            setupColor(typeColor)
                        }
                    }
                } else {
                    val currentColor = calendar!!.color
                    val colors =
                        activity.calDAVHelper.getAvailableCalDAVCalendarColors(calendar!!).keys.toIntArray()
                    SelectCalendarColorDialog(
                        activity = activity,
                        colors = colors,
                        currentColor = currentColor
                    ) {
                        calendar!!.color = it
                        setupColor(typeColor)
                    }
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(
                    view = binding.root,
                    dialog = this,
                    titleId = if (isNewEvent) R.string.add_new_type else R.string.edit_type
                ) { alertDialog ->
                    alertDialog.showKeyboard(binding.typeTitle)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        ensureBackgroundThread {
                            calendarConfirmed(binding.typeTitle.value, alertDialog)
                        }
                    }
                }
            }
    }

    private fun setupColor(view: ImageView) {
        view.setFillWithStroke(calendar!!.color, activity.getProperBackgroundColor())
    }

    private fun calendarConfirmed(title: String, dialog: AlertDialog) {
        val calendarClass = calendar?.type ?: OTHER_EVENT
        val calendarId = if (calendarClass == OTHER_EVENT) {
            activity.eventsHelper.getCalendarIdWithTitle(title)
        } else {
            activity.eventsHelper.getCalendarIdWithClass(calendarClass)
        }

        var isCalendarTitleTaken = isNewEvent && calendarId != -1L
        if (!isCalendarTitleTaken) {
            isCalendarTitleTaken = !isNewEvent && calendar!!.id != calendarId && calendarId != -1L
        }

        if (title.isEmpty()) {
            activity.toast(R.string.title_empty)
            return
        } else if (isCalendarTitleTaken) {
            activity.toast(R.string.type_already_exists)
            return
        }

        calendar!!.title = title
        if (calendar!!.caldavCalendarId != 0) {
            calendar!!.caldavDisplayName = title
        }

        calendar!!.id = activity.eventsHelper.insertOrUpdateCalendarSync(calendar!!)

        if (calendar!!.id != -1L) {
            activity.runOnUiThread {
                dialog.dismiss()
                callback(calendar!!)
            }
        } else {
            activity.toast(R.string.editing_calendar_failed)
        }
    }
}
