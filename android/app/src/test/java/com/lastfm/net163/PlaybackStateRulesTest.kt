package com.lastfm.net163

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateRulesTest {
    @Test fun onlyPlayingStateCountsAsPlaying() {
        assertTrue(PlaybackStateRules.isPlaying(3)) // STATE_PLAYING
    }

    @Test fun bufferingIsNotPlaying() {
        assertFalse(PlaybackStateRules.isPlaying(6)) // STATE_BUFFERING
    }

    @Test fun pausedAndNullAreNotPlaying() {
        assertFalse(PlaybackStateRules.isPlaying(2)) // STATE_PAUSED
        assertFalse(PlaybackStateRules.isPlaying(null))
    }
}
