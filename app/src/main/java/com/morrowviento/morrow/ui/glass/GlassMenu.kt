package com.morrowviento.morrow.ui.glass

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.ui.GlasmioMenu
import io.github.neoruaa.glasmio.ui.GlasmioMenuItem

/**
 * 下拉菜单（首页「⋯」）。
 *
 * 之前是自己画的「半透明遮罩 + 底部一张玻璃面板」——本质是动作表（action sheet），
 * 既没有从按钮处展开的形变，也不跟按钮对齐。现在用 Glasmio 的 [GlasmioMenu]：
 *
 * - 菜单从 [anchor] 的位置**液态形变**展开（`LiquidMenuMorphMotion`），关闭时缩回按钮；
 * - 自动判断上下翻转、贴着屏幕边缘收缩，`itemCount` 用于预先算好高度参与形变，
 *   必须与实际条目数一致；
 * - 每一格是独立的玻璃材质，按下有折射高光（与底栏、卡片同一套）。
 *
 * [content] 拿到的 `backdrop` 是菜单自己的图层采样源，必须传给 [GlassMenuItem]，
 * 否则菜单项会去采「屏幕上还有什么」，形变过程中会出现色块。
 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    itemCount: Int,
    modifier: Modifier = Modifier,
    anchor: @Composable (onClick: () -> Unit) -> Unit,
    content: @Composable ColumnScope.(backdrop: Backdrop, close: () -> Unit) -> Unit,
) {
    GlasmioMenu(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        itemCount = itemCount,
        modifier = modifier,
        anchor = anchor,
    ) { backdrop, close ->
        content(backdrop, close)
    }
}

/** 菜单项。[destructive] 用系统红，[enabled] 为 false 时整行变淡且不可点。 */
@Composable
fun GlassMenuItem(
    backdrop: Backdrop,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    subtitle: String? = null,
) {
    GlasmioMenuItem(
        title = title,
        onClick = onClick,
        subtitle = subtitle,
        destructive = destructive,
        backdrop = backdrop,
        enabled = enabled,
    )
}
