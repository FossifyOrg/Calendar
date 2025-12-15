package org.fossify.calendar.activities

import android.os.Bundle
import org.fossify.calendar.R
import org.fossify.calendar.adapters.ManageCalendarsAdapter
import org.fossify.calendar.databinding.ActivityManageCalendarsBinding
import org.fossify.calendar.dialogs.EditCalendarDialog
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.interfaces.DeleteCalendarsListener
import org.fossify.calendar.models.CalendarEntity
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread

class ManageCalendarsActivity : SimpleActivity(), DeleteCalendarsListener {

    private val binding by viewBinding(ActivityManageCalendarsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        setupEdgeToEdge(padBottomSystem = listOf(binding.manageCalendarsList))
        setupMaterialScrollListener(binding.manageCalendarsList, binding.manageCalendarsAppbar)

        getCalendars()
        updateTextColors(binding.manageCalendarsList)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageCalendarsAppbar, NavigationIcon.Arrow)
    }

    private fun showEditCalendarDialog(calendar: CalendarEntity? = null) {
        EditCalendarDialog(this, calendar?.copy()) {
            getCalendars()
        }
    }

    private fun getCalendars() {
        eventsHelper.getCalendars(this, false) {
            val adapter = ManageCalendarsAdapter(this, it, this, binding.manageCalendarsList) {
                showEditCalendarDialog(it as CalendarEntity)
            }
            binding.manageCalendarsList.adapter = adapter
        }
    }

    private fun setupOptionsMenu() {
        binding.manageCalendarsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_calendar -> showEditCalendarDialog()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun deleteCalendars(
        calendars: ArrayList<CalendarEntity>,
        deleteEvents: Boolean
    ): Boolean {
        if (calendars.any { it.caldavCalendarId != 0 }) {
            toast(R.string.unsync_caldav_calendar)
            if (calendars.size == 1) {
                return false
            }
        }

        ensureBackgroundThread {
            eventsHelper.deleteCalendars(calendars, deleteEvents)
        }

        return true
    }
}
