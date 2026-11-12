package com.morrowviento.morrow.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.glass.GlassCirclePanel
import com.morrowviento.morrow.ui.glass.glassPressFeedback
import com.morrowviento.morrow.ui.glass.glassPressable
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.glass.rememberGlassPress

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

/** 可选中的胶囊标签。 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(primary.copy(alpha = if (selected) 0.18f else 0.06f))
            .clickable(onClick = onClick)
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

/**
 * 新建事件的主按钮（日历页右下角玻璃圆钮）。
 *
 * 首页不再用它——首页的新建入口在底栏中央（见 `HomeBottomBar`）。
 * 日历是整页覆盖且有不透明底色，会把首页底栏整个盖住，因此日历页必须自己再放一颗。
 */
@Composable
fun CreateFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val press = rememberGlassPress()
    GlassCirclePanel(
        modifier = modifier
            .size(60.dp)
            .glassPressFeedback(press, scale = 0.07f, shape = CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .glassPressable(press, contentDescription = "新建事件", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Light, color = glassTextPrimary()),
            )
        }
    }
}
