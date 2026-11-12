package com.morrowviento.morrow.data

import com.morrowviento.morrow.data.legacy.LegacyEventParser
import com.morrowviento.morrow.data.legacy.LegacyPrefsSplitter
import com.morrowviento.morrow.data.settings.LegacySettingsKeys
import com.morrowviento.morrow.domain.model.CountDirection
import com.morrowviento.morrow.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LegacyMigrationTest {

    private val sample = """
        [
          {"id":"e1","title":"生日","targetDate":"2026-12-25T08:30:00.000","category":"纪念",
           "note":"记得买蛋糕","icon":"cake","direction":"countup",
           "reminders":[{"id":"r1","minutesBefore":60,"enabled":true},{"id":"r2","minutesBefore":1440}],
           "isAllDay":false,"isCompleted":false,"isPinned":true,
           "repeatType":"yearly","repeatMonth":12,"repeatDay":25,
           "createdAt":"2024-01-01T10:00:00.000"},
          {"id":"e2","title":"旧版事件","targetDate":"2026-11-20T00:00:00.000",
           "reminderMinutes":30,"createdAt":"2024-01-01T10:00:00.000"},
          {"title":"缺少 id"},
          {"id":"e4","title":"坏日期","targetDate":"not-a-date","createdAt":"2024-01-01T10:00:00.000"}
        ]
    """.trimIndent()

    @Test
    fun `完整字段解析`() {
        val result = LegacyEventParser.parse(sample)
        assertEquals(2, result.events.size)
        // 缺失 id 与坏日期各计一次失败
        assertEquals(2, result.failed)

        val e1 = result.events.first { it.id == "e1" }
        assertEquals("生日", e1.title)
        assertEquals("纪念", e1.category)
        assertEquals("记得买蛋糕", e1.note)
        assertEquals("cake", e1.icon)
        assertEquals(CountDirection.COUNTUP, e1.direction)
        assertTrue(e1.isPinned)
        assertEquals(RepeatType.YEARLY, e1.repeatType)
        assertEquals(12, e1.repeatMonth)
        assertEquals(25, e1.repeatDay)
        assertEquals(LocalDateTime.of(2026, 12, 25, 8, 30), e1.targetDate)
        assertEquals(LocalDateTime.of(2024, 1, 1, 10, 0), e1.createdAt)
        assertEquals(listOf(1440, 60), e1.reminders.map { it.minutesBefore })
    }

    @Test
    fun `缺省字段使用默认值`() {
        val result = LegacyEventParser.parse(sample)
        val e2 = result.events.first { it.id == "e2" }
        assertEquals("生活", e2.category)
        assertEquals("", e2.note)
        assertEquals(CountDirection.AUTO, e2.direction)
        assertEquals(RepeatType.NONE, e2.repeatType)
        assertNull(e2.repeatMonth)
        assertNull(e2.repeatDay)
    }

    @Test
    fun `旧版单提醒字段转为提醒列表`() {
        val result = LegacyEventParser.parse(sample)
        val e2 = result.events.first { it.id == "e2" }
        assertEquals(1, e2.reminders.size)
        assertEquals(30, e2.reminders.first().minutesBefore)
        assertEquals("legacy-30", e2.reminders.first().id)
        assertTrue(e2.reminders.first().enabled)
    }

    @Test
    fun `空值与非法 JSON 安全返回`() {
        assertEquals(0, LegacyEventParser.parse(null).events.size)
        assertEquals(0, LegacyEventParser.parse("").events.size)
        assertEquals(0, LegacyEventParser.parse("{not json").events.size)
        assertEquals(0, LegacyEventParser.parse("{\"a\":1}").events.size)
    }

    @Test
    fun `UTC 时间也能解析`() {
        val json = """[{"id":"u1","title":"UTC","targetDate":"2026-11-20T02:00:00.000Z","createdAt":"2024-01-01T00:00:00.000Z"}]"""
        val result = LegacyEventParser.parse(json)
        assertEquals(1, result.events.size)
        // 转为本地时刻，具体小时数依赖运行环境时区，只校验非空与日期可解析
        assertTrue(result.events.first().targetDate.year == 2026)
    }

    @Test
    fun `偏好项按前缀拆分`() {
        val raw = mapOf<String, Any?>(
            "flutter.countdown_events_v1" to "[{\"id\":\"x\",\"title\":\"t\",\"targetDate\":\"2026-01-01T00:00:00.000\",\"createdAt\":\"2026-01-01T00:00:00.000\"}]",
            "flutter.theme_mode" to "dark",
            "flutter.widget_show_note" to true,
            "flutter.widget_font_scale" to 1.2f,
            "flutter.widget_color" to 0xFF00FF00.toInt(),
            "android_something" to "ignore",
            "flutter.null_key" to null,
        )
        val split = LegacyPrefsSplitter.split(raw)
        assertEquals("dark", split.settings[LegacySettingsKeys.THEME_MODE])
        assertEquals(true, split.settings["widget_show_note"])
        assertEquals(1.2f, split.settings["widget_font_scale"])
        assertEquals(0xFF00FF00.toInt(), split.settings["widget_color"])
        assertTrue(split.settings.keys.none { it.startsWith("flutter.") })
        assertTrue(split.eventsJson!!.startsWith("["))
        assertEquals(2, split.ignored.size)
    }
}
