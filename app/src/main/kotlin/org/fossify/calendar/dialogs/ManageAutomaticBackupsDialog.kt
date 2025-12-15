package org.fossify.calendar.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.DialogManageAutomaticBackupsBinding
import org.fossify.calendar.extensions.config
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.isAValidFilename
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ensureBackgroundThread
import java.io.File

class ManageAutomaticBackupsDialog(private val activity: SimpleActivity, onSuccess: () -> Unit) {
    private val binding by activity.viewBinding(DialogManageAutomaticBackupsBinding::inflate)
    private val config = activity.config
    private var backupFolder = config.autoBackupFolder
    private var selectedCalendars = config.autoBackupCalendars.ifEmpty { config.displayCalendars }

    init {
        binding.apply {
            backupEventsFolder.setText(activity.humanizePath(backupFolder))
            val filename = config.autoBackupFilename.ifEmpty {
                "${activity.getString(R.string.events)}_%Y%M%D_%h%m%s"
            }

            backupEventsFilename.setText(filename)
            backupEventsFilenameHint.setEndIconOnClickListener {
                DateTimePatternInfoDialog(activity)
            }

            backupEventsFilenameHint.setEndIconOnLongClickListener {
                DateTimePatternInfoDialog(activity)
                true
            }

            backupEventsCheckbox.isChecked = config.autoBackupEvents
            backupEventsCheckboxHolder.setOnClickListener {
                backupEventsCheckbox.toggle()
            }

            backupTasksCheckbox.isChecked = config.autoBackupTasks
            backupTasksCheckboxHolder.setOnClickListener {
                backupTasksCheckbox.toggle()
            }

            backupPastEventsCheckbox.isChecked = config.autoBackupPastEntries
            backupPastEventsCheckboxHolder.setOnClickListener {
                backupPastEventsCheckbox.toggle()
            }

            backupEventsFolder.setOnClickListener {
                selectBackupFolder()
            }

            manageCalendarsHolder.setOnClickListener {
                SelectCalendarsDialog(activity, selectedCalendars) {
                    selectedCalendars = it
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
                    titleId = org.fossify.commons.R.string.manage_automatic_backups
                ) { dialog ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = binding.backupEventsFilename.value
                        when {
                            filename.isEmpty() -> activity.toast(org.fossify.commons.R.string.empty_name)
                            filename.isAValidFilename() -> {
                                val file = File(backupFolder, "$filename.ics")
                                if (file.exists() && !file.canWrite()) {
                                    activity.toast(org.fossify.commons.R.string.name_taken)
                                    return@setOnClickListener
                                }

                                val backupEventsChecked = binding.backupEventsCheckbox.isChecked
                                val backupTasksChecked = binding.backupTasksCheckbox.isChecked
                                if (!backupEventsChecked && !backupTasksChecked || selectedCalendars.isEmpty()) {
                                    activity.toast(org.fossify.commons.R.string.no_entries_for_exporting)
                                    return@setOnClickListener
                                }

                                ensureBackgroundThread {
                                    config.apply {
                                        autoBackupFolder = backupFolder
                                        autoBackupFilename = filename
                                        autoBackupEvents = backupEventsChecked
                                        autoBackupTasks = backupTasksChecked
                                        autoBackupPastEntries =
                                            binding.backupPastEventsCheckbox.isChecked
                                        if (autoBackupCalendars != selectedCalendars) {
                                            autoBackupCalendars = selectedCalendars
                                        }
                                    }

                                    activity.runOnUiThread {
                                        onSuccess()
                                    }

                                    dialog.dismiss()
                                }
                            }

                            else -> activity.toast(org.fossify.commons.R.string.invalid_name)
                        }
                    }
                }
            }
    }

    private fun selectBackupFolder() {
        activity.hideKeyboard(binding.backupEventsFilename)
        FilePickerDialog(activity, backupFolder, pickFile = false, showFAB = true) { path ->
            activity.handleSAFDialog(path) { grantedSAF ->
                if (!grantedSAF) {
                    return@handleSAFDialog
                }

                activity.handleSAFDialogSdk30(path) { grantedSAF30 ->
                    if (!grantedSAF30) {
                        return@handleSAFDialogSdk30
                    }

                    backupFolder = path
                    binding.backupEventsFolder.setText(activity.humanizePath(path))
                }
            }
        }
    }
}

