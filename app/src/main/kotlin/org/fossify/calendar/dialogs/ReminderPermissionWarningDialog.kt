package org.fossify.calendar.dialogs

import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.openNotificationSettings

class ReminderPermissionWarningDialog(activity: SimpleActivity, onSave: () -> Unit) {
    init {
        activity.getAlertDialogBuilder()
            .setTitle(R.string.reminder_permission_warning_title)
            .setMessage(R.string.save_without_notification_permission_warning)
            .setPositiveButton(org.fossify.commons.R.string.grant_permission) { _, _ ->
                activity.openNotificationSettings()
            }
            .setNeutralButton(R.string.save_anyway) { _, _ -> onSave() }
            .setCancelable(true)
            .show()
            .setCanceledOnTouchOutside(true)
    }
}
