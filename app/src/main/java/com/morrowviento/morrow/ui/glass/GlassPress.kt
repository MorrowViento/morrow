package com.morrowviento.morrow.ui.glass

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.InteractiveHighlight

/**
 * 玻璃按压（Glasmio 0.1.3 的 `InteractiveHighlight`）契约层。
 *
 * **为什么不用 `Modifier.clickable`**：Material 的 ripple 只按**布局边界**裁剪，不认玻璃的
 * 圆角/胶囊——真机上点「排序 · 距离」，胶囊里会冒出一个方形高亮块。这里换成「手势自己处理 +
 * 玻璃自带按压光学」，全程不出现 Material 指示器。
 *
 * 按压表现分三层，叠加起来才是「玻璃被顶起来」的观感：
 * 1. [glassPressHalo]：**元素之外**的一圈放大的白光 + 形状外扩后的亮边（折射圈）；
 * 2. [glassPressFeedback]：轻微放大 + 上浮 + 内侧提亮；
 * 3. Glasmio 的 shader 液体光斑（跟随手指的那团光）。
 *
 * API 36 以下 Glasmio 的 shader 高光会自己跳过（它用 `RuntimeShader`），
 * 前两层是纯 Canvas 绘制，任何机型都有。
 */
class GlassPress internal constructor(private val highlight: InteractiveHighlight) {

    /** 0 → 1 的按压进度。只在绘制阶段读取（graphicsLayer / drawWithContent），不触发重组。 */
    val progress: Float get() = highlight.pressProgress

    /**
     * 手指按下的位置（元素内局部坐标）。
     *
     * 光晕以它为中心扩散才「跟着手指」。进度是动画量，坐标只是按下那一刻的快照，
     * 因此这里存成 State 供绘制阶段读取，不参与重组。
     */
    var center: Offset? by mutableStateOf(null)
        private set

    internal fun press(position: Offset) {
        center = position
        highlight.startPress(position)
    }

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
 * 按压光晕：元素**之外**的一圈大光晕 + 形状外扩后的高光亮边。
 *
 * 观感目标：按下时像有一束光从玻璃底下亮起来，玻璃边缘折射出一圈更亮的轮廓，
 * 整体「透明地浮起来」——而不是 Material 那种把矩形填灰。
 *
 * **必须挂在裁剪之前**（`drawBehind` 早于 `clip`，因此不受元素自身圆角裁剪影响）：
 * 光晕的一半生来就在元素轮廓之外，被裁掉就只剩一条硬边。
 *
 * @param shape 元素形状。轮廓要按它外扩，圆形按钮给 `CircleShape`、胶囊给
 *   `glassPanelShape(capsule = true)`，与玻璃本体一致才不会出现「圆的按钮、方的光圈」。
 * @param spread 按下到底时轮廓外扩的比例。小图标 0.25–0.35，中央「＋」可以到 0.5。
 * @param intensity 整体强度。深色底上白光是主角，浅色底给 0.8 左右避免糊成一片。
 */
fun Modifier.glassPressHalo(
    press: GlassPress,
    shape: Shape,
    spread: Float = 0.3f,
    intensity: Float = 1f,
): Modifier = this.drawBehind {
    val p = press.progress
    if (p <= 0.02f) return@drawBehind

    val origin = press.center ?: center
    val grow = 1f + spread * p
    val a = intensity * p
    // 柔光半径 = 半边长 × 外扩 × 1.35：外扩轮廓之外再留一圈更淡的余辉，
    // 否则光环边界会像刀切。取 minDimension 是为了长条形（底栏）不被拉成椭圆。
    val bloom = size.minDimension * 0.5f * grow * 1.35f

    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.55f to Color.White.copy(alpha = 0.03f * a),
            0.78f to Color.White.copy(alpha = 0.16f * a),
            0.92f to Color.White.copy(alpha = 0.07f * a),
            1f to Color.Transparent,
            center = origin,
            radius = bloom,
        ),
        radius = bloom,
        center = origin,
    )

    // 折射亮边：把形状轮廓按 grow 放大后描一圈。两次描边（粗而淡 + 细而亮）
    // 比单次描边更像玻璃的色散边缘。
    val outlinePath = shape.createOutline(size, layoutDirection, this).toOutlinePath()
    scale(grow, grow, pivot = origin) {
        drawPath(outlinePath, Color.White.copy(alpha = 0.10f * a), style = Stroke(width = 3.2.dp.toPx()))
        drawPath(outlinePath, Color.White.copy(alpha = 0.42f * a), style = Stroke(width = 1.2.dp.toPx()))
    }
}

/** 任意 `Shape` 的轮廓统一转成 Path，避免分别处理 Rectangle / Rounded / Generic 三种描边。 */
private fun Outline.toOutlinePath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

/**
 * 按压视觉：轻微放大 + 上浮 + 内侧提亮（兜底）+ Glasmio 液体高光。
 *
 * @param scale 按下时的额外缩放。列表卡片用 0.012（大块面积放大太明显会晕），
 *   底栏小图标用 0.05。
 * @param lift 按下时整体上浮的距离。0 表示只放大不位移（列表卡片用 0，卡片整体挪动会带偏
 *   相邻卡片的视觉间距）。底栏按钮 1–2dp 就够，多了会像在跳。
 * @param shape 提亮层的裁剪形状。传 null 表示外层已经裁好（例如列表项自己
 *   `clip(RoundedCornerShape(50))`）。圆钮/卡片一定要传，否则白色提亮会在圆角外露出
 *   矩形四角——正是这次要消灭的那个方框。
 */
@Composable
fun Modifier.glassPressFeedback(
    press: GlassPress,
    scale: Float = 0.04f,
    lift: Dp = 0.dp,
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
            translationY = -lift.toPx() * p
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

/**
 * 「点一下」的完整组合：光晕 + 裁剪 + 缩放上浮 + 手势。
 *
 * 日历页这种一次性按钮密集的地方用它，省掉每个按钮都写四行 Modifier。
 * 光晕必须排在最前（未裁剪），顺序错了就只剩硬边。
 */
@Composable
fun Modifier.glassPressClick(
    shape: Shape,
    scale: Float = 0.03f,
    lift: Dp = 1.dp,
    spread: Float = 0.3f,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onClick: () -> Unit,
): Modifier {
    val press = rememberGlassPress()
    return this
        .glassPressHalo(press, shape, spread = spread)
        .glassPressFeedback(press, scale = scale, lift = lift, shape = shape)
        .glassPressable(press, contentDescription = contentDescription, enabled = enabled, onClick = onClick)
}
