package com.morrowviento.morrow.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.ui.GlasmioBottomSheet

/**
 * 底部弹层的统一入口（新建 / 编辑 / 详情 / 分类 / 提醒 / 更新）。
 *
 * 之前这些面板都是「同页面内手写的 Box + GlassPanel + 半透明遮罩」，没有入场动效：
 * 点一下「＋」面板直接出现，也没有下拉关闭。现在整条交给 Glasmio 的
 * [GlasmioBottomSheet]：
 *
 * - UIKit 的 modal 呈现曲线（上滑 + 圆角收拢 + 背景压暗），下拉手势跟随手指，
 *   拖过阈值 / 甩出速度足够就关闭，回弹用 `IosSheetMotion` 的弹簧；
 * - 支持 detent（半屏 / 全屏）、滚动到顶继续上拉换成展开，这些细节手写很难对齐；
 * - 自带 back 键、点击遮罩关闭、无障碍的 dismiss / expand 动作。
 *
 * 跨窗口采样：库内部用 `rememberCrossWindowBackdrop` 把主窗口的画面喂给 Dialog 里的
 * 玻璃材质，所以弹层里的玻璃仍然能折射应用背景，不会变成一块死板的灰。
 *
 * 关闭动效：面板由调用方按需挂载（`form != null` 时才调用），若在这里直接回调
 * `onDismiss`，Dialog 会被立刻移除、看不到收起动画。因此内容拿到的是 [content] 里的
 * `dismiss` 回调：它先把 `visible` 置 false 交给库播放收起动画，动画结束库再回调
 * `onDismissRequest` → 真正的 `onDismiss`。
 */
@Composable
fun GlassSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fitContent: Boolean = false,
    skipPartiallyExpanded: Boolean = true,
    /** 下载 / 安装这类进行中的流程不允许手势或返回键关掉。 */
    dismissible: Boolean = true,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var closing by remember { mutableStateOf(false) }
    GlasmioBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        skipPartiallyExpanded = skipPartiallyExpanded,
        visible = !closing,
        fitContent = fitContent,
        dismissOnClickOutside = dismissible,
        dismissOnBackPress = dismissible,
        title = title,
    ) {
        content { closing = true }
    }
}

/**
 * 弹层里的标题行（取消 / 标题 / 确认）。
 *
 * 不复用库的 `GlasmioSheetTopToolbar`：它需要配合 `topBar` 槽与
 * `LocalGlasmioSheetContentPadding` 一起用，而我们的面板都是「整块内容自己滚」，
 * 固定一条顶部工具条反而会切掉滚动区域。这里只是一行文字按钮，用玻璃色即可。
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
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
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

/** 弹层内的文字按钮（取消 / 保存 / 完成）。 */
@Composable
fun SheetTextButton(
    text: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) glassTextPrimary() else glassTextSecondary(),
        ),
    )
}
