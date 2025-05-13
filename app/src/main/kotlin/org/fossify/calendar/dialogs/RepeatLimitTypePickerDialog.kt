package org.fossify.calendar.dialogs

import android.app.Activity
import android.app.DatePickerDialog
import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.databinding.DialogRepeatLimitTypePickerBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.getNowSeconds
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.getJavaDayOfWeekFromISO
import org.joda.time.DateTime

class RepeatLimitTypePickerDialog(val activity: Activity, var repeatLimit: Long, val startTS: Long, val callback: (repeatLimit: Long) -> Unit) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogRepeatLimitTypePickerBinding::inflate)

    init {
        binding.apply {
            repeatTypeDate.setOnClickListener { showRepetitionLimitDialog() }
            repeatTypeCount.setOnClickListener { dialogRadioView.check(R.id.repeat_type_x_times) }
            repeatTypeForever.setOnClickListener {
                callback(0)
                dialog?.dismiss()
            }
        }

        binding.dialogRadioView.check(getCheckedItem())

        if (repeatLimit in 1..startTS) {
            repeatLimit = startTS
        }

        updateRepeatLimitText()

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> confirmRepetition() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                    activity.currentFocus?.clearFocus()

                    binding.repeatTypeCount.onTextChangeListener {
                        binding.dialogRadioView.check(R.id.repeat_type_x_times)
                    }
                }
            }
    }

    private fun getCheckedItem() = when {
        repeatLimit > 0 -> R.id.repeat_type_till_date
        repeatLimit < 0 -> {
            binding.repeatTypeCount.setText((-repeatLimit).toString())
            R.id.repeat_type_x_times
        }

        else -> R.id.repeat_type_forever
    }

    private fun updateRepeatLimitText() {
        if (repeatLimit <= 0) {
            repeatLimit = getNowSeconds()
        }

        val repeatLimitDateTime = Formatter.getDateTimeFromTS(repeatLimit)
        binding.repeatTypeDate.setText(Formatter.getFullDate(activity, repeatLimitDateTime))
    }

    private fun confirmRepetition() {
        when (binding.dialogRadioView.checkedRadioButtonId) {
            R.id.repeat_type_till_date -> callback(repeatLimit)
            R.id.repeat_type_forever -> callback(0)
            else -> {
                var count = binding.repeatTypeCount.value
                count = if (count.isEmpty()) {
                    "0"
                } else {
                    "-$count"
                }
                callback(count.toLong())
            }
        }
        dialog?.dismiss()
    }

    private fun showRepetitionLimitDialog() {
        val repeatLimitDateTime = Formatter.getDateTimeFromTS(if (repeatLimit != 0L) repeatLimit else getNowSeconds())
        val datePicker = DatePickerDialog(
            activity, activity.getDatePickerDialogTheme(), repetitionLimitDateSetListener, repeatLimitDateTime.year,
            repeatLimitDateTime.monthOfYear - 1, repeatLimitDateTime.dayOfMonth
        )

        datePicker.datePicker.firstDayOfWeek = getJavaDayOfWeekFromISO(activity.config.firstDayOfWeek)
        datePicker.show()
    }

    private val repetitionLimitDateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
        val repeatLimitDateTime = DateTime().withDate(year, monthOfYear + 1, dayOfMonth).withTime(23, 59, 59, 0)
        repeatLimit = if (repeatLimitDateTime.seconds() < startTS) {
            0
        } else {
            repeatLimitDateTime.seconds()
        }

        updateRepeatLimitText()
        binding.dialogRadioView.check(R.id.repeat_type_till_date)
    }
}
