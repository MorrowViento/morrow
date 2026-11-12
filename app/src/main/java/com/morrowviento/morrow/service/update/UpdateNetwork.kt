package com.morrowviento.morrow.service.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 HTTP 客户端。
 *
 * M7 不为「检查更新 + 下一个 APK」引入 OkHttp/Retrofit：只需要 GET、Range、
 * 超时和重定向跟随，`HttpURLConnection` 全都有，且零依赖、离线构建也能过。
 * 所有调用都在 `Dispatchers.IO`，这里不做线程切换。
 */
object UpdateNetwork {

    private const val USER_AGENT = "Morrow-Updater/1.0 (Android; +https://github.com/MorrowViento/morrow-native)"

    fun open(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMillis: Int = 8_000,
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMillis
        conn.readTimeout = timeoutMillis
        conn.instanceFollowRedirects = true
        // GitHub API 明确要求 User-Agent，缺失直接 403
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/vnd.github+json, application/json, */*")
        headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code: $url")
        }
        return conn
    }

    fun fetch(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = open(url, headers)
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val text = reader.readText()
            conn.disconnect()
            text
        }
    }
}
