package com.lastfm.net163

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastfmClientTest {
    @Test fun signIsMd5OfSortedParamsPlusSecret() {
        val client = LastfmClient("key", "secret")
        assertEquals("a1b2secret".md5(), client.sign(mapOf("b" to "2", "a" to "1")))
    }

    @Test fun authUrlContainsKeyAndToken() {
        val client = LastfmClient("key", "secret")
        val url = client.authUrl("tok123")
        assertTrue(url.contains("api_key=key"))
        assertTrue(url.contains("token=tok123"))
    }
}

private fun String.md5(): String {
    val digest = java.security.MessageDigest.getInstance("MD5")
    return digest.digest(toByteArray()).joinToString("") { "%02x".format(it) }
}
