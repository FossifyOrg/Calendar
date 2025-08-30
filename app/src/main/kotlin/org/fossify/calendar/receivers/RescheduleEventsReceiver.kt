package org.fossify.calendar.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.calendar.jobs.AppStartupWorker

class RescheduleEventsReceiver : BroadcastReceiver() {

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        AppStartupWorker.start(
            context = context,
            replaceExistingWork = intent.action == Intent.ACTION_TIME_CHANGED
                    || intent.action == Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}

