package com.morrowviento.morrow.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 备份文件的读写（SAF URI）。
 *
 * 只碰 `ContentResolver` 的流，不持有 URI 权限、不缓存 File：
 * 用户可能选云盘（Google Drive / 各家网盘）里的文件，那里根本没有「路径」这个概念。
 */
object BackupFiles {

    const val MIME_TYPE = "application/json"

    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /** 建议文件名：`morrow-backup-20261111-1830.json`，多次导出不会互相覆盖。 */
    fun suggestedName(now: LocalDateTime = LocalDateTime.now()): String =
        "morrow-backup-${now.format(FILE_STAMP)}.json"

    /** @throws IOException 读不到（权限被收回、文件被删、云盘离线）时抛出，由调用方转成提示文案。 */
    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.applicationContext.contentResolver
        val text = resolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
        if (text.isNullOrBlank()) throw IOException("文件是空的或无法读取")
        text
    }

    /** @throws IOException 写入失败（空间不足、只读位置）时抛出。 */
    suspend fun write(context: Context, uri: Uri, text: String) {
        withContext(Dispatchers.IO) {
            val resolver = context.applicationContext.contentResolver
            // "wt" 会截断已有文件：覆盖导出时不带它，旧内容比新内容长就会残留尾巴，
            // 文件不再是合法 JSON
            resolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: throw IOException("无法写入所选位置")
        }
    }

    /** 项目主页（关于页跳转）。 */
    fun projectUri(): Uri = "https://github.com/MorrowViento/morrow".toUri()
}
