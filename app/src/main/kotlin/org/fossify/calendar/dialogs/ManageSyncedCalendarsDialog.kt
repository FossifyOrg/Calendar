package org.fossify.calendar.dialogs

import android.text.TextUtils
import android.widget.RelativeLayout
import androidx.core.view.children
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.CalendarItemAccountBinding
import org.fossify.calendar.databinding.CalendarItemCalendarBinding
import org.fossify.calendar.databinding.DialogSelectCalendarsBinding
import org.fossify.calendar.extensions.calDAVHelper
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.tasksRepository
import org.fossify.calendar.sync.tasks.RemoteTaskList
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.views.MyAppCompatCheckbox

class ManageSyncedCalendarsDialog(val activity: SimpleActivity, val callback: () -> Unit) {
    private var prevAccount = ""
    private val shownTaskListKeys = HashSet<String>()
    private val binding by activity.viewBinding(DialogSelectCalendarsBinding::inflate)

    init {
        val ids = activity.config.getSyncedCalendarIdsAsList()
        val calendars = activity.calDAVHelper.getCalDAVCalendars("", true)
        val taskLists = activity.tasksRepository.getRemoteLists()
        binding.apply {
            dialogSelectCalendarsPlaceholder.beVisibleIf(calendars.isEmpty() && taskLists.isEmpty())
            dialogSelectCalendarsHolder.beVisibleIf(calendars.isNotEmpty() || taskLists.isNotEmpty())
            if (calendars.isEmpty() && taskLists.isEmpty()) {
                val placeholders = listOf(activity.getString(R.string.no_synchronized_calendars), getTaskSyncHint())
                dialogSelectCalendarsPlaceholder.text = placeholders.joinToString("\n\n")
            }
        }

        val sorted = calendars.sortedWith(compareBy({ it.accountName }, { it.displayName }))
        sorted.forEach {
            if (prevAccount != it.accountName) {
                prevAccount = it.accountName
                addCalendarItem(false, it.accountName)
            }

            addCalendarItem(true, it.displayName, it.id, ids.contains(it.id))
        }

        if (calendars.isNotEmpty() || taskLists.isNotEmpty()) {
            addTaskListItems(taskLists)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> confirmSelection() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.select_caldav_calendars)
            }
    }

    // task lists come from a separate task provider (OpenTasks, Tasks.org) filled by DAVx5, the tag is the list key
    private fun addTaskListItems(taskLists: List<RemoteTaskList>) {
        val selectedTaskLists = activity.config.caldavSyncedTaskLists
        addCalendarItem(false, activity.getString(R.string.task_lists))
        if (taskLists.isEmpty()) {
            addCalendarItem(false, getTaskSyncHint())
            return
        }

        val showProviderName = taskLists.map { it.providerId }.distinct().size > 1
        val backendNames = activity.tasksRepository.backends.associate { it.providerId to it.displayName }
        taskLists.sortedWith(compareBy({ it.accountName }, { it.displayName })).forEach {
            val accountName = if (showProviderName) {
                "${it.accountName} (${backendNames[it.providerId]})"
            } else {
                it.accountName
            }
            addCalendarItem(true, "${it.displayName} ($accountName)", it.key, selectedTaskLists.contains(it.key))
            shownTaskListKeys.add(it.key)
        }
    }

    private fun getTaskSyncHint(): String {
        val repository = activity.tasksRepository
        val hint = when {
            repository.getAvailableBackends().isEmpty() -> R.string.task_sync_no_provider
            repository.getMissingPermissions().isNotEmpty() -> R.string.task_sync_no_permission
            else -> R.string.task_sync_no_lists
        }
        return activity.getString(hint)
    }

    private fun addCalendarItem(
        isEvent: Boolean,
        text: String,
        tag: Any = 0,
        shouldCheck: Boolean = false
    ) {
        val itemBinding = if (isEvent) {
            CalendarItemCalendarBinding.inflate(
                activity.layoutInflater,
                binding.dialogSelectCalendarsHolder,
                false
            ).apply {
                calendarItemCalendarSwitch.tag = tag
                calendarItemCalendarSwitch.text = text
                calendarItemCalendarSwitch.isChecked = shouldCheck
                root.setOnClickListener {
                    calendarItemCalendarSwitch.toggle()
                }
            }
        } else {
            CalendarItemAccountBinding.inflate(
                activity.layoutInflater,
                binding.dialogSelectCalendarsHolder,
                false
            ).apply {
                calendarItemAccount.text = text
            }
        }

        binding.dialogSelectCalendarsHolder.addView(itemBinding.root)
    }

    private fun confirmSelection() {
        val checkedTags = binding.dialogSelectCalendarsHolder.children
            .filterIsInstance<RelativeLayout>()
            .map { it.getChildAt(0) }
            .filter { it is MyAppCompatCheckbox && it.isChecked }
            .map { it.tag }
            .toList()
        val calendarIds = checkedTags.filterIsInstance<Int>()
        val taskListKeys = checkedTags.filterIsInstance<String>()
        // keep lists that could not be shown, e.g. because the task app is temporarily unavailable
        val hiddenTaskListKeys = activity.config.caldavSyncedTaskLists.filter { it !in shownTaskListKeys }

        activity.config.caldavSyncedCalendarIds = TextUtils.join(",", calendarIds)
        activity.config.caldavSyncedTaskLists = (taskListKeys + hiddenTaskListKeys).toSet()
        callback()
    }
}
