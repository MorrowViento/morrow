package com.morrowviento.morrow.data.backup

import com.morrowviento.morrow.domain.model.CountDirection
import com.morrowviento.morrow.domain.model.CountdownEvent
import com.morrowviento.morrow.domain.model.EventReminder
import com.morrowviento.morrow.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BackupCodecTest {

    private fun event(
        id: String = "e1",
        title: String = "生日",
        target: LocalDateTime = LocalDateTime.of(2027, 3, 8, 0, 0),
    ) = CountdownEvent(
        id = id,
        title = title,
        targetDate = target,
        category = "生活",
        note = "买蛋糕\n别忘了蜡烛",
        icon = "cake",
        direction = CountDirection.COUNTDOWN,
        reminders = listOf(
            EventReminder(id = "r1", minutesBefore = 1440, enabled = true),
            EventReminder(id = "r2", minutesBefore = 0, enabled = false),
        ),
        isAllDay = true,
        isCompleted = false,
        isPinned = true,
        repeatType = RepeatType.YEARLY,
        repeatMonth = 3,
        repeatDay = 8,
        createdAt = LocalDateTime.of(2026, 1, 2, 9, 30),
    )

    private fun document(
        events: List<CountdownEvent> = listOf(event()),
        categories: List<String> = listOf("生活", "工作"),
        widgets: List<BackupWidget> = listOf(BackupWidget(widgetId = 7, style = """{"preset":"STANDARD"}""")),
    ) = BackupDocument(
        exportedAt = "2026-11-11T18:30",
        appVersion = "4.0.0-alpha1 (40)",
        events = events.map(BackupMapper::of),
        categories = categories,
        settings = BackupSettings(
            themeMode = "dark",
            sortMode = "targetDate",
            reduceTransparency = true,
            reduceMotion = false,
        ),
        widgets = widgets,
    )

    // ---------------------------------------------------------------- 往返

    @Test
    fun `编码后解码应完整还原事件`() {
        val parsed = BackupCodec.decode(BackupCodec.encode(document()))
        assertTrue(parsed is BackupParse.Ok)
        val ok = parsed as BackupParse.Ok

        assertEquals(0, ok.skippedEvents)
        assertEquals(1, ok.document.events.size)
        assertEquals("2026-11-11T18:30", ok.document.exportedAt)
        assertEquals("4.0.0-alpha1 (40)", ok.document.appVersion)

        val restored = BackupMapper.toDomain(ok.document.events.single())
        assertNotNull(restored)
        restored!!
        assertEquals("e1", restored.id)
        assertEquals("生日", restored.title)
        assertEquals(LocalDateTime.of(2027, 3, 8, 0, 0), restored.targetDate)
        assertEquals("生活", restored.category)
        // 备注里的换行不能被转义吃掉
        assertEquals("买蛋糕\n别忘了蜡烛", restored.note)
        assertEquals(CountDirection.COUNTDOWN, restored.direction)
        assertEquals(RepeatType.YEARLY, restored.repeatType)
        assertEquals(3, restored.repeatMonth)
        assertEquals(8, restored.repeatDay)
        assertTrue(restored.isAllDay)
        assertTrue(restored.isPinned)
        assertEquals(2, restored.reminders.size)
        // 提醒按分钟降序保存，解码后顺序不变
        assertEquals(1440, restored.reminders.first().minutesBefore)
        assertEquals(LocalDateTime.of(2026, 1, 2, 9, 30), restored.createdAt)
    }

    @Test
    fun `分类与小部件样式原样往返`() {
        val ok = BackupCodec.decode(BackupCodec.encode(document())) as BackupParse.Ok
        assertEquals(listOf("生活", "工作"), ok.document.categories)
        assertEquals(7, ok.document.widgets.single().widgetId)
        assertEquals("""{"preset":"STANDARD"}""", ok.document.widgets.single().style)
    }

    @Test
    fun `设置原样往返`() {
        val ok = BackupCodec.decode(BackupCodec.encode(document())) as BackupParse.Ok
        val settings = BackupMapper.toSettings(ok.document.settings)
        assertEquals("dark", settings.themeMode.prefValue)
        assertEquals("targetDate", settings.sortMode.prefValue)
        assertTrue(settings.reduceTransparency)
        assertTrue(!settings.reduceMotion)
    }

    // ---------------------------------------------------------------- 容错

    @Test
    fun `非 JSON 与错误格式都给出可读原因`() {
        assertTrue(BackupCodec.decode("not json") is BackupParse.Failure)
        assertTrue(BackupCodec.decode("[]") is BackupParse.Failure)
        assertTrue(BackupCodec.decode("""{"format":"other","version":1}""") is BackupParse.Failure)
    }

    @Test
    fun `版本高于当前时拒绝而不是猜测`() {
        val raw = """{"format":"morrow-backup","version":99,"events":[]}"""
        val failure = BackupCodec.decode(raw) as BackupParse.Failure
        assertTrue(failure.reason.contains("更新版本"))
    }

    @Test
    fun `缺版本号视为损坏`() {
        assertTrue(BackupCodec.decode("""{"format":"morrow-backup"}""") is BackupParse.Failure)
    }

    @Test
    fun `坏事件被跳过并计数，其余照常导入`() {
        val raw = """
            {
              "format": "morrow-backup",
              "version": 1,
              "events": [
                {"id": "ok", "title": "好的", "targetDate": "2027-05-01T00:00"},
                {"title": "缺 id", "targetDate": "2027-05-01T00:00"},
                {"id": "bad-time", "title": "时间坏了", "targetDate": "不是时间"},
                {"id": "no-target", "title": "缺目标"}
              ]
            }
        """.trimIndent()
        val ok = BackupCodec.decode(raw) as BackupParse.Ok
        assertEquals(3, ok.skippedEvents)
        assertEquals(1, ok.document.events.size)
        assertEquals("ok", ok.document.events.single().id)
    }

    @Test
    fun `缺字段的事件用默认值补全而不是丢弃`() {
        val raw = """
            {"format":"morrow-backup","version":1,
             "events":[{"id":"e","targetDate":"2027-05-01T08:00"}]}
        """.trimIndent()
        val ok = BackupCodec.decode(raw) as BackupParse.Ok
        assertEquals(0, ok.skippedEvents)
        val restored = BackupMapper.toDomain(ok.document.events.single())!!
        assertEquals(CountdownEvent.DEFAULT_CATEGORY, restored.category)
        assertEquals(CountDirection.AUTO, restored.direction)
        assertEquals(RepeatType.NONE, restored.repeatType)
        assertEquals(0, restored.reminders.size)
        // createdAt 缺失时用目标时间兜底，避免进 Room 时是 1970
        assertEquals(restored.targetDate, restored.createdAt)
    }

    @Test
    fun `settings 与 categories 缺失时给出安全默认`() {
        val ok = BackupCodec.decode("""{"format":"morrow-backup","version":1}""") as BackupParse.Ok
        assertEquals(0, ok.document.events.size)
        assertEquals(0, ok.document.categories.size)
        assertEquals("system", ok.document.settings.themeMode)
        assertEquals("distance", ok.document.settings.sortMode)
        assertTrue(!ok.document.settings.reduceTransparency)
    }

    @Test
    fun `提醒超量或负数会被清洗`() {
        val raw = """
            {"format":"morrow-backup","version":1,
             "events":[{"id":"e","targetDate":"2027-05-01T08:00",
               "reminders":[
                 {"minutesBefore":-5,"enabled":true},
                 {"minutesBefore":0},{"minutesBefore":10},{"minutesBefore":20},
                 {"minutesBefore":30},{"minutesBefore":40},{"minutesBefore":50}
               ]}]}
        """.trimIndent()
        val ok = BackupCodec.decode(raw) as BackupParse.Ok
        val restored = BackupMapper.toDomain(ok.document.events.single())!!
        assertEquals(EventReminder.MAX_COUNT, restored.reminders.size)
        assertTrue(restored.reminders.none { it.minutesBefore < 0 })
        // 降序：最新的在前
        assertEquals(50, restored.reminders.first().minutesBefore)
    }

    // ---------------------------------------------------------------- 时间

    @Test
    fun `时间同时兼容 ISO 字符串与毫秒时间戳`() {
        val iso = BackupCodec.parseTime("2027-03-08T09:30")
        assertEquals(LocalDateTime.of(2027, 3, 8, 9, 30), iso)

        // 2027-03-08T09:30 在本机时区的毫秒值
        val millis = LocalDateTime.of(2027, 3, 8, 9, 30)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals(LocalDateTime.of(2027, 3, 8, 9, 30), BackupCodec.parseTime(millis.toString()))

        assertNull(BackupCodec.parseTime(""))
        assertNull(BackupCodec.parseTime("2027/03/08"))
        assertNull(BackupCodec.parseTime(null))
    }

    @Test
    fun `无法解析目标时间的事件不会进入导入`() {
        val backup = BackupEvent(
            id = "e",
            title = "坏时间",
            targetDate = "2027/03/08",
            category = "生活",
            note = "",
            icon = "",
            direction = "AUTO",
            reminders = emptyList(),
            isAllDay = false,
            isCompleted = false,
            isPinned = false,
            repeatType = "NONE",
            repeatMonth = null,
            repeatDay = null,
            createdAt = "2027/03/08",
        )
        assertNull(BackupMapper.toDomain(backup))
    }

    @Test
    fun `未知枚举值回落默认而不是崩溃`() {
        val raw = """
            {"format":"morrow-backup","version":1,
             "events":[{"id":"e","targetDate":"2027-05-01T08:00",
               "direction":"SIDEWAYS","repeatType":"WEEKLY"}]}
        """.trimIndent()
        val ok = BackupCodec.decode(raw) as BackupParse.Ok
        val restored = BackupMapper.toDomain(ok.document.events.single())!!
        assertEquals(CountDirection.AUTO, restored.direction)
        assertEquals(RepeatType.NONE, restored.repeatType)
    }

    @Test
    fun `空备份也能导出与导入`() {
        val raw = BackupCodec.encode(document(events = emptyList(), categories = emptyList(), widgets = emptyList()))
        val ok = BackupCodec.decode(raw) as BackupParse.Ok
        assertEquals(0, ok.document.events.size)
        assertEquals(0, ok.document.widgets.size)
    }

    @Test
    fun `建议文件名带时间戳且是 json`() {
        val name = BackupFiles.suggestedName(LocalDateTime.of(2026, 11, 11, 18, 30))
        assertEquals("morrow-backup-20261111-1830.json", name)
    }
}
