package com.morrowviento.morrow.ui.glass

import androidx.compose.runtime.Composable
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonLayout
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog

/** 弹窗按钮的语义角色（映射到系统的取消 / 破坏性样式）。 */
enum class GlassAlertRole { Default, Cancel, Destructive }

/**
 * 一个按钮。[enabled] 用于「标题没填就不能保存」这类校验，禁用态由库自己画。
 */
data class GlassAlertButton(
    val label: String,
    val onClick: () -> Unit,
    val role: GlassAlertRole = GlassAlertRole.Default,
    val enabled: Boolean = true,
)

/**
 * 玻璃弹窗（清空全部、导入确认、退出确认）。
 *
 * 之前是自己画的 `Box + 遮罩 + GlassPanel`：点「清空全部」瞬间弹出一块面板，没有入场动效，
 * 也没有按系统语义排布按钮。现在交给 [GlasmioAlertDialog]：
 *
 * - UIKit 的弹窗呈现曲线（0.92 → 1 的缩放 + 淡入 + 背景压暗），关闭时反向收回；
 * - 按钮自动按 iOS 规则排布（两个并排、三个以上堆叠），Cancel 加粗、Destructive 走系统红；
 * - 弹窗自带 Dialog 窗口，并用跨窗口采样让玻璃仍能折射背后的页面。
 *
 * 保持挂载、只翻转 [visible]，关闭动画才播得完（与 [GlassSheet] 同理）。
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
    GlasmioAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        visible = visible,
        buttonLayout = if (stacked) GlasmioAlertButtonLayout.Stacked else GlasmioAlertButtonLayout.SideBySide,
        buttons = buttons.map { button ->
            GlasmioAlertButtonSpec(
                label = button.label,
                onClick = button.onClick,
                role = when (button.role) {
                    GlassAlertRole.Default -> GlasmioAlertButtonRole.Default
                    GlassAlertRole.Cancel -> GlasmioAlertButtonRole.Cancel
                    GlassAlertRole.Destructive -> GlasmioAlertButtonRole.Destructive
                },
                enabled = button.enabled,
            )
        },
    )
}
