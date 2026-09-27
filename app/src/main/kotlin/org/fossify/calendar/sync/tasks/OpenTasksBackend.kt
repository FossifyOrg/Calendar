package org.fossify.calendar.sync.tasks

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import org.fossify.calendar.models.CalendarEntity
import org.fossify.calendar.models.Event
import org.fossify.calendar.sync.tasks.OpenTasksContract.Alarm
import org.fossify.calendar.sync.tasks.OpenTasksContract.Properties
import org.fossify.calendar.sync.tasks.OpenTasksContract.TaskLists
import org.fossify.calendar.sync.tasks.OpenTasksContract.Tasks
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getIntValueOrNull
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getLongValueOrNull
import org.fossify.commons.extensions.getStringValue

/**
 * Reads and writes tasks of a provider implementing the OpenTasks contract, like OpenTasks or Tasks.org.
 * DAVx5 syncs these with CalDAV servers the same way it syncs events with the Android calendar provider.
 */
@Suppress("TooManyFunctions")
class OpenTasksBackend(
    private val context: Context,
    private val provider: OpenTasksContract.Provider,
) : TaskSyncBackend {
    companion object {
        private const val TAG = "OpenTasksBackend"

        private val TASK_PROJECTION = arrayOf(
            Tasks.ID,
            Tasks.TITLE,
            Tasks.DESCRIPTION,
            Tasks.LOCATION,
            Tasks.DTSTART,
            Tasks.DUE,
            Tasks.IS_ALLDAY,
            Tasks.TZ,
            Tasks.RRULE,
            Tasks.EXDATE,
            Tasks.STATUS,
            Tasks.TASK_COLOR,
            Tasks.ORIGINAL_INSTANCE_ID,
            Tasks.ORIGINAL_INSTANCE_TIME,
            Tasks.ORIGINAL_INSTANCE_ALLDAY
        )
    }

    override val providerId = provider.authority

    override val displayName = provider.appName

    private val listsUri = OpenTasksContract.getUri(provider.authority, TaskLists.PATH)
    private val tasksUri = OpenTasksContract.getUri(provider.authority, Tasks.PATH)

    override val changesUri = tasksUri
    private val propertiesUri = OpenTasksContract.getUri(provider.authority, Properties.PATH)

    override fun isAvailable() =
        context.packageManager.resolveContentProvider(provider.authority, 0) != null

    override fun getMissingPermissions() =
        listOf(provider.readPermission, provider.writePermission).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    override fun getRemoteLists(): List<RemoteTaskList> {
        val lists = ArrayList<RemoteTaskList>()
        val projection = arrayOf(
            TaskLists.ID,
            TaskLists.LIST_NAME,
            TaskLists.LIST_COLOR,
            TaskLists.ACCOUNT_NAME,
            TaskLists.ACCOUNT_TYPE
        )

        queryRows(listsUri, projection) { cursor ->
            lists.add(
                RemoteTaskList(
                    providerId = providerId,
                    id = cursor.getLongValue(TaskLists.ID),
                    displayName = cursor.getStringValue(TaskLists.LIST_NAME) ?: "",
                    accountName = cursor.getStringValue(TaskLists.ACCOUNT_NAME) ?: "",
                    accountType = cursor.getStringValue(TaskLists.ACCOUNT_TYPE) ?: "",
                    color = cursor.getIntValue(TaskLists.LIST_COLOR)
                )
            )
        }

        return lists
    }

    override fun getRemoteTasks(list: CalendarEntity): List<RemoteTask> {
        val rows = ArrayList<OpenTasksMapper.TaskRow>()
        // completed occurrences of repeating tasks are stored as overrides of the original task
        val completedOverrides = HashMap<Long, HashSet<Long>>()
        val selection = "${Tasks.LIST_ID} = ? AND ${Tasks.DELETED} = 0"
        val selectionArgs = arrayOf(list.taskListId.toString())

        queryRows(tasksUri, TASK_PROJECTION, selection, selectionArgs) { cursor ->
            val originalId = cursor.getLongValueOrNull(Tasks.ORIGINAL_INSTANCE_ID)
            if (originalId == null) {
                rows.add(readTaskRow(cursor))
            } else {
                readCompletedOccurrence(cursor)?.let {
                    completedOverrides.getOrPut(originalId) { HashSet() }.add(it)
                }
            }
        }

        val alarms = getAlarms()
        return rows.mapNotNull { row ->
            val task = OpenTasksMapper.toTask(row, alarms[row.id].orEmpty()) ?: return@mapNotNull null
            val completed = HashSet(completedOverrides[row.id].orEmpty())
            if (task.repeatInterval == 0 && OpenTasksMapper.isCompleted(row.status)) {
                completed.add(task.startTS)
            }
            RemoteTask(row.id, task, completed)
        }
    }

    override fun insertTask(list: CalendarEntity, task: Event): Long {
        val values = getTaskContentValues(task, existingDtStart = null).apply {
            put(Tasks.LIST_ID, list.taskListId)
        }

        val uri = checkNotNull(context.contentResolver.insert(tasksUri, values)) {
            "Inserting a task into ${provider.authority} failed"
        }
        val remoteId = ContentUris.parseId(uri)
        writeAlarms(remoteId, task)
        return remoteId
    }

    override fun updateTask(list: CalendarEntity, remoteId: Long, task: Event) {
        updateTaskRow(remoteId, getTaskContentValues(task, getDtStart(remoteId)))
        writeAlarms(remoteId, task)
    }

    override fun deleteTask(list: CalendarEntity, remoteId: Long) {
        context.contentResolver.delete(ContentUris.withAppendedId(tasksUri, remoteId), null, null)
    }

    override fun setTaskCompleted(
        list: CalendarEntity,
        remoteId: Long,
        task: Event,
        occurrenceTS: Long?,
        completed: Boolean
    ) {
        val statusValues = ContentValues().apply {
            if (completed) {
                put(Tasks.STATUS, Tasks.STATUS_COMPLETED)
                put(Tasks.COMPLETED, System.currentTimeMillis())
            } else {
                put(Tasks.STATUS, Tasks.STATUS_NEEDS_ACTION)
                putNull(Tasks.COMPLETED)
            }
        }

        if (occurrenceTS == null) {
            updateTaskRow(remoteId, statusValues)
            return
        }

        val isAllDay = task.getIsAllDay()
        val originalTime = OpenTasksMapper.toProviderTime(occurrenceTS, isAllDay)
        val overrideId = getOverrideId(remoteId, originalTime)
        if (overrideId != null) {
            updateTaskRow(overrideId, statusValues)
        } else if (completed) {
            val values = ContentValues(statusValues).apply {
                put(Tasks.LIST_ID, list.taskListId)
                put(Tasks.ORIGINAL_INSTANCE_ID, remoteId)
                put(Tasks.ORIGINAL_INSTANCE_TIME, originalTime)
                put(Tasks.ORIGINAL_INSTANCE_ALLDAY, if (isAllDay) 1 else 0)
                put(Tasks.TITLE, task.title)
                put(Tasks.DESCRIPTION, task.description)
                put(Tasks.LOCATION, task.location)
                put(Tasks.IS_ALLDAY, if (isAllDay) 1 else 0)
                put(Tasks.TZ, if (isAllDay) null else task.getTimeZoneString())
                put(Tasks.DTSTART, originalTime)
                put(Tasks.DUE, originalTime)
            }
            context.contentResolver.insert(tasksUri, values)
        }
    }

    override fun requestSync(listIds: Set<Long>, manual: Boolean) {
        val accounts = getRemoteLists()
            .filter { it.id in listIds && it.accountType != OpenTasksContract.ACCOUNT_TYPE_LOCAL }
            .map { Account(it.accountName, it.accountType) }
            .toSet()

        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            if (manual) {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            }
        }

        accounts.forEach {
            ContentResolver.requestSync(it, provider.authority, extras)
        }
    }

    private fun getTaskContentValues(task: Event, existingDtStart: DtStart?): ContentValues {
        val taskValues = OpenTasksMapper.toValues(task)
        return ContentValues().apply {
            put(Tasks.TITLE, taskValues.title)
            put(Tasks.DESCRIPTION, taskValues.description)
            put(Tasks.LOCATION, taskValues.location)
            put(Tasks.IS_ALLDAY, if (taskValues.isAllDay) 1 else 0)
            put(Tasks.TZ, taskValues.timeZone)
            put(Tasks.DUE, taskValues.due)
            putNull(Tasks.DURATION)
            put(Tasks.RRULE, taskValues.rrule)
            put(Tasks.EXDATE, taskValues.exdate)
            put(Tasks.TASK_COLOR, taskValues.color)

            // RFC 5545 requires a start for repeating tasks.
            // Keep a start date set elsewhere (e.g. "hide until" in Tasks.org) if it stays valid.
            val keepDtStart = existingDtStart != null &&
                existingDtStart.isAllDay == taskValues.isAllDay &&
                existingDtStart.time <= taskValues.due
            if (!keepDtStart && (taskValues.rrule != null || existingDtStart != null)) {
                put(Tasks.DTSTART, taskValues.due)
            }
        }
    }

    private data class DtStart(val time: Long, val isAllDay: Boolean)

    private fun getDtStart(remoteId: Long): DtStart? {
        var dtStart: DtStart? = null
        queryRows(ContentUris.withAppendedId(tasksUri, remoteId), arrayOf(Tasks.DTSTART, Tasks.IS_ALLDAY)) { cursor ->
            dtStart = cursor.getLongValueOrNull(Tasks.DTSTART)?.let {
                DtStart(it, cursor.getIntValue(Tasks.IS_ALLDAY) == 1)
            }
        }
        return dtStart
    }

    private fun getOverrideId(remoteId: Long, originalTime: Long): Long? {
        var overrideId: Long? = null
        val selection =
            "${Tasks.ORIGINAL_INSTANCE_ID} = ? AND ${Tasks.ORIGINAL_INSTANCE_TIME} = ? AND ${Tasks.DELETED} = 0"
        val selectionArgs = arrayOf(remoteId.toString(), originalTime.toString())
        queryRows(tasksUri, arrayOf(Tasks.ID), selection, selectionArgs) { cursor ->
            overrideId = cursor.getLongValue(Tasks.ID)
        }
        return overrideId
    }

    private fun updateTaskRow(id: Long, values: ContentValues) {
        context.contentResolver.update(ContentUris.withAppendedId(tasksUri, id), values, null, null)
    }

    private fun readTaskRow(cursor: Cursor) = OpenTasksMapper.TaskRow(
        id = cursor.getLongValue(Tasks.ID),
        title = cursor.getStringValue(Tasks.TITLE) ?: "",
        description = cursor.getStringValue(Tasks.DESCRIPTION) ?: "",
        location = cursor.getStringValue(Tasks.LOCATION) ?: "",
        dtStart = cursor.getLongValueOrNull(Tasks.DTSTART),
        due = cursor.getLongValueOrNull(Tasks.DUE),
        isAllDay = cursor.getIntValue(Tasks.IS_ALLDAY) == 1,
        timeZone = cursor.getStringValue(Tasks.TZ),
        rrule = cursor.getStringValue(Tasks.RRULE),
        exdate = cursor.getStringValue(Tasks.EXDATE),
        status = cursor.getIntValueOrNull(Tasks.STATUS) ?: Tasks.STATUS_NEEDS_ACTION,
        color = cursor.getIntValueOrNull(Tasks.TASK_COLOR)
    )

    // the start of the occurrence an override completes, null if it does not complete one
    private fun readCompletedOccurrence(cursor: Cursor): Long? {
        val status = cursor.getIntValueOrNull(Tasks.STATUS) ?: Tasks.STATUS_NEEDS_ACTION
        val originalTime = cursor.getLongValueOrNull(Tasks.ORIGINAL_INSTANCE_TIME)
        if (originalTime == null || !OpenTasksMapper.isCompleted(status)) {
            return null
        }

        val isAllDay = cursor.getIntValue(Tasks.ORIGINAL_INSTANCE_ALLDAY) == 1
        return OpenTasksMapper.fromProviderTime(originalTime, isAllDay)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun getAlarms(): Map<Long, List<OpenTasksMapper.AlarmRow>> {
        val alarms = HashMap<Long, ArrayList<OpenTasksMapper.AlarmRow>>()
        val projection = arrayOf(Properties.TASK_ID, Alarm.MINUTES_BEFORE, Alarm.REFERENCE, Alarm.ALARM_TYPE)
        val selection = "${Properties.MIMETYPE} = ?"
        val selectionArgs = arrayOf(Alarm.CONTENT_ITEM_TYPE)
        try {
            queryRows(propertiesUri, projection, selection, selectionArgs) { cursor ->
                // reminders are relative to the task time (due), alarms relative to the start cannot be represented
                val reference = cursor.getIntValueOrNull(Alarm.REFERENCE) ?: Alarm.ALARM_REFERENCE_DUE_DATE
                if (reference == Alarm.ALARM_REFERENCE_DUE_DATE) {
                    val alarm = OpenTasksMapper.AlarmRow(
                        minutesBefore = cursor.getIntValue(Alarm.MINUTES_BEFORE),
                        alarmType = cursor.getIntValueOrNull(Alarm.ALARM_TYPE) ?: Alarm.ALARM_TYPE_MESSAGE
                    )
                    alarms.getOrPut(cursor.getLongValue(Properties.TASK_ID)) { ArrayList() }.add(alarm)
                }
            }
        } catch (e: Exception) {
            // reminders are optional, never fail the whole sync because of them
            Log.e(TAG, "Fetching alarms of ${provider.authority} failed", e)
        }
        return alarms
    }

    @Suppress("TooGenericExceptionCaught")
    private fun writeAlarms(remoteId: Long, task: Event) {
        try {
            val selection = "${Properties.TASK_ID} = ? AND ${Properties.MIMETYPE} = ?"
            val selectionArgs = arrayOf(remoteId.toString(), Alarm.CONTENT_ITEM_TYPE)
            context.contentResolver.delete(propertiesUri, selection, selectionArgs)

            OpenTasksMapper.toAlarms(task).forEach {
                val values = ContentValues().apply {
                    put(Properties.TASK_ID, remoteId)
                    put(Properties.MIMETYPE, Alarm.CONTENT_ITEM_TYPE)
                    put(Alarm.MINUTES_BEFORE, it.minutesBefore)
                    put(Alarm.REFERENCE, Alarm.ALARM_REFERENCE_DUE_DATE)
                    put(Alarm.ALARM_TYPE, it.alarmType)
                }
                context.contentResolver.insert(propertiesUri, values)
            }
        } catch (e: Exception) {
            // reminders are optional, never fail the whole sync because of them
            Log.e(TAG, "Writing alarms of task $remoteId failed", e)
        }
    }

    // unlike Context.queryCursor this does not swallow errors, a failed query must never look like an empty list
    private inline fun queryRows(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        onRow: (Cursor) -> Unit
    ) {
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                onRow(cursor)
            }
        }
    }
}
