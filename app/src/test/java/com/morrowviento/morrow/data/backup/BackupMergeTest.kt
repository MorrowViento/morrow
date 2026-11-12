package com.morrowviento.morrow.data.backup

import com.morrowviento.morrow.data.settings.AppSettings
import com.morrowviento.morrow.data.settings.ThemeMode
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BackupMergeTest {

    private fun event(id: String, title: String = id) = CountdownEvent(
        id = id,
        title = title,
        targetDate = LocalDateTime.of(2027, 1, 1, 0, 0),
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
    )

    @Test
    fun `合并时同 id 用备份覆盖且保持本机位置`() {
        val current = listOf(event("a", "本机A"), event("b", "本机B"))
        val incoming = listOf(event("b", "备份B"), event("c", "备份C"))

        val merged = BackupMerge.events(current, incoming)

        // a 在前、b 就地替换、c 追加：顺序稳定，用户不会看到列表整体重排
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("本机A", merged.first { it.id == "a" }.title)
        assertEquals("备份B", merged.first { it.id == "b" }.title)
    }

    @Test
    fun `合并空备份等于不改动`() {
        val current = listOf(event("a"))
        assertSame(current, BackupMerge.events(current, emptyList()))
    }

    @Test
    fun `覆盖模式由调用方先清空，这里只保证不重复`() {
        val merged = BackupMerge.events(emptyList(), listOf(event("a"), event("a")))
        assertEquals(1, merged.size)
    }

    @Test
    fun `分类取并集并保持本机顺序`() {
        val merged = BackupMerge.categories(listOf("生活", "工作"), listOf("工作", "旅行"))
        assertEquals(listOf("生活", "工作", "旅行"), merged)
    }

    @Test
    fun `分类空备份不改动`() {
        val current = listOf("生活")
        assertSame(current, BackupMerge.categories(current, emptyList()))
    }
}

class AppSettingsTest {

    @Test
    fun `主题为跟随系统时由系统深浅决定`() {
        val settings = AppSettings(themeMode = ThemeMode.SYSTEM)
        assertTrue(settings.isDark(systemDark = true))
        assertFalse(settings.isDark(systemDark = false))
    }

    @Test
    fun `强制浅色与强制深色不受系统影响`() {
        assertFalse(AppSettings(themeMode = ThemeMode.LIGHT).isDark(systemDark = true))
        assertTrue(AppSettings(themeMode = ThemeMode.DARK).isDark(systemDark = false))
    }

    @Test
    fun `主题取值与旧版 Dart 枚举名一致`() {
        assertEquals("system", ThemeMode.SYSTEM.prefValue)
        assertEquals("light", ThemeMode.LIGHT.prefValue)
        assertEquals("dark", ThemeMode.DARK.prefValue)
    }

    @Test
    fun `认不出的主题取值回落跟随系统`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref("midnight"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPref("dark"))
    }

    @Test
    fun `默认设置是跟随系统加距离排序且不减弱任何效果`() {
        val defaults = AppSettings()
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(SortMode.DISTANCE, defaults.sortMode)
        assertFalse(defaults.reduceTransparency)
        assertFalse(defaults.reduceMotion)
    }
}
