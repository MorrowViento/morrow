package com.morrowviento.morrow.service.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * APK 下载：断点续传 + 线路回退 + 完整性校验。
 *
 * 不引 WorkManager：更新下载由用户在设置页**主动发起且全程看着进度**，进程被杀后
 * 没必要后台续跑；协程取消即停，下次进来靠 `.part` 文件续传即可。
 * （TODO 里 WorkManager 的「不需要」结论证的是精确闹钟，这里同样不引入。）
 */
class ApkDownloader(private val context: Context) {

    suspend fun download(
        asset: UpdateAsset,
        onProgress: (done: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val finalFile = File(dir, APK_NAME)
        val partFile = File(dir, PART_NAME)
        var lastError: Throwable? = null

        for (url in UpdateEndpoints.downloadUrls(asset.url)) {
            try {
                return@withContext downloadFrom(url, asset, partFile, finalFile, onProgress)
            } catch (error: Exception) {
                lastError = error
                // 换线路后不能接着上一家的分片续传：不同代理返回的分片未必一致，拼起来会坏包
                partFile.delete()
            }
        }
        throw lastError ?: IOException("没有可用的下载地址")
    }

    private suspend fun downloadFrom(
        url: String,
        asset: UpdateAsset,
        partFile: File,
        finalFile: File,
        onProgress: (done: Long, total: Long) -> Unit,
    ): File {
        val existing = if (partFile.exists()) partFile.length() else 0L
        var offset = UpdatePolicy.resumeOffset(existing, asset.size)
        val conn = UpdateNetwork.open(
            url,
            headers = if (offset > 0) mapOf("Range" to "bytes=$offset-") else emptyMap(),
            timeoutMillis = 20_000,
        )
        // 服务端忽略 Range 会回 200 并把整个文件重发：此时必须从头写，不能 append
        val rangeIgnored = offset > 0 && conn.responseCode == HttpURLConnection.HTTP_OK
        if (rangeIgnored) offset = 0

        val contentRange = conn.getHeaderField("Content-Range")
        val total = asset.size.takeIf { it > 0 }
            ?: contentRange?.substringAfterLast('/')?.toLongOrNull()?.takeIf { it > 0 }
            ?: conn.contentLengthLong.takeIf { it > 0 }?.plus(offset)?.takeIf { it > 0 }
            ?: -1L

        conn.inputStream.use { input ->
            FileOutputStream(partFile, !rangeIgnored && offset > 0).use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = offset
                var sinceNotify = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    sinceNotify += read
                    if (sinceNotify >= 256 * 1024 || total < 0) {
                        onProgress(done, total)
                        sinceNotify = 0
                    }
                }
                output.flush()
                onProgress(done, total)
            }
        }
        conn.disconnect()

        if (total > 0 && partFile.length() != total) {
            throw IOException("下载不完整：${partFile.length()} / $total 字节")
        }
        val expected = UpdatePolicy.normalizeSha256(asset.digest)
        if (expected != null && !sha256(partFile).equals(expected, ignoreCase = true)) {
            partFile.delete()
            throw IOException("APK 校验失败，已丢弃下载文件")
        }
        if (finalFile.exists()) finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
            throw IOException("无法保存 APK")
        }
        return finalFile
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val APK_NAME = "morrow-update.apk"
        const val PART_NAME = "morrow-update.apk.part"
    }
}
