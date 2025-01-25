package org.fossify.calendar.adapters

import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.databinding.ItemAutocompleteTitleSubtitleBinding
import org.fossify.calendar.models.Attendee
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.helpers.SimpleContactsHelper

class AutoCompleteTextViewAdapter(
    val activity: SimpleActivity,
    val attendees: ArrayList<Attendee>,
) : ArrayAdapter<Attendee>(activity, 0, attendees) {
    var resultList = ArrayList<Attendee>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val attendee = resultList[position]
        val attendeeHasName = attendee.name.isNotEmpty()
        var listItem = convertView
        if (listItem == null || listItem.tag != attendeeHasName) {
            listItem = ItemAutocompleteTitleSubtitleBinding.inflate(
                activity.layoutInflater, parent, false
            ).apply {
                // TODO: Dark text color is set for dynamic theme only because light themes are
                //  configured with dark overflow menu background for some reason ¯\_(ツ)_/¯
                if (activity.isDynamicTheme()) {
                    val textColor = activity.getProperTextColor()
                    itemAutocompleteTitle.setTextColor(textColor)
                    itemAutocompleteSubtitle.setTextColor(textColor)
                }
            }.root
        }

        val nameToUse = when {
            attendee.name.isNotEmpty() -> attendee.name
            attendee.email.isNotEmpty() -> attendee.email
            else -> "A"
        }

        listItem.tag = attendeeHasName
        ItemAutocompleteTitleSubtitleBinding.bind(listItem).apply {
            itemAutocompleteTitle.text = if (attendeeHasName) {
                attendee.name
            } else {
                attendee.email
            }

            itemAutocompleteSubtitle.text = attendee.email
            itemAutocompleteSubtitle.beVisibleIf(attendeeHasName)

            val placeholder = BitmapDrawable(
                activity.resources,
                SimpleContactsHelper(context).getContactLetterIcon(nameToUse)
            )
            attendee.updateImage(context, itemAutocompleteImage, placeholder)
        }

        return listItem
    }

    override fun getFilter() = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterResults = FilterResults()
            if (constraint != null) {
                val results = mutableListOf<Attendee>()
                val searchString = constraint.toString().normalizeString()
                attendees.forEach {
                    if (
                        it.email.contains(searchString, true) ||
                        it.name.contains(searchString, true)
                    ) {
                        results.add(it)
                    }
                }

                results.sortWith(compareBy<Attendee>
                { it.name.startsWith(searchString, true) }.thenBy
                { it.email.startsWith(searchString, true) }.thenBy
                { it.name.contains(searchString, true) }.thenBy
                { it.email.contains(searchString, true) })
                results.reverse()

                filterResults.values = results
                filterResults.count = results.size
            }

            return filterResults
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            resultList.clear()
            if (results != null && results.count > 0) {
                @Suppress("UNCHECKED_CAST")
                resultList.addAll(results.values as List<Attendee>)
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?): String? {
            return (resultValue as? Attendee)?.getPublicName()
        }
    }

    override fun getItem(index: Int) = resultList[index]

    override fun getCount() = resultList.size
}
