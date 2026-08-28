package com.lastfm.net163

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("lastfm_net163", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(value) = sp.edit().putString("api_key", value).apply()

    var apiSecret: String
        get() = sp.getString("api_secret", "") ?: ""
        set(value) = sp.edit().putString("api_secret", value).apply()

    var sessionKey: String
        get() = sp.getString("session_key", "") ?: ""
        set(value) = sp.edit().putString("session_key", value).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(value) = sp.edit().putString("username", value).apply()
}
