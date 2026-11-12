package com.jiuxina.ying.service.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GitHub Releases JSON / 仓库清单共用一套解析，容错必须兜住脏数据。 */
class ReleaseJsonTest {

    @Test
    fun `解析标准 Release 载荷`() {
        val raw = """
            {
              "tag_name": "v4.0.0-alpha2-41",
              "name": "4.0.0-alpha2 (41)",
              "body": "修好了一些东西",
              "published_at": "2026-11-12T04:00:00Z",
              "prerelease": true,
              "assets": [
                {
                  "name": "ying-41-4.0.0-alpha2-arm64-v8a.apk",
                  "browser_download_url": "https://github.com/x/y/releases/download/v1/ying.apk",
                  "size": 78000000,
                  "digest": "sha256:${"a".repeat(64)}"
                },
                { "name": "changelog.md", "browser_download_url": "https://example.test/c.md" }
              ]
            }
        """.trimIndent()

        val info = ReleaseJson.parse(raw)
        checkNotNull(info)
        assertEquals("v4.0.0-alpha2-41", info.tag)
        assertEquals(41, info.versionCode)
        assertEquals("修好了一些东西", info.notes)
        assertTrue(info.prerelease)
        assertEquals(2, info.assets.size)
        assertEquals(78_000_000L, info.assets[0].size)
        assertEquals("a".repeat(64), UpdatePolicy.normalizeSha256(info.assets[0].digest))
    }

    @Test
    fun `坏 JSON 缺 tag 空串都返回 null 而不是抛异常`() {
        assertNull(ReleaseJson.parse(""))
        assertNull(ReleaseJson.parse("not json"))
        assertNull(ReleaseJson.parse("{}"))
        assertNull(ReleaseJson.parse("""{"name":"没有 tag"}"""))
    }

    @Test
    fun `缺 url 或名字的资产被跳过`() {
        val raw = """
            {"tag_name":"v1-41","assets":[
              {"name":"a.apk"},
              {"browser_download_url":"https://x/b.apk"},
              {"name":"c.apk","browser_download_url":"https://x/c.apk"}
            ]}
        """.trimIndent()
        val info = ReleaseJson.parse(raw)
        checkNotNull(info)
        assertEquals(1, info.assets.size)
        assertEquals("c.apk", info.assets.single().name)
    }

    @Test
    fun `仓库清单模板也能被同一解析器读出`() {
        val raw = """
            {"tag_name":"v4.0.0-alpha1-40","name":"4.0.0-alpha1 (40)","body":"","published_at":"",
             "prerelease":true,"assets":[]}
        """.trimIndent()
        val info = ReleaseJson.parse(raw)
        checkNotNull(info)
        assertEquals(40, info.versionCode)
        assertTrue(info.assets.isEmpty())
    }
}
