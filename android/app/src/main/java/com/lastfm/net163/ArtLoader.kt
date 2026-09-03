package com.lastfm.net163

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object ArtLoader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = HashMap<String, Bitmap>()

    fun load(view: ImageView, url: String) {
        if (url.isBlank()) return
        cache[url]?.let {
            view.setImageBitmap(it)
            return
        }
        view.tag = url
        thread {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val bytes = resp.body?.bytes() ?: return@use
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    cache[url] = bitmap
                    view.post {
                        if (view.tag == url) view.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                // 图片加载失败静默处理，不阻塞主流程
            }
        }
    }
}
