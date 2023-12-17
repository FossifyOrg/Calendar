package org.fossify.calendar.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.databinding.DialogDeleteEventBinding
import org.fossify.calendar.helpers.DELETE_ALL_OCCURRENCES
import org.fossify.calendar.helpers.DELETE_FUTURE_OCCURRENCES
import org.fossify.calendar.helpers.DELETE_SELECTED_OCCURRENCE
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding

class DeleteEventDialog(
    val activity: Activity,
    eventIds: List<Long>,
    hasRepeatableEvent: Boolean,
    isTask: Boolean = false,
    val callback: (deleteRule: Int) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogDeleteEventBinding::inflate)

    init {
        binding.apply {
            deleteEventRepeatDescription.beVisibleIf(hasRepeatableEvent)
            deleteEventRadioView.beVisibleIf(hasRepeatableEvent)
            if (!hasRepeatableEvent) {
                deleteEventRadioView.check(R.id.delete_event_all)
            }

            if (eventIds.size > 1) {
                deleteEventRepeatDescription.setText(R.string.selection_contains_repetition)
            }

            if (isTask) {
                deleteEventRepeatDescription.setText(R.string.task_is_repeatable)
            } else {
                deleteEventRepeatDescription.setText(R.string.event_is_repeatable)
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.yes) { _, _ -> dialogConfirmed(binding) }
            .setNegativeButton(org.fossify.commons.R.string.no, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun dialogConfirmed(binding: DialogDeleteEventBinding) {
        val deleteRule = when (binding.deleteEventRadioView.checkedRadioButtonId) {
            R.id.delete_event_all -> DELETE_ALL_OCCURRENCES
            R.id.delete_event_future -> DELETE_FUTURE_OCCURRENCES
            else -> DELETE_SELECTED_OCCURRENCE
        }
        dialog?.dismiss()
        callback(deleteRule)
    }
}
