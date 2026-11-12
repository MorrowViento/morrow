package com.jiuxina.ying.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiuxina.ying.R
import com.jiuxina.ying.YingApp
import com.jiuxina.ying.domain.model.CountdownEvent
import com.jiuxina.ying.ui.component.ChoiceChip
import com.jiuxina.ying.ui.component.SectionTitle
import com.jiuxina.ying.ui.glass.GlassBackdrop
import com.jiuxina.ying.ui.glass.GlassBackdropHost
import com.jiuxina.ying.ui.glass.GlassTheme
import com.jiuxina.ying.ui.glass.glassTextPrimary
import com.jiuxina.ying.ui.glass.glassTextSecondary
import kotlinx.coroutines.launch

/**
 * 小部件配置台。
 *
 * 两个子类而不是一个类里判断 provider 类型：`getAppWidgetInfo` 在个别启动器上
 * 配置阶段会返回 null，靠它猜"这是不是单事件小部件"会出现配置面板选错类型的情况。
 * 让每个子类从注册时就明确自己的类型，零歧义。
 */
abstract class BaseWidgetConfigActivity : ComponentActivity() {

    protected abstract val isDetail: Boolean

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    /**
     * 桌面还没分配 id，说明这是「先设置、再落桌面」的入口（长按图标 / 设置页添加）。
     *
     * 与「编辑已存在的小部件」区分开：那条路径带着 id 来，保存即生效；这条路径保存之后
     * 还要真的把实例 pin 到桌面，所以按钮与文案都不同。
     */
    private var pinMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 先设为取消：用户直接返回/杀进程时系统会移除这个还没配置好的小部件
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        pinMode = appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID
        enableEdgeToEdge()

