package com.morrowviento.morrow.ui.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.neoruaa.glasmio.glass.LiquidContactCapsule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 平滑并集几何的回归测试。
 *
 * 这组数对应底栏的真实几何（62 高的胶囊、58 的圆球、k = 10dp），单位取 px：
 * 关键结论是「连通发生在 gap < k/2」，也就是 [GlassBottomBar] 里切换
 * 「两块独立玻璃 / 一块并集玻璃」的依据。如果这里的判定跟库的 SDF 走偏，
 * 屏幕上就会在贴合段出现两个轮廓（融合处的分界线、圆球里的胶囊圆弧）。
 */
class MergedCapsuleUnionTest {

    private val barHeight = 62f
    private val ball = 58f
    private val k = 10f

    private fun capsules(gap: Float, ballDy: Float = 0f): Pair<LiquidContactCapsule, LiquidContactCapsule> {
        val pillRight = 410f
        val top = 4f
        val pill = LiquidContactCapsule(
            size = Size(pillRight, barHeight),
            bounds = Rect(0f, top, pillRight, top + barHeight),
        )
        val ballLeft = pillRight + gap
        val ballTop = top + (barHeight - ball) / 2f + ballDy
        val circle = LiquidContactCapsule(
            size = Size(ball, ball),
            bounds = Rect(ballLeft, ballTop, ballLeft + ball, ballTop + ball),
        )
        return pill to circle
    }

    private fun connected(gap: Float): Boolean {
        val (pill, circle) = capsules(gap)
        val union = MergedCapsuleUnion.create(pill, circle, k)
        assertNotNull("合法几何应能建立并集", union)
        return union!!.isConnected
    }

    @Test
    fun `常态 12dp 缝是分离的`() {
        // 底栏常态就是 12dp 的缝，此时必须画两块独立玻璃。
        assertFalse(connected(12f))
    }

    @Test
    fun `贴合到 k 的一半以内就连成一片`() {
        assertTrue("拖到 4px 缝时应已连通", connected(4f))
        assertTrue("压到胶囊上（负缝）当然连通", connected(-8f))
    }

    @Test
    fun `连通阈值与 smoothness 成比例`() {
        val (pill, circle) = capsules(6f)
        val wide = MergedCapsuleUnion.create(pill, circle, 24f)
        val narrow = MergedCapsuleUnion.create(pill, circle, 4f)
        assertNotNull(wide)
        assertNotNull(narrow)
        // k 越大越早黏上：同一 6px 缝，k=24 已连通，k=4 不连通。
        assertTrue(wide!!.isConnected)
        assertFalse(narrow!!.isConnected)
    }

    @Test
    fun `非法几何返回 null，由调用方退化`() {
        val (pill, circle) = capsules(4f)
        assertNull(MergedCapsuleUnion.create(pill, circle, 0f))
        assertNull(MergedCapsuleUnion.create(pill, circle, Float.NaN))
        assertNull(
            MergedCapsuleUnion.create(
                pill,
                LiquidContactCapsule(Size(0f, 0f), Rect(500f, 0f, 500f, 0f)),
                k,
            ),
        )
        // 两片垂直错开超过 1px：库同样拒绝，避免把不在同一基线上的控件并起来。
        val (offsetPill, offsetBall) = capsules(4f, ballDy = 6f)
        assertNull(MergedCapsuleUnion.create(offsetPill, offsetBall, k))
    }

    @Test
    fun `并集只比两片的外接框略大一点`() {
        val (pill, circle) = capsules(4f)
        val union = MergedCapsuleUnion.create(pill, circle, k)!!
        // bounds 是两片的外接框；颈部只在这之内鼓出，不会把整条栏撑大。
        assertTrue(union.bounds.left <= pill.bounds.left + 0.01f)
        assertTrue(union.bounds.right >= circle.bounds.right - 0.01f)
        assertTrue(union.bounds.height <= barHeight + 0.01f)
    }
}
