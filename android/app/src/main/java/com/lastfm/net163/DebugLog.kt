package com.lastfm.net163

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private val buffer = StringBuilder()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun append(line: String) {
        val ts = fmt.format(Date())
        buffer.append(ts).append(' ').append(line).append('\n')
        if (buffer.length > 4000) {
            buffer.delete(0, buffer.length - 4000)
        }
    }

    @Synchronized
    fun text(): String = buffer.toString()
}