        val app = application as YingApp
        setContent {
            GlassTheme {
                GlassBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { GlassBackdrop() },
                    overlayContent = {
                        var style by remember { mutableStateOf(WidgetStyle()) }
                        var events by remember { mutableStateOf<List<CountdownEvent>>(emptyList()) }
                        var adding by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()
                        val store = remember { WidgetConfigStore(app.settingsRepository) }

                        LaunchedEffect(Unit) {
                            // 未完成的排前面：单事件小部件默认绑"最近要到的那个"
                            events = app.eventRepository.loadAll()
                                .sortedWith(compareBy({ it.isCompleted }, { it.targetDate }))
                            // pin 模式没有 id 可查，起点就是「上次保存的样式」（剥掉绑定）
                            val loaded = if (pinMode) store.defaultStyle() else store.load(appWidgetId)
                            style = if (isDetail) {
                                // 已绑的事件可能已经被删掉：那样"已选"标记会挂在一条不存在的行上，
                                // 又没有哪一行能被再次选中，面板等于失效，所以统一回落到最近事件
                                val bound = loaded.eventId?.takeIf { id -> events.any { it.id == id } }
                                loaded.copy(eventId = bound ?: events.firstOrNull()?.id)
                            } else {
                                loaded
                            }
                        }

                        ConfigContent(
                            style = style,
                            isDetail = isDetail,
                            pinMode = pinMode,
                            events = events,
                            onStyleChange = { style = it },
                            onCancel = { finish() },
                            onSave = {
                                // 连点两下会 pin 两个实例，用标志挡住
                                if (!adding) {
                                    adding = true
                                    scope.launch {
                                        if (!pinMode) {
                                            store.save(appWidgetId, style)
                                            // 配置完成时系统还没走 onUpdate，先主动刷一次，点完成就能看到效果
                                            app.widgetRefresher.refreshIds(intArrayOf(appWidgetId), isDetail)
                                            setResult(
                                                RESULT_OK,
                                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                                            )
                                            finish()
                                            return@launch
                                        }
                                        // 待认领：pin 成功前桌面还没给 id，样式只能先挂在槽位上，
                                        // 由新实例的首次 onUpdate 认领（见 WidgetRefresher.refresh）
                                        store.savePending(isDetail, style)
                                        if (pinToHomeScreen()) {
                                            finish()
                                        } else {
                                            store.clearPending(isDetail)
                                            Toast.makeText(
                                                applicationContext,
                                                R.string.widget_pin_failed,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            adding = false
                                        }
                                    }
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    /**
     * 把实例交给桌面 pin。
     *
     * 这里成功率比 `WidgetPinActivity` 那条路高得多：调用方就是用户正在看的这一页，
     * 窗口早已可见（冷启动时系统会因「调用方不可见」丢弃请求，那条坑记在 WidgetPinActivity 里）。
     */
    private fun pinToHomeScreen(): Boolean {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val provider = ComponentName(
            applicationContext,
            if (isDetail) YingDetailWidgetProvider::class.java else YingWidgetProvider::class.java,
        )
        if (!manager.isRequestPinAppWidgetSupported) {
            Log.w(TAG, "桌面不支持 requestPinAppWidget：$provider")
            return false
        }
        return runCatching { manager.requestPinAppWidget(provider, null, null) }
            .onFailure { Log.w(TAG, "requestPinAppWidget 失败：$provider", it) }
            .getOrDefault(false)
    }

    private companion object {
        const val TAG = "YingWidgetConfig"
    }
}

/** 总览小部件的配置台。 */
class WidgetConfigActivity : BaseWidgetConfigActivity() {
    override val isDetail: Boolean get() = false
}

/** 单事件小部件的配置台。 */
class DetailWidgetConfigActivity : BaseWidgetConfigActivity() {
    override val isDetail: Boolean get() = true
}

@Composable
private fun ConfigContent(
    style: WidgetStyle,
    isDetail: Boolean,
    pinMode: Boolean,
    events: List<CountdownEvent>,
    onStyleChange: (WidgetStyle) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            // 头行固定，选项区用 LazyColumn：事件列表可能很长，而 verticalScroll
            // 里再套 LazyColumn 会撞上"无限高约束"崩溃（M2b 的 + 号闪退就是这个原因）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.widget_config_cancel),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    style = TextStyle(fontSize = 14.sp, color = secondary),
                )
                Text(
                    text = stringResource(
                        if (pinMode) R.string.widget_config_title_add else R.string.widget_config_title,
                    ),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = primary),
                )
                Text(
                    text = stringResource(
                        if (pinMode) R.string.widget_config_add else R.string.widget_config_done,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSave)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primary),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (pinMode) {
                    item(key = "pin-hint") {
                        Text(
                            text = stringResource(R.string.widget_config_add_hint),
                            style = TextStyle(fontSize = 11.sp, color = secondary),
                        )
                    }
                }

                item(key = "preview") {
                    WidgetPreview(style, isDetail)
                }

                item(key = "preset-title") { SectionTitle("样式预设") }
                item(key = "preset") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetPreset.entries.forEach { preset ->
                            ChoiceChip(preset.label, style.preset == preset) {
                                onStyleChange(style.withPreset(preset))
                            }
                        }
                    }
                }

                item(key = "preset-hint") {
                    // 行数现在由卡片高度决定，预设只影响信息密度，不再"卡"住条数
                    Text(
                        text = when (style.preset) {
                            WidgetPreset.MINIMAL -> "只留标题和天数，最省地方"
                            WidgetPreset.STANDARD -> "标题 + 分类/日期，日常够用"
                            WidgetPreset.DETAILED -> "再加上进度条，信息最全"
                        } + "；卡片拉多高就排多少行，内容多了可以在卡片里上下滑动",
                        style = TextStyle(fontSize = 11.sp, color = secondary),
                    )
                }

                item(key = "font-title") { SectionTitle("字号") }
                item(key = "font") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetFontScale.entries.forEach { scale ->
                            ChoiceChip(scale.label, style.fontScale == scale) {
                                onStyleChange(style.copy(fontScale = scale))
                            }
                        }
                    }
                }

                item(key = "palette-title") { SectionTitle("配色") }
                item(key = "palette") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetPalette.entries.forEach { palette ->
                            ChoiceChip(palette.label, style.palette == palette) {
                                onStyleChange(style.copy(palette = palette))
                            }
                        }
                    }
                }

                item(key = "elements-title") {
                    SectionTitle(if (isDetail) "显示元素" else "行内元素")
                }
                item(key = "elements") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("分类", style.showCategory) {
                            onStyleChange(style.copy(showCategory = !style.showCategory))
                        }
                        ChoiceChip("日期", style.showDate) {
                            onStyleChange(style.copy(showDate = !style.showDate))
                        }
                        ChoiceChip("进度", style.showProgress) {
                            onStyleChange(style.copy(showProgress = !style.showProgress))
                        }
                    }
                }

                if (isDetail) {
                    item(key = "event-title") { SectionTitle("绑定事件") }
                    if (events.isEmpty()) {
                        item(key = "event-empty") {
                            Text(
                                text = "还没有事件，先在 App 里新建一个",
                                style = TextStyle(fontSize = 12.sp, color = secondary),
                            )
                        }
                    }
                    items(events, key = { "ev-${it.id}" }) { event ->
                        EventChoiceRow(
                            event = event,
                            selected = style.eventId == event.id,
                            onClick = { onStyleChange(style.copy(eventId = event.id)) },
                        )
                    }
                }
            }
        }
    }
}

