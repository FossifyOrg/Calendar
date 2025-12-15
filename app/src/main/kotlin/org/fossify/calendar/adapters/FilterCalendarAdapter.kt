package org.fossify.calendar.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.FilterCalendarViewBinding
import org.fossify.calendar.models.CalendarEntity
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setFillWithStroke

class FilterCalendarAdapter(
    val activity: SimpleActivity,
    val calendars: List<CalendarEntity>,
    val displayCalendars: Set<String>
) :
    RecyclerView.Adapter<FilterCalendarAdapter.CalendarViewHolder>() {
    private val selectedKeys = HashSet<Long>()

    init {
        calendars.forEach { calendar ->
            if (displayCalendars.contains(calendar.id.toString())) {
                selectedKeys.add(calendar.id!!)
            }
        }
    }

    private fun toggleItemSelection(select: Boolean, calendar: CalendarEntity, pos: Int) {
        if (select) {
            selectedKeys.add(calendar.id!!)
        } else {
            selectedKeys.remove(calendar.id)
        }

        notifyItemChanged(pos)
    }

    fun getSelectedItemsList() =
        selectedKeys.asSequence().map { it }.toMutableList() as ArrayList<Long>

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        return CalendarViewHolder(
            binding = FilterCalendarViewBinding.inflate(activity.layoutInflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) =
        holder.bindView(calendar = calendars[position])

    override fun getItemCount() = calendars.size

    inner class CalendarViewHolder(val binding: FilterCalendarViewBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(calendar: CalendarEntity) {
            val isSelected = selectedKeys.contains(calendar.id)
            binding.apply {
                filterCalendarCheckbox.isChecked = isSelected
                filterCalendarCheckbox.setColors(
                    activity.getProperTextColor(),
                    activity.getProperPrimaryColor(),
                    activity.getProperBackgroundColor()
                )
                filterCalendarCheckbox.text = calendar.getDisplayTitle()
                filterCalendarColor.setFillWithStroke(
                    calendar.color,
                    activity.getProperBackgroundColor()
                )
                filterCalendarHolder.setOnClickListener {
                    viewClicked(!isSelected, calendar)
                }
            }
        }

        private fun viewClicked(select: Boolean, calendar: CalendarEntity) {
            toggleItemSelection(select, calendar, adapterPosition)
        }
    }
}
