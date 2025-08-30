package org.fossify.calendar.jobs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.calendar.R
import org.fossify.calendar.extensions.checkAndBackupEventsOnBoot
import org.fossify.calendar.extensions.recheckCalDAVCalendars
import org.fossify.calendar.extensions.scheduleAllEvents
import org.fossify.calendar.extensions.scheduleDummyAlarm
import org.fossify.calendar.extensions.scheduleNextAutomaticBackup
import org.fossify.commons.extensions.notificationManager

/**
 * Does everything that needs to be done on device boot, app updates, etc.
 */
class AppStartupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = with(applicationContext) {
        try {
            scheduleAllEvents()
            scheduleDummyAlarm()
            scheduleNextAutomaticBackup()
            checkAndBackupEventsOnBoot()
        } catch (_: Throwable) {
            return Result.retry()
        }

        recheckCalDAVCalendars(true) {}
        Result.success()
    }

    // expedited work on before Android 12 requires a foreground service
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelId = "app_startup_worker"
        val channelName = applicationContext.getString(R.string.app_name)
        NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN).apply {
            setSound(null, null)
            applicationContext.notificationManager.createNotificationChannel(this)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_calendar_vector)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setOngoing(true)
            .build()
        return ForegroundInfo(42, notification)
    }

    companion object {
        fun start(
            context: Context,
            replaceExistingWork: Boolean = false
        ) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "initialize_fossify_calendar",
                    if (replaceExistingWork) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<AppStartupWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                )
        }
    }
}
