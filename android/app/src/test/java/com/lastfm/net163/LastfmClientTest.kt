package com.lastfm.net163

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastfmClientTest {
    @Test fun signIsMd5OfSortedParamsPlusSecret() {
        val client = LastfmClient("key", "secret")
        assertEquals("a1b2secret".md5(), client.sign(mapOf("b" to "2", "a" to "1")))
    }

    @Test fun signMatchesKnownVector() {
        val client = LastfmClient("key", "secret")
        assertEquals(
            "71ac736d13d8f2db61e65b01bc4e4b46",
            client.sign(mapOf("method" to "auth.gettoken", "api_key" to "key"))
        )
    }

    @Test fun authUrlContainsKeyAndToken() {
        val client = LastfmClient("key", "secret")
        val url = client.authUrl("tok123")
        assertTrue(url.contains("api_key=key"))
        assertTrue(url.contains("token=tok123"))
    }

    @Test fun imageUrlPrefersExtralarge() {
        val client = LastfmClient("key", "secret")
        val arr = JSONArray()
        arr.put(JSONObject().put("size", "small").put("#text", "https://small"))
        arr.put(JSONObject().put("size", "large").put("#text", "https://large"))
        arr.put(JSONObject().put("size", "extralarge").put("#text", "https://extralarge"))
        assertEquals("https://extralarge", client.imageUrl(arr))
    }

    @Test fun imageUrlFallsBackToLarge() {
        val client = LastfmClient("key", "secret")
        val arr = JSONArray()
        arr.put(JSONObject().put("size", "small").put("#text", "https://small"))
        arr.put(JSONObject().put("size", "large").put("#text", "https://large"))
        assertEquals("https://large", client.imageUrl(arr))
    }
}

private fun String.md5(): String {
    val digest = java.security.MessageDigest.getInstance("MD5")
    return digest.digest(toByteArray()).joinToString("") { "%02x".format(it) }
}
