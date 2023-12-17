package org.fossify.calendar.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.FilterEventTypeViewBinding
import org.fossify.calendar.models.EventType
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setFillWithStroke

class FilterEventTypeAdapter(val activity: SimpleActivity, val eventTypes: List<EventType>, val displayEventTypes: Set<String>) :
    RecyclerView.Adapter<FilterEventTypeAdapter.EventTypeViewHolder>() {
    private val selectedKeys = HashSet<Long>()

    init {
        eventTypes.forEach { eventType ->
            if (displayEventTypes.contains(eventType.id.toString())) {
                selectedKeys.add(eventType.id!!)
            }
        }
    }

    private fun toggleItemSelection(select: Boolean, eventType: EventType, pos: Int) {
        if (select) {
            selectedKeys.add(eventType.id!!)
        } else {
            selectedKeys.remove(eventType.id)
        }

        notifyItemChanged(pos)
    }

    fun getSelectedItemsList() = selectedKeys.asSequence().map { it }.toMutableList() as ArrayList<Long>

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventTypeViewHolder {
        return EventTypeViewHolder(
            binding = FilterEventTypeViewBinding.inflate(activity.layoutInflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: EventTypeViewHolder, position: Int) = holder.bindView(eventType = eventTypes[position])

    override fun getItemCount() = eventTypes.size

    inner class EventTypeViewHolder(val binding: FilterEventTypeViewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(eventType: EventType) {
            val isSelected = selectedKeys.contains(eventType.id)
            binding.apply {
                filterEventTypeCheckbox.isChecked = isSelected
                filterEventTypeCheckbox.setColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), activity.getProperBackgroundColor())
                filterEventTypeCheckbox.text = eventType.getDisplayTitle()
                filterEventTypeColor.setFillWithStroke(eventType.color, activity.getProperBackgroundColor())
                filterEventTypeHolder.setOnClickListener {
                    viewClicked(!isSelected, eventType)
                }
            }
        }

        private fun viewClicked(select: Boolean, eventType: EventType) {
            toggleItemSelection(select, eventType, adapterPosition)
        }
    }
}
