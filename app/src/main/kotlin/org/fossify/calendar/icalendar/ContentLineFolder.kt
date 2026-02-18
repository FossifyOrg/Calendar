package org.fossify.calendar.icalendar

internal class ContentLineFolder {
    companion object {
        private const val MAX_LINE_LENGTH = 75
    }

    fun fold(line: String): Sequence<String> = sequence {
        var index = 0
        var isFirstLine = true

        while (index < line.length) {
            var end = index + MAX_LINE_LENGTH
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
