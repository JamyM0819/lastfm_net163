package com.lastfm.net163

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshDynamic()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        ScrobbleHistory.init(this)

        val apiKey = findViewById<EditText>(R.id.api_key)
        val apiSecret = findViewById<EditText>(R.id.api_secret)
        apiKey.setText(prefs.apiKey)
        apiSecret.setText(prefs.apiSecret)

        findViewById<Button>(R.id.save).setOnClickListener {
            prefs.apiKey = apiKey.text.toString().trim()
            prefs.apiSecret = apiSecret.text.toString().trim()
            ScrobbleNotificationListener.instance?.configure(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
            refreshStatus()
            Toast.makeText(this, "凭据已保存", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.auth).setOnClickListener {
            val key = apiKey.text.toString().trim()
            val secret = apiSecret.text.toString().trim()
            if (key.isBlank() || secret.isBlank()) {
                Toast.makeText(this, "请先填写 api_key / api_secret", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.apiKey = key
            prefs.apiSecret = secret
            ScrobbleNotificationListener.instance?.configure(key, secret, prefs.sessionKey)
            startActivity(Intent(this, AuthActivity::class.java))
        }

        findViewById<Button>(R.id.edit_credentials).setOnClickListener {
            findViewById<LinearLayout>(R.id.credentials).visibility = View.VISIBLE
            it.visibility = View.GONE
        }

        findViewById<Button>(R.id.notification_access).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.clear_history).setOnClickListener {
            ScrobbleHistory.clear()
            refreshDynamic()
            Toast.makeText(this, "同步列表已清除", Toast.LENGTH_SHORT).show()
        }

        val debugScroll = findViewById<ScrollView>(R.id.debug_scroll)
        val toggleDebug = findViewById<Button>(R.id.toggle_debug)
        toggleDebug.setOnClickListener {
            val visible = debugScroll.visibility == View.VISIBLE
            debugScroll.visibility = if (visible) View.GONE else View.VISIBLE
            toggleDebug.text = if (visible) "后台信息 ▸" else "后台信息 ▾"
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshDynamic()
        handler.postDelayed(refreshRunnable, 1_000L)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun refreshStatus() {
        val authorized = prefs.sessionKey.isNotBlank() &&
            prefs.apiKey.isNotBlank() &&
            prefs.apiSecret.isNotBlank()
        findViewById<TextView>(R.id.status).text =
            if (prefs.sessionKey.isNotBlank()) "已登录：${prefs.username}" else "未授权 last.fm"
        findViewById<LinearLayout>(R.id.credentials).visibility =
            if (authorized) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.edit_credentials).visibility =
            if (authorized) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.auth).text =
            if (authorized) "重新授权 last.fm" else "授权 last.fm"
    }

    private fun refreshDynamic() {
        findViewById<TextView>(R.id.debug).text = DebugLog.text()
        val history = ScrobbleHistory.list()
        findViewById<TextView>(R.id.history).text =
            if (history.isEmpty()) "（暂无同步记录）" else history.joinToString("\n")
    }
}
