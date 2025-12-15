package org.fossify.calendar.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.adapters.CheckableColorAdapter
import org.fossify.calendar.databinding.DialogSelectColorBinding
import org.fossify.calendar.views.AutoGridLayoutManager
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding

class SelectCalendarColorDialog(
    val activity: Activity,
    val colors: IntArray,
    var currentColor: Int,
    val callback: (color: Int) -> Unit
) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogSelectColorBinding::inflate)

    init {
        val colorAdapter = CheckableColorAdapter(activity, colors, currentColor) { color ->
            callback(color)
            dialog?.dismiss()
        }

        binding.colorGrid.apply {
            val width = activity.resources.getDimensionPixelSize(R.dimen.smaller_icon_size)
            val spacing =
                activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin) * 2
            layoutManager = AutoGridLayoutManager(context = activity, itemWidth = width + spacing)
            adapter = colorAdapter
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.color) {
                    dialog = it
                }

                if (colors.isEmpty()) {
                    showCustomColorPicker()
                }
            }
    }

    private fun showCustomColorPicker() {
        ColorPickerDialog(activity, currentColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                callback(color)
            }

            dialog?.dismiss()
        }
    }
}
