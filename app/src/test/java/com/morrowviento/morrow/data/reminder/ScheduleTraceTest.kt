package com.morrowviento.morrow.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「最近一次排期走哪条通道」的编解码。
 *
 * 这段格式写坏不会崩，只会让诊断页安静地显示「没有记录」——比崩更难发现，
 * 所以三条边界（正常、字段缺失、枚举不认识）都锁一遍。
 */
class ScheduleTraceTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `编解码往返不丢字段`() {
        val trace = ScheduleTrace(SchedulePath.EXACT, now + 300_000L, now)
        assertEquals(trace, ScheduleTraceCodec.decode(ScheduleTraceCodec.encode(trace)))
    }

    @Test
    fun `三种通道都能解回自己`() {
        SchedulePath.entries.forEach { path ->
            val trace = ScheduleTrace(path, now + 1, now)
            assertEquals(path, ScheduleTraceCodec.decode(ScheduleTraceCodec.encode(trace))?.path)
        }
    }

    @Test
    fun `没有记录时返回 null 而不是抛异常`() {
        assertNull(ScheduleTraceCodec.decode(null))
        assertNull(ScheduleTraceCodec.decode(""))
    }

    @Test
    fun `字段缺失或损坏按无记录处理`() {
        assertNull(ScheduleTraceCodec.decode("ALARM_CLOCK|1700000000000"))
        assertNull(ScheduleTraceCodec.decode("ALARM_CLOCK|not_a_number|1700000000000"))
        assertNull(ScheduleTraceCodec.decode("ALARM_CLOCK|1700000000000|not_a_number"))
    }

    /** 旧版本 / 新版本枚举不一致时不能让诊断页炸掉，直接当作没记录。 */
    @Test
    fun `未知通道按无记录处理`() {
        assertNull(ScheduleTraceCodec.decode("WORK_MANAGER|1700000000000|1700000000000"))
    }
}