/** 实时预览：把当前字号/配色/显隐按真实小部件的样子画一遍。 */
@Composable
private fun WidgetPreview(style: WidgetStyle, isDetail: Boolean) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (style.palette) {
        WidgetPalette.SYSTEM -> systemDark
        WidgetPalette.DARK, WidgetPalette.ACCENT -> true
        WidgetPalette.LIGHT -> false
    }
    val background = when {
        style.palette == WidgetPalette.ACCENT ->
            Brush.linearGradient(listOf(Color(0xFF3B6FE0), Color(0xFFE84B8A)))
        dark -> Brush.linearGradient(listOf(Color(0xFF1B1533), Color(0xFF2B1030)))
        else -> Brush.linearGradient(listOf(Color(0xFFF2EEFA), Color(0xFFFFFFFF)))
    }
    val primary = if (dark) Color.White else Color(WidgetStyle.INK)
    val secondary = if (dark) Color.White.copy(alpha = 0.62f) else Color(WidgetStyle.INK_60)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .padding(16.dp),
    ) {
        if (!isDetail) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("萤", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primary))
                Spacer(Modifier.weight(1f))
                Text("3 个事件", style = TextStyle(fontSize = 11.sp, color = secondary))
            }
        }
        val samples = if (isDetail) {
            listOf(Triple("春节回家", "6", "天"))
        } else {
            listOf(
                Triple("春节回家", "6", "天"),
                Triple("妈妈生日", "23", "天"),
                Triple("毕业纪念", "4", "已过"),
            ).take(style.rows)
        }
        samples.forEach { (title, badge, unit) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontSize = (style.fontScale.titleSp * 0.9f).sp,
                            color = primary,
                        ),
                    )
                    if (style.showCategory || style.showDate) {
                        val meta = buildList {
                            if (style.showCategory) add("生活")
                            if (style.showDate) add("3月20日")
                        }.joinToString(" · ")
                        Text(
                            text = meta,
                            maxLines = 1,
                            style = TextStyle(fontSize = (style.fontScale.metaSp * 0.9f).sp, color = secondary),
                        )
                    }
                    if (style.showProgress) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(if (dark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.62f)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(if (dark) Color.White.copy(alpha = 0.85f) else Color(WidgetStyle.INK_80)),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = badge,
                        style = TextStyle(
                            fontSize = (style.fontScale.badgeSp * 0.9f).sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                        ),
                    )
                    Text(
                        text = unit,
                        style = TextStyle(fontSize = (style.fontScale.metaSp * 0.9f).sp, color = secondary),
                    )
                }
            }
        }
    }
}

/** 事件选择行：标题 + 目标日期，选中时左侧加一条高亮。 */
@Composable
private fun EventChoiceRow(event: CountdownEvent, selected: Boolean, onClick: () -> Unit) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(primary.copy(alpha = if (selected) 0.14f else 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = primary,
                ),
            )
            Text(
                text = "${event.targetDate.year}年${event.targetDate.monthValue}月${event.targetDate.dayOfMonth}日" +
                    " · ${event.category}" + if (event.isCompleted) " · 已完成" else "",
                maxLines = 1,
                style = TextStyle(fontSize = 11.sp, color = secondary),
            )
        }
        if (selected) {
            Text(
                text = "已选",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = primary),
            )
        }
    }
}
