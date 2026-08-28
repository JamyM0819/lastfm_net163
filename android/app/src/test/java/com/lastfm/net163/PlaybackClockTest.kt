package com.lastfm.net163

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackClockTest {
    private val key = Triple("t", "a", "")

    @Test fun accumulatesWhilePlaying() {
        val clock = PlaybackClock()
        assertEquals(0, clock.tick(key, true, 100L))
        assertEquals(10, clock.tick(key, true, 110L))
        assertEquals(15, clock.tick(key, true, 115L))
    }

    @Test fun pauseDoesNotAccumulate() {
        val clock = PlaybackClock()
        clock.tick(key, true, 100L)
        assertEquals(0, clock.tick(key, false, 130L))
        assertEquals(10, clock.tick(key, true, 140L))
    }

    @Test fun keyChangeResets() {
        val clock = PlaybackClock()
        clock.tick(key, true, 100L)
        clock.tick(key, true, 120L)
        assertEquals(0, clock.tick(Triple("u", "a", ""), true, 125L))
    }

    @Test fun resetClears() {
        val clock = PlaybackClock()
        clock.tick(key, true, 100L)
        clock.tick(key, true, 110L)
        clock.reset()
        assertEquals(0, clock.tick(key, true, 120L))
    }
}
