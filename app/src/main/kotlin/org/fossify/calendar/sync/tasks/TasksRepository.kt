package org.fossify.calendar.sync.tasks

import android.content.Context
import android.net.Uri
import android.util.Log
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.completedTasksDB
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsDB
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.getRemoteTaskId
import org.fossify.calendar.extensions.getTaskListCalendarId
import org.fossify.calendar.extensions.scheduleNextEventReminder
import org.fossify.calendar.helpers.FLAG_TASK_COMPLETED
import org.fossify.calendar.helpers.SOURCE_SIMPLE_CALENDAR
import org.fossify.calendar.helpers.SOURCE_TASK_LIST
import org.fossify.calendar.helpers.generateImportId
import org.fossify.calendar.models.CalendarEntity
import org.fossify.calendar.models.Event
import org.fossify.calendar.models.Task

/**
 * Keeps tasks of synced task lists in sync with their [TaskSyncBackend].
 *
 * Tasks in local calendars are not touched. Tasks in a synced task list are stored locally with
 * source "task-list-<local calendar id>" and import id "task-list-<local calendar id>-<remote task id>".
 * Task sync is part of the CalDAV sync setting, it is only active while that is enabled.
 *
 * Content providers of other apps can throw about anything (SecurityException, IllegalArgumentException...),
 * a failing backend must never break local task handling, so errors are caught and logged.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
class TasksRepository(private val context: Context) {
    companion object {
        private const val TAG = "TasksRepository"

        // serializes pulls and write-throughs, so a pull never sees a pushed task before it is linked locally
        private val lock = Any()

        fun getSource(calendarId: Long) = "$SOURCE_TASK_LIST-$calendarId"

        fun getImportId(calendarId: Long, remoteId: Long) = "$SOURCE_TASK_LIST-$calendarId-$remoteId"
    }

    val backends: List<TaskSyncBackend> = OpenTasksContract.PROVIDERS.map { OpenTasksBackend(context, it) }

    private val config get() = context.config

    fun getAvailableBackends() = backends.filter { it.isAvailable() }

    fun getMissingPermissions() = getAvailableBackends().flatMap { it.getMissingPermissions() }

    /** Task lists of all installed providers we have access to. */
    fun getRemoteLists(): List<RemoteTaskList> = getAvailableBackends()
        .filter { it.getMissingPermissions().isEmpty() }
        .flatMap { backend ->
            try {
                backend.getRemoteLists()
            } catch (e: Exception) {
                Log.e(TAG, "Fetching task lists of ${backend.providerId} failed", e)
                emptyList()
            }
        }

    fun getSyncedTaskLists(): List<CalendarEntity> = context.calendarsDB.getSyncedTaskLists()

    /** Creates local calendars for newly selected task lists and removes the ones not selected anymore. */
    fun applySelectedTaskLists(selectedKeys: Set<String>) {
        synchronized(lock) {
            val existing = getSyncedTaskLists()
            existing.filter { getKey(it) !in selectedKeys }.forEach { removeTaskList(it) }

            val existingKeys = existing.map { getKey(it) }.toSet()
            val newKeys = selectedKeys - existingKeys
            if (newKeys.isNotEmpty()) {
                getRemoteLists().filter { it.key in newKeys }.forEach {
                    val calendar = CalendarEntity(
                        id = null,
                        title = it.displayName,
                        color = it.color,
                        caldavDisplayName = it.displayName,
                        caldavEmail = it.accountName,
                        taskProvider = it.providerId,
                        taskListId = it.id
                    )
                    context.eventsHelper.insertOrUpdateCalendarSync(calendar)
                }
            }

            config.caldavSyncedTaskLists = selectedKeys
        }
    }

    /** Removes all synced task lists locally, the selection is kept for when sync gets enabled again. */
    fun removeAllTaskLists() {
        synchronized(lock) {
            getSyncedTaskLists().forEach { removeTaskList(it) }
        }
    }

    /** Mirrors all synced task lists into the local database. */
    fun syncAll() {
        if (!config.caldavSync) {
            return
        }

        synchronized(lock) {
            val lists = getSyncedTaskLists()
            if (lists.isEmpty()) {
                return
            }

            updateTaskListNames(lists)
            lists.forEach { pull(it) }
        }
    }

    /** Asks the sync adapters (e.g. DAVx5) of the synced task lists to sync now. */
    fun requestSync(manual: Boolean) {
        if (!config.caldavSync) {
            return
        }

        getSelectedTaskListIds().forEach { (providerId, listIds) ->
            val backend = getUsableBackend(providerId) ?: return@forEach
            try {
                backend.requestSync(listIds, manual)
            } catch (e: Exception) {
                Log.e(TAG, "Requesting a sync of $providerId failed", e)
            }
        }
    }

    fun onTaskInserted(task: Event) {
        if (!task.isTask() || task.id == null) {
            return
        }

        synchronized(lock) {
            val list = getSyncedList(task.calendarId)
            if (list != null) {
                push(list, task)
            } else if (task.getTaskListCalendarId() != null) {
                // a copy of a synced task (duplicate, edited occurrence...) stored in a local calendar
                unlink(task)
            }
        }
    }

    fun onTaskUpdated(task: Event) {
        if (!task.isTask() || task.id == null || !config.caldavSync) {
            return
        }

        synchronized(lock) {
            val list = getSyncedList(task.calendarId)
            val currentListId = task.getTaskListCalendarId()
            val remoteId = task.getRemoteTaskId()
            if (list != null && list.id == currentListId && remoteId != null) {
                val backend = getUsableBackend(list.taskProvider) ?: return
                runCatching { backend.updateTask(list, remoteId, task) }
                    .onFailure { Log.e(TAG, "Updating task ${task.id} failed", it) }
                return
            }

            // the task moved to another list, or it was never pushed
            if (currentListId != null && remoteId != null) {
                deleteRemote(currentListId, remoteId)
            }

            if (list != null) {
                push(list, task)
            } else if (currentListId != null) {
                unlink(task)
            }
        }
    }

    fun onTasksDeleted(tasks: List<Event>) {
        if (!config.caldavSync) {
            return
        }

        synchronized(lock) {
            tasks.forEach { task ->
                val listId = task.getTaskListCalendarId() ?: return@forEach
                val remoteId = task.getRemoteTaskId() ?: return@forEach
                deleteRemote(listId, remoteId)
            }
        }
    }

    /** @param occurrence the task with startTS set to the occurrence that was (un)completed */
    fun onCompletionChanged(occurrence: Event, completed: Boolean) {
        if (occurrence.id == null || !config.caldavSync) {
            return
        }

        synchronized(lock) {
            val task = context.eventsDB.getTaskWithId(occurrence.id!!) ?: return
            val list = getSyncedList(task.calendarId) ?: return
            val remoteId = task.getRemoteTaskId() ?: return
            if (task.getTaskListCalendarId() != list.id) {
                return
            }

            val backend = getUsableBackend(list.taskProvider) ?: return
            val occurrenceTS = if (task.repeatInterval > 0) occurrence.startTS else null
            runCatching { backend.setTaskCompleted(list, remoteId, task, occurrenceTS, completed) }
                .onFailure { Log.e(TAG, "Updating the completion of task ${task.id} failed", it) }
        }
    }

    private fun pull(list: CalendarEntity) {
        val backend = getUsableBackend(list.taskProvider) ?: return
        val remoteTasks = try {
            backend.getRemoteTasks(list)
        } catch (e: Exception) {
            // never delete local tasks because the provider could not be read
            Log.e(TAG, "Fetching tasks of list ${list.id} failed", e)
            return
        }

        val calendarId = list.id!!
        val source = getSource(calendarId)
        val existingTasks = context.eventsDB.getTasksFromSource(source).associateBy { it.importId }
        val fetchedImportIds = HashSet<String>()

        remoteTasks.forEach { remoteTask ->
            val task = remoteTask.task.apply {
                this.calendarId = calendarId
                this.source = source
                importId = getImportId(calendarId, remoteTask.remoteId)
            }
            fetchedImportIds.add(task.importId)

            val existingTask = existingTasks[task.importId]
            if (existingTask != null) {
                task.id = existingTask.id
                task.parentId = existingTask.parentId
                task.lastUpdated = existingTask.lastUpdated
            }

            if (task != existingTask) {
                task.id = context.eventsDB.insertOrUpdate(task)
                context.scheduleNextEventReminder(task, false)
            }

            updateCompletedOccurrences(task.id!!, remoteTask.completedOccurrences)
        }

        val idsToDelete = existingTasks.filterKeys { it !in fetchedImportIds }.values.mapNotNull { it.id }
        context.eventsHelper.deleteEvents(idsToDelete.toMutableList(), deleteFromCalDAV = false, updateWidgets = false)
    }

    private fun updateCompletedOccurrences(taskId: Long, completedOccurrences: Set<Long>) {
        val stored = context.completedTasksDB.getTasksWithId(taskId).map { it.startTS }.toSet()
        (completedOccurrences - stored).forEach {
            context.completedTasksDB.insertOrUpdate(Task(null, taskId, it, FLAG_TASK_COMPLETED))
        }
        (stored - completedOccurrences).forEach {
            context.completedTasksDB.deleteTaskWithIdAndTs(taskId, it)
        }
    }

    private fun updateTaskListNames(lists: List<CalendarEntity>) {
        val remoteLists = getRemoteLists().associateBy { it.key }
        lists.forEach { list ->
            val remoteList = remoteLists[getKey(list)] ?: return@forEach
            if (list.caldavDisplayName != remoteList.displayName || list.caldavEmail != remoteList.accountName) {
                list.title = remoteList.displayName
                list.caldavDisplayName = remoteList.displayName
                list.caldavEmail = remoteList.accountName
                context.calendarsDB.insertOrUpdate(list)
            }
        }
    }

    private fun push(list: CalendarEntity, task: Event) {
        val backend = getUsableBackend(list.taskProvider) ?: return
        try {
            val remoteId = backend.insertTask(list, task)
            task.source = getSource(list.id!!)
            task.importId = getImportId(list.id!!, remoteId)
            context.eventsDB.updateTaskImportIdAndSource(task.importId, task.source, task.id!!)

            context.completedTasksDB.getTasksWithId(task.id!!).forEach {
                val occurrenceTS = if (task.repeatInterval > 0) it.startTS else null
                backend.setTaskCompleted(list, remoteId, task, occurrenceTS, true)
            }
        } catch (e: Exception) {
            // the task stays local only, the next edit tries again
            Log.e(TAG, "Inserting task ${task.id} failed", e)
        }
    }

    private fun deleteRemote(calendarId: Long, remoteId: Long) {
        val list = context.calendarsDB.getCalendarWithId(calendarId)?.takeIf { it.isSyncedTaskList() } ?: return
        val backend = getUsableBackend(list.taskProvider) ?: return
        runCatching { backend.deleteTask(list, remoteId) }
            .onFailure { Log.e(TAG, "Deleting task $remoteId failed", it) }
    }

    private fun unlink(task: Event) {
        task.source = SOURCE_SIMPLE_CALENDAR
        task.importId = generateImportId()
        context.eventsDB.updateTaskImportIdAndSource(task.importId, task.source, task.id!!)
    }

    private fun removeTaskList(list: CalendarEntity) {
        val taskIds = context.eventsDB.getEventAndTasksIdsByCalendar(list.id!!).toMutableList()
        context.eventsHelper.deleteEvents(taskIds, deleteFromCalDAV = false)
        context.calendarsDB.deleteCalendars(listOf(list))
        config.removeDisplayCalendars(setOf(list.id.toString()))
        if (config.lastUsedTaskCalendarId == list.id) {
            config.lastUsedTaskCalendarId = -1L
        }
    }

    private fun getSyncedList(calendarId: Long) = if (config.caldavSync) {
        context.calendarsDB.getCalendarWithId(calendarId)?.takeIf { it.isSyncedTaskList() }
    } else {
        null
    }

    private fun getUsableBackend(providerId: String) = backends.firstOrNull {
        it.providerId == providerId && it.isAvailable() && it.getMissingPermissions().isEmpty()
    }

    /** Content uris to observe for remote task changes, safe to call on the main thread. */
    fun getTaskChangesUris(): List<Uri> = if (config.caldavSync) {
        getSelectedTaskListIds().keys.mapNotNull { getUsableBackend(it)?.changesUri }
    } else {
        emptyList()
    }

    /** Remote list ids of the synced task lists by provider, read from the preferences to work on the main thread. */
    fun getSelectedTaskListIds(): Map<String, Set<Long>> = config.caldavSyncedTaskLists
        .mapNotNull {
            val providerId = it.substringBeforeLast("|")
            val listId = it.substringAfterLast("|").toLongOrNull() ?: return@mapNotNull null
            providerId to listId
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }

    private fun getKey(list: CalendarEntity) = "${list.taskProvider}|${list.taskListId}"
}
