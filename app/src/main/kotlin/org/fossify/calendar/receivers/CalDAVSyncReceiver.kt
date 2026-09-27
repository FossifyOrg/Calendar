package org.fossify.calendar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.recheckCalDAVCalendars
import org.fossify.calendar.extensions.refreshCalDAVCalendars
import org.fossify.calendar.extensions.tasksRepository
import org.fossify.calendar.extensions.updateWidgets
import org.fossify.commons.helpers.ensureBackgroundThread

class CalDAVSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.config.caldavSync) {
            context.refreshCalDAVCalendars(context.config.caldavSyncedCalendarIds, false)
            ensureBackgroundThread {
                context.tasksRepository.requestSync(manual = false)
            }
        }

        context.recheckCalDAVCalendars(true) {
            context.updateWidgets()
        }
    }
}
