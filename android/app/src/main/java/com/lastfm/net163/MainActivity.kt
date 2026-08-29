package com.lastfm.net163

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = Prefs(this)
        val apiKey = findViewById<EditText>(R.id.api_key)
        val apiSecret = findViewById<EditText>(R.id.api_secret)
        val status = findViewById<TextView>(R.id.status)
        apiKey.setText(prefs.apiKey)
        apiSecret.setText(prefs.apiSecret)

        findViewById<Button>(R.id.save).setOnClickListener {
            prefs.apiKey = apiKey.text.toString().trim()
            prefs.apiSecret = apiSecret.text.toString().trim()
            ScrobbleNotificationListener.instance?.configure(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
            status.text = "凭据已保存"
        }

        findViewById<Button>(R.id.auth).setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }

        findViewById<Button>(R.id.notification_access).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = Prefs(this)
        val status = findViewById<TextView>(R.id.status)
        status.text = if (prefs.sessionKey.isBlank()) "未授权 last.fm" else "已登录：${prefs.username}"
        findViewById<TextView>(R.id.debug).text = DebugLog.text()
    }
}
