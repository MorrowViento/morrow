package com.morrowviento.morrow.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

/**
 * 底部弹层的统一入口（新建 / 编辑 / 详情 / 分类 / 提醒 / 更新 / 许可）。
 *
 * ## 为什么不再用 `GlasmioBottomSheet`
 *
 * 库的底部弹层是一个真正的 `Dialog`，玻璃效果在两处同时失效，而且都改不动：
 *
 * 1. **材质写死成不透明的那一档**。`GlasmioSheetSurface` 内部固定传
 *    `material = NavigationGlassMaterial.RegularLarge`，这一档的 `fills`
 *    （深色下三层 #1A1A1A，其中一层是 `Lighten`，把比 26/255 暗的像素全抬到 26/255）
 *    会把整块面板压成一片均匀实色；展开到全屏时 `vibrancy = 0`，连那层半透明的
 *    内容底也退成不透明的 `#1C1C1E`。观感就是一块哑光黑板——「弹窗没有玻璃效果」。
 * 2. **采样源要跨窗口**。弹层在主窗口之外，得先把主窗口光栅化成位图再采样
 *    （`rememberCrossWindowBackdrop`），多一次整窗录制的开销，折射出来的还只是
 *    主窗口那一层的壁纸，和眼前的内容对不上。
 *
 * 现在：**弹层就是同页浮层**，用和卡片完全相同的 [GlassPanel]（Clear 材质、跟随厚度
 * 变化的模糊半径、同一份色纱）。于是弹层与卡片是同一块玻璃、同一个采样源；
 * 厚度滑块对弹层同样生效；主题切换也即时生效（同在一条组合里，不需要跨窗口通信）。
 *
 * 交互照样保留：遮罩点击关闭、返回键关闭、抽屉式下拉关闭（手势只挂在顶部抓手上，
 * 不与内部滚动抢手势）。呈现曲线用 [GlassMotion.sheet]（UIKit 的 presenting 曲线，
 * 与库同源）。
 *
 * 关闭动效：面板由调用方按需挂载（`form != null` 时才调用），若这里直接回调
 * `onDismiss`，浮层会被立刻移除、看不到收起动画。因此内容拿到的是 [content] 里的
 * `dismiss` 回调：它把进度退到 0，动画结束后再回调真正的 `onDismiss`。
 */
@Composable
fun GlassSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 内容自己决定高度（许可 / 更新这类短面板）；默认铺到 96% 高。 */
    fitContent: Boolean = false,
    /** 下载 / 安装这类进行中的流程不允许手势或返回键关掉。 */
    dismissible: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 0 = 完全收起，1 = 完全展开。位移与遮罩浓度都由它推出来。
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var panelHeightPx by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) { progress.animateTo(1f, GlassMotion.sheet) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        progress.animateTo(0f, tween(190))
        onDismiss()
    }

    BackHandler(enabled = dismissible && !closing) { closing = true }

    // 下拉关闭：位移直接折算成进度；松手时按「拖过 28% 或甩得够快」判定，
    // 没过阈值就弹回去。手势只挂在抓手上——面板内部是纵向滚动，
    // 整块接管会把滚动吃掉。
    val dragState = rememberDraggableState { delta ->
        scope.launch {
            progress.snapTo((progress.value + delta / panelHeightPx).coerceIn(0f, 1f))
        }
    }

    Box(modifier.fillMaxSize()) {
        // 遮罩：压暗程度跟着进度走，松手回弹时也跟着回。
        // 不用 Material 的 Scrim 颜色——它带一层灰，会把玻璃透出来的暖色压掉。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black.copy(alpha = 0.26f))
                .then(
                    if (dismissible) {
                        Modifier.pointerInput(closing) {
                            detectTapGestures { if (!closing) closing = true }
                        }
                    } else {
                        Modifier
                    },
                ),
        )

        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (fitContent) Modifier.wrapContentHeight() else Modifier.fillMaxHeight(0.96f))
                // 吞掉落在面板空白处的点击：否则事件会穿透到下层遮罩，「点面板」等于「点外面」。
                // 面板里的控件（滚动、chips、输入框）先命中，不受影响。
                .pointerInput(Unit) { detectTapGestures { } }
                .graphicsLayer {
                    // 收起时整块面板降到屏幕外；拖动时跟着手指走
                    translationY = (1f - progress.value) * size.height
                },
            cornerRadius = SheetCorner,
        ) {
            // 抓手：既是「可以往下拉」的视觉提示，也是唯一的手势入口
            val grabber: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .then(
                            if (dismissible) {
                                Modifier.draggable(
                                    state = dragState,
                                    orientation = Orientation.Vertical,
                                    onDragStopped = { velocity ->
                                        // 往上甩（velocity < 0）不关；往下甩或拖过 28% 才关
                                        if (progress.value < 0.72f || velocity > 900f) {
                                            closing = true
                                        } else {
                                            progress.animateTo(
                                                1f,
                                                spring(dampingRatio = 0.86f, stiffness = 500f),
                                            )
                                        }
                                    },
                                )
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(52.dp)
                            .height(4.dp)
                            .background(glassTextTertiary().copy(alpha = 0.55f), Capsule()),
                    )
                }
            }
            val measured: Modifier = Modifier.onSizeChanged {
                if (it.height > 0) panelHeightPx = it.height.toFloat()
            }
            if (fitContent) {
                Column(Modifier.fillMaxWidth().then(measured)) {
                    grabber()
                    content { if (!closing) closing = true }
                }
            } else {
                Column(Modifier.fillMaxSize().then(measured)) {
                    grabber()
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        content { if (!closing) closing = true }
                    }
                }
            }
        }
    }
}

/** 弹层面板的圆角：比卡片（26dp）更圆，iOS 的 sheet 是 34~38dp。 */
private val SheetCorner = 36.dp

/**
 * 弹层里的标题行（取消 / 标题 / 确认）。
 *
 * 只是一行文字按钮，不单独铺一条工具条：面板本身就是玻璃，再加一条底会把玻璃切开。
 */
@Composable
fun SheetHeader(
    title: String,
    onCancel: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SheetTextButton(text = "取消", onClick = onCancel)
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
        )
        if (onConfirm != null && confirmLabel != null) {
            SheetTextButton(text = confirmLabel, emphasized = true, onClick = onConfirm)
        }
    }
}

/**
 * 弹层内的文字按钮（取消 / 保存 / 完成）。
 *
 * iOS 的文字按钮按下时会浮起一小块玻璃，而不是只变个色。这里就做这一下：
 * 胶囊底在按下时淡入、松手淡出，同时缩到 0.94。
 * 强调按钮（保存）平时就带一层强调色，否则「主操作」和「取消」看起来一样重
 * ——这也是之前「选中效果不明显」的一半原因。
 */
@Composable
fun SheetTextButton(
    text: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    val primary = glassTextPrimary()
    val accent = glassAccent()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 1200f, visibilityThreshold = 0.01f),
        label = "sheetTextPress",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                val scale = 1f - 0.06f * press
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (emphasized) {
                    accent.copy(alpha = 0.16f + 0.08f * press)
                } else {
                    primary.copy(alpha = 0.10f * press)
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                color = if (emphasized) primary else glassTextSecondary(),
            ),
        )
    }
}
