package com.lastfm.net163

class PlaybackTracker(
    private val minDurationSeconds: Int = 30,
    private val minRatio: Double = 0.5,
    private val minPositionSeconds: Int = 240
) {
    private var currentKey: Triple<String, String, String>? = null
    private var scrobbled = false

    fun onTrack(track: Track?): Boolean {
        if (track == null) {
            currentKey = null
            scrobbled = false
            return false
        }
        if (track.key != currentKey) {
            currentKey = track.key
            scrobbled = false
        }
        if (scrobbled || !track.isPlaying) return false
        if (track.durationSeconds in 1 until minDurationSeconds) return false

        val eligible = if (track.durationSeconds > 0) {
            track.positionSeconds >= track.durationSeconds * minRatio ||
                track.positionSeconds >= minPositionSeconds
        } else {
            track.positionSeconds >= minPositionSeconds
        }
        if (!eligible) return false

        scrobbled = true
        return true
    }
}
