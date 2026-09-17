package com.lastfm.net163

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 网易云官方接口中的标准曲目信息（与电脑端解密 playingInfo 后取的 track 字段同源）。
 * title 永远是官方原名 name，不含通知栏里拼接的 (译名/别名)。
 */
data class CanonicalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Int
)

class NetEaseClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val SEARCH_URL = "https://music.163.com/api/search/get"
        private const val SONG_DETAIL_URL = "https://music.163.com/api/song/detail"

        // 网易云开启“歌曲名翻译”后，通知标题形如 “官方原名 (译名/别名)”，
        // 原名本身可能自带括号（如 Ambitions (Introduction)），所以只允许去掉
        // 紧跟在官方原名之后的“最后一个括号组”，且纯字符串层面不删除任何内容——
        // 删除前必须由 song/detail 的 name + alias/transName 做整串验证。
        private val BRACKET_SUFFIX = Regex("""^\s*[(（]\s*(.+?)\s*[)）]\s*$""")
        private val FULL_PAREN_WRAP = Regex("""^\s*[(（].*[)）]\s*$""")

        /** MediaSession 的 METADATA_KEY_MEDIA_ID 里提取网易云曲目 ID。 */
        fun idFromMediaId(raw: String?): Long? {
            val s = raw?.trim().orEmpty()
            if (s.isEmpty()) return null
            s.toLongOrNull()?.let { return it }
            Regex("""(?:song[/?]id=|song/|media[/_]?id=|id=)(\d+)""")
                .find(s)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
            // 兜底：取串中第一个足够长的数字串（网易云 ID 至少 6 位）
            return Regex("""\d{6,}""").find(s)?.value?.toLongOrNull()
        }

        /** 从 MIUI 通知扩展 miui.focus.param.media 的分享链接里提取曲目 ID。 */
        fun idFromShareText(raw: String?): Long? =
            Regex("""song\?id=(\d+)""").find(raw.orEmpty())?.groupValues?.get(1)?.toLongOrNull()

        /**
         * 判断通知标题 composite 是否是官方原名 name 的“原名 +( 译名)”合成形式。
         * 用于按曲目 ID 查到原名后做防串歌校验：ID 权威，但要排除切歌瞬间 ID/标题错拍。
         */
        fun isCompositeTitle(compositeRaw: String, nameRaw: String): Boolean {
            val composite = compositeRaw.trim()
            val name = nameRaw.trim()
            if (name.isEmpty() || !composite.startsWith(name)) return false
            if (composite == name) return true
            return FULL_PAREN_WRAP.matches(composite.removePrefix(name))
        }
    }

    private val cache = ConcurrentHashMap<Pair<String, String>, Int>()
    private val imageCache = ConcurrentHashMap<Pair<Int, String>, String>()
    private val idCache = ConcurrentHashMap<Long, CanonicalSong?>()
    private val canonicalCache = ConcurrentHashMap<Pair<String, String>, CanonicalSong>()

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

    /**
     * 按网易云曲目 ID 查 song/detail，返回与电脑端一致的官方字段（原名/歌手/专辑/时长）。
     */
    fun getTrackById(id: Long): CanonicalSong? {
        if (id <= 0) return null
        if (idCache.containsKey(id)) return idCache[id]
        return try {
            val details = fetchByIds(listOf(id))
            var found: CanonicalSong? = null
            for (i in 0 until details.length()) {
                val song = details.optJSONObject(i) ?: continue
                val canonical = parseCanonical(song)
                if (canonical?.id == id) {
                    found = canonical
                    break
                }
            }
            idCache[id] = found
            found
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 没有曲目 ID 时的回退路径：按“歌手 + 通知标题”搜索，再用 song/detail 的
     * name + alias/transName 整串验证。只有验证通过才剥离译名，
     * 原名自带括号（如 “Hotel California (Live)”）时不会被误伤。
     */
    fun resolveCanonical(artist: String, compositeTitle: String): CanonicalSong? {
        if (artist.isBlank() || compositeTitle.isBlank()) return null
        val key = artist.trim().lowercase(Locale.ROOT) to compositeTitle.trim().lowercase(Locale.ROOT)
        canonicalCache[key]?.let { return it }
        return try {
            val json = search(artist, compositeTitle, 1)
            val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
            val ids = (0 until minOf(5, songs.length())).mapNotNull { i ->
                songs.optJSONObject(i)?.optLong("id")?.takeIf { it > 0 }
            }
            val resolved = if (ids.isEmpty()) null else {
                val details = fetchByIds(ids)
                var match: CanonicalSong? = null
                for (i in 0 until details.length()) {
                    val song = details.optJSONObject(i) ?: continue
                    val candidate = matchCanonicalSong(compositeTitle, artist, song)
                    if (candidate != null) {
                        match = candidate
                        break
                    }
                }
                match
            }
            if (resolved != null) canonicalCache[key] = resolved
            resolved
        } catch (e: Exception) {
            null
        }
    }

    /** 纯函数：用 song/detail 的 name + alias/transName 验证整串通知标题。 */
    fun matchCanonicalSong(compositeRaw: String, artistRaw: String, song: JSONObject): CanonicalSong? {
        val canonical = parseCanonical(song) ?: return null
        if (!artistMatches(artistRaw, canonical.artist)) return null
        val composite = compositeRaw.trim()
        if (composite == canonical.title) return canonical
        val tailMatch = BRACKET_SUFFIX.matchEntire(composite.removePrefix(canonical.title))
            ?: return null
        val suffix = tailMatch.groupValues[1].trim()
        val aliases = mutableListOf<String>()
        song.optJSONArray("alias")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotBlank() }?.let { aliases.add(it.trim()) }
        }
        song.optJSONArray("transNames")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotBlank() }?.let { aliases.add(it.trim()) }
        }
        song.optString("transName")?.takeIf { it.isNotBlank() }?.let { aliases.add(it.trim()) }
        return if (aliases.any { it == suffix }) canonical else null
    }

    private fun parseCanonical(song: JSONObject): CanonicalSong? {
        val id = song.optLong("id")
        val name = song.optString("name")?.trim().orEmpty()
        if (id <= 0 || name.isEmpty()) return null
        val artists = song.optJSONArray("artists") ?: JSONArray()
        val artist = if (artists.length() > 0) artists.optJSONObject(0)?.optString("name")?.trim().orEmpty() else ""
        val album = song.optJSONObject("album")?.optString("name")?.trim().orEmpty()
        return CanonicalSong(
            id = id,
            title = name,
            artist = artist,
            album = album,
            durationMs = song.optInt("duration")
        )
    }

    private fun artistMatches(wantedRaw: String, actualRaw: String): Boolean {
        val wanted = wantedRaw.trim().lowercase(Locale.ROOT)
        val actual = actualRaw.trim().lowercase(Locale.ROOT)
        if (wanted.isEmpty() || actual.isEmpty()) return false
        return wanted == actual || wanted in actual || actual in wanted
    }

    private fun fetchByIds(ids: List<Long>): JSONArray {
        if (ids.isEmpty()) return JSONArray()
        val idsParam = ids.joinToString(prefix = "[", postfix = "]")
        val url = SONG_DETAIL_URL.toHttpUrl().newBuilder()
            .addQueryParameter("ids", idsParam)
            .addQueryParameter("id", ids.first().toString())
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11)")
            .header("Referer", "https://music.163.com/")
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return JSONArray()
            val detail = JSONObject(resp.body?.string() ?: "")
            return detail.optJSONArray("songs") ?: JSONArray()
        }
    }

    fun searchImageUrl(artist: String, title: String, type: Int): String {
        val key = type to "${artist.trim().lowercase(Locale.ROOT)}|${title.trim().lowercase(Locale.ROOT)}"
        imageCache[key]?.let { return it }
        val result = try {
            val raw = when (type) {
                1 -> searchTrackImage(artist, title)
                10 -> {
                    val json = search(artist, title, 10)
                    val albums = json.optJSONObject("result")?.optJSONArray("albums") ?: JSONArray()
                    if (albums.length() > 0) albums.optJSONObject(0)?.optString("picUrl").orEmpty() else ""
                }
                100 -> {
                    val json = search(artist, title, 100)
                    val artists = json.optJSONObject("result")?.optJSONArray("artists") ?: JSONArray()
                    if (artists.length() > 0) artists.optJSONObject(0)?.optString("picUrl").orEmpty() else ""
                }
                else -> ""
            }
            raw.replaceFirst("http://", "https://")
        } catch (e: Exception) {
            ""
        }
        imageCache[key] = result
        return result
    }

    private fun searchTrackImage(artist: String, title: String): String {
        val json = search(artist, title, 1)
        val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
        val id = bestSongId(artist, title, songs)
        if (id <= 0L) return ""
        val url = SONG_DETAIL_URL.toHttpUrl().newBuilder()
            .addQueryParameter("ids", "[$id]")
            .addQueryParameter("id", id.toString())
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11)")
            .header("Referer", "https://music.163.com/")
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return ""
            val detail = JSONObject(resp.body?.string() ?: "")
            val songsDetail = detail.optJSONArray("songs") ?: JSONArray()
            if (songsDetail.length() == 0) return ""
            return songsDetail.optJSONObject(0)?.optJSONObject("album")?.optString("picUrl").orEmpty()
        }
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

    private fun bestSongId(artist: String, title: String, songs: JSONArray): Long {
        val wantedTitle = title.trim().lowercase(Locale.ROOT)
        val wantedArtist = artist.trim().lowercase(Locale.ROOT)
        var bestId = 0L
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
                bestId = song.optLong("id")
            }
        }
        return bestId
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
