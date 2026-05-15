package org.fossify.calendar.icalendar

import java.io.OutputStream
import java.nio.charset.Charset

internal class ContentLineWriter(
    private val charset: Charset = DEFAULT_CHARSET,
    private val folder: ContentLineFolder = ContentLineFolder()
) {
    companion object {
        val DEFAULT_CHARSET: Charset = Charsets.UTF_8
        private const val CRLF = "\r\n"
    }

    fun write(outputStream: OutputStream, line: String) {
        for (segment in folder.fold(line)) {
            outputStream.write(segment.toByteArray(charset))
            outputStream.write(CRLF.toByteArray(charset))
        }
    }
}
