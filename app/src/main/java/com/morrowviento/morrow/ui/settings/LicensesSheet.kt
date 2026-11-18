package com.morrowviento.morrow.ui.settings

/** 开源许可弹层：目录 → 全文两态，正文按块懒加载，返回键先回目录。 */
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morrowviento.morrow.ui.component.Text
import com.morrowviento.morrow.ui.glass.GlassSheet
import com.morrowviento.morrow.ui.glass.SheetTextButton
import com.morrowviento.morrow.ui.glass.glassTextPrimary
import com.morrowviento.morrow.ui.glass.glassTextSecondary
import com.morrowviento.morrow.ui.glass.glassTextTertiary

/**
 * 开源许可：目录 → 全文阅读。
 *
 * 全文来自随包 assets（见 [LicenseCatalog]），**不在代码里复制许可正文**，也不一次布局整份文件：
 * 目录用 [LazyColumn]，正文按段落切块后再懒加载（[licenseBlocks]），
 * 因此读完 100 KB 级的 `runtime.txt` 也只组合可见的那几块。
 *
 * 弹层不设 `fitContent`：需要 `GlassSheet` 里那个 `weight(1f)` 的有界槽，否则长正文会把面板
 * 撑出屏幕。读取在 IO 线程，打开瞬间不会卡住动画。
 */
@Composable
internal fun LicensesSheet(onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf<LicenseDocument?>(null) }
    GlassSheet(
        title = selected?.title ?: "开源许可",
        onDismiss = onDismiss,
    ) { dismiss ->
        val document = selected
        if (document == null) {
            LicenseIndex(onOpen = { selected = it }, onDone = dismiss)
        } else {
            LicenseReader(document = document, onBack = { selected = null })
        }
    }
}

@Composable
private fun LicenseIndex(onOpen: (LicenseDocument) -> Unit, onDone: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 14.dp),
    ) {
        item(key = "intro") {
            Text(
                text = "开源许可",
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
            )
            Text(
                text = "本应用自有代码以 MIT 发布；界面材质 Glasmio 为 Apache-2.0。" +
                    "以下全文随安装包离线提供，点击条目可阅读。",
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = glassTextTertiary()),
            )
        }
        items(LicenseCatalog, key = { it.assetPath }) { document ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpen(document) }
                    .padding(horizontal = 6.dp, vertical = 9.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = document.title,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(fontSize = 13.sp, color = glassTextPrimary()),
                    )
                    Text(
                        text = document.license,
                        style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = document.summary,
                    style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, color = glassTextSecondary()),
                )
            }
        }
        item(key = "done") {
            Spacer(Modifier.height(4.dp))
            MenuAction(label = "好", onClick = onDone)
        }
    }
}

@Composable
private fun LicenseReader(document: LicenseDocument, onBack: () -> Unit) {
    val context = LocalContext.current
    // null = 读取中；失败保留原因，提示用户安装包可能不完整。
    val result by produceState<Result<String>?>(initialValue = null, document.assetPath) {
        value = runCatching { readLicenseText(context, document.assetPath) }
    }
    // 返回键先回目录，而不是直接关掉整个弹层。
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetTextButton(text = "返回", onClick = onBack)
            Text(
                text = document.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = glassTextPrimary()),
            )
            Text(
                text = document.license,
                style = TextStyle(fontSize = 11.sp, color = glassTextSecondary()),
            )
        }
        LicenseBody(
            document = document,
            result = result,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun LicenseBody(document: LicenseDocument, result: Result<String>?, modifier: Modifier) {
    val loaded = result
    val bodyStyle = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = glassTextSecondary())
    if (loaded == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(text = "读取中…", style = bodyStyle)
        }
        return
    }
    val text = loaded.getOrNull()
    if (text == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "无法读取 ${document.assetPath}。安装包可能不完整，请重新安装后再试。",
                style = bodyStyle,
            )
        }
        return
    }
    if (text.isBlank()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(text = "该文件为空。", style = bodyStyle)
        }
        return
    }
    val blocks = remember(text) { licenseBlocks(text) }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        items(blocks) { block ->
            Text(
                text = block,
                modifier = Modifier.padding(bottom = 10.dp),
                style = bodyStyle,
            )
        }
    }
}
