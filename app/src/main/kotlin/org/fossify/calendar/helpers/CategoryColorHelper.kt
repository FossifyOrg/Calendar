package org.fossify.calendar.helpers

import android.content.Context
import org.fossify.calendar.extensions.config
import java.util.Locale

/** Device-local colors for iCalendar categories. They are deliberately never synced. */
object CategoryColorHelper {
    private val palette = intArrayOf(
        0xff1565c0.toInt(), // blue
        0xff2e7d32.toInt(), // green
        0xffef6c00.toInt(), // orange
        0xff6a1b9a.toInt(), // purple
        0xffc62828.toInt(), // red
        0xff00838f.toInt(), // teal
        0xffad1457.toInt(), // pink
        0xff4e342e.toInt(), // brown
    )

    fun colorFor(context: Context, category: String): Int? {
        val key = categoryKey(category)
        if (key.isEmpty()) return null

        val colors = context.config.categoryColors
        colors[key]?.let { return it }

        val color = palette[(key.hashCode() and Int.MAX_VALUE) % palette.size]
        colors[key] = color
        context.config.categoryColors = colors
        return color
    }

    fun setColor(context: Context, category: String, color: Int) {
        val key = categoryKey(category)
        if (key.isEmpty()) return
        val colors = context.config.categoryColors
        colors[key] = color
        context.config.categoryColors = colors
    }

    private fun categoryKey(category: String) = category.trim().lowercase(Locale.ROOT)
}
