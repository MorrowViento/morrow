package com.jiuxina.ying.service.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M7 更新判定的纯逻辑回归。这些分支错了，表现都是「反复提示装旧包 / 装不上」。 */
class UpdatePolicyTest {

    private fun release(
        code: Int,
        assets: List<UpdateAsset> = listOf(asset("ying-$code-4.0.0-alpha2-universal.apk")),
    ) = ReleaseInfo(
        tag = "v4.0.0-alpha2-$code",
        name = "4.0.0-alpha2 ($code)",
        notes = "",
        publishedAt = "",
        assets = assets,
    )

    private fun asset(name: String = "ying-41-4.0.0-alpha2-universal.apk") = UpdateAsset(
        name = name,
        url = "https://example.test/$name",
        size = 1024L,
    )

    private fun probe(url: String, millis: Long, info: ReleaseInfo?) = ProbeResult(url, millis, info)

    @Test
    fun `版本号按资产名 名称 括号 tag 尾依次解析`() {
        assertEquals(41, UpdatePolicy.versionCode("v4.0.0-alpha2", "whatever", listOf("ying-41-4.0.0-alpha2.apk")))
        assertEquals(42, UpdatePolicy.versionCode("v4.0.0-alpha3", "build 42", emptyList()))
        assertEquals(43, UpdatePolicy.versionCode("v4.0.0", "4.0.0 (43)", emptyList()))
        assertEquals(44, UpdatePolicy.versionCode("v4.0.0-44", "", emptyList()))
        // 解析不出来时宁可判「没有更新」，不能误判成新版本
        assertEquals(0, UpdatePolicy.versionCode("v4.0.0-alpha2", "4.0.0-alpha2", emptyList()))
    }

    @Test
    fun `只有版本号更高且有可装 APK 才算有更新`() {
        assertTrue(UpdatePolicy.isUpdateAvailable(release(41), 40, listOf("arm64-v8a")))
        assertFalse(UpdatePolicy.isUpdateAvailable(release(40), 40, listOf("arm64-v8a")))
        assertFalse(UpdatePolicy.isUpdateAvailable(release(39), 40, listOf("arm64-v8a")))
        assertFalse(UpdatePolicy.isUpdateAvailable(release(41, emptyList()), 40, listOf("arm64-v8a")))
    }

    @Test
    fun `分 ABI 打包时优先设备架构 其次 universal`() {
        val assets = listOf(
            asset("ying-41-arm64-v8a.apk"),
            asset("ying-41-x86_64.apk"),
            asset("ying-41-universal.apk"),
        )
        assertEquals("ying-41-arm64-v8a.apk", UpdatePolicy.selectAsset(assets, listOf("arm64-v8a"))?.name)
        assertEquals("ying-41-universal.apk", UpdatePolicy.selectAsset(assets, emptyList())?.name)
        assertNull(UpdatePolicy.selectAsset(listOf(asset("changelog.md")), listOf("arm64-v8a")))
    }

    @Test
    fun `版本判定取版本号最大的一份 而不是最快的一份`() {
        val oldButFast = release(40)
        val newButSlow = release(41)
        val probes = listOf(
            probe("cdn", 5, oldButFast),
            probe("api", 900, newButSlow),
            probe("broken", 1, null),
        )
        assertEquals(41, UpdatePolicy.newest(probes)?.versionCode)
        assertEquals(40, UpdatePolicy.fastestValid(probes)?.versionCode)
    }

    @Test
    fun `断点续传起点只在文件确实不完整时生效`() {
        assertEquals(0L, UpdatePolicy.resumeOffset(0, 1000))
        assertEquals(400L, UpdatePolicy.resumeOffset(400, 1000))
        assertEquals(0L, UpdatePolicy.resumeOffset(1000, 1000))
        assertEquals(0L, UpdatePolicy.resumeOffset(1200, 1000))
        assertEquals(0L, UpdatePolicy.resumeOffset(400, -1))
    }

    @Test
    fun `sha256 摘要格式与进度换算`() {
        val hex = "a".repeat(64)
        assertEquals(hex, UpdatePolicy.normalizeSha256("sha256:$hex"))
        assertEquals(hex, UpdatePolicy.normalizeSha256(hex))
        assertNull(UpdatePolicy.normalizeSha256("md5:abc"))
        assertNull(UpdatePolicy.normalizeSha256(null))
        assertEquals(50, UpdatePolicy.percent(500, 1000))
        assertEquals(0, UpdatePolicy.percent(10, -1))
        assertEquals("1.0 KB", UpdatePolicy.formatBytes(1024))
        assertEquals("1.0 MB", UpdatePolicy.formatBytes(1024 * 1024))
    }
}
