package com.lastfm.net163

import android.app.AlertDialog
import android.app.ProgressDialog
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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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

    private lateinit var swipe: SwipeRefreshLayout

    private var artistPeriod = "overall"
    private var albumPeriod = "overall"
    private var trackPeriod = "overall"

    private var recentLimit = 5
    private var albumsLimit = 3
    private var tracksLimit = 5

    private var recentItems: List<TrackItem> = emptyList()
    private var artistsItems: List<ArtistItem> = emptyList()
    private var albumsItems: List<AlbumItem> = emptyList()
    private var tracksItems: List<TrackItem> = emptyList()

    private val netease = NetEaseClient()

    private val refreshingSections = mutableSetOf<String>()

    private data class PeriodOption(val label: String, val apiValue: String)

    private val periodOptions = listOf(
        PeriodOption("All Time", "overall"),
        PeriodOption("7天", "7day"),
        PeriodOption("30天", "1month"),
        PeriodOption("90天", "3month"),
        PeriodOption("180天", "6month"),
        PeriodOption("365天", "12month")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = lfmRed
        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        ScrobbleHistory.init(this)

        findViewById<ImageView>(R.id.avatar).setOnClickListener { showAvatarMenu() }
        swipe = findViewById<SwipeRefreshLayout>(R.id.swipe_refresh)
        swipe.setColorSchemeResources(android.R.color.holo_red_light)
        swipe.setOnRefreshListener { reload() }
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
        val header = popup.menu.add("构建 ${BuildConfig.GIT_HASH}")
        header.isEnabled = false
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
        setAvatarPlaceholder(avatar)
        if (prefs.apiKey.isBlank() || prefs.username.isBlank()) {
            return
        }
        thread {
            try {
                val client = LastfmClient(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
                val info = client.getUserInfo(prefs.username)
                runOnUiThread {
                    if (info.imageUrl.isNotBlank()) {
                        ArtLoader.load(avatar, info.imageUrl)
                    }
                }
            } catch (e: Exception) {
                // 占位头像保持显示
            }
        }
    }

    private fun setAvatarPlaceholder(avatar: ImageView) {
        val text = BuildConfig.GIT_HASH.ifBlank { "?" }
        val size = dp(38)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = lfmRed
        paint.textSize = dp(9).toFloat()
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val baseline = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, size / 2f, baseline, paint)
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
            status.text = "已登录 · 同步中"
        }
    }

    private fun reload() {
        swipe.isRefreshing = true
        loadAvatar()
        refreshStatus()
        loadDashboard { swipe.isRefreshing = false }
    }

    private fun loadDashboard(onDone: (() -> Unit)? = null) {
        if (prefs.apiKey.isBlank() || prefs.username.isBlank()) {
            onDone?.invoke()
            return
        }
        thread {
            try {
                val client = LastfmClient(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
                val username = prefs.username
                recentItems = fetchRecent(client, username)
                artistsItems = fetchArtists(client, username)
                albumsItems = fetchAlbums(client, username)
                tracksItems = fetchTracks(client, username)
                runOnUiThread {
                    renderDashboard()
                    onDone?.invoke()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "加载 last.fm 数据失败：${e.message}", Toast.LENGTH_LONG).show()
                    onDone?.invoke()
                }
            }
        }
    }

    private fun refreshRecent() {
        if (prefs.apiKey.isBlank() || prefs.username.isBlank()) return
        refreshingSections.add("recent")
        renderDashboard()
        thread {
            try {
                val client = LastfmClient(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
                recentItems = fetchRecent(client, prefs.username)
                runOnUiThread {
                    refreshingSections.remove("recent")
                    renderDashboard()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    refreshingSections.remove("recent")
                    renderDashboard()
                    Toast.makeText(this, "刷新 Recent Tracks 失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshArtists() {
        if (prefs.apiKey.isBlank() || prefs.username.isBlank()) return
        refreshingSections.add("artists")
        renderDashboard()
        thread {
            try {
                val client = LastfmClient(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
                artistsItems = fetchArtists(client, prefs.username)
                runOnUiThread {
                    refreshingSections.remove("artists")
                    renderDashboard()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    refreshingSections.remove("artists")
                    renderDashboard()
                    Toast.makeText(this, "刷新 Top Artists 失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshAlbums() {
        if (prefs.apiKey.isBlank() || prefs.username.isBlank()) return
        refreshingSections.add("albums")
        renderDashboard()
        thread {
            try {
                val client = LastfmClient(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
                albumsItems = fetchAlbums(client, prefs.username)
                runOnUiThread {
                    refreshingSections.remove("albums")
                    renderDashboard()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    refreshingSections.remove("albums")
                    renderDashboard()
                    Toast.makeText(this, "刷新 Top Albums 失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshTracks() {
        if (prefs.apiKey.isBlank() || prefs.username.isBlank()) return
        refreshingSections.add("tracks")
        renderDashboard()
        thread {
            try {
                val client = LastfmClient(prefs.apiKey, prefs.apiSecret, prefs.sessionKey)
                tracksItems = fetchTracks(client, prefs.username)
                runOnUiThread {
                    refreshingSections.remove("tracks")
                    renderDashboard()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    refreshingSections.remove("tracks")
                    renderDashboard()
                    Toast.makeText(this, "刷新 Top Tracks 失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchRecent(client: LastfmClient, username: String): List<TrackItem> {
        return client.getRecentTracks(username, recentLimit).map { item ->
            val img = netease.searchImageUrl(item.artist, item.title, 1)
                .ifBlank { netease.searchImageUrl("", item.artist, 100) }
            item.copy(imageUrl = img.ifBlank { item.imageUrl })
        }
    }

    private fun fetchArtists(client: LastfmClient, username: String): List<ArtistItem> {
        return client.getTopArtists(username, 5, artistPeriod).map { item ->
            item.copy(imageUrl = netease.searchImageUrl("", item.name, 100).ifBlank { item.imageUrl })
        }
    }

    private fun fetchAlbums(client: LastfmClient, username: String): List<AlbumItem> {
        return client.getTopAlbums(username, albumsLimit, albumPeriod).map { item ->
            val img = netease.searchImageUrl(item.artist, item.name, 10)
                .ifBlank { netease.searchImageUrl("", item.artist, 100) }
            item.copy(imageUrl = img.ifBlank { item.imageUrl })
        }
    }

    private fun fetchTracks(client: LastfmClient, username: String): List<TrackItem> {
        return client.getTopTracks(username, tracksLimit, trackPeriod).map { item ->
            val img = netease.searchImageUrl(item.artist, item.title, 1)
                .ifBlank { netease.searchImageUrl("", item.artist, 100) }
            item.copy(imageUrl = img.ifBlank { item.imageUrl })
        }
    }

    private fun renderDashboard() {
        val container = findViewById<LinearLayout>(R.id.dashboard)
        for (i in container.childCount - 1 downTo 0) {
            container.removeViewAt(i)
        }

        addSection(container, "Recent Tracks", refreshing = "recent" in refreshingSections, onRefresh = { refreshRecent() })
        addCountSelector(container, recentLimit, listOf(5, 10)) { recentLimit = it; refreshRecent() }
        recentItems.forEachIndexed { _, item -> addTrackRow(container, null, item, item.timeLabel) }

        addSection(container, "Top Artists", refreshing = "artists" in refreshingSections)
        addPeriodSelector(container, artistPeriod) { p -> artistPeriod = p; refreshArtists() }
        artistsItems.forEachIndexed { index, item ->
            addArtistRow(container, index + 1, item.name, "${item.scrobbles} scrobbles", item.imageUrl)
        }

        addSection(container, "Top Albums", refreshing = "albums" in refreshingSections)
        addPeriodSelector(container, albumPeriod) { p -> albumPeriod = p; refreshAlbums() }
        addCountSelector(container, albumsLimit, listOf(3, 6)) { albumsLimit = it; refreshAlbums() }
        addAlbumGrid(container, albumsItems)

        addSection(container, "Top Tracks", refreshing = "tracks" in refreshingSections)
        addPeriodSelector(container, trackPeriod) { p -> trackPeriod = p; refreshTracks() }
        addCountSelector(container, tracksLimit, listOf(5, 10)) { tracksLimit = it; refreshTracks() }
        tracksItems.forEachIndexed { index, item ->
            addTrackRow(container, index + 1, item, item.timeLabel)
        }
    }

    private fun addPeriodSelector(
        container: LinearLayout,
        current: String,
        onSelect: (String) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(8))
        }
        periodOptions.forEach { option ->
            val selected = option.apiValue == current
            val chip = TextView(this).apply {
                text = option.label
                textSize = 11f
                setPadding(dp(10), dp(5), dp(10), dp(5))
                setTextColor(if (selected) Color.WHITE else lfmRedDark)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (selected) lfmRed else Color.WHITE)
                    setStroke(dp(1), lfmRed)
                }
                setOnClickListener {
                    if (!selected) onSelect(option.apiValue)
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            row.addView(chip, params)
        }
        container.addView(row)
    }

    private fun addCountSelector(
        container: LinearLayout,
        current: Int,
        options: List<Int>,
        onSelect: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(8))
        }
        options.forEach { option ->
            val selected = option == current
            val chip = TextView(this).apply {
                text = "${option}条"
                textSize = 11f
                setPadding(dp(10), dp(5), dp(10), dp(5))
                setTextColor(if (selected) Color.WHITE else lfmRedDark)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (selected) lfmRed else Color.WHITE)
                    setStroke(dp(1), lfmRed)
                }
                setOnClickListener {
                    if (!selected) onSelect(option)
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            row.addView(chip, params)
        }
        container.addView(row)
    }

    private fun addSection(
        container: LinearLayout,
        title: String,
        refreshing: Boolean = false,
        onRefresh: (() -> Unit)? = null
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(this).apply {
            text = title
            setTextColor(lfmRed)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
            setPadding(0, dp(4), 0, dp(5))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(titleView)
        if (refreshing) {
            val progress = ProgressBar(this).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            row.addView(progress)
        } else if (onRefresh != null) {
            val refreshView = TextView(this).apply {
                text = "刷新"
                setTextColor(lfmRed)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(4), dp(0), dp(5))
                setOnClickListener { onRefresh() }
            }
            row.addView(refreshView)
        }
        container.addView(row)
        val lineView = View(this).apply {
            setBackgroundColor(lfmRed)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)
            )
        }
        container.addView(lineView)
    }

    private fun addTrackRow(
        container: LinearLayout,
        rankOrIndex: Any?,
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
        val dialog = ProgressDialog(this).apply {
            setMessage("正在下载更新，请稍候…")
            setCancelable(false)
            show()
        }
        thread {
            try {
                val apk = UpdateChecker.downloadApk(cacheDir) { downloaded, total ->
                    runOnUiThread {
                        val msg = if (total > 0) {
                            "正在下载更新… ${(downloaded * 100 / total).toInt()}%"
                        } else {
                            "正在下载更新… ${downloaded / 1024} KB"
                        }
                        dialog.setMessage(msg)
                    }
                }
                runOnUiThread {
                    dialog.dismiss()
                    installApk(apk)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "App 内下载失败，已改用浏览器下载", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.APK_URL)))
                }
            }
        }
    }

    private fun installApk(apk: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "打开安装页失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
