package com.lastfm.net163

object NotificationParser {
    fun parse(titleRaw: String?, textRaw: String?, subTextRaw: String? = null, isPlaying: Boolean = true): Track? {
        val title = titleRaw?.trim().orEmpty()
        val text = textRaw?.trim().orEmpty()
        val artist: String
        val album: String
        if (text.contains(" - ")) {
            artist = text.substringBefore(" - ").trim()
            album = text.substringAfter(" - ").trim()
        } else {
            artist = text
            album = subTextRaw?.trim().orEmpty()
        }
        if (title.isBlank() || artist.isBlank()) return null
        return Track(title, artist, album, 0, 0, isPlaying)
    }
}
