package com.morrowviento.morrow.service.update

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** 极简 HTTP：调用方在 IO 线程运行；错误和协程取消均释放连接。 */
object UpdateNetwork {
    private const val USER_AGENT = "Morrow-Updater/1.0 (Android; +https://github.com/MorrowViento/morrow)"

    suspend fun <T> withConnection(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMillis: Int = 8_000,
        block: suspend (HttpURLConnection) -> T,
    ): T = coroutineScope {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMillis
        conn.readTimeout = timeoutMillis
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/vnd.github+json, application/json, */*")
        headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
        // HttpURLConnection 的阻塞读不会自行响应 Job 取消；旁路协程负责关闭连接。
        val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                conn.disconnect()
            }
        }
        try {
            coroutineContext.ensureActive()
            conn.connect()
            val code = conn.responseCode
            coroutineContext.ensureActive()
            if (code !in 200..299) throw IOException("HTTP $code: $url")
            block(conn)
        } catch (error: Exception) {
            // disconnect 导致的 IOException 不能掩盖原本的协程取消。
            coroutineContext.ensureActive()
            throw error
        } finally {
            closer.cancel()
            conn.disconnect()
        }
    }

    suspend fun fetch(url: String, headers: Map<String, String> = emptyMap()): String =
        withConnection(url, headers) { conn ->
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8 * 1024)
                val text = StringBuilder()
                while (true) {
                    coroutineContext.ensureActive()
                    val read = reader.read(buffer)
                    if (read < 0) break
                    text.append(buffer, 0, read)
                }
                text.toString()
            }
        }
}
