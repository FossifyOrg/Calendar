package org.fossify.calendar.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.adapters.FilterCalendarAdapter
import org.fossify.calendar.databinding.DialogFilterCalendarsBinding
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding

class SelectCalendarsDialog(
    val activity: SimpleActivity,
    selectedCalendars: Set<String>,
    val callback: (HashSet<String>) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogFilterCalendarsBinding::inflate)

    init {
        activity.eventsHelper.getCalendars(activity, false) {
            binding.filterCalendarsList.adapter =
                FilterCalendarAdapter(activity, it, selectedCalendars)

            activity.getAlertDialogBuilder()
                .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> confirmCalendars() }
                .setNegativeButton(org.fossify.commons.R.string.cancel, null)
                .apply {
                    activity.setupDialogStuff(binding.root, this) { alertDialog ->
                        dialog = alertDialog
                    }
                }
        }
    }

    private fun confirmCalendars() {
        val adapter = binding.filterCalendarsList.adapter as FilterCalendarAdapter
        val selectedItems = adapter.getSelectedItemsList()
            .map { it.toString() }
            .toHashSet()
        callback(selectedItems)
        dialog?.dismiss()
    }
}
