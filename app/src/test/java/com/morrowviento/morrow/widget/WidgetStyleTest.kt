package com.morrowviento.morrow.widget

import com.morrowviento.morrow.data.settings.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStyleTest {

    private fun colors(
        style: WidgetStyle,
        systemDark: Boolean = false,
        accent: AccentColor = AccentColor.DEFAULT,
    ) = style.colors(systemDark, accent)

    // ---- 预设 ----

    @Test
    fun `预设决定行数与该预设的默认显隐`() {
        assertEquals(1, WidgetPreset.MINIMAL.rows)
        assertEquals(3, WidgetPreset.STANDARD.rows)
        assertEquals(5, WidgetPreset.DETAILED.rows)

        assertFalse(WidgetPreset.MINIMAL.defaultShowCategory)
        assertTrue(WidgetPreset.STANDARD.defaultShowDate)
        assertTrue(WidgetPreset.DETAILED.defaultShowProgress)
    }

    @Test
    fun `切预设时显隐回到新预设的默认`() {
        // 用户先手动关掉日期，再切到详细：日期应重新打开，而不是保留"极简"的关
        val style = WidgetStyle()
            .copy(showDate = false)
            .withPreset(WidgetPreset.DETAILED)

        assertEquals(WidgetPreset.DETAILED, style.preset)
        assertEquals(5, style.rows)
        assertTrue(style.showDate)
        assertTrue(style.showProgress)
    }

    @Test
    fun `默认样式是标准预设`() {
        val style = WidgetStyle()
        assertEquals(WidgetPreset.STANDARD, style.preset)
        assertEquals(3, style.rows)
        assertEquals(WidgetFontScale.MEDIUM, style.fontScale)
        assertEquals(WidgetPalette.SYSTEM, style.palette)
    }

    // ---- 配色 ----

    @Test
    fun `跟随系统时按系统深浅切明暗`() {
        val style = WidgetStyle(palette = WidgetPalette.SYSTEM)
        assertTrue(colors(style, systemDark = true).dark)
        assertFalse(colors(style, systemDark = false).dark)
        assertEquals(WidgetStyle.WHITE, colors(style, systemDark = true).textPrimary)
        assertEquals(WidgetStyle.INK, colors(style, systemDark = false).textPrimary)
        // 跟随系统不等于「主题色」档，底色仍是中性玻璃
        assertFalse(colors(style, systemDark = true).vivid)
    }

    @Test
    fun `固定深色与固定浅色不受系统影响`() {
        assertTrue(colors(WidgetStyle(palette = WidgetPalette.DARK), systemDark = false).dark)
        assertFalse(colors(WidgetStyle(palette = WidgetPalette.LIGHT), systemDark = true).dark)
    }

    @Test
    fun `主题色档跟随系统明暗并用当前配色的强调色`() {
        val style = WidgetStyle(palette = WidgetPalette.ACCENT)
        assertTrue(colors(style, systemDark = true).vivid)
        assertFalse(colors(style, systemDark = false).dark)

        // 强调色必须是用户选的那一套，而不是写死的蓝
        val jade = colors(style, systemDark = false, accent = AccentColor.JADE)
        assertEquals(AccentColor.JADE.light.toInt(), jade.accent)
        val rose = colors(style, systemDark = true, accent = AccentColor.ROSE)
        assertEquals(AccentColor.ROSE.dark.toInt(), rose.accent)
    }

    // ---- 编解码 ----

    @Test
    fun `编解码往返保持所有字段`() {
        val style = WidgetStyle(
            preset = WidgetPreset.DETAILED,
            fontScale = WidgetFontScale.LARGE,
            palette = WidgetPalette.ACCENT,
            showCategory = false,
            showDate = true,
            showProgress = true,
            eventId = "evt-1",
        )
        assertEquals(style, WidgetStyleCodec.decode(WidgetStyleCodec.encode(style)))
    }

    @Test
    fun `空内容与非法内容返回 null 由调用方回落默认`() {
        assertNull(WidgetStyleCodec.decode(null))
        assertNull(WidgetStyleCodec.decode(""))
        assertNull(WidgetStyleCodec.decode("不是 JSON"))
        assertNull(WidgetStyleCodec.decode("[1,2,3]"))
    }

    @Test
    fun `未知枚举名与缺失字段各自回落 不影响其他字段`() {
        val style = WidgetStyleCodec.decode(
            """{"preset":"FUTURE_PRESET","font":"LARGE","palette":"DARK"}""",
        )
        assertEquals(WidgetPreset.STANDARD, style!!.preset)
        assertEquals(WidgetFontScale.LARGE, style.fontScale)
        assertEquals(WidgetPalette.DARK, style.palette)
        // 显隐缺失 → 按回落后的预设默认
        assertTrue(style.showDate)
        assertFalse(style.showProgress)
    }

    @Test
    fun `显隐字段缺失时按预设默认而不是 false`() {
        val style = WidgetStyleCodec.decode("""{"preset":"DETAILED"}""")!!
        assertTrue(style.showCategory)
        assertTrue(style.showDate)
        assertTrue(style.showProgress)
    }

    @Test
    fun `未绑定事件时 eventId 为 null`() {
        val decoded = WidgetStyleCodec.decode(WidgetStyleCodec.encode(WidgetStyle()))!!
        assertNull(decoded.eventId)
    }

    // ---- 旧配置映射 ----

    @Test
    fun `旧键可识别时映射到预设与字号`() {
        val style = WidgetStyleCodec.fromLegacy(
            mapOf(
                "widget_style_preset" to "minimal",
                "widget_font_scale" to "large",
                "widget_theme" to "accent",
            ),
        )
        assertEquals(WidgetPreset.MINIMAL, style.preset)
        assertEquals(WidgetFontScale.LARGE, style.fontScale)
        assertEquals(WidgetPalette.ACCENT, style.palette)
        // 极简预设的默认显隐
        assertFalse(style.showCategory)
        assertEquals(1, style.rows)
    }

    @Test
    fun `旧键认不出来时回落默认 不清空也不抛错`() {
        val style = WidgetStyleCodec.fromLegacy(mapOf("widget_style_preset" to "什么鬼", "widget_theme" to null))
        assertEquals(WidgetPreset.STANDARD, style.preset)
        assertEquals(WidgetFontScale.MEDIUM, style.fontScale)
        assertEquals(WidgetPalette.SYSTEM, style.palette)
    }
}
