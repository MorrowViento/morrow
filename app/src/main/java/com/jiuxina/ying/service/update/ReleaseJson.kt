package com.jiuxina.ying.service.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * GitHub Releases `/releases/latest` 的解析层。
 *
 * 用现成的 Gson（M1 迁移旧 JSON 时已引入），不为此再引 Retrofit / Moshi。
 * 解析失败一律返回 null：**检查更新失败不该影响应用本体**，探路时其它候选地址还能顶上。
 */
object ReleaseJson {

    fun parse(raw: String): ReleaseInfo? {
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JsonParser.parseString(raw).asJsonObject
            val tag = obj.string("tag_name") ?: obj.string("tag") ?: ""
            if (tag.isBlank()) return@runCatching null
            val assets = obj.getAsJsonArray("assets")
                ?.mapNotNull { element ->
                    val a = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val url = a.string("browser_download_url") ?: a.string("url") ?: ""
                    val name = a.string("name") ?: ""
                    if (url.isBlank() || name.isBlank()) return@mapNotNull null
                    UpdateAsset(
                        name = name,
                        url = url,
                        size = a.long("size") ?: 0L,
                        digest = a.string("digest"),
                    )
                }
                .orEmpty()
            ReleaseInfo(
                tag = tag,
                name = obj.string("name") ?: tag,
                notes = obj.string("body") ?: "",
                publishedAt = obj.string("published_at") ?: "",
                assets = assets,
                prerelease = obj.bool("prerelease") ?: false,
            )
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull()
}
