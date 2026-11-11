package com.jiuxina.ying.ui.reminder

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jiuxina.ying.ui.component.ChoiceChip
import com.jiuxina.ying.ui.component.SectionTitle
import com.jiuxina.ying.ui.glass.GlassPanel
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary

/**
 * 提醒诊断浮层（M4）。
 *
 * 一屏回答三个问题：通知权限开没开、精确闹钟开没开、到底排了哪些提醒。
 * 权限状态在回到前台时重新读一次（`LifecycleEventEffect`），
 * 用户从系统设置页返回后无需手动刷新。
 */
@Composable
fun ReminderSheet(
    state: ReminderUiState,
    actions: ReminderActions,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    // 运行时授权：拒绝时不再纠缠，直接把人送到系统设置页（那里能看到"不再询问"的终态）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) actions.openNotificationSettings()
        actions.refresh()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { actions.refresh() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onDismiss),
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(Modifier.fillMaxSize()) {
                SheetHeader(onDismiss)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp, end = 18.dp),
                ) {
                    SectionTitle("系统权限")
                    Spacer(Modifier.height(6.dp))
                    PermissionRow(
                        title = "通知",
                        detail = if (state.notificationsAllowed) "已开启" else "未开启，到点不会有任何提示",
                        ok = state.notificationsAllowed,
                        actionLabel = "开启",
                        onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                    Spacer(Modifier.height(6.dp))
                    PermissionRow(
                        title = "精确闹钟",
                        detail = if (state.exactAlarmsAllowed) {
                            "已允许，提醒准时"
                        } else {
                            "未允许，系统可能把提醒推迟几分钟"
                        },
                        ok = state.exactAlarmsAllowed,
                        actionLabel = "去开启",
                        onClick = actions.openExactAlarmSettings,
                    )

                    Spacer(Modifier.height(14.dp))
                    SectionTitle("排期中的提醒 · ${state.alarmCount} 条 / ${state.eventCount} 个事件")

                    // 上一条提醒的实际延迟：这是用户唯一能自己看到的「系统有没有拖延」的证据。
                    // 精确闹钟未授权时通常明显为正，授权后应接近 0
                    state.lastDelayMillis?.let { delay ->
                        Text(
                            text = describeDelay(delay),
                            style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                        )
                    }

                    if (state.rows.isEmpty()) {
                        Text(
                            text = if (state.isLoading) "读取中…" else "暂无待触发的提醒，去给事件加个提醒吧",
                            style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                            modifier = Modifier.padding(top = 12.dp, start = 2.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(state.rows, key = { it.id }) { row -> ReminderRowView(row) }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChoiceChip("发送测试通知", false, onClick = actions.sendTest)
                        ChoiceChip("重新排期", false, onClick = actions.resync)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    ok: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(primary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 状态点用字符而非图标：不引 material-icons-extended，也避免缺字形
                Text(
                    text = if (ok) "●" else "○",
                    style = TextStyle(fontSize = 9.sp, color = if (ok) Color(0xFF6BE39B) else Color(0xFFFFB86B)),
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 6.dp),
                    style = TextStyle(fontSize = 14.sp, color = primary),
                )
            }
            Text(
                text = detail,
                style = TextStyle(fontSize = 11.sp, color = secondary),
                modifier = Modifier.padding(top = 2.dp, start = 15.dp),
            )
        }
        if (!ok) {
            Text(
                text = actionLabel,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primary),
            )
        }
    }
}

@Composable
private fun ReminderRowView(row: ReminderRow) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(primary.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(text = row.title, maxLines = 1, style = TextStyle(fontSize = 14.sp, color = primary))
        Text(
            text = "${row.label} · ${row.dateText}",
            style = TextStyle(fontSize = 11.sp, color = secondary),
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = row.relativeText,
            style = TextStyle(fontSize = 11.sp, color = secondary.copy(alpha = 0.75f)),
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/**
 * 把「上一条提醒的实际延迟」说成人话。
 *
 * 阈值取 5 秒：正常人感觉不到几秒的偏差，而一旦到了「分钟」级，用户就是真的觉得不准了，
 * 这时必须直接指出原因（系统推迟），否则用户只会以为应用坏了。
 */
private fun describeDelay(delayMillis: Long): String = when {
    delayMillis > 5_000L -> "上次提醒晚了 ${formatSpan(delayMillis)}，系统在推迟闹钟"
    delayMillis < -5_000L -> "上次提醒早了 ${formatSpan(-delayMillis)}"
    else -> "上次提醒准时"
}

private fun formatSpan(millis: Long): String {
    val minutes = millis / 60_000L
    val seconds = (millis % 60_000L) / 1_000L
    return when {
        minutes > 0 && seconds > 0 -> "${minutes} 分 ${seconds} 秒"
        minutes > 0 -> "${minutes} 分"
        else -> "${seconds} 秒"
    }
}

@Composable
private fun SheetHeader(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
    ) {
        Text(
            text = "提醒",
            modifier = Modifier.align(Alignment.Center),
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
        )
        Text(
            text = "完成",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
        )
    }
}
