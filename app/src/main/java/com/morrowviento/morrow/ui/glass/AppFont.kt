package com.morrowviento.morrow.ui.glass

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.morrowviento.morrow.data.settings.AppFont
import com.morrowviento.morrow.data.settings.FontStore
import com.morrowviento.morrow.ui.component.LocalAppFontFamily

/**
 * 应用字体的全局接入点。
 *
 * 两件事一起做：
 * 1. 提供 [LocalAppFontFamily]，项目自己的 `Text` 包装（`ui/component/AppText.kt`）
 *    会把没有显式指定家族的文本自动套上；
 * 2. 同时改写 [LocalTextStyle]，让 Glasmio 内部那些直接读默认文本样式的控件也跟上。
 *
 * 最初想直接替换 `LocalFontFamilyResolver`（一次覆盖所有文本，连调用点都不用碰），
 * 但 `FontFamily.Resolver` 是 `sealed interface`，跨模块实现会在编译期被拒。
 * 于是退回「同名同签名包装 + 替换导入」：24 个文件改一行 import，调用点零改动。
 *
 * 覆盖不到的地方要说清楚：Glasmio 里自己持有 `TextStyle` 的控件、桌面小组件、
 * 通知（RemoteViews）以及 Canvas 里的 `drawText` 仍是系统字体。
 */
@Composable
fun AppFontTheme(font: AppFont, content: @Composable () -> Unit) {
    val family = rememberAppFontFamily(font)
    if (family == null) {
        content()
        return
    }
    val base = LocalTextStyle.current
    CompositionLocalProvider(
        LocalAppFontFamily provides family,
        LocalTextStyle provides base.copy(fontFamily = family),
        content = content,
    )
}

/**
 * 把用户选中的字体文件解析成 [FontFamily]。
 *
 * 文件不在（被系统清理、用户删了）就返回 null，调用方回落系统字体，
 * 而不是拿一个空家族去渲染——空家族会让所有文字变成豆腐块。
 */
@Composable
fun rememberAppFontFamily(font: AppFont): FontFamily? {
    if (!font.isCustom) return null
    val context = LocalContext.current
    return remember(font.fileName) {
        val file = FontStore.fileOf(context, font.fileName)
        if (!file.isFile || file.length() == 0L) {
            null
        } else {
            // 字体文件损坏（截断、被改过）时解析会抛异常，那会让整棵树崩掉；
            // 这里先兜住，坏文件等同于「没设置字体」
            runCatching { FontFamily(Font(file)) }.getOrNull()
        }
    }
}
