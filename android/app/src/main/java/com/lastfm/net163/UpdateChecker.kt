package com.lastfm.net163

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object UpdateChecker {
    const val APK_URL =
        "https://raw.githubusercontent.com/JamyM0819/lastfm_net163/main/apk/app-debug.apk"

    private val MIRROR_URLS = listOf(
        "https://ghproxy.net/https://raw.githubusercontent.com/JamyM0819/lastfm_net163/main/apk/app-debug.apk",
        "https://gh-proxy.com/https://raw.githubusercontent.com/JamyM0819/lastfm_net163/main/apk/app-debug.apk",
        APK_URL
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    fun downloadApk(
        cacheDir: File,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): File {
        val dir = File(cacheDir, "apk")
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, "app-debug.apk")

        var lastError: Exception? = null
        for (url in MIRROR_URLS) {
            try {
                return downloadFrom(url, out, onProgress)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: RuntimeException("下载失败")
    }

    private fun downloadFrom(
        url: String,
        out: File,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)?
    ): File {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11) lastfm_net163")
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("下载失败：HTTP ${resp.code}")
            }
            val body = resp.body ?: throw RuntimeException("下载失败：空响应")
            val total = body.contentLength()
            var downloaded = 0L
            body.byteStream().use { input ->
                FileOutputStream(out).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress?.invoke(downloaded, total)
                    }
                }
            }
            if (total > 0 && downloaded < total) {
                out.delete()
                throw RuntimeException("下载不完整")
            }
        }
        return out
    }
}
