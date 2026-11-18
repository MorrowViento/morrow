package com.morrowviento.morrow.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * 当前应用字体（未设置为自定义字体时为 null）。
 *
 * 用 `static`：字体是整棵树一起变的东西，没必要让每个 Text 都登记一次读取。
 */
val LocalAppFontFamily = staticCompositionLocalOf<FontFamily?> { null }

/**
 * 与 `androidx.compose.material3.Text` 同签名的包装，唯一区别是把当前应用字体
 * 注入到没有显式指定 `fontFamily` 的文本上。
 *
 * **为什么需要它**：项目里有 148 处 `TextStyle(fontSize = ...)`，全都没写 `fontFamily`。
 * 本来最省事的做法是替换 `LocalFontFamilyResolver`，但 `FontFamily.Resolver` 是
 * `sealed interface`，跨模块不允许实现，这条路走不通。于是改成「同名同签名替换导入」：
 * 各文件把 `import androidx.compose.material3.Text` 换成
 * `import com.morrowviento.morrow.ui.component.Text`，调用点一个字都不用改。
 *
 * 显式传了 `fontFamily` 的地方仍以调用方为准——包装只管「没指定的」。
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val appFamily = LocalAppFontFamily.current
    // 只补「没指定家族」的情况：`style.fontFamily == null` 才注入，
    // 免得把某处特意指定的等宽/图标字体顶掉
    val applied = if (appFamily != null && style.fontFamily == null) {
        style.copy(fontFamily = appFamily)
    } else {
        style
    }
    MaterialText(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = applied,
    )
}
