package com.morrowviento.morrow.ui.glass

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.component.Text

/**
 * 文字玻璃按钮（胶囊）。
 *
 * 原先直接用 Glasmio 的 `GlasmioButton`：高度 / 内边距 / 禁用态都省事，但**按压光效**
 * 也一并交给了材质——那套是按大面板调的，28~34dp 的胶囊属于「小控件」，
 * `pressOpacity` 直接满档、`Clear` 材质增益 3.33 倍，按下去整颗泛白。
 * 4.5.0 起改用 [GlassPanel]（应用自绘按压，见 `rememberGlassPress`），
 * 几何与字号照抄原实现，视觉只在「按下时」这一帧不同。
 *
 * @param active 选中态：走 `GlassEmphasis.Prominent`（iOS 的 tinted glass），
 *   而不是换一个背景色——底色仍由材质给，边框与文字才跟着变。
 * @param compact 小号（28dp 档）用于头部这类密集位置，默认档 34dp。
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
    GlassPanel(
        modifier = modifier.height(if (compact) 28.dp else 34.dp),
        capsule = true,
        prominent = active,
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = TextStyle(
                fontSize = if (compact) 12.5.sp else 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                // 禁用态：按原 Glasmio 的处理「压灰」，这里用主文字色的 38% 近似，
                // 玻璃明暗两边都不需要另配一套灰
                color = glassTextPrimary().copy(alpha = if (enabled) 1f else 0.38f),
            ),
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp),
        )
    }
}
