package com.lastfm.net163

class PlaybackClock {
    private var currentKey: Triple<String, String, String>? = null
    private var accumulatedSec = 0L
    private var lastTickSec = -1L

    fun reset() {
        currentKey = null
        accumulatedSec = 0L
        lastTickSec = -1L
    }

    fun tick(key: Triple<String, String, String>, isPlaying: Boolean, nowSec: Long): Int {
        if (key != currentKey) {
            currentKey = key
            accumulatedSec = 0L
            lastTickSec = nowSec
        }
        if (lastTickSec < 0L) lastTickSec = nowSec
        val delta = nowSec - lastTickSec
        if (isPlaying && delta > 0) accumulatedSec += delta
        lastTickSec = nowSec
        return accumulatedSec.toInt()
    }
}
