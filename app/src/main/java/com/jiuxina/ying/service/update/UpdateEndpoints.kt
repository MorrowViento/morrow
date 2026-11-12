package com.jiuxina.ying.service.update

import com.jiuxina.ying.BuildConfig

/**
 * 检查地址与下载地址的候选集。
 *
 * **为什么不止一个检查地址**：`api.github.com` 在部分网络下会超时，而仓库里的
 * `update/manifest.json` 走 jsDelivr / raw CDN 通常可达。三路并发探测，谁先成功就用谁；
 * 版本判定取三份里**版本号最大**的那份（见 `UpdatePolicy.newest`），避免 CDN 缓存旧清单
 * 把用户困在旧版本上。
 *
 * **镜像前缀**从 `gradle.properties` 的 `ying.updateMirrors` 注入（分号分隔，形如
 * `https://ghproxy.net/;https://gh-proxy.com/`），默认空 = 只走直连。
 * 不把第三方代理写死在代码里：这类域名寿命短，写死会变成「上线即失效」；
 * 要开镜像，构建时填一行配置即可，测速选路机制会同时作用于检查与下载。
 */
object UpdateEndpoints {

    private const val REPO = "MorrowViento/ying-native"

    const val RELEASES_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CDN_MANIFEST = "https://cdn.jsdelivr.net/gh/$REPO@main/update/manifest.json"
    private const val RAW_MANIFEST = "https://raw.githubusercontent.com/$REPO/main/update/manifest.json"

    private val mirrorPrefixes: List<String>
        get() = BuildConfig.UPDATE_MIRROR_PREFIXES
            .split(';')
            .map { it.trim() }
            .filter { it.startsWith("https://", ignoreCase = true) }

    /** 检查用的全部候选地址：直连 API + 两个 CDN 清单 + 可选镜像。 */
    fun checkUrls(): List<String> {
        val direct = listOf(RELEASES_API, CDN_MANIFEST, RAW_MANIFEST)
        return (direct + mirrorPrefixes.map { it + RELEASES_API }).distinct()
    }

    /** 下载用的全部候选地址：原始 asset + 可选镜像。 */
    fun downloadUrls(assetUrl: String): List<String> =
        (listOf(assetUrl) + mirrorPrefixes.map { it + assetUrl }).distinct()
}
