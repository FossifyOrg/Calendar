package org.fossify.calendar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.calendar.extensions.scheduleDummyAlarm

class DummyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.scheduleDummyAlarm()
    }
}
