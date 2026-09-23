package org.fossify.calendar.dialogs

import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.DialogManageHolidaysBinding
import org.fossify.calendar.databinding.ItemHolidayCountryBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.helpers.OTHER_EVENT
import org.fossify.calendar.models.HolidayInfo
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding

class ManageHolidaysDialog(
    private val activity: SimpleActivity,
    countries: List<HolidayInfo>,
    callback: (Set<String>, ArrayList<Int>) -> Unit
) {
    private val binding by activity.viewBinding(DialogManageHolidaysBinding::inflate)
    private val selectedPaths = activity.config.holidayPaths.toMutableSet()

    init {
        countries.sortedWith(compareByDescending<HolidayInfo> { country -> country.paths.any { it in selectedPaths } }
            .thenBy { it.country }).forEach(::addCountry)
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                val paths = selectedPaths.toSet()
                val reminders = activity.config.holidayReminders
                if (paths.isEmpty()) {
                    callback(paths, reminders)
                } else {
                    SetRemindersDialog(activity, OTHER_EVENT, reminders) { callback(paths, it) }
                }
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.manage_holidays)
            }
    }

    private fun addCountry(info: HolidayInfo) {
        val item = ItemHolidayCountryBinding.inflate(activity.layoutInflater, binding.holidayCountries, false)
        val types = listOf(
            item.publicHolidays to info.public,
            item.regionalHolidays to info.regional,
            item.otherHolidays to info.other
        )

        fun updateChecks() {
            item.country.isChecked = info.paths.any { it in selectedPaths }
            item.holidayTypes.beVisibleIf(item.country.isChecked && info.paths.size > 1)
            types.forEach { (checkbox, path) -> checkbox.isChecked = path in selectedPaths }
        }

        item.country.text = info.country
        item.country.setOnClickListener {
            if (item.country.isChecked) {
                selectedPaths.add(info.paths.first())
            } else {
                selectedPaths.removeAll(info.paths.toSet())
            }
            updateChecks()
        }
        types.forEach { (checkbox, path) ->
            checkbox.beVisibleIf(!path.isNullOrEmpty())
            checkbox.setOnClickListener {
                if (checkbox.isChecked) selectedPaths.add(path!!) else selectedPaths.remove(path)
                updateChecks()
            }
        }
        updateChecks()
        binding.holidayCountries.addView(item.root)
    }
}
