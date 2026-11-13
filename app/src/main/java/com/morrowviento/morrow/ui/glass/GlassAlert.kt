package com.morrowviento.morrow.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 弹窗按钮的圆角：13dp，比面板（30dp）硬一档，才有「按得动」的手感。 */
private val AlertButtonShape = RoundedCornerShape(13.dp)

/** 弹窗按钮的语义角色（决定颜色与粗细）。 */
enum class GlassAlertRole { Default, Cancel, Destructive }

/**
 * 一个按钮。[enabled] 用于「标题没填就不能保存」这类校验。
 */
data class GlassAlertButton(
    val label: String,
    val onClick: () -> Unit,
    val role: GlassAlertRole = GlassAlertRole.Default,
    val enabled: Boolean = true,
)

/**
 * 玻璃确认弹窗（清空全部、导入确认、退出确认）。
 *
 * ## 为什么不用 `GlasmioAlertDialog`
 *
 * 它是个 `Dialog`，材质固定为 `NavigationGlassMaterial.RegularMedium`——和底部弹层
 * 同一个问题：这一档的 `fills` 是不透明的，弹窗看起来像一块实心板，既不跟随玻璃厚度，
 * 也没法在主题切换时立即变色（材质配方缓存在 remember 里）。
 *
 * 现在是一块居中的 [GlassPanel]：和卡片同一档 Clear 材质、同一个采样源、同一个色纱。
 * 呈现动效按 UIKit 的 alert 走：背景压暗 + 0.92 → 1 的缩放 + 淡入，退出反向。
 *
 * 保持挂载、只翻转 [visible]，关闭动画才播得完。按钮按 iOS 规则排布：
 * [stacked] 为真时纵向堆叠，否则并排（两个并排、第三个换行由调用方决定）。
 */
@Composable
fun GlassAlert(
    visible: Boolean,
    title: String,
    message: String? = null,
    onDismiss: () -> Unit,
    buttons: List<GlassAlertButton>,
    stacked: Boolean = false,
) {
    // 关闭动画期间仍要能画：AnimatedVisibility 自己保留最后一帧内容，无需额外快照
    Box(Modifier.fillMaxSize()) {
        // 遮罩：弹窗不做下拉手势，遮罩本身就是「点外面取消」
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(GlassMotion.scrim),
            exit = fadeOut(GlassMotion.scrim),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(GlassMotion.scrim) +
                scaleIn(GlassMotion.sheet, initialScale = 1.08f),
            exit = fadeOut(GlassMotion.scrim) +
                scaleOut(GlassMotion.sheet, targetScale = 0.96f),
        ) {
            GlassPanel(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .width(300.dp)
                    // 同上：面板空白处的点击不能穿透到遮罩，否则点弹窗就等于点「取消」
                    .pointerInput(Unit) { detectTapGestures { } },
                cornerRadius = 30.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = glassTextPrimary(),
                        ),
                    )
                    message?.let {
                        Text(
                            text = it,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                            style = TextStyle(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = glassTextSecondary(),
                            ),
                        )
                    }
                    if (buttons.isNotEmpty()) {
                        if (stacked) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                buttons.forEach { AlertButton(it, Modifier.fillMaxWidth()) }
                            }
                        } else {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                buttons.forEach { AlertButton(it, Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
    BackHandler(enabled = visible) { onDismiss() }
}

/**
 * 弹窗里的一颗按钮。
 *
 * 破坏性操作（清空 / 删除）走系统的红，取消加粗——这两条是 iOS 的固定语义，
 * 不跟强调色走，否则「清空全部」会和普通按钮长得一样。
 */
@Composable
private fun AlertButton(button: GlassAlertButton, modifier: Modifier = Modifier) {
    val accent = glassAccent()
    val destructive = glassColors().destructive
    val ink = when {
        button.role == GlassAlertRole.Destructive -> destructive
        button.role == GlassAlertRole.Cancel -> glassTextPrimary()
        else -> accent
    }
    val fill = when (button.role) {
        GlassAlertRole.Destructive -> destructive.copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .background(fill, AlertButtonShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = button.enabled,
                onClick = button.onClick,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = button.label,
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = if (button.role == GlassAlertRole.Cancel) FontWeight.SemiBold else FontWeight.Medium,
                color = if (button.enabled) ink else ink.copy(alpha = 0.4f),
            ),
        )
    }
}
