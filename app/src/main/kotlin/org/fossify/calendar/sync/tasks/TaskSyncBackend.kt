package org.fossify.calendar.sync.tasks

import android.net.Uri
import org.fossify.calendar.models.CalendarEntity
import org.fossify.calendar.models.Event

/**
 * A place tasks can be synced with. The local Room database stays the source of truth for the UI,
 * backends only mirror remote tasks into it ([getRemoteTasks]) and write local changes through.
 *
 * Every task list is stored locally as a [CalendarEntity] with [CalendarEntity.taskProvider] and
 * [CalendarEntity.taskListId] set, which is what the list parameters below refer to.
 */
interface TaskSyncBackend {
    /** Identifies the backend, stored in [CalendarEntity.taskProvider]. */
    val providerId: String

    /** Human readable name, e.g. of the app providing the tasks. */
    val displayName: String

    /** Observed to notice remote changes, null if the backend cannot be observed. */
    val changesUri: Uri?

    fun isAvailable(): Boolean

    /** Runtime permissions that still have to be granted before the backend can be used. */
    fun getMissingPermissions(): List<String>

    fun getRemoteLists(): List<RemoteTaskList>

    fun getRemoteTasks(list: CalendarEntity): List<RemoteTask>

    /** @return the remote id of the new task */
    fun insertTask(list: CalendarEntity, task: Event): Long

    fun updateTask(list: CalendarEntity, remoteId: Long, task: Event)

    fun deleteTask(list: CalendarEntity, remoteId: Long)

    /**
     * @param occurrenceTS the start of the completed occurrence of a repeating task, null for non-repeating tasks
     */
    fun setTaskCompleted(
        list: CalendarEntity,
        remoteId: Long,
        task: Event,
        occurrenceTS: Long?,
        completed: Boolean
    )

    /** Ask whatever syncs the backend (e.g. DAVx5) to sync the given lists now. */
    fun requestSync(listIds: Set<Long>, manual: Boolean)
}

data class RemoteTaskList(
    val providerId: String,
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
) {
    val key get() = "$providerId|$id"
}

/**
 * @param task the task to store locally, without the local id, source and import id
 * @param completedOccurrences start timestamps of completed occurrences,
 * including the task start of a completed non-repeating task
 */
data class RemoteTask(
    val remoteId: Long,
    val task: Event,
    val completedOccurrences: Set<Long>,
)
