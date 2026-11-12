package com.morrowviento.morrow.ui.glass

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.github.neoruaa.glasmio.glass.InteractiveHighlight

/**
 * 玻璃按压（Glasmio 0.1.3 的 `InteractiveHighlight`）契约层。
 *
 * **为什么不用 `Modifier.clickable`**：Material 的 ripple 只按**布局边界**裁剪，不认玻璃的
 * 圆角/胶囊——真机上点「排序 · 距离」，胶囊里会冒出一个方形高亮块。这里换成「手势自己处理 +
 * 玻璃自带按压光学」，全程不出现 Material 指示器。
 *
 * API 36 以下 Glasmio 的 shader 高光会自己跳过（它用 `RuntimeShader`），所以
 * [glassPressFeedback] 另加一层很淡的白色提亮兜底，保证任何机型按下去都有反应。
 */
class GlassPress internal constructor(private val highlight: InteractiveHighlight) {

    /** 0 → 1 的按压进度。只在绘制阶段读取（graphicsLayer / drawWithContent），不触发重组。 */
    val progress: Float get() = highlight.pressProgress

    internal fun press(position: Offset) = highlight.startPress(position)

    internal fun release() = highlight.endPress()

    /** Glasmio 原生按压高光（跟随手指的液体光斑）。 */
    internal val glowModifier: Modifier get() = highlight.modifier
}

/** 每个可按元素各自持有一份：高光按元素边界裁剪，不会漏到相邻按钮上。 */
@Composable
fun rememberGlassPress(): GlassPress {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassPress(InteractiveHighlight(scope)) }
}

/**
 * 可点击的玻璃元素。
 *
 * 用手势而不是 `clickable`：既避开矩形 ripple，又能拿到按下的**坐标**喂给高光。
 * `tryAwaitRelease()` 返回 false（滑走 / 被上层浮层抢走）时不触发点击，与 `clickable` 一致，
 * 列表滚动时不会误点。
 *
 * 回调走 `rememberUpdatedState`：首页倒计时每秒重组一次，若把 lambda 当 `pointerInput` 的 key，
 * 手指按住的那一秒手势会被重启，按压光效直接断掉。
 */
@Composable
fun Modifier.glassPressable(
    press: GlassPress,
    contentDescription: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    return this
        .semantics {
            role = Role.Button
            contentDescription?.let { this.contentDescription = it }
            onClick {
                if (enabled) currentOnClick()
                enabled
            }
        }
        .pointerInput(press, enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = { position ->
                    press.press(position)
                    val released = tryAwaitRelease()
                    press.release()
                    if (released) currentOnClick()
                },
            )
        }
}

/**
 * 按压视觉：轻微放大 + 白色提亮（兜底）+ Glasmio 液体高光。
 *
 * @param scale 按下时的额外缩放。列表卡片用 0.012（大块面积放大太明显会晕），
 *   底栏小图标用 0.06。
 * @param shape 提亮层的裁剪形状。传 null 表示外层已经裁好（例如列表项自己
 *   `clip(RoundedCornerShape(50))`）。圆钮/卡片一定要传，否则白色提亮会在圆角外露出
 *   矩形四角——正是这次要消灭的那个方框。
 */
@Composable
fun Modifier.glassPressFeedback(
    press: GlassPress,
    scale: Float = 0.04f,
    shape: Shape? = null,
): Modifier {
    val reduceMotion = LocalReduceMotion.current
    val clipped = if (shape != null) this.clip(shape) else this
    return clipped
        .graphicsLayer {
            val p = if (reduceMotion) 0f else press.progress
            val s = 1f + scale * p
            scaleX = s
            scaleY = s
        }
        .drawWithContent {
            drawContent()
            val p = press.progress
            if (p > 0.01f) {
                // API 36+ 时这层与 Glasmio 的 shader 高光叠加，取很低 alpha 才不糊成一片白。
                drawRect(Color.White.copy(alpha = 0.10f * p))
            }
        }
        .then(if (reduceMotion) Modifier else press.glowModifier)
}
