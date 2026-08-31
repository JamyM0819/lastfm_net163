package com.lastfm.net163

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScrobbleHistory {
    private const val PREFS_NAME = "lastfm_scrobble_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 200
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    @Synchronized
    fun add(artist: String, title: String) {
        val ctx = appContext ?: return
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = sp.getString(KEY_ENTRIES, "") ?: ""
        val list = entries.split('\n').filter { it.isNotBlank() }.toMutableList()
        list.add(0, "${fmt.format(Date())}  $artist - $title")
        while (list.size > MAX_ENTRIES) list.removeAt(list.size - 1)
        sp.edit().putString(KEY_ENTRIES, list.joinToString("\n")).apply()
    }

    @Synchronized
    fun list(): List<String> {
        val ctx = appContext ?: return emptyList()
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = sp.getString(KEY_ENTRIES, "") ?: ""
        return entries.split('\n').filter { it.isNotBlank() }
    }

    @Synchronized
    fun clear() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ENTRIES).apply()
    }
}
