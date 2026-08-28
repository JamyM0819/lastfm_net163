package com.lastfm.net163

object NotificationParser {
    fun parse(titleRaw: String?, textRaw: String?): Track? {
        val title = titleRaw?.trim().orEmpty()
        val text = textRaw?.trim().orEmpty()
        val artist = if (text.contains(" - ")) text.substringBefore(" - ").trim() else text
        if (title.isBlank() || artist.isBlank()) return null
        return Track(title, artist, "", 0, 0, true)
    }
}
