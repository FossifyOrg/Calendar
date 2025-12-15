package org.fossify.calendar.dialogs

import android.text.TextUtils
import android.widget.RelativeLayout
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.CalendarItemAccountBinding
import org.fossify.calendar.databinding.CalendarItemCalendarBinding
import org.fossify.calendar.databinding.DialogSelectCalendarsBinding
import org.fossify.calendar.extensions.calDAVHelper
import org.fossify.calendar.extensions.config
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.views.MyAppCompatCheckbox

class ManageSyncedCalendarsDialog(val activity: SimpleActivity, val callback: () -> Unit) {
    private var prevAccount = ""
    private val binding by activity.viewBinding(DialogSelectCalendarsBinding::inflate)

    init {
        val ids = activity.config.getSyncedCalendarIdsAsList()
        val calendars = activity.calDAVHelper.getCalDAVCalendars("", true)
        binding.apply {
            dialogSelectCalendarsPlaceholder.beVisibleIf(calendars.isEmpty())
            dialogSelectCalendarsHolder.beVisibleIf(calendars.isNotEmpty())
        }

        val sorted = calendars.sortedWith(compareBy({ it.accountName }, { it.displayName }))
        sorted.forEach {
            if (prevAccount != it.accountName) {
                prevAccount = it.accountName
                addCalendarItem(false, it.accountName)
            }

            addCalendarItem(true, it.displayName, it.id, ids.contains(it.id))
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> confirmSelection() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.select_caldav_calendars)
            }
    }

    private fun addCalendarItem(
        isEvent: Boolean,
        text: String,
        tag: Int = 0,
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
        val calendarIds = ArrayList<Int>()
        val childCnt = binding.dialogSelectCalendarsHolder.childCount
        for (i in 0..childCnt) {
            val child = binding.dialogSelectCalendarsHolder.getChildAt(i)
            if (child is RelativeLayout) {
                val check = child.getChildAt(0)
                if (check is MyAppCompatCheckbox && check.isChecked) {
                    calendarIds.add(check.tag as Int)
                }
            }
        }

        activity.config.caldavSyncedCalendarIds = TextUtils.join(",", calendarIds)
        callback()
    }
}
