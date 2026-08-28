package com.lastfm.net163

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackClockTest {
    private val key = Triple("t", "a", "")

    @Test fun accumulatesWhilePlaying() {
        val clock = PlaybackClock()
        assertEquals(0, clock.tick(key, 100_000L))
        assertEquals(10, clock.tick(key, 110_000L))
        assertEquals(15, clock.tick(key, 115_000L))
    }

    @Test fun keyChangeResets() {
        val clock = PlaybackClock()
        clock.tick(key, 100_000L)
        clock.tick(key, 120_000L)
        assertEquals(0, clock.tick(Triple("u", "a", ""), 125_000L))
    }

    @Test fun resetClears() {
        val clock = PlaybackClock()
        clock.tick(key, 100_000L)
        clock.tick(key, 110_000L)
        clock.reset()
        assertEquals(0, clock.tick(key, 120_000L))
    }
}
