package com.lastfm.net163

object PlaybackStateRules {
    // android.media.session.PlaybackState.STATE_PLAYING == 3
    private const val STATE_PLAYING = 3

    fun isPlaying(state: Int?): Boolean = state == STATE_PLAYING
}
