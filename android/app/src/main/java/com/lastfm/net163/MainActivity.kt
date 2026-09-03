package com.lastfm.net163

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val lfmRed = 0xFFD51007.toInt()
    private val lfmRedDark = 0xFFB00E06.toInt()
    private val muted = 0xFF8A8A8A.toInt()
    private val line = 0xFFE8E8EC.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        ScrobbleHistory.init(this)

        findViewById<ImageView>(R.id.avatar).setOnClickListener { showAvatarMenu() }
        loadAvatar()
        refreshStatus()
        loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        loadAvatar()
        handler.postDelayed(refreshRunnable, 1_000L)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showAvatarMenu() {
        val anchor = findViewById<View>(R.id.avatar)
        val popup = PopupMenu(this, anchor)
        popup.menu.add("重新授权 last.fm")
        popup.menu.add("修改凭据")
        popup.menu.add("开启通知使用权")
        popup.menu.add("检查更新")
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                "重新授权 last.fm" -> startActivity(Intent(this, AuthActivity::class.java))
                "修改凭据" -> showCredentialsDialog()
                "开启通知使用权" -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                "检查更新" -> checkUpdate()
            }
            true
        }
        popup.show()
    }

    private fun loadAvatar() {
        val avatar = findViewById<ImageView>(R.id.avatar)
        if (prefs.apiKey.isBlank() || prefs.username.isBlank()) {
            setInitialsAvatar(avatar)
            return
        }
        thread {
            try {
                val client = LastfmClient(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
                val info = client.getUserInfo(prefs.username)
                runOnUiThread {
                    if (info.imageUrl.isNotBlank()) {
                        ArtLoader.load(avatar, info.imageUrl)
                    } else {
                        setInitialsAvatar(avatar)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { setInitialsAvatar(avatar) }
            }
        }
    }

    private fun setInitialsAvatar(avatar: ImageView) {
        val letter = prefs.username.trim().firstOrNull()?.uppercase() ?: "?"
        val size = dp(38)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = lfmRed
        paint.textSize = dp(16).toFloat()
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val baseline = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(letter, size / 2f, baseline, paint)
        avatar.setImageBitmap(bitmap)
    }

    private fun showCredentialsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), 0)
        }
        val apiKey = EditText(this).apply {
            hint = "api_key"
            setText(prefs.apiKey)
        }
        val apiSecret = EditText(this).apply {
            hint = "api_secret"
            setText(prefs.apiSecret)
        }
        box.addView(apiKey)
        box.addView(apiSecret)

        AlertDialog.Builder(this)
            .setTitle("修改凭据")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                prefs.apiKey = apiKey.text.toString().trim()
                prefs.apiSecret = apiSecret.text.toString().trim()
                ScrobbleNotificationListener.instance?.configure(
                    prefs.apiKey, prefs.apiSecret, prefs.sessionKey
                )
                refreshStatus()
                Toast.makeText(this, "凭据已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshStatus() {
        val status = findViewById<TextView>(R.id.status)
        if (prefs.sessionKey.isBlank() || prefs.username.isBlank()) {
            status.text = "未授权 last.fm"
        } else {
            status.text = "已登录：${prefs.username} · 同步中"
        }
    }

    private fun loadDashboard() {
        if (prefs.apiKey.isBlank() || prefs.username.isBlank()) {
            return
        }
        thread {
            try {
                val client = LastfmClient(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
                val username = prefs.username
                val recent = client.getRecentTracks(username, 10)
                val artists = client.getTopArtists(username, 10)
                val albums = client.getTopAlbums(username, 6)
                val tracks = client.getTopTracks(username, 10)
                runOnUiThread { renderDashboard(recent, artists, albums, tracks) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "加载 last.fm 数据失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renderDashboard(
        recent: List<TrackItem>,
        artists: List<ArtistItem>,
        albums: List<AlbumItem>,
        tracks: List<TrackItem>
    ) {
        val container = findViewById<LinearLayout>(R.id.dashboard)
        // 清空除状态卡之外的所有 section
        for (i in container.childCount - 1 downTo 1) {
            container.removeViewAt(i)
        }

        addSection(container, "Recent Tracks")
        recent.forEachIndexed { index, item -> addTrackRow(container, index, item, item.timeLabel) }

        addSection(container, "Top Artists")
        artists.forEachIndexed { index, item ->
            addArtistRow(container, index + 1, item.name, "${item.scrobbles} scrobbles", item.imageUrl)
        }

        addSection(container, "Top Albums")
        addAlbumGrid(container, albums)

        addSection(container, "Top Tracks")
        tracks.forEachIndexed { index, item ->
            addTrackRow(container, index + 1, item, item.timeLabel)
        }
    }

    private fun addSection(container: LinearLayout, title: String) {
        val titleView = TextView(this).apply {
            text = title
            setTextColor(lfmRed)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
            setPadding(0, dp(4), 0, dp(5))
        }
        val lineView = View(this).apply {
            setBackgroundColor(lfmRed)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)
            )
        }
        container.addView(titleView)
        container.addView(lineView)
    }

    private fun addTrackRow(
        container: LinearLayout,
        rankOrIndex: Any,
        item: TrackItem,
        rightLabel: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        if (rankOrIndex is Int) {
            val rank = TextView(this).apply {
                text = rankOrIndex.toString()
                setTextColor(muted)
                textSize = 12f
                width = dp(18)
            }
            row.addView(rank)
        }
        val image = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(9)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFFF0C0B8.toInt())
            if (item.imageUrl.isNotBlank()) ArtLoader.load(this, item.imageUrl)
        }
        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = item.title.ifBlank { "未知曲目" }
            setTextColor(lfmRedDark)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val artist = TextView(this).apply {
            text = item.artist.ifBlank { "未知歌手" }
            setTextColor(muted)
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        meta.addView(title)
        meta.addView(artist)
        val right = TextView(this).apply {
            text = rightLabel
            setTextColor(muted)
            textSize = 11f
        }
        row.addView(image)
        row.addView(meta)
        row.addView(right)
        container.addView(row)
    }

    private fun addArtistRow(container: LinearLayout, rank: Int, name: String, plays: String, imageUrl: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        val rankView = TextView(this).apply {
            text = rank.toString()
            setTextColor(muted)
            textSize = 12f
            width = dp(18)
        }
        val image = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(9) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFFF0C0B8.toInt())
            if (imageUrl.isNotBlank()) ArtLoader.load(this, imageUrl)
        }
        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameView = TextView(this).apply {
            text = name
            setTextColor(lfmRedDark)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val playsView = TextView(this).apply { text = plays; setTextColor(muted); textSize = 12f }
        meta.addView(nameView)
        meta.addView(playsView)
        row.addView(rankView)
        row.addView(image)
        row.addView(meta)
        container.addView(row)
    }

    private fun addAlbumGrid(container: LinearLayout, albums: List<AlbumItem>) {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(4))
        }
        albums.forEach { album ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val image = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFFF0C0B8.toInt())
                if (album.imageUrl.isNotBlank()) ArtLoader.load(this, album.imageUrl)
            }
            val name = TextView(this).apply {
                text = album.name.ifBlank { "未知专辑" }
                setTextColor(lfmRedDark)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val artist = TextView(this).apply {
                text = album.artist.ifBlank { "未知歌手" }
                setTextColor(muted)
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            cell.addView(image)
            cell.addView(name)
            cell.addView(artist)
            grid.addView(cell)
        }
        container.addView(grid)
    }

    private fun checkUpdate() {
        Toast.makeText(this, "开始下载更新…", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val apk = UpdateChecker.downloadApk(cacheDir)
                runOnUiThread { installApk(apk) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "更新失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun installApk(apk: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
