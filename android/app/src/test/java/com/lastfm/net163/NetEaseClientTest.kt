package com.lastfm.net163

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun bestMatchDoesNotReturnArtistOnlyMatch() {
        val songs = JSONArray()
        songs.put(JSONObject().put("name", "Unrelated Song").put("duration", 999000)
            .put("artists", JSONArray().put(JSONObject().put("name", "Taylor Swift"))))
        assertEquals(0, NetEaseClient().bestMatchMs("Taylor Swift", "Mean", songs))
    }

    private fun songJson(
        id: Long,
        name: String,
        artist: String,
        album: String = "Album",
        duration: Int = 83813,
        aliases: List<String> = emptyList(),
        transName: String? = null
    ) = JSONObject().put("id", id).put("name", name).put("duration", duration)
        .put("artists", JSONArray().put(JSONObject().put("name", artist)))
        .put("album", JSONObject().put("name", album))
        .apply {
            if (aliases.isNotEmpty()) {
                val arr = JSONArray()
                aliases.forEach { arr.put(it) }
                put("alias", arr)
            }
            if (transName != null) put("transName", transName)
        }

    @Test fun matchCanonicalStripsValidatedAliasSuffix() {
        // 实测格式：原名自带括号，网易云再拼一个 " (别名)"：
        // "Ambitions (Introduction) (壮志雄心 -导引-)"
        val song = songJson(441102538, "Ambitions (Introduction)", "ONE OK ROCK",
            album = "Ambitions", aliases = listOf("壮志雄心 -导引-"))
        val canonical = NetEaseClient().matchCanonicalSong(
            "Ambitions (Introduction) (壮志雄心 -导引-)", "ONE OK ROCK", song)
        assertEquals("Ambitions (Introduction)", canonical?.title)
        assertEquals("Ambitions", canonical?.album)
    }

    @Test fun matchCanonicalAcceptsFullWidthParensAndExactName() {
        val song = songJson(1L, "Lemon", "米津玄師", aliases = listOf("柠檬"))
        assertEquals("Lemon",
            NetEaseClient().matchCanonicalSong("Lemon（柠檬）", "米津玄師", song)?.title)
        // 没有翻译后缀时整串等于官方名，直接通过
        assertEquals("Lemon",
            NetEaseClient().matchCanonicalSong("Lemon", "米津玄師", song)?.title)
    }

    @Test fun matchCanonicalDoesNotStripUnvalidatedParentheses() {
        // 原名本来就以 (Live) 结尾，且 detail 没有对应 alias：禁止盲切
        val song = songJson(2L, "Hotel California (Live on MTV, 1994)", "Eagles")
        assertNull(NetEaseClient().matchCanonicalSong(
            "Hotel California (Live on MTV, 1994)", "Eagles", song)?.title
            ?.takeIf { it == "Hotel California" })
        assertEquals("Hotel California (Live on MTV, 1994)",
            NetEaseClient().matchCanonicalSong(
                "Hotel California (Live on MTV, 1994)", "Eagles", song)?.title)
        // 后缀不在 alias 列表里，也不能剥离
        val songWithAlias = songJson(3L, "Song", "Singer", aliases = listOf("正确译名"))
        assertNull(NetEaseClient().matchCanonicalSong("Song (错误译名)", "Singer", songWithAlias))
    }

    @Test fun matchCanonicalRejectsWrongArtist() {
        val song = songJson(4L, "Ambitions (Introduction)", "ONE OK ROCK",
            aliases = listOf("壮志雄心 -导引-"))
        assertNull(NetEaseClient().matchCanonicalSong(
            "Ambitions (Introduction) (壮志雄心 -导引-)", "Someone Else", song))
    }

    @Test fun idExtractionHelpers() {
        assertEquals(441102538L, NetEaseClient.idFromMediaId("441102538"))
        assertEquals(441102538L, NetEaseClient.idFromMediaId("song?id=441102538"))
        assertEquals(441102538L, NetEaseClient.idFromMediaId("media_id:441102538"))
        assertNull(NetEaseClient.idFromMediaId(""))
        val share = """{"shareContent":"https://y.music.163.com/m/song?id=441102538&app_version=9"}"""
        assertEquals(441102538L, NetEaseClient.idFromShareText(share))
    }

    @Test fun compositeTitleGuardsByIdRace() {
        // ID 与标题不匹配（切歌错拍）时不得信任 ID 结果
        assertEquals(true, NetEaseClient.isCompositeTitle(
            "Ambitions (Introduction) (壮志雄心 -导引-)", "Ambitions (Introduction)"))
        assertEquals(true, NetEaseClient.isCompositeTitle("Lemon", "Lemon"))
        assertEquals(false, NetEaseClient.isCompositeTitle("完全不同的标题", "Lemon"))
        assertEquals(false, NetEaseClient.isCompositeTitle("Lemon (Live)", "Hotel California"))
    }
}
