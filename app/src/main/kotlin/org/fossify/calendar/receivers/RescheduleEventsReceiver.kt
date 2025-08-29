package org.fossify.calendar.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.calendar.extensions.initializeFossifyCalendar
import org.fossify.commons.helpers.ensureBackgroundThread

class RescheduleEventsReceiver : BroadcastReceiver() {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync() // TO DO: switch to WorkManager for more resilence
        ensureBackgroundThread {
            context.apply {
                initializeFossifyCalendar()
                pendingResult.finish()
            }
        }
    }
}
