package com.lastfm.net163

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NetEaseClientTest {
    @Test fun bestMatchPrefersExactTitle() {
        val songs = JSONArray()
        songs.put(JSONObject().put("name", "Mean").put("duration", 231000)
            .put("artists", JSONArray().put(JSONObject().put("name", "Taylor Swift"))))
        songs.put(JSONObject().put("name", "Mean (Live)").put("duration", 200000)
            .put("artists", JSONArray().put(JSONObject().put("name", "Taylor Swift"))))
        assertEquals(231000, NetEaseClient().bestMatchMs("Taylor Swift", "Mean", songs))
    }

    @Test fun bestMatchReturnsZeroWhenNoMatch() {
        val songs = JSONArray()
        songs.put(JSONObject().put("name", "Unrelated").put("duration", 999000)
            .put("artists", JSONArray().put(JSONObject().put("name", "Someone Else"))))
        assertEquals(0, NetEaseClient().bestMatchMs("A", "T", songs))
    }
}
