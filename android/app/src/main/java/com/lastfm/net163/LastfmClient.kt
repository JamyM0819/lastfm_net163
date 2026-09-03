package com.lastfm.net163

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LastfmError(message: String) : Exception(message)

data class AvatarInfo(val username: String, val imageUrl: String)
data class TrackItem(val title: String, val artist: String, val imageUrl: String, val timeLabel: String)
data class ArtistItem(val name: String, val scrobbles: Long, val imageUrl: String)
data class AlbumItem(val name: String, val artist: String, val imageUrl: String, val playcount: Long)

class LastfmClient(
    private val apiKey: String,
    private val apiSecret: String,
    var sessionKey: String = "",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val API_ROOT = "https://ws.audioscrobbler.com/2.0/"
        private const val AUTH_ROOT = "https://www.last.fm/api/auth/"
    }

    fun sign(params: Map<String, String>): String {
        val payload = params.entries.sortedBy { it.key }.joinToString("") { "${it.key}${it.value}" }
        return md5(payload + apiSecret)
    }

    fun authUrl(token: String): String = "${AUTH_ROOT}?api_key=$apiKey&token=$token"

    fun getToken(): String = call(mapOf("method" to "auth.gettoken")).getString("token")

    fun getSession(token: String): Pair<String, String> {
        val session = call(mapOf("method" to "auth.getsession", "token" to token))
            .getJSONObject("session")
        return session.getString("key") to session.getString("name")
    }

    fun scrobble(
        artist: String,
        title: String,
        album: String = "",
        timestamp: Long = System.currentTimeMillis() / 1000
    ) {
        val params = mutableMapOf(
            "method" to "track.scrobble",
            "artist" to artist,
            "track" to title,
            "timestamp" to timestamp.toString(),
            "sk" to sessionKey
        )
        if (album.isNotBlank()) params["album"] = album
        call(params, method = "POST")
    }

    fun getUserInfo(username: String): AvatarInfo {
        val json = call(mapOf("method" to "user.getinfo", "user" to username))
        val user = json.getJSONObject("user")
        return AvatarInfo(
            username = user.optString("name"),
            imageUrl = imageUrl(user.optJSONArray("image"))
        )
    }

    fun getRecentTracks(username: String, limit: Int = 10): List<TrackItem> {
        val json = call(
            mapOf("method" to "user.getrecenttracks", "user" to username, "limit" to limit.toString())
        )
        val tracks = json.optJSONObject("recenttracks")?.optJSONArray("track") ?: return emptyList()
        val list = mutableListOf<TrackItem>()
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            val nowPlaying = t.optJSONObject("@attr")?.optString("nowplaying").orEmpty().isNotBlank()
            list.add(
                TrackItem(
                    title = t.optString("name"),
                    artist = t.optJSONObject("artist")?.optString("#text").orEmpty(),
                    imageUrl = imageUrl(t.optJSONArray("image")),
                    timeLabel = if (nowPlaying) "正在播放" else (t.optJSONObject("date")?.optString("#text").orEmpty())
                )
            )
        }
        return list
    }

    fun getTopArtists(username: String, limit: Int = 10): List<ArtistItem> {
        val json = call(
            mapOf("method" to "user.gettopartists", "user" to username, "limit" to limit.toString())
        )
        val artists = json.optJSONObject("topartists")?.optJSONArray("artist") ?: return emptyList()
        val list = mutableListOf<ArtistItem>()
        for (i in 0 until artists.length()) {
            val a = artists.optJSONObject(i) ?: continue
            list.add(
                ArtistItem(
                    name = a.optString("name"),
                    scrobbles = a.optLong("playcount"),
                    imageUrl = imageUrl(a.optJSONArray("image"))
                )
            )
        }
        return list
    }

    fun getTopAlbums(username: String, limit: Int = 10): List<AlbumItem> {
        val json = call(
            mapOf("method" to "user.gettopalbums", "user" to username, "limit" to limit.toString())
        )
        val albums = json.optJSONObject("topalbums")?.optJSONArray("album") ?: return emptyList()
        val list = mutableListOf<AlbumItem>()
        for (i in 0 until albums.length()) {
            val a = albums.optJSONObject(i) ?: continue
            list.add(
                AlbumItem(
                    name = a.optString("name"),
                    artist = a.optJSONObject("artist")?.optString("name").orEmpty(),
                    imageUrl = imageUrl(a.optJSONArray("image")),
                    playcount = a.optLong("playcount")
                )
            )
        }
        return list
    }

    fun getTopTracks(username: String, limit: Int = 10): List<TrackItem> {
        val json = call(
            mapOf("method" to "user.gettoptracks", "user" to username, "limit" to limit.toString())
        )
        val tracks = json.optJSONObject("toptracks")?.optJSONArray("track") ?: return emptyList()
        val list = mutableListOf<TrackItem>()
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            list.add(
                TrackItem(
                    title = t.optString("name"),
                    artist = t.optJSONObject("artist")?.optString("name").orEmpty(),
                    imageUrl = imageUrl(t.optJSONArray("image")),
                    timeLabel = t.optLong("playcount").toString() + " 次"
                )
            )
        }
        return list
    }

    internal fun imageUrl(arr: org.json.JSONArray?): String {
        if (arr == null) return ""
        var large = ""
        var extra = ""
        for (i in 0 until arr.length()) {
            val img = arr.optJSONObject(i) ?: continue
            when (img.optString("size")) {
                "large" -> large = img.optString("#text")
                "extralarge" -> extra = img.optString("#text")
            }
        }
        return extra.ifBlank { large }
    }

    private fun call(params: Map<String, String>, method: String = "GET"): JSONObject {
        val merged = params.toMutableMap()
        merged["api_key"] = apiKey
        merged["format"] = "json"
        merged["api_sig"] = sign(merged.filterKeys { it != "api_sig" && it != "format" })

        val request = if (method == "POST") {
            val form = FormBody.Builder()
            merged.forEach { (k, v) -> form.add(k, v) }
            Request.Builder().url(API_ROOT).post(form.build()).build()
        } else {
            val urlBuilder = API_ROOT.toHttpUrl().newBuilder()
            merged.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
            Request.Builder().url(urlBuilder.build()).build()
        }

        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw LastfmError("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw LastfmError("empty body")
            val json = JSONObject(body)
            if (json.has("error") && json.optInt("error") != 0) {
                throw LastfmError("last.fm error ${json.optInt("error")}: ${json.optString("message")}")
            }
            return json
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
