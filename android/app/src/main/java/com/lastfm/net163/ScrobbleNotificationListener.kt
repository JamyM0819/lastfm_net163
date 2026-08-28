package com.lastfm.net163

import android.app.Notification
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlin.concurrent.thread

class ScrobbleNotificationListener : NotificationListenerService() {
    private val tracker = PlaybackTracker()
    private val clock = PlaybackClock()
    private var lastfm: LastfmClient? = null
    private var netease: NetEaseClient? = null

    fun configure(apiKey: String, apiSecret: String, sessionKey: String) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            lastfm = null
            netease = null
            return
        }
        lastfm = LastfmClient(apiKey, apiSecret, sessionKey)
        netease = NetEaseClient()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != "com.netease.cloudmusic") return
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)
        val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT)
        thread {
            val parsed = NotificationParser.parse(title, text)
            val track = enrich(parsed)
            if (tracker.onTrack(track)) {
                track?.let { submit(it) }
            }
        }
    }

    private fun enrich(track: Track?): Track? {
        if (track == null) {
            clock.reset()
            return null
        }
        val position = clock.tick(track.key, track.isPlaying, SystemClock.elapsedRealtime() / 1000)
        var duration = track.durationSeconds
        if (duration <= 0) {
            val ms = netease?.getDurationMs(track.artist, track.title) ?: 0
            if (ms > 0) duration = ms / 1000
        }
        return track.copy(durationSeconds = duration, positionSeconds = position)
    }

    private fun submit(track: Track) {
        try {
            lastfm?.scrobble(track.artist, track.title, track.album)
            Log.i(TAG, "scrobbled: ${track.artist} - ${track.title}")
        } catch (e: Exception) {
            Log.w(TAG, "scrobble failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "lastfm_net163"
    }
}
