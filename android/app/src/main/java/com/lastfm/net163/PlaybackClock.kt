package com.lastfm.net163

class PlaybackClock {
    private var currentKey: Triple<String, String, String>? = null
    private var accumulatedMs = 0L
    private var lastTickMs = -1L

    fun reset() {
        currentKey = null
        accumulatedMs = 0L
        lastTickMs = -1L
    }

    fun tick(key: Triple<String, String, String>, nowMs: Long): Int {
        if (key != currentKey) {
            currentKey = key
            accumulatedMs = 0L
            lastTickMs = nowMs
        }
        if (lastTickMs < 0L) lastTickMs = nowMs
        accumulatedMs += nowMs - lastTickMs
        lastTickMs = nowMs
        return (accumulatedMs / 1000L).toInt()
    }
}
