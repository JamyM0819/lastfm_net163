package com.lastfm.net163

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LastfmError(message: String) : Exception(message)

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
