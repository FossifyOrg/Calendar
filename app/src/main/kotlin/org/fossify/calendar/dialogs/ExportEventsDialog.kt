package org.fossify.calendar.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.adapters.FilterCalendarAdapter
import org.fossify.calendar.databinding.DialogExportEventsBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getCurrentFormattedDateTime
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isAValidFilename
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import java.io.File

class ExportEventsDialog(
    val activity: SimpleActivity,
    val path: String,
    val hidePath: Boolean,
    val callback: (file: File, calendars: ArrayList<Long>) -> Unit
) {
    private var realPath = path.ifEmpty { activity.internalStoragePath }
    private val config = activity.config
    private val binding by activity.viewBinding(DialogExportEventsBinding::inflate)

    init {
        binding.apply {
            exportEventsFolder.setText(activity.humanizePath(realPath))
            exportEventsFilename.setText("${activity.getString(R.string.events)}_${activity.getCurrentFormattedDateTime()}")

            exportEventsCheckbox.isChecked = config.exportEvents
            exportEventsCheckboxHolder.setOnClickListener {
                exportEventsCheckbox.toggle()
            }
            exportTasksCheckbox.isChecked = config.exportTasks
            exportTasksCheckboxHolder.setOnClickListener {
                exportTasksCheckbox.toggle()
            }
            exportPastEventsCheckbox.isChecked = config.exportPastEntries
            exportPastEventsCheckboxHolder.setOnClickListener {
                exportPastEventsCheckbox.toggle()
            }

            if (hidePath) {
                exportEventsFolderHint.beGone()
                exportEventsFolder.beGone()
            } else {
                exportEventsFolder.setOnClickListener {
                    activity.hideKeyboard(exportEventsFilename)
                    FilePickerDialog(activity, realPath, false, showFAB = true) {
                        exportEventsFolder.setText(activity.humanizePath(it))
                        realPath = it
                    }
                }
            }

            activity.eventsHelper.getCalendars(activity, false) {
                val calendars = HashSet<String>()
                it.mapTo(calendars) { it.id.toString() }

                exportEventsTypesList.adapter = FilterCalendarAdapter(activity, it, calendars)
                if (it.size > 1) {
                    exportEventsPickTypes.beVisible()
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
                    titleId = R.string.export_events
                ) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = binding.exportEventsFilename.value
                        when {
                            filename.isEmpty() -> activity.toast(org.fossify.commons.R.string.empty_name)
                            filename.isAValidFilename() -> {
                                val file = File(realPath, "$filename.ics")
                                if (!hidePath && file.exists()) {
                                    activity.toast(org.fossify.commons.R.string.name_taken)
                                    return@setOnClickListener
                                }

                                val exportEventsChecked = binding.exportEventsCheckbox.isChecked
                                val exportTasksChecked = binding.exportTasksCheckbox.isChecked
                                if (!exportEventsChecked && !exportTasksChecked) {
                                    activity.toast(org.fossify.commons.R.string.no_entries_for_exporting)
                                    return@setOnClickListener
                                }

                                ensureBackgroundThread {
                                    config.apply {
                                        lastExportPath = file.absolutePath.getParentPath()
                                        exportEvents = exportEventsChecked
                                        exportTasks = exportTasksChecked
                                        exportPastEntries =
                                            binding.exportPastEventsCheckbox.isChecked
                                    }

                                    val calendars =
                                        (binding.exportEventsTypesList.adapter as FilterCalendarAdapter).getSelectedItemsList()
                                    callback(file, calendars)
                                    alertDialog.dismiss()
                                }
                            }

                            else -> activity.toast(org.fossify.commons.R.string.invalid_name)
                        }
                    }
                }
            }
    }
}
