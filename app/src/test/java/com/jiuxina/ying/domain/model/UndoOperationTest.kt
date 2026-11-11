package com.jiuxina.ying.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoOperationTest {

    private fun operation(expiresAt: Long) = UndoOperation(
        id = "u",
        kind = UndoKind.DELETE,
        message = "已删除",
        snapshot = emptyList(),
        expiresAtMillis = expiresAt,
    )

    @Test
    fun `到期判定含边界（等于到期时刻即失效）`() {
        val op = operation(expiresAt = 1_000L)
        assertFalse(op.isExpired(nowMillis = 999L))
        assertTrue(op.isExpired(nowMillis = 1_000L))
        assertTrue(op.isExpired(nowMillis = 1_001L))
    }

    @Test
    fun `剩余毫秒不为负`() {
        assertEquals(0L, operation(expiresAt = 1_000L).remainingMillis(nowMillis = 5_000L))
        assertEquals(500L, operation(expiresAt = 1_000L).remainingMillis(nowMillis = 500L))
    }

    @Test
    fun `剩余秒数向上取整（避免最后不足一秒显示 0）`() {
        val op = operation(expiresAt = 6_000L)
        assertEquals(6, op.remainingSeconds(nowMillis = 0L))
        assertEquals(1, op.remainingSeconds(nowMillis = 5_999L))
        assertEquals(1, op.remainingSeconds(nowMillis = 5_001L))
        assertEquals(0, op.remainingSeconds(nowMillis = 6_000L))
    }
}
