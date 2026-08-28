package com.lastfm.net163

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {
    @Test fun parsesTitleAndArtist() {
        val track = NotificationParser.parse("  Mean  ", "Taylor Swift")
        assertEquals("Mean", track?.title)
        assertEquals("Taylor Swift", track?.artist)
        assertEquals(true, track?.isPlaying)
    }

    @Test fun returnsNullWithoutTitleOrArtist() {
        assertNull(NotificationParser.parse("", "Taylor Swift"))
        assertNull(NotificationParser.parse("Mean", ""))
    }

    @Test fun extractsArtistFromTextWhenTextHasDash() {
        val track = NotificationParser.parse("Mean", "Taylor Swift - 专辑名")
        assertEquals("Taylor Swift", track?.artist)
    }

    @Test fun returnsNullForNullOrBlankInputs() {
        assertNull(NotificationParser.parse(null, "Taylor Swift"))
        assertNull(NotificationParser.parse("Mean", null))
        assertNull(NotificationParser.parse("   ", "   "))
    }
}
