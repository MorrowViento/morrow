package com.morrowviento.morrow.ui.glass

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioButtonSize

/**
 * 文字玻璃按钮（Glasmio 0.1.3 的 `GlasmioButton`）。
 *
 * 为什么用它而不是自己拼 `GlassSurface`：`GlasmioButton` 已经带了完整的一套——
 * 高度 / 内边距 / 文字色（跟随玻璃明暗自适应）/ 按下时的折射光斑 / 禁用态降级
 * （`LocalGlassColors provides disabledButtonColors()`）。自己复刻只会各处跑偏。
 *
 * @param active 选中态：用 `GlassEmphasis.Prominent`（iOS 的 tinted glass）代替
 *   「换一个背景色」，这样按下时的光斑仍然画在材质里。
 * @param compact 小号（28dp 档）用于头部这类密集位置，默认档高 34dp。
 */
@Composable
fun GlassTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = true,
) {
    GlasmioButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        emphasis = if (active) GlassEmphasis.Prominent else GlassEmphasis.Regular,
        size = if (compact) GlasmioButtonSize.Small else GlasmioButtonSize.Medium,
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = TextStyle(
                fontSize = if (compact) 12.5.sp else 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}
