package com.morrowviento.morrow.ui.settings

/** 背景裁剪：固定窗口比例、移动/双指缩放、确认后才提交图片与取色。 */
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.data.backdrop.BackdropCrop
import com.morrowviento.morrow.ui.glass.GlassTextButton
import com.morrowviento.morrow.ui.glass.glassColors
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary

@Composable
internal fun BackdropCropScreen(
    pending: PendingBackdrop,
    busy: Boolean,
    onConfirm: (Bitmap, BackdropCrop) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler { if (!busy) onCancel() }
    val bitmap = pending.bitmap
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val initial = remember(pending) { BackdropCrop.centered(bitmap.width, bitmap.height, pending.aspectRatio) }
    var crop by remember(pending) { mutableStateOf(initial) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxSize().background(glassColors().groupedBackground)
            .clickable(interactionSource = interaction, indication = null) { }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("裁剪背景", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary()))
        Spacer(Modifier.height(8.dp))
        Text("比例固定为背景窗口；移动图片或双指缩放", style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            val previewWidth = minOf(maxWidth, maxHeight * pending.aspectRatio)
            val previewHeight = previewWidth / pending.aspectRatio
            Canvas(
                Modifier.width(previewWidth).height(previewHeight)
                    .semantics {
                        contentDescription = "背景裁剪预览"
                        if (!busy) customActions = listOf(
                            "向左移动" to Offset(-0.1f, 0f), "向右移动" to Offset(0.1f, 0f),
                            "向上移动" to Offset(0f, -0.1f), "向下移动" to Offset(0f, 0.1f),
                        ).map { (label, pan) ->
                            CustomAccessibilityAction(label) {
                                crop = crop.transform(bitmap.width, bitmap.height, pending.aspectRatio,
                                    0.5f, 0.5f, pan.x, pan.y, 1f)
                                true
                            }
                        }
                    }
                    .pointerInput(pending, busy) {
                        if (!busy) detectTransformGestures { centroid, pan, zoom, _ ->
                            crop = crop.transform(bitmap.width, bitmap.height, pending.aspectRatio,
                                centroid.x / size.width, centroid.y / size.height,
                                pan.x / size.width, pan.y / size.height, zoom)
                        }
                    },
            ) {
                clipRect {
                    withTransform({
                        scale(size.width / crop.width, size.height / crop.height, Offset.Zero)
                        translate(-crop.left, -crop.top)
                    }) { drawImage(image) }
                    repeat(2) { index ->
                        val fraction = (index + 1) / 3f
                        drawLine(Color.White.copy(alpha = 0.45f), Offset(size.width * fraction, 0f),
                            Offset(size.width * fraction, size.height), strokeWidth = 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, size.height * fraction),
                            Offset(size.width, size.height * fraction), strokeWidth = 1.dp.toPx())
                    }
                    drawRect(Color.White.copy(alpha = 0.8f), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextButton("缩小", { crop = crop.transform(bitmap.width, bitmap.height, pending.aspectRatio,
                0.5f, 0.5f, 0f, 0f, 1f / 1.25f) }, enabled = !busy, modifier = Modifier.height(44.dp))
            GlassTextButton("重置", { crop = initial }, enabled = !busy, modifier = Modifier.height(44.dp))
            GlassTextButton("放大", { crop = crop.transform(bitmap.width, bitmap.height, pending.aspectRatio,
                0.5f, 0.5f, 0f, 0f, 1.25f) }, enabled = !busy, modifier = Modifier.height(44.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassTextButton("取消", onCancel, modifier = Modifier.weight(1f).height(48.dp), enabled = !busy, compact = false)
            GlassTextButton(if (busy) "保存中…" else "使用此背景", { onConfirm(bitmap, crop) },
                modifier = Modifier.weight(1f).height(48.dp), active = true, enabled = !busy, compact = false)
        }
    }
}
