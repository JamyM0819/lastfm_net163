package com.lastfm.net163

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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

    private val cache = ConcurrentHashMap<Pair<String, String>, Int>()
    private val imageCache = ConcurrentHashMap<Pair<Int, String>, String>()

    fun getDurationMs(artist: String, title: String): Int {
        val key = artist.trim().lowercase(Locale.ROOT) to title.trim().lowercase(Locale.ROOT)
        cache[key]?.let { return it }
        return try {
            val json = search(artist, title, 1)
            val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
            val ms = bestMatchMs(artist, title, songs)
            cache[key] = ms
            ms
        } catch (e: Exception) {
            0
        }
    }

    fun searchImageUrl(artist: String, title: String, type: Int): String {
        val key = type to "${artist.trim().lowercase(Locale.ROOT)}|${title.trim().lowercase(Locale.ROOT)}"
        imageCache[key]?.let { return it }
        val result = try {
            val json = search(artist, title, type)
            val res = json.optJSONObject("result") ?: JSONObject()
            when (type) {
                1 -> {
                    val songs = res.optJSONArray("songs") ?: JSONArray()
                    bestSongImage(artist, title, songs)
                }
                10 -> {
                    val albums = res.optJSONArray("albums") ?: JSONArray()
                    if (albums.length() > 0) albums.optJSONObject(0)?.optString("picUrl").orEmpty() else ""
                }
                100 -> {
                    val artists = res.optJSONArray("artists") ?: JSONArray()
                    if (artists.length() > 0) artists.optJSONObject(0)?.optString("picUrl").orEmpty() else ""
                }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
        imageCache[key] = result
        return result
    }

    private fun search(artist: String, title: String, type: Int): JSONObject {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", type.toString())
            .addQueryParameter("limit", "5")
            .addQueryParameter("offset", "0")
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11)")
            .header("Referer", "https://music.163.com/")
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return JSONObject()
            return JSONObject(resp.body?.string() ?: "")
        }
    }

    private fun bestSongImage(artist: String, title: String, songs: JSONArray): String {
        val wantedTitle = title.trim().lowercase(Locale.ROOT)
        val wantedArtist = artist.trim().lowercase(Locale.ROOT)
        var bestUrl = ""
        var bestScore = 0
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val name = song.optString("name").trim().lowercase(Locale.ROOT)
            val artists = song.optJSONArray("artists") ?: JSONArray()
            var score = 0
            if (name == wantedTitle) score += 3
            else if (wantedTitle.length > 1 && wantedTitle.isNotBlank() && name.isNotBlank() &&
                (wantedTitle in name || name in wantedTitle)
            ) score += 1
            if (score == 0) continue
            for (j in 0 until artists.length()) {
                val artistName = artists.optJSONObject(j)?.optString("name")?.trim()
                    ?.lowercase(Locale.ROOT).orEmpty()
                if (wantedArtist.isNotBlank() &&
                    (wantedArtist in artistName || artistName in wantedArtist)
                ) {
                    score += 2
                    break
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestUrl = song.optJSONObject("album")?.optString("picUrl").orEmpty()
                    .ifBlank { artists.optJSONObject(0)?.optString("picUrl").orEmpty() }
            }
        }
        return bestUrl
    }

    fun bestMatchMs(artist: String, title: String, songs: JSONArray): Int {
        val wantedTitle = title.trim().lowercase(Locale.ROOT)
        val wantedArtist = artist.trim().lowercase(Locale.ROOT)
        var bestMs = 0
        var bestScore = 0
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val name = song.optString("name").trim().lowercase(Locale.ROOT)
            val artists = song.optJSONArray("artists") ?: JSONArray()
            var score = 0
            if (name == wantedTitle) {
                score += 3
            } else if (wantedTitle.length > 1 && wantedTitle.isNotBlank() && name.isNotBlank() &&
                (wantedTitle in name || name in wantedTitle)
            ) {
                score += 1
            }
            if (score == 0) continue
            for (j in 0 until artists.length()) {
                val artistName = artists.optJSONObject(j)?.optString("name")?.trim()
                    ?.lowercase(Locale.ROOT).orEmpty()
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
