package com.jiuxina.ying.service.update

/**
 * 一次 GitHub Release 里我们需要的部分。
 *
 * 刻意不把 GitHub 的原始 JSON 结构渗进 UI：解析层把字段摊平到这里，
 * 版本判断、选包、下载、安装只认这几个字段。
 */
data class UpdateAsset(
    val name: String,
    val url: String,
    val size: Long,
    /** 形如 `sha256:abcdef…`；Release 资产没有这个字段，保留扩展位。 */
    val digest: String? = null,
)

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val publishedAt: String,
    val assets: List<UpdateAsset>,
    val prerelease: Boolean = false,
) {
    /** 版本号的判定完全交给 [UpdatePolicy]，这里只做展示。 */
    val versionCode: Int get() = UpdatePolicy.versionCode(tag, name, assets.map { it.name })

    val versionLabel: String
        get() = name.trim().removeSuffix("#$versionCode").trim().ifBlank { tag }.ifBlank { "新版本" }
}

/** 一个候选地址的探测结果。失败时 [release] 为 null，只保留耗时用于排障。 */
data class ProbeResult(
    val url: String,
    val elapsedMillis: Long,
    val release: ReleaseInfo? = null,
    val error: String? = null,
)
