package com.lastfm.net163

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class AuthActivity : AppCompatActivity() {
    @Volatile private var token: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val prefs = Prefs(this)
        val apiKey = prefs.apiKey
        val apiSecret = prefs.apiSecret
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            Toast.makeText(this, "请先在主页面填写 api_key / api_secret", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val client = LastfmClient(apiKey, apiSecret)
        val webView = findViewById<WebView>(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        thread {
            try {
                token = client.getToken()
                runOnUiThread { webView.loadUrl(client.authUrl(token)) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "获取 token 失败：${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        findViewById<Button>(R.id.done).setOnClickListener {
            if (token.isBlank()) {
                Toast.makeText(this, "授权页还没加载完成，请稍候", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            thread {
                val deadline = System.currentTimeMillis() + 300_000L
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val (sessionKey, username) = client.getSession(token)
                        prefs.sessionKey = sessionKey
                        prefs.username = username
                        ScrobbleNotificationListener.instance?.configure(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
                        runOnUiThread {
                            Toast.makeText(this, "授权成功：$username", Toast.LENGTH_LONG).show()
                            finish()
                        }
                        return@thread
                    } catch (e: Exception) {
                        Thread.sleep(2_000L)
                    }
                }
                runOnUiThread {
                    Toast.makeText(this, "授权超时，请重试", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
