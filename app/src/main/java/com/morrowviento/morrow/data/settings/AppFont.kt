package com.morrowviento.morrow.data.settings

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * 应用字体：系统默认，或用户导入到应用私有目录的一款字体文件。
 *
 * 只存「文件名 + 显示名」两个字符串，不存路径——私有目录在重装后会变，
 * 存路径等于把选择绑死在某次安装上。
 */
data class AppFont(
    val fileName: String = "",
    /** 导入时的原始文件名（去掉扩展名），只用于界面展示。 */
    val displayName: String = "",
) {
    val isCustom: Boolean get() = fileName.isNotBlank()

    /** 设置行右侧与字体列表里显示的名字。 */
    val label: String
        get() = if (isCustom) displayName.ifBlank { fileName.removeSuffix(".ttf") } else "系统默认"

    companion object {
        val SYSTEM = AppFont()
    }
}

/**
 * 导入字体的存放位置与读写。
 *
 * 为什么复制进 `filesDir` 而不是记住 SAF 的 `content://`：SAF 授权在重启、
 * 清理缓存、用户移动文件后都可能失效，字体一失效整个界面就掉回默认字体，
 * 用户会以为设置没保存。复制一份（几 MB）换来的是「选了就一直有效」。
 *
 * 文件名带内容哈希前缀：同一款字体导入两次不会堆出两份，不同字体也不会互相覆盖。
 */
object FontStore {
    private const val DIR = "app_fonts"

    /** 单个字体文件上限。中文字体动辄 10~20 MB，给到 32 MB 足够，再大就是选错文件了。 */
    const val MAX_BYTES = 32L * 1024 * 1024

    private val ALLOWED_SUFFIXES = setOf("ttf", "otf", "ttc")

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun fileOf(context: Context, fileName: String): File = File(dir(context), fileName)

