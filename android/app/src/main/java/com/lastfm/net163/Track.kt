package com.lastfm.net163

import java.util.Locale

data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val positionSeconds: Int,
    val isPlaying: Boolean
) {
    val key: Triple<String, String, String> = Triple(
        title.trim().lowercase(Locale.ROOT),
        artist.trim().lowercase(Locale.ROOT),
        album.trim().lowercase(Locale.ROOT)
    )
}
