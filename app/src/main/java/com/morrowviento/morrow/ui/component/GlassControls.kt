package com.morrowviento.morrow.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary

/**
 * 玻璃风基础控件集合（表单浮层与分类管理浮层共用）。
 *
 * 统一用 BasicTextField + 自绘圆角底，而不是 Material 组件：
 * MaterialTheme 的默认色在深色玻璃上不可读，且会带入与玻璃材质冲突的阴影/描边。
 */

/** 玻璃风输入框。 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 46.dp,
    error: String? = null,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(primary.copy(alpha = 0.06f))
                .heightIn(min = minHeight)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = TextStyle(fontSize = 15.sp, color = secondary.copy(alpha = 0.7f)),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(fontSize = 15.sp, color = primary),
                cursorBrush = SolidColor(primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = TextStyle(fontSize = 11.sp, color = Color(0xFFFF6B6B)))
        }
    }
}

/**
 * 可选中的胶囊标签（多选语义，例如「全天 / 每年重复 / 显示元素」）。
 *
 * 按下去的反馈照 Glasmio 的玻璃按钮做，但**不套一层 `GlassSurface`**：编辑面板里
 * 这样的 chip 一次能看到十几颗，每颗都开一次材质采样与折射，滚动表单会明显掉帧。
 * 这里只取它那两下「可感知」的部分——缩小 + 边缘高光 + 底色加深，用同一条弹簧：
 * 压下 0.94、松手弹回（`dampingRatio = 0.55` 会有一点点回弹，和库的按压曲线一致）。
 *
 * 不用默认 ripple：它只按布局边界裁，会在圆角胶囊外面留一圈方块（与底栏那次同一个坑）。
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 1200f, visibilityThreshold = 0.01f),
        label = "chipPress",
    )
    val base = if (selected) 0.16f else 0.06f
    Box(
        modifier = modifier
            .graphicsLayer {
                // 缩小而不是放大：手指压下去，控件"沉"进玻璃里
                val scale = 1f - 0.055f * press
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(primary.copy(alpha = base + 0.05f * press))
            .drawWithContent {
                drawContent()
                // 边缘高光：按住时胶囊外圈亮一圈，像被光照到的玻璃边
                if (press > 0.01f) {
                    drawRoundRect(
                        color = primary.copy(alpha = 0.18f * press),
                        style = Stroke(width = 0.8.dp.toPx()),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) primary else secondary,
            ),
        )
    }
}

/** 小节标题。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = glassTextSecondary()),
    )
}

/** 横向可滚动的 chips 容器。 */
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