    /** 已导入的字体，按显示名排序。 */
    fun installed(context: Context): List<AppFont> {
        val files = dir(context).listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && it.length() > 0 && suffixOf(it.name) in ALLOWED_SUFFIXES }
            .map { AppFont(fileName = it.name, displayName = displayNameOf(it.name)) }
            .sortedBy { it.displayName.lowercase() }
    }

    /**
     * 把用户选中的字体复制进来。
     *
     * 失败一律返回 [Result.failure] 并带可读原因：字体文件被挑错（选了图片）、
     * 文件太大、格式不是 sfnt，这几种在真机上都很常见，静默失败会让人反复重试。
     */
    fun import(context: Context, uri: Uri): Result<AppFont> = runCatching {
        val resolver = context.contentResolver
        val rawName = resolver.displayNameOf(uri) ?: "font"
        val suffix = suffixOf(rawName).ifBlank { "ttf" }
        require(suffix in ALLOWED_SUFFIXES) { "只支持 TTF / OTF / TTC 字体文件" }

        val bytes = (if (uri.scheme == "file") uri.path?.let { File(it).inputStream() } else null)
            ?.let { it.use { stream -> stream.readUpTo() } }
            ?: resolver.openInputStream(uri)?.use { it.readUpTo() }
            ?: error("打不开这个文件")

        require(bytes.isNotEmpty()) { "文件是空的" }
        require(bytes.size <= MAX_BYTES) { "字体文件超过 32 MB，换一款试试" }
        require(looksLikeFont(bytes)) { "这不是有效的字体文件" }

        val display = rawName.substringBeforeLast('.').ifBlank { "自定义字体" }.sanitize()
        val fileName = "font-${bytes.shortHash()}-$display.$suffix"
        val target = fileOf(context, fileName)
        target.writeBytes(bytes)

        // 导入了同名同内容的字体：旧的直接复用，不必留两份
        AppFont(fileName = fileName, displayName = display)
    }

    fun delete(context: Context, fileName: String): Boolean {
        if (fileName.isBlank()) return false
        // 只允许删自己目录下的文件，别把传进来的路径当命令
        val target = File(dir(context), File(fileName).name)
        return target.isFile && target.delete()
    }

    /**
     * 选中的字体还在不在。
     *
     * 私有目录可能被系统清理（低存储）、也可能用户在设置里删掉了这款字体，
     * 读到空壳选择时必须回落系统默认，否则界面上字体选择写着「霞鹜文楷」而实际是默认。
     */
    fun exists(context: Context, fileName: String): Boolean =
        fileName.isNotBlank() && fileOf(context, fileName).let { it.isFile && it.length() > 0 }

    private fun displayNameOf(fileName: String): String {
        // font-<hash8>-<display>.<ext>
        val base = fileName.substringBeforeLast('.')
        val parts = base.split('-', limit = 3)
        return if (parts.size == 3) parts[2] else base
    }

    private fun suffixOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    private fun String.sanitize(): String =
        filter { it.isLetterOrDigit() || it == '-' || it == '_' || it.code > 127 }.take(40)
            .ifBlank { "font" }

    private fun ByteArray.shortHash(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        // Byte 是有符号的，直接 "%02x".format 会打出 ffffff 之类
        return digest.take(4).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /**
     * 读到「上限 + 1」个字节就停。
     *
     * 多读的那一个字节用来判断是否超限，避免把几百 MB 的文件整份读进内存——
     * 用户完全可能误选一个视频。
     */
    private fun java.io.InputStream.readUpTo(): ByteArray {
        val buffer = ByteArray(MAX_BYTES.toInt() + 1)
        var read = 0
        while (read < buffer.size) {
            val n = read(buffer, read, buffer.size - read)
            if (n <= 0) break
            read += n
        }
        return buffer.copyOf(read)
    }

    /**
     * sfnt 魔数判断：TrueType 0x00010000、OpenType 'OTTO'、旧 Mac 'true'、
     * 字体集合 'ttcf'。扩展名可以随便改，头几个字节改不了。
     */
    private fun looksLikeFont(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        fun at(i: Int) = bytes[i].toInt() and 0xFF
        return when {
            at(0) == 0x00 && at(1) == 0x01 && at(2) == 0x00 && at(3) == 0x00 -> true
            at(0) == 0x4F && at(1) == 0x54 && at(2) == 0x54 && at(3) == 0x4F -> true // OTTO
            at(0) == 0x74 && at(1) == 0x72 && at(2) == 0x75 && at(3) == 0x65 -> true // true
            at(0) == 0x74 && at(1) == 0x74 && at(2) == 0x63 && at(3) == 0x66 -> true // ttcf
            else -> false
        }
    }

    /**
     * 取原始文件名。
     *
     * `file://` 直接按路径取最后一段：`Uri.lastPathSegment` 只按 `/` 切，
     * 而 Windows 风格的路径（以及部分文件管理器给的串）里没有 `/`，
     * 会把整条路径当成文件名，显示出来就是一长串 `CUsers...`。
     */
    private fun android.content.ContentResolver.displayNameOf(uri: Uri): String? =
        if (uri.scheme == "file") {
            uri.path?.let { File(it).name }
        } else {
            runCatching {
                query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
            }.getOrNull()
        }
}

/**
 * 推荐字体：只做「下载方式索引」，不随包内置、也不代下。
 *
 * 应用内不内置字体有三个原因：中文字体动辄十几 MB，塞进包体对所有人都是负担；
 * 字体授权必须逐款遵守（OFL 允许随软件分发，但要带许可全文与版权声明）；
 * 用户真正想要的字体往往是自己的，内置几款反而占地方。
 *
 * 这里只列 OFL-1.1 的几款开源字体，点一下跳到项目发布页，由用户自己下载后
 * 回「导入字体文件」——同时也把「以字体自身许可为准」这句话写在界面上。
 */
data class RecommendedFont(
    val name: String,
    val license: String,
    val url: String,
    val note: String,
)

val RECOMMENDED_FONTS: List<RecommendedFont> = listOf(
    RecommendedFont(
        name = "霞鹜文楷",
        license = "OFL-1.1",
        url = "https://github.com/lxgw/LxgwWenKai/releases",
        note = "楷体，正文偏书卷气，字重齐全",
    ),
    RecommendedFont(
        name = "思源黑体",
        license = "OFL-1.1",
        url = "https://github.com/adobe-fonts/source-han-sans/releases",
        note = "中性无衬线，字重多，最接近系统观感",
    ),
    RecommendedFont(
        name = "思源宋体",
        license = "OFL-1.1",
        url = "https://github.com/adobe-fonts/source-han-serif/releases",
        note = "宋体，标题更端正",
    ),
    RecommendedFont(
        name = "JetBrains Mono",
        license = "OFL-1.1",
        url = "https://github.com/JetBrains/JetBrainsMono/releases",
        note = "等宽，数字与英文字形更整齐",
    ),
)
