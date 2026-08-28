package com.lastfm.net163

data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val positionSeconds: Int,
    val isPlaying: Boolean
) {
    val key: Triple<String, String, String>
        get() = Triple(title.trim().lowercase(), artist.trim().lowercase(), album.trim().lowercase())
}
