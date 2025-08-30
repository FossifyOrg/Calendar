package org.fossify.calendar.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.calendar.extensions.notifyRunningEvents
import org.fossify.calendar.jobs.AppStartupWorker
import org.fossify.commons.helpers.ensureBackgroundThread

class RescheduleEventsReceiver : BroadcastReceiver() {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        AppStartupWorker.start(
            context = context,
            replaceExistingWork = action == Intent.ACTION_TIME_CHANGED
                    || action == Intent.ACTION_TIMEZONE_CHANGED
        )

        val shouldNotifyRunningEvents = action != Intent.ACTION_TIME_CHANGED
                && action != Intent.ACTION_TIMEZONE_CHANGED
                && action != Intent.ACTION_MY_PACKAGE_REPLACED

        if (shouldNotifyRunningEvents) {
            val result = goAsync()
            ensureBackgroundThread {
                context.notifyRunningEvents()
                result.finish()
            }
        }
    }
}

