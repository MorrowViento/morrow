package com.morrowviento.morrow.service.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** 用户主动下载：资产隔离、可信断点续传、线路回退与完整性校验。 */
class ApkDownloader(
    private val context: Context,
    private val downloadUrls: (String) -> List<String> = UpdateEndpoints::downloadUrls,
) {
    private val downloadMutex = Mutex()

    suspend fun download(asset: UpdateAsset, onProgress: (done: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            downloadMutex.withLock {
                val dir = File(context.cacheDir, "updates")
                if (!dir.isDirectory && !dir.mkdirs()) throw DiskFailure(IOException("无法创建下载目录"))
                val expected = UpdatePolicy.normalizeSha256(asset.digest)
                val key = hash("${asset.url}\n${asset.name}\n${asset.size}\n$expected".toByteArray())
                val finalFile = File(dir, "morrow-update-$key.apk")
                var lastError: IOException? = null
                for (url in downloadUrls(asset.url)) {
                    coroutineContext.ensureActive()
                    // 无摘要时，每条线路有独立分片；不能把不同镜像的内容拼起来。
                    val sourceKey = if (expected == null) "-${hash(url.toByteArray())}" else ""
                    val partFile = File(dir, "morrow-update-$key$sourceKey.apk.part")
                    try {
                        if (expected != null && partFile.isFile &&
                            (asset.size <= 0 || partFile.length() == asset.size) && sha256(partFile) == expected.lowercase()) {
                            onProgress(partFile.length(), partFile.length())
                            return@withLock publish(partFile, finalFile)
                        }
                        downloadFrom(url, asset, expected, partFile, onProgress)
                        return@withLock publish(partFile, finalFile)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (disk: DiskFailure) {
                        throw disk
                    } catch (protocol: ProtocolFailure) {
                        discard(partFile)
                        lastError = protocol
                    } catch (network: IOException) {
                        coroutineContext.ensureActive()
                        // 网络断开/短读保留已验证起点的分片，下次从同一资产接着下。
                        lastError = network
                    }
                }
                throw lastError ?: IOException("没有可用的下载地址")
            }
        }

    private suspend fun downloadFrom(
        url: String,
        asset: UpdateAsset,
        expected: String?,
        partFile: File,
        onProgress: (Long, Long) -> Unit,
    ) {
        var offset = UpdatePolicy.resumeOffset(partFile.length(), asset.size)
        UpdateNetwork.withConnection(
            url,
            headers = buildMap {
                put("Accept-Encoding", "identity")
                if (offset > 0L) put("Range", "bytes=$offset-")
            },
            timeoutMillis = 20_000,
        ) { conn ->
            val total: Long
            val responseLength: Long
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    offset = 0L // 200 忽略 Range：必须从头覆盖，而非追加。
                    responseLength = conn.contentLengthLong
                    total = asset.size.takeIf { it > 0L } ?: responseLength
                    if (asset.size > 0L && responseLength >= 0L && responseLength != asset.size) {
                        throw ProtocolFailure("下载长度与资产不一致")
                    }
                }
                HttpURLConnection.HTTP_PARTIAL -> {
                    val match = CONTENT_RANGE.matchEntire(conn.getHeaderField("Content-Range").orEmpty())
                        ?: throw ProtocolFailure("无效的 Content-Range")
                    val start = match.groupValues[1].toLongOrNull() ?: throw ProtocolFailure("无效的 Range 起点")
                    val end = match.groupValues[2].toLongOrNull() ?: throw ProtocolFailure("无效的 Range 终点")
                    total = match.groupValues[3].toLongOrNull() ?: throw ProtocolFailure("无效的 Range 总长")
                    if (start != offset || end < start || end >= total ||
                        (asset.size > 0L && total != asset.size)) throw ProtocolFailure("续传范围与资产不一致")
                    responseLength = end - start + 1L
                    if (conn.contentLengthLong >= 0L && conn.contentLengthLong != responseLength) {
                        throw ProtocolFailure("续传响应长度不一致")
                    }
                }
                else -> throw ProtocolFailure("无法下载：HTTP ${conn.responseCode}")
            }
            if (!conn.getHeaderField("Content-Encoding").isNullOrBlank() &&
                !conn.getHeaderField("Content-Encoding").equals("identity", ignoreCase = true)) {
                throw ProtocolFailure("下载响应使用了非预期压缩")
            }
            conn.inputStream.use { input ->
                disk { FileOutputStream(partFile, offset > 0L) }.useDisk { output ->
                    var done = offset
                    var received = 0L
                    var sinceNotify = 0L
                    val buffer = ByteArray(64 * 1024)
                    onProgress(done, total)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        coroutineContext.ensureActive()
                        if (read < 0) break
                        if ((total > 0L && read > total - done) ||
                            (responseLength >= 0L && read > responseLength - received)) {
                            throw ProtocolFailure("下载内容超出声明长度")
                        }
                        disk { output.write(buffer, 0, read) }
                        done += read
                        received += read
                        sinceNotify += read
                        if (sinceNotify >= 256 * 1024 || total <= 0L) {
                            onProgress(done, total)
                            sinceNotify = 0L
                        }
                    }
                    disk { output.flush() }
                    onProgress(done, total)
                    if ((total > 0L && done != total) || (responseLength >= 0L && received != responseLength)) {
                        throw IOException("下载不完整：$done / $total 字节")
                    }
                }
            }
        }
        if (partFile.length() == 0L) throw ProtocolFailure("下载内容为空")
        if (expected != null && sha256(partFile) != expected.lowercase()) {
            throw ProtocolFailure("APK 校验失败，已丢弃下载文件")
        }
    }

    private fun publish(part: File, finalFile: File): File = disk {
        Files.move(part.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        finalFile
    }

    private fun discard(file: File) {
        if (file.exists() && !file.delete()) throw DiskFailure(IOException("无法清理损坏分片"))
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        disk { file.inputStream() }.useDisk { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val read = disk { input.read(buffer) }
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private inline fun <T> disk(block: () -> T): T = try {
        block()
    } catch (error: IOException) {
        throw DiskFailure(error)
    }

    /** 本地 close 失败同样阻止换镜像；原有取消始终优先传播。 */
    private inline fun <T : java.io.Closeable, R> T.useDisk(block: (T) -> R): R {
        var failure: Throwable? = null
        try {
            return block(this)
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                disk { close() }
            } catch (closeError: DiskFailure) {
                if (failure is CancellationException) {
                    failure.addSuppressed(closeError)
                } else {
                    if (failure != null) closeError.addSuppressed(failure)
                    throw closeError
                }
            }
        }
    }

    private class DiskFailure(cause: IOException) : IOException("本地文件操作失败：${cause.message}", cause)
    private class ProtocolFailure(message: String) : IOException(message)
    private companion object {
        val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}
