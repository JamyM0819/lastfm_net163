package com.lastfm.net163

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ScrobbleNotificationListener : NotificationListenerService() {
    private val tracker = PlaybackTracker()
    private val clock = PlaybackClock()
    @Volatile private var lastfm: LastfmClient? = null
    @Volatile private var netease: NetEaseClient? = null
    @Volatile private var mediaController: MediaController? = null
    @Volatile private var playbackState: PlaybackState? = null
    @Volatile private var mediaDurationSec: Int = 0
    @Volatile private var mediaAlbum: String = ""
    @Volatile private var mediaId: String = ""
    private var currentTrack: Track? = null
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "lastfm-net163-worker").apply { isDaemon = true }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            playbackState = state
            DebugLog.append("STATE ${state?.state} (2=暂停 3=播放)")
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val ms = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
            mediaDurationSec = (ms / 1000).toInt()
            mediaAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
            val id = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
            if (id.isNotBlank() && id != mediaId) {
                mediaId = id
                DebugLog.append("MEDIA_ID=$id")
            }
        }
    }

    private fun isPlaying(): Boolean = PlaybackStateRules.isPlaying(playbackState?.state)

    private fun attachController(token: MediaSession.Token) {
        val existing = mediaController
        if (existing?.sessionToken == token) return
        existing?.unregisterCallback(controllerCallback)
        val c = MediaController(this, token)
        c.registerCallback(controllerCallback)
        mediaController = c
        // 保留上次已知状态，等新 controller 的回调到达后再更新。
        c.playbackState?.let { playbackState = it }
        val md = c.metadata
        val ms = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
        mediaDurationSec = (ms / 1000).toInt()
        mediaAlbum = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        mediaId = md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
    }

    fun configure(apiKey: String, apiSecret: String, sessionKey: String) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            lastfm = null
            netease = null
            return
        }
        lastfm = LastfmClient(apiKey, apiSecret, sessionKey)
        netease = NetEaseClient()
    }

    override fun onCreate() {
        super.onCreate()
        ScrobbleHistory.init(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        val prefs = Prefs(this)
        configure(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
        executor.scheduleWithFixedDelay({ heartbeat() }, 2, 2, TimeUnit.SECONDS)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != "com.netease.cloudmusic") return
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getString(Notification.EXTRA_TEXT)
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT)
        @Suppress("DEPRECATION")
        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token == null) {
            DebugLog.append("POST title=$title text=$text mediaToken=false ignored")
            return
        }
        attachController(token)
        val playing = isPlaying()
        val miuiMedia = try {
            extras.getString("miui.focus.param.media")
        } catch (e: Exception) {
            null
        }
        DebugLog.append("POST title=$title text=$text mediaToken=true playing=$playing")
        executor.execute {
            val parsed = NotificationParser.parse(title, text, subText, playing)
            val withAlbum = parsed?.let {
                if (it.album.isBlank() && mediaAlbum.isNotBlank()) it.copy(album = mediaAlbum) else it
            }
            val canonical = canonicalize(withAlbum, mediaId, miuiMedia)
            val track = enrich(canonical)
            currentTrack = track
            if (tracker.onTrack(track)) {
                track?.let { submit(it) }
            }
        }
    }

    /**
     * 用网易云官方曲名替换通知里“原名 (译名)”的合成标题，和电脑端记录方式对齐：
     * 1) MediaSession/MIUI 扩展里有曲目 ID → song/detail 直接取官方 name（主路径）；
     * 2) 没有 ID → 搜索 + song/detail 的 name/alias 整串验证后才剥离（回退路径）；
     * 3) 都失败则保留通知原文，绝不按括号盲切，避免误伤 "(Live)" 这类原名。
     */
    private fun canonicalize(track: Track?, mediaIdHint: String?, miuiExtra: String?): Track? {
        if (track == null) {
            clock.reset()
            return null
        }
        val client = netease ?: return track
        val id = NetEaseClient.idFromMediaId(mediaIdHint)
            ?: NetEaseClient.idFromShareText(miuiExtra)
        var source = "none"
        var canonical: CanonicalSong? = null
        if (id != null) {
            val byId = client.getTrackById(id)
            if (byId != null && NetEaseClient.isCompositeTitle(track.title, byId.title)) {
                canonical = byId
                source = "id"
            }
        }
        if (canonical == null) {
            val searched = client.resolveCanonical(track.artist, track.title)
            if (searched != null) {
                canonical = searched
                source = "search"
            }
        }
        if (canonical == null) {
            DebugLog.append("RESOLVE src=none keep=${track.title}")
            return track
        }
        val resolved = track.copy(
            title = canonical.title,
            artist = canonical.artist.ifBlank { track.artist },
            album = canonical.album.ifBlank { track.album },
            durationSeconds = if (canonical.durationMs > 0) canonical.durationMs / 1000 else track.durationSeconds
        )
        if (resolved.title != track.title) {
            DebugLog.append("RESOLVE src=$source raw=${track.title} -> ${resolved.title}")
        }
        return resolved
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != "com.netease.cloudmusic") return
        val extras = sbn.notification.extras
        @Suppress("DEPRECATION")
        val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
        if (token == null) {
            DebugLog.append("REMOVED mediaToken=false ignored")
            return
        }
        DebugLog.append("REMOVED")
        mediaController?.unregisterCallback(controllerCallback)
        mediaController = null
        playbackState = null
        mediaId = ""
        executor.execute {
            currentTrack = null
            tracker.onTrack(null)
            clock.reset()
        }
    }

    override fun onDestroy() {
        instance = null
        executor.shutdown()
        super.onDestroy()
    }

    private fun heartbeat() {
        val track = currentTrack ?: return
        val playing = isPlaying()
        val position = clock.tick(track.key, playing, SystemClock.elapsedRealtime() / 1000)
        val updated = track.copy(isPlaying = playing, positionSeconds = position)
        if (tracker.onTrack(updated)) {
            submit(updated)
        }
    }

    private fun enrich(track: Track?): Track? {
        if (track == null) {
            clock.reset()
            return null
        }
        val position = clock.tick(track.key, track.isPlaying, SystemClock.elapsedRealtime() / 1000)
        var duration = track.durationSeconds
        if (duration <= 0 && mediaDurationSec > 0) {
            duration = mediaDurationSec
        } else if (duration <= 0) {
            val ms = netease?.getDurationMs(track.artist, track.title) ?: 0
            if (ms > 0) duration = ms / 1000
        }
        return track.copy(durationSeconds = duration, positionSeconds = position)
    }

    private fun submit(track: Track) {
        try {
            lastfm?.scrobble(track.artist, track.title, track.album)
            ScrobbleHistory.add(track.artist, track.title)
            DebugLog.append("SCROBBLED ${track.artist} - ${track.title}")
        } catch (e: Exception) {
            DebugLog.append("SCROBBLE FAILED ${track.artist} - ${track.title}: ${e.message}")
            Log.w(TAG, "scrobble failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "lastfm_net163"

        @Volatile var instance: ScrobbleNotificationListener? = null
    }
}
