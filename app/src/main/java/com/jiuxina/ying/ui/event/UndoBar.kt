package com.jiuxina.ying.ui.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary

/**
 * 6 秒撤销条。
 *
 * 倒计时由首页每秒时间源统一驱动（[UndoUiState.remainingSeconds]），自身不持有计时器，
 * 避免撤销条消失后仍残留一个协程。
 */
@Composable
fun UndoBar(
    undo: UndoUiState,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = undo.message,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 12.sp, color = secondary),
            )
            Text(
                text = "${undo.remainingSeconds}s",
                style = TextStyle(fontSize = 11.sp, color = secondary.copy(alpha = 0.7f)),
            )
            Text(
                text = "撤销",
                modifier = Modifier
                    .clickable(onClick = onUndo)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primary),
            )
        }
    }
}
