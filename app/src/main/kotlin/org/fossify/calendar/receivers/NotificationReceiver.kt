package org.fossify.calendar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import org.fossify.calendar.extensions.eventsDB
import org.fossify.calendar.extensions.notifyEventSync
import org.fossify.calendar.extensions.scheduleNextEventReminderSync
import org.fossify.calendar.extensions.updateListWidget
import org.fossify.calendar.helpers.EVENT_ID
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.helpers.REMINDER_NOTIFICATION
import org.fossify.commons.helpers.ensureBackgroundThread

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Keep the broadcast alive until all work is done. Without goAsync(), the system considers
        // the receiver finished as soon as onReceive() returns and may freeze or kill the process
        // before the next reminder is scheduled, so recurring events stop notifying.
        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "simplecalendar:notificationreceiver"
        )
        wakelock.acquire(WAKELOCK_TIMEOUT_MS)

        ensureBackgroundThread {
            try {
                handleIntent(context, intent)
            } finally {
                if (wakelock.isHeld) {
                    wakelock.release()
                }
                pendingResult.finish()
            }
        }
    }

    private fun handleIntent(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EVENT_ID, -1L)
        if (id == -1L) {
            return
        }

        context.updateListWidget()
        val event = context.eventsDB.getEventOrTaskWithId(id)
        if (
            event == null
            || event.getReminders().none { it.type == REMINDER_NOTIFICATION }
            || event.isAttendeeInviteDeclined()
        ) {
            return
        }

        // Schedule the next occurrence first, so it is set even if showing this one fails.
        context.scheduleNextEventReminderSync(event, false)

        if (
            !event.repetitionExceptions.contains(Formatter.getTodayCode())
            && !event.repetitionExceptions.contains(Formatter.getDayCodeFromTS(event.startTS))
        ) {
            context.notifyEventSync(event)
        }
    }

    companion object {
        private const val WAKELOCK_TIMEOUT_MS = 10_000L
    }
}
