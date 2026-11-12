package com.morrowviento.morrow.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.service.update.UpdatePolicy
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary

/**
 * 应用内更新面板（M7）。
 *
 * 与设置页其它弹层同一套路：整页半透明遮罩 + 底部玻璃面板，
 * 不引 Material Dialog，避免玻璃材质被独立窗口切断采样源。
 */
@Composable
fun UpdateSheet(
    state: UpdateUiState,
    actions: UpdateActions,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    val release = state.release

    // 下载 / 安装进行中禁止点外部或返回键关掉，避免用户以为进度停了
    GlassSheet(
        title = "应用更新",
        onDismiss = actions.dismiss,
        fitContent = true,
        dismissible = !state.busy,
    ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                Text(
                    text = when (state.stage) {
                        UpdateStage.CHECKING -> "正在检查更新"
                        UpdateStage.DOWNLOADING -> "正在下载更新"
                        UpdateStage.READY -> "下载完成"
                        UpdateStage.INSTALLING -> "正在启动安装"
                        UpdateStage.FAILED -> "更新没完成"
                        else -> "发现新版本"
                    },
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = primary),
                )
                Spacer(Modifier.height(8.dp))

                when {
                    state.stage == UpdateStage.CHECKING -> SheetHint("正在同时探测多个来源，稍等一下…")
                    state.message != null -> SheetHint(state.message.orEmpty())
                    state.stage == UpdateStage.DOWNLOADING -> {
                        val totalText = if (state.totalBytes > 0) {
                            " / ${UpdatePolicy.formatBytes(state.totalBytes)}"
                        } else {
                            ""
                        }
                        SheetHint("${UpdatePolicy.formatBytes(state.doneBytes)}$totalText")
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(if (state.totalBytes > 0) state.percent / 100f else 0f)
                    }
                    state.stage == UpdateStage.INSTALLING -> SheetHint("系统还会弹一次安装确认，请点「安装」")
                    state.stage == UpdateStage.READY -> SheetHint("安装包已校验，可以安装了")
                    release != null -> {
                        Text(
                            text = "${release.versionLabel} · #${release.versionCode}",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primary),
                        )
                        Spacer(Modifier.height(6.dp))
                        if (state.asset != null) {
                            SheetHint("安装包 ${state.asset.name}（${UpdatePolicy.formatBytes(state.asset.size)}）")
                        }
                        if (release.notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = release.notes.trim(),
                                    style = TextStyle(fontSize = 12.sp, color = secondary, lineHeight = 18.sp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row {
                    SheetAction("稍后", secondary, enabled = !state.busy, onClick = actions.dismiss)
                    Spacer(Modifier.width(12.dp))
                    when (state.stage) {
                        UpdateStage.AVAILABLE -> SheetAction("下载", primary, onClick = actions.download)
                        UpdateStage.READY -> SheetAction("安装", primary, onClick = actions.install)
                        UpdateStage.FAILED -> SheetAction("重试", primary, onClick = actions.check)
                        else -> Unit
                    }
                }
            }
        }
    }

@Composable
private fun SheetHint(text: String) {
    Text(text = text, style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()))
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(glassTextPrimary().copy(alpha = 0.14f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(glassTextPrimary().copy(alpha = 0.8f)),
        )
    }
}

@Composable
private fun SheetAction(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color.copy(alpha = if (enabled) 1f else 0.4f),
        ),
    )
}
