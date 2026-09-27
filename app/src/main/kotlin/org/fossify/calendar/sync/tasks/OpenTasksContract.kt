package org.fossify.calendar.sync.tasks

import android.net.Uri
import androidx.core.net.toUri

/**
 * The subset of the OpenTasks provider contract we use, see
 * https://github.com/dmfs/opentasks/blob/master/opentasks-contract/src/main/java/org/dmfs/tasks/contract/TaskContract.java
 *
 * The same contract is implemented by OpenTasks and Tasks.org, DAVx5 syncs CalDAV tasks (VTODO) into both.
 */
object OpenTasksContract {
    data class Provider(
        val authority: String,
        val packageName: String,
        val appName: String,
        val readPermission: String,
        val writePermission: String,
    )

    val PROVIDERS = listOf(
        Provider(
            authority = "org.dmfs.tasks",
            packageName = "org.dmfs.tasks",
            appName = "OpenTasks",
            readPermission = "org.dmfs.permission.READ_TASKS",
            writePermission = "org.dmfs.permission.WRITE_TASKS"
        ),
        Provider(
            authority = "org.tasks.opentasks",
            packageName = "org.tasks",
            appName = "Tasks.org",
            readPermission = "org.tasks.permission.READ_TASKS",
            writePermission = "org.tasks.permission.WRITE_TASKS"
        ),
    )

    const val ACCOUNT_TYPE_LOCAL = "org.dmfs.account.LOCAL"

    object TaskLists {
        const val PATH = "tasklists"
        const val ID = "_id"
        const val LIST_NAME = "list_name"
        const val LIST_COLOR = "list_color"
        const val SYNC_ENABLED = "sync_enabled"
        const val ACCOUNT_NAME = "account_name"
        const val ACCOUNT_TYPE = "account_type"
    }

    object Tasks {
        const val PATH = "tasks"
        const val ID = "_id"
        const val LIST_ID = "list_id"
        const val TITLE = "title"
        const val LOCATION = "location"
        const val DESCRIPTION = "description"
        const val COMPLETED = "completed"
        const val STATUS = "status"
        const val TASK_COLOR = "task_color"
        const val DTSTART = "dtstart"
        const val IS_ALLDAY = "is_allday"
        const val TZ = "tz"
        const val DUE = "due"
        const val DURATION = "duration"
        const val EXDATE = "exdate"
        const val RRULE = "rrule"
        const val ORIGINAL_INSTANCE_ID = "original_instance_id"
        const val ORIGINAL_INSTANCE_TIME = "original_instance_time"
        const val ORIGINAL_INSTANCE_ALLDAY = "original_instance_allday"
        const val DELETED = "_deleted"

        const val STATUS_NEEDS_ACTION = 0
        const val STATUS_COMPLETED = 2
        const val STATUS_CANCELLED = 3
    }

    object Properties {
        const val PATH = "properties"
        const val TASK_ID = "task_id"
        const val MIMETYPE = "mimetype"
    }

    object Alarm {
        const val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/alarm"
        const val MINUTES_BEFORE = "data0"
        const val REFERENCE = "data1"
        const val ALARM_TYPE = "data3"

        const val ALARM_TYPE_MESSAGE = 1
        const val ALARM_TYPE_EMAIL = 2
        const val ALARM_REFERENCE_DUE_DATE = 1
    }

    fun getUri(authority: String, path: String): Uri = "content://$authority/$path".toUri()
}
