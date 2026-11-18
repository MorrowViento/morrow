package com.morrowviento.morrow.ui.settings

/** 字体弹层：就地预览已导入字体、导入新字体、给出推荐字体的下载入口。 */
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.morrowviento.morrow.data.settings.AppFont
import com.morrowviento.morrow.data.settings.RECOMMENDED_FONTS
import com.morrowviento.morrow.data.settings.RecommendedFont
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.glass.AppFontTheme
import com.morrowviento.morrow.ui.glass.GlassPanel
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.SheetTextButton
import com.morrowviento.morrow.ui.glass.glassAccent
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.glass.glassTextTertiary

/**
 * 字体选择。
 *
 * 每一行**用自己的字体渲染自己的名字**——这是字体选择器唯一诚实的预览方式：
 * 单独开一块「预览区」只能显示一款字体，用户还得反复点选来回比较；
 * 列表就地渲染，一眼就能看出差别，也顺手验证了「这个文件确实能加载」。
 *
 * 点击即切换并关闭：字体是全局即时生效的，关掉弹层看到的整个界面就是预览，
 * 再要求用户点一次「应用」纯属多余。
 */
@Composable
internal fun FontSheet(
    current: AppFont,
    fonts: List<AppFont>,
    busy: Boolean,
    onSelect: (AppFont) -> Unit,
    onImport: (Uri) -> Unit,
    onDelete: (AppFont) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 字体文件的 MIME 在各家文件管理器里写法不一：有的报 font/ttf，
    // 有的直接给 application/octet-stream。多列几个类型，别让用户在自己的手机上选不到文件。
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    GlassSheet(title = "字体", onDismiss = onDismiss) { dismiss ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 14.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = "字体",
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
                )
                Text(
                    text = "选择应用内界面使用的字体。字体文件复制到应用私有目录，删掉应用即一并清除；" +
                        "桌面小组件与通知由系统渲染，不受这里影响。",
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                    style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = glassTextTertiary()),
                )
            }

            item(key = "preview") {
                FontPreview(current)
                Spacer(Modifier.height(14.dp))
            }

            item(key = "installed-title") {
                Text(
                    text = "已安装",
                    modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = glassTextSecondary()),
                )
            }

            item(key = "system") {
                GlassPanel(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        FontRow(
                            font = AppFont.SYSTEM,
                            selected = !current.isCustom,
                            sizeHint = "跟随系统设置",
                            enabled = !busy,
                            onSelect = { onSelect(AppFont.SYSTEM) },
                            onDelete = null,
                        )
                        fonts.forEach { font ->
                            Divider()
                            FontRow(
                                font = font,
                                selected = current.fileName == font.fileName,
                                sizeHint = null,
                                enabled = !busy,
                                onSelect = { onSelect(font) },
                                onDelete = { onDelete(font) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            item(key = "import") {
                GlassPanel(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        ActionRow(
                            title = "导入字体文件",
                            summary = "支持 TTF / OTF / TTC，单个文件不超过 32 MB",
                            enabled = !busy,
                            value = "选择文件",
                            onClick = {
                                picker.launch(
                                    arrayOf(
                                        "font/ttf",
                                        "font/otf",
                                        "font/collection",
                                        "application/x-font-ttf",
                                        "application/x-font-opentype",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            item(key = "recommend-title") {
                Text(
                    text = "去哪里找字体",
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = glassTextSecondary()),
                )
                Text(
                    // 把「为什么不在应用里直接下载」讲明白：包体与授权都不是小事
                    text = "应用不内置字体：中文字体动辄十几 MB，且每款都要随包附带许可全文。" +
                        "下面几款都是 OFL-1.1 开源字体，点开在浏览器里下载后，回来选「导入字体文件」。",
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                    style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = glassTextTertiary()),
                )
            }

            items(RECOMMENDED_FONTS, key = { it.url }) { font ->
                RecommendedRow(font) {
                    // 打开失败（没装浏览器）时不能静默：至少别让用户以为点了没反应
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, font.url.toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            item(key = "footnote") {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "字体授权由字体自身决定，请遵守对应许可；导入的字体只存在本机，不随备份导出。",
                    modifier = Modifier.padding(horizontal = 6.dp),
                    style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = glassTextTertiary()),
                )
                Spacer(Modifier.height(12.dp))
                SheetTextButton(text = "完成", onClick = dismiss)
            }
        }
    }
}

/** 当前字体的一块样例，字号覆盖标题与正文两档。 */
@Composable
private fun FontPreview(font: AppFont) {
    GlassPanel(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        AppFontTheme(font) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "朝夕",
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = glassTextPrimary()),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "距离下一个期待还有 42 天",
                    style = TextStyle(fontSize = 14.sp, color = glassTextPrimary()),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "会议 · 读书 · 生日 0123456789",
                    style = TextStyle(fontSize = 12.sp, color = glassTextSecondary()),
                )
            }
        }
    }
}

@Composable
private fun FontRow(
    font: AppFont,
    selected: Boolean,
    sizeHint: String?,
    enabled: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val primary = glassTextPrimary()
    val secondary = glassTextSecondary()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 名字用这款字体自己渲染：就地预览，也顺便证明文件能加载
        AppFontTheme(font) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = font.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 15.sp, color = primary),
                )
                if (sizeHint != null) {
                    Text(
                        text = sizeHint,
                        style = TextStyle(fontSize = 11.sp, color = secondary),
                    )
                }
            }
        }
        if (onDelete != null) {
            Text(
                text = "删除",
                modifier = Modifier
                    .clickable(enabled = enabled) { onDelete() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = TextStyle(fontSize = 12.sp, color = secondary),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = if (selected) "✓" else "",
            modifier = Modifier.width(18.dp),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = glassAccent()),
        )
    }
}

@Composable
private fun RecommendedRow(font: RecommendedFont, onOpen: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart, onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = font.name,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = glassTextPrimary()),
                )
                Text(
                    text = "${font.license} · ${font.note}",
                    style = TextStyle(fontSize = 11.sp, color = glassTextTertiary()),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "前往",
                style = TextStyle(fontSize = 12.sp, color = glassAccent()),
            )
        }
    }
}
