package org.fossify.calendar.adapters

import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.QuickFilterEventTypeViewBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.models.EventType
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.LOWER_ALPHA

class QuickFilterEventTypeAdapter(
    val activity: SimpleActivity,
    private val allEventTypes: List<EventType>,
    private val quickFilterEventTypeIds: Set<String>,
    val callback: () -> Unit
) : RecyclerView.Adapter<QuickFilterEventTypeAdapter.QuickFilterViewHolder>() {
    private val activeKeys = HashSet<Long>()
    private val quickFilterEventTypes = ArrayList<EventType>()
    private val displayEventTypes = activity.config.displayEventTypes

    private val textColorActive = activity.getProperTextColor()
    private val textColorInactive = textColorActive.adjustAlpha(LOWER_ALPHA)

    private val minItemWidth = activity.resources.getDimensionPixelSize(R.dimen.quick_filter_min_width)
    private var lastClickTS = 0L

    init {
        quickFilterEventTypeIds.forEach { quickFilterEventType ->
            val eventType = allEventTypes.firstOrNull { eventType -> eventType.id.toString() == quickFilterEventType } ?: return@forEach
            quickFilterEventTypes.add(eventType)

            if (displayEventTypes.contains(eventType.id.toString())) {
                activeKeys.add(eventType.id!!)
            }
        }

        quickFilterEventTypes.sortBy { it.title.lowercase() }
    }

    private fun toggleItemSelection(select: Boolean, eventType: EventType, pos: Int) {
        if (select) {
            activeKeys.add(eventType.id!!)
        } else {
            activeKeys.remove(eventType.id)
        }

        notifyItemChanged(pos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickFilterViewHolder {
        val parentWidth = parent.measuredWidth
        val numberOfItems = quickFilterEventTypes.size
        val binding = QuickFilterEventTypeViewBinding.inflate(activity.layoutInflater, parent, false)
        binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
            width = if (numberOfItems * minItemWidth > parentWidth) {
                minItemWidth
            } else {
                parentWidth / numberOfItems
            }
        }

        return QuickFilterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuickFilterViewHolder, position: Int) {
        val eventType = quickFilterEventTypes[position]
        holder.bindView(eventType)
    }

    override fun getItemCount() = quickFilterEventTypes.size

    inner class QuickFilterViewHolder(val binding: QuickFilterEventTypeViewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindView(eventType: EventType) {
            val isSelected = activeKeys.contains(eventType.id)
            binding.apply {
                quickFilterEventType.text = eventType.title
                val textColor = if (isSelected) textColorActive else textColorInactive
                quickFilterEventType.setTextColor(textColor)

                val indicatorHeightRes = if (isSelected) R.dimen.quick_filter_active_line_size else R.dimen.quick_filter_inactive_line_size
                quickFilterEventTypeColor.layoutParams.height = root.resources.getDimensionPixelSize(indicatorHeightRes)
                quickFilterEventTypeColor.setBackgroundColor(eventType.color)

                // avoid too quick clicks, could cause glitches
                quickFilterEventType.setOnClickListener {
                    if (System.currentTimeMillis() - lastClickTS > 300) {
                        lastClickTS = System.currentTimeMillis()
                        viewClicked(!isSelected, eventType)
                        callback()
                    }
                }
            }
        }

        private fun viewClicked(select: Boolean, eventType: EventType) {
            activity.config.displayEventTypes = if (select) {
                activity.config.displayEventTypes.plus(eventType.id.toString())
            } else {
                activity.config.displayEventTypes.minus(eventType.id.toString())
            }

            toggleItemSelection(select, eventType, adapterPosition)
        }
    }
}
