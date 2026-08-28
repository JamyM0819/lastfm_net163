package com.lastfm.net163

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTrackerTest {
    private fun track(
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        duration: Int = 200,
        position: Int = 100,
        playing: Boolean = true
    ) = Track(title, artist, album, duration, position, playing)

    @Test fun shortTrackNotScrobbled() {
        assertFalse(PlaybackTracker().onTrack(track(duration = 20, position = 10)))
    }

    @Test fun halfPlayedScrobbles() {
        assertTrue(PlaybackTracker().onTrack(track(duration = 200, position = 100)))
    }

    @Test fun fourMinutesScrobblesEvenBeforeHalf() {
        assertTrue(PlaybackTracker().onTrack(track(duration = 600, position = 240)))
    }

    @Test fun underHalfNotScrobbled() {
        assertFalse(PlaybackTracker().onTrack(track(duration = 200, position = 99)))
    }

    @Test fun sameTrackOnlyOnce() {
        val tracker = PlaybackTracker()
        assertTrue(tracker.onTrack(track(duration = 200, position = 100)))
        assertFalse(tracker.onTrack(track(duration = 200, position = 150)))
    }

    @Test fun trackChangeResets() {
        val tracker = PlaybackTracker()
        assertTrue(tracker.onTrack(track(title = "A", duration = 200, position = 100)))
        assertTrue(tracker.onTrack(track(title = "B", duration = 200, position = 100)))
    }

    @Test fun pausedDoesNotScrobble() {
        assertFalse(PlaybackTracker().onTrack(track(playing = false)))
    }

    @Test fun nullTrackResets() {
        val tracker = PlaybackTracker()
        tracker.onTrack(track(duration = 200, position = 100))
        assertFalse(tracker.onTrack(null))
        assertTrue(tracker.onTrack(track(duration = 200, position = 100)))
    }
}
