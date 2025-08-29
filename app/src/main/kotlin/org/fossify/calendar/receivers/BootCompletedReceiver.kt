package org.fossify.calendar.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.calendar.extensions.checkAndBackupEventsOnBoot
import org.fossify.calendar.extensions.notifyRunningEvents
import org.fossify.calendar.extensions.recheckCalDAVCalendars
import org.fossify.calendar.extensions.scheduleAllEvents
import org.fossify.calendar.extensions.scheduleNextAutomaticBackup
import org.fossify.commons.helpers.ensureBackgroundThread

class BootCompletedReceiver : BroadcastReceiver() {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync() // TO DO: switch to WorkManager for more resilence
        ensureBackgroundThread {
            context.apply {
                scheduleAllEvents()
                notifyRunningEvents()
                recheckCalDAVCalendars(true) {}
                scheduleNextAutomaticBackup()
                checkAndBackupEventsOnBoot()
                pendingResult.finish()
            }
        }
    }
}
