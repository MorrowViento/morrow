package com.morrowviento.morrow.service.update

import kotlin.math.roundToInt

/**
 * 更新判定与选包的**纯逻辑**，不碰网络、不碰 Android API，因此可以直接跑 JVM 单测。
 *
 * 版本号的约定（按优先级取第一个命中的）：
 * 1. 资产名 `morrow-<code>-<…>.apk`（推荐，CI 打包时天然带得上）；
 * 2. Release 名称里的 `#40` / `build 40` / `(40)`；
 * 3. tag 结尾的 `-40`，例如 `v4.0.0-alpha2-40`。
 *
 * 取不到 code 时返回 0 —— 宁可判成「没有更新」，也不能因为解析错把旧版本当新版本
 * 反复提示安装（降级安装会被系统直接拒绝，用户体验更差）。
 */
object UpdatePolicy {

    private val ASSET_CODE = Regex("""(?:^|[-_])morrow[-_](\d{1,9})(?:[-_.]|$)""", RegexOption.IGNORE_CASE)
    private val BUILD_CODE = Regex("""(?:#|build[-_]?)\s*(\d{1,9})\b""", RegexOption.IGNORE_CASE)
    private val PAREN_CODE = Regex("""\((\d{1,9})\)""")
    private val TAG_TAIL_CODE = Regex("""-(\d{1,9})$""")

    fun versionCode(tag: String, name: String, assetNames: List<String>): Int =
        assetNames.firstNotNullOfOrNull { ASSET_CODE.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: BUILD_CODE.find(name)?.groupValues?.get(1)?.toIntOrNull()
            ?: PAREN_CODE.find(name)?.groupValues?.get(1)?.toIntOrNull()
            ?: TAG_TAIL_CODE.find(tag)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0

    /** 有更新 = 版本号更高，且确实有一个能装到这台机器上的 APK。 */
    fun isUpdateAvailable(release: ReleaseInfo, localCode: Int, supportedAbis: List<String>): Boolean =
        release.versionCode > localCode && selectAsset(release.assets, supportedAbis) != null

    /**
     * 按设备 ABI 选包：优先第一个匹配的分包，其次 universal，最后随便一个 apk。
     *
     * 分 ABI 打包后一个 Release 会有好几份 APK，选错架构安装会直接失败；
     * 只有 universal 时也能正常回落。
     */
    fun selectAsset(assets: List<UpdateAsset>, supportedAbis: List<String>): UpdateAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) && it.url.isNotBlank() }
        if (apks.isEmpty()) return null
        for (abi in supportedAbis) {
            apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        return apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: apks.firstOrNull()
    }

    /** 多地址测速：只看成功的，取耗时最短的那份（用于挑下载线路、写日志）。 */
    fun fastestValid(results: List<ProbeResult>): ReleaseInfo? =
        results.asSequence()
            .filter { it.release != null }
            .minByOrNull { it.elapsedMillis }
            ?.release

    /**
     * 版本判定取**版本号最大**的一份，不是最快的一份：CDN 清单可能有缓存，
     * 只按速度选会把用户困在旧版本；同版本号时才让更快的那份胜出。
     */
    fun newest(results: List<ProbeResult>): ReleaseInfo? =
        results.filter { it.release != null }
            .sortedWith(
                compareByDescending<ProbeResult> { it.release!!.versionCode }
                    .thenBy { it.elapsedMillis },
            )
            .firstOrNull()
            ?.release

    /**
     * 断点续传的起点。文件损坏 / 服务端换了包 / 已下载完时一律从头下，
     * 否则会把两份不同的 APK 拼在一起。
     */
    fun resumeOffset(existingBytes: Long, totalBytes: Long): Long = when {
        totalBytes <= 0L -> 0L
        existingBytes <= 0L -> 0L
        existingBytes >= totalBytes -> 0L
        else -> existingBytes
    }

    /** GitHub 资产的 digest 形如 `sha256:abc…`；没有 / 格式不对就不校验。 */
    fun normalizeSha256(digest: String?): String? {
        val raw = digest?.trim()?.substringAfter(':')?.takeIf { it.length == 64 } ?: return null
        return raw.takeIf { it.all { ch -> ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F' } }
    }

    /** 下载进度文案用。 */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    fun percent(done: Long, total: Long): Int =
        if (total <= 0L) 0 else ((done * 100.0 / total).coerceIn(0.0, 100.0)).roundToInt()
}
