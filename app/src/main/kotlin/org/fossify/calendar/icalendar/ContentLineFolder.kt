package org.fossify.calendar.icalendar

internal class ContentLineFolder(private val maxLength: Int = DEFAULT_MAX_LENGTH) {
    companion object {
        private const val DEFAULT_MAX_LENGTH = 75
    }

    fun fold(line: String): Sequence<String> = sequence {
        var index = 0
        var isFirstLine = true

        while (index < line.length) {
            var end = index + maxLength
            // Take the prepended space into account.
            if (!isFirstLine) end--
            if (end > line.length) {
                end = line.length
            } else {
                // Avoid splitting surrogate pairs
                if (Character.isHighSurrogate(line[end - 1])) {
                    end--
                }
            }

            val substring = line.substring(index, end)
            if (isFirstLine) {
                yield(substring)
            } else {
                yield(" $substring")
            }

            isFirstLine = false
            index = end
        }
    }
}
