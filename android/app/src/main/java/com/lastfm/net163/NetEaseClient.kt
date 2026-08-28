package com.lastfm.net163

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NetEaseClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val SEARCH_URL = "https://music.163.com/api/search/get/web"
    }

    private val cache = mutableMapOf<Pair<String, String>, Int>()

    fun getDurationMs(artist: String, title: String): Int {
        val key = artist.trim().lowercase() to title.trim().lowercase()
        cache[key]?.let { return it }
        return try {
            val url = SEARCH_URL.toHttpUrl().newBuilder()
                .addQueryParameter("s", "$title $artist")
                .addQueryParameter("type", "1")
                .addQueryParameter("limit", "5")
                .addQueryParameter("offset", "0")
                .build()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 11)")
                .header("Referer", "https://music.163.com/")
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val ms = if (!resp.isSuccessful) {
                    0
                } else {
                    val json = JSONObject(resp.body?.string() ?: "")
                    val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
                    bestMatchMs(artist, title, songs)
                }
                cache[key] = ms
                ms
            }
        } catch (e: Exception) {
            0
        }
    }

    fun bestMatchMs(artist: String, title: String, songs: JSONArray): Int {
        val wantedTitle = title.trim().lowercase()
        val wantedArtist = artist.trim().lowercase()
        var bestMs = 0
        var bestScore = 0
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val name = song.optString("name").trim().lowercase()
            val artists = song.optJSONArray("artists") ?: JSONArray()
            var score = 0
            if (name == wantedTitle) {
                score += 3
            } else if (wantedTitle.length > 1 && wantedTitle.isNotBlank() && name.isNotBlank() &&
                (wantedTitle in name || name in wantedTitle)
            ) {
                score += 1
            }
            for (j in 0 until artists.length()) {
                val artistName = artists.optJSONObject(j)?.optString("name")?.trim()?.lowercase().orEmpty()
                if (wantedArtist.isNotBlank() &&
                    (wantedArtist in artistName || artistName in wantedArtist)
                ) {
                    score += 2
                    break
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestMs = song.optInt("duration")
            }
        }
        return bestMs
    }
}
