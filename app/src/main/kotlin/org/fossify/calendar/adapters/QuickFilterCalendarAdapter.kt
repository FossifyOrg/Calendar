package org.fossify.calendar.adapters

import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.QuickFilterCalendarViewBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.models.CalendarEntity
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.LOWER_ALPHA

class QuickFilterCalendarAdapter(
    val activity: SimpleActivity,
    private val allCalendars: List<CalendarEntity>,
    private val quickFilterCalendarIds: Set<String>,
    val callback: () -> Unit
) : RecyclerView.Adapter<QuickFilterCalendarAdapter.QuickFilterViewHolder>() {
    private val activeKeys = HashSet<Long>()
    private val quickFilterCalendars = ArrayList<CalendarEntity>()
    private val displayCalendars = activity.config.displayCalendars

    private val textColorActive = activity.getProperTextColor()
    private val textColorInactive = textColorActive.adjustAlpha(LOWER_ALPHA)

    private val minItemWidth =
        activity.resources.getDimensionPixelSize(R.dimen.quick_filter_min_width)
    private var lastClickTS = 0L

    private var lastLongClickedType: CalendarEntity? = null
    private var lastActiveKeys = HashSet<Long>()

    init {
        quickFilterCalendarIds.forEach { quickFilterCalendar ->
            val calendar = allCalendars
                .firstOrNull { calendar -> calendar.id.toString() == quickFilterCalendar }
                ?: return@forEach
            quickFilterCalendars.add(calendar)
        }

        allCalendars.forEach {
            if (displayCalendars.contains(it.id.toString())) {
                activeKeys.add(it.id!!)
            }
        }

        quickFilterCalendars.sortBy { it.title.lowercase() }
    }

    private fun toggleItemSelection(select: Boolean, calendar: CalendarEntity, pos: Int) {
        if (select) {
            activeKeys.add(calendar.id!!)
        } else {
            activeKeys.remove(calendar.id)
        }

        notifyItemChanged(pos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickFilterViewHolder {
        val parentWidth = parent.measuredWidth
        val numberOfItems = quickFilterCalendars.size
        val binding = QuickFilterCalendarViewBinding.inflate(
            activity.layoutInflater, parent, false
        )

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
        val calendar = quickFilterCalendars[position]
        holder.bindView(calendar)
    }

    override fun getItemCount() = quickFilterCalendars.size

    inner class QuickFilterViewHolder(val binding: QuickFilterCalendarViewBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindView(calendar: CalendarEntity) {
            val isSelected = activeKeys.contains(calendar.id)
            binding.apply {
                quickFilterCalendar.text = calendar.title
                val textColor = if (isSelected) textColorActive else textColorInactive
                quickFilterCalendar.setTextColor(textColor)

                val indicatorHeightRes =
                    if (isSelected) R.dimen.quick_filter_active_line_size else R.dimen.quick_filter_inactive_line_size
                quickFilterCalendarColor.layoutParams.height =
                    root.resources.getDimensionPixelSize(indicatorHeightRes)
                quickFilterCalendarColor.setBackgroundColor(calendar.color)

                // avoid too quick clicks, could cause glitches
                quickFilterCalendar.setOnClickListener {
                    if (System.currentTimeMillis() - lastClickTS > 300) {
                        lastClickTS = System.currentTimeMillis()
                        viewClicked(!isSelected, calendar)
                        callback()
                        lastLongClickedType = null
                    }
                }

                quickFilterCalendar.setOnLongClickListener {
                    if (lastLongClickedType != calendar) {
                        lastActiveKeys.clear()
                    }
                    val activeKeysCopy = HashSet(activeKeys)
                    allCalendars.forEach {
                        viewClicked(select = lastActiveKeys.contains(it.id!!), calendar = it)
                    }

                    val shouldSelectCurrent = if (lastLongClickedType != calendar) {
                        true
                    } else {
                        lastActiveKeys.contains(calendar.id!!)
                    }

                    viewClicked(shouldSelectCurrent, calendar)
                    notifyItemRangeChanged(0, itemCount)
                    callback()
                    lastLongClickedType = calendar
                    lastActiveKeys = activeKeysCopy
                    true
                }
            }
        }

        private fun viewClicked(select: Boolean, calendar: CalendarEntity) {
            activity.config.displayCalendars = if (select) {
                activity.config.displayCalendars.plus(calendar.id.toString())
            } else {
                activity.config.displayCalendars.minus(calendar.id.toString())
            }

            toggleItemSelection(select, calendar, adapterPosition)
        }
    }
}
