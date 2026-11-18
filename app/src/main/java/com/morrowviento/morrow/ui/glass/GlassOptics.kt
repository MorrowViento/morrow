package com.morrowviento.morrow.ui.glass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle

/** 首页与离线 RemoteViews 共用的光学参数；不向业务层暴露玻璃库类型。 */
internal object GlassOptics {
    const val CARD_CORNER_DP = 26f
    const val CARD_PADDING_HORIZONTAL_DP = 18f
    const val CARD_PADDING_VERTICAL_DP = 14f

    fun frostRadiusDp(frost: Float): Float = 0.5f + 25f * frost.coerceIn(0f, 1f)

    fun veilAlpha(frost: Float): Float {
        val t = frost.coerceIn(0f, 1f)
        return 0.26f * t * t
    }

    fun veilAlpha(frost: Float, opaque: Boolean): Float =
        if (opaque) maxOf(veilAlpha(frost), 0.72f) else veilAlpha(frost)

    fun accentAlpha(dark: Boolean): Float = if (dark) 0.09f else 0.05f

    fun cardPath(width: Int, height: Int, density: Float): android.graphics.Path = Path().apply {
        addOutline(ContinuousRoundedRectangle(CARD_CORNER_DP.dp).createOutline(
            Size(width.toFloat(), height.toFloat()), LayoutDirection.Ltr, Density(density),
        ))
    }.asAndroidPath()
}
