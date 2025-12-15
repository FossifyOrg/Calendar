package org.fossify.calendar.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.DialogImportEventsBinding
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.helpers.IcsImporter
import org.fossify.calendar.helpers.IcsImporter.ImportResult.IMPORT_FAIL
import org.fossify.calendar.helpers.IcsImporter.ImportResult.IMPORT_NOTHING_NEW
import org.fossify.calendar.helpers.IcsImporter.ImportResult.IMPORT_OK
import org.fossify.calendar.helpers.IcsImporter.ImportResult.IMPORT_PARTIAL
import org.fossify.calendar.helpers.LOCAL_CALENDAR_ID
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.setFillWithStroke
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread

class ImportEventsDialog(
    val activity: SimpleActivity,
    val path: String,
    val callback: (refreshView: Boolean) -> Unit
) {
    private var currCalendarId = LOCAL_CALENDAR_ID
    private var currCalendarCalDAVCalendarId = 0
    private val config = activity.config
    private val binding by activity.viewBinding(DialogImportEventsBinding::inflate)

    init {
        ensureBackgroundThread {
            if (activity.calendarsDB.getCalendarWithId(config.lastUsedLocalCalendarId) == null) {
                config.lastUsedLocalCalendarId = LOCAL_CALENDAR_ID
            }

            val isLastCaldavCalendarOK = config.caldavSync && config.getSyncedCalendarIdsAsList()
                .contains(config.lastUsedCaldavCalendarId)
            currCalendarId = if (isLastCaldavCalendarOK) {
                val lastUsedCalDAVCalendar =
                    activity.eventsHelper.getCalendarWithCalDAVCalendarId(config.lastUsedCaldavCalendarId)
                if (lastUsedCalDAVCalendar != null) {
                    currCalendarCalDAVCalendarId = config.lastUsedCaldavCalendarId
                    lastUsedCalDAVCalendar.id!!
                } else {
                    LOCAL_CALENDAR_ID
                }
            } else {
                config.lastUsedLocalCalendarId
            }
            binding.importEventsCheckbox.isChecked = config.lastUsedIgnoreCalendarsState

            activity.runOnUiThread {
                initDialog()
            }
        }
    }

    private fun initDialog() {
        binding.apply {
            updateCalendar(this)
            importCalendarTitle.setOnClickListener {
                SelectCalendarDialog(
                    activity = activity,
                    currCalendar = currCalendarId,
                    showCalDAVCalendars = true,
                    showNewCalendarOption = true,
                    addLastUsedOneAsFirstOption = false,
                    showOnlyWritable = true,
                    showManageCalendars = false
                ) {
                    currCalendarId = it.id!!
                    currCalendarCalDAVCalendarId = it.caldavCalendarId

                    config.lastUsedLocalCalendarId = it.id!!
                    config.lastUsedCaldavCalendarId = it.caldavCalendarId

                    updateCalendar(this)
                }
            }

            importEventsCheckboxHolder.setOnClickListener {
                importEventsCheckbox.toggle()
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(
                    view = binding.root,
                    dialog = this,
                    titleId = R.string.import_events
                ) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(null)
                        activity.toast(org.fossify.commons.R.string.importing)
                        ensureBackgroundThread {
                            val overrideFileCalendars = binding.importEventsCheckbox.isChecked
                            config.lastUsedIgnoreCalendarsState = overrideFileCalendars
                            val result = IcsImporter(activity).importEvents(
                                path = path,
                                defaultCalendarId = currCalendarId,
                                calDAVCalendarId = currCalendarCalDAVCalendarId,
                                overrideFileCalendars = overrideFileCalendars
                            )
                            handleParseResult(result)
                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }

    private fun updateCalendar(binding: DialogImportEventsBinding) {
        ensureBackgroundThread {
            val calendar = activity.calendarsDB.getCalendarWithId(currCalendarId)
            activity.runOnUiThread {
                binding.importCalendarTitle.setText(calendar!!.getDisplayTitle())
                binding.importCalendarColor.setFillWithStroke(
                    calendar.color,
                    activity.getProperBackgroundColor()
                )
            }
        }
    }

    private fun handleParseResult(result: IcsImporter.ImportResult) {
        activity.toast(
            when (result) {
                IMPORT_NOTHING_NEW -> org.fossify.commons.R.string.no_new_items
                IMPORT_OK -> org.fossify.commons.R.string.importing_successful
                IMPORT_PARTIAL -> org.fossify.commons.R.string.importing_some_entries_failed
                else -> org.fossify.commons.R.string.no_items_found
            }
        )
        callback(result != IMPORT_FAIL)
    }
}
