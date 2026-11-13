package com.morrowviento.morrow.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.morrowviento.morrow.data.settings.AccentColor

/**
 * 小部件的「液态玻璃」外观。
 *
 * ## 为什么是画一张位图
 *
 * 应用内的质感来自**实时模糊背后的壁纸**，RemoteViews 没有这条通道：`GlassSurface`
 * 是 Compose 的绘制，跨进程给小部件用不了；`android:background` 只能是静态 drawable，
 * 而配色是用户可选、运行时才知道的，XML 里写不了。
 *
 * 所以这里用 Canvas 把「同一幅壁纸」画进一张位图：同样的四团色斑、同样的斜向光带、
 * 同样的圆角与描边。桌面拿到的是一张图，静态、零成本，但观感与应用内的卡片同源——
 * 换配色时位图跟着重画，小部件与 App 不会各说各话。
 *
 * ## 与应用内的对应关系
 *
 * | 应用内 | 这里 |
 * |---|---|
 * | `GlassBackdrop` 的四团径向色斑 | 同 `AccentColor.blobs(dark)`，同相对位置 |
 * | 斜向光带（布料折了一下） | 一条对角 LinearGradient |
 * | 玻璃上沿收光 | 顶部 34% 高度的白色柔光 + 1.5dp 发丝高光 |
 * | 玻璃有厚度 | 底部 24% 的暗渐变（内阴影） |
 * | 边缘折射出一圈亮 | 沿圆角画模糊描边（只留内侧那半，见 [render] 第 6 步） |
 * | 卡片圆角 22dp | 同 22dp，位图外圈透明 |
 *
 * 位图按小部件实际尺寸生成（见 [render]），圆角不会被拉伸成椭圆。
 */
internal object WidgetAppearance {

    /** 与应用内卡片一致的圆角。 */
    private const val CornerRadiusDp = 22f

    /** 四个色斑的中心（相对宽高的比例），与应用内 `GlassBackdrop` 同序：暖 / 冷 / 天青 / 深。 */
    private val BlobCenters = arrayOf(
        0.08f to 0.05f,
        0.98f to 0.30f,
        0.18f to 0.90f,
        0.92f to 1.00f,
    )

    /**
     * 生成一张小部件背景。
     *
     * @param vivid true = 「主题色」档：底色以强调色为主，色斑往强调色上偏；
     *   false = 「玻璃」档：底色是中性玻璃，色斑保持配色方案原本的冷暖。
     * @return 一张 [widthPx] × [heightPx] 的位图，圆角外是透明的。
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        accent: AccentColor,
        dark: Boolean,
        vivid: Boolean,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = CornerRadiusDp * density
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        // 1) 底色。上一版压得太深（深色 #1A1A20、主题色档压到只剩 38% 强调色），
        //    在桌面上看着像一块黑色板子——玻璃本身是「浅而透」的，底色一黑，
        //    上面的色斑与高光就全浮不出来。这里整体抬亮一档。
        val accentArgb = accent.argb(dark).toInt()
        val base = when {
            vivid && dark -> blend(accentArgb, BLACK, 0.40f)
            vivid -> blend(accentArgb, WHITE, 0.74f)
            dark -> rgb(42, 42, 52)
            else -> rgb(250, 250, 253)
        }
        canvas.drawColor(base)

        // 2) 四团色斑。半径给到长边的 0.70，几团光连成一片才像「被模糊过的壁纸」，
        //    小一点就退化成四个彩色圆点。深色下先把色斑往白里提一档：
        //    暗色斑铺在暗底上等于没有颜色。
        val blobs = accent.blobs(dark)
        val blobRadius = maxOf(w, h) * 0.70f
        val blobAlpha = if (dark) 205 else 215
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        BlobCenters.forEachIndexed { index, (fx, fy) ->
            val raw = blobs[index].toInt()
            val lifted = if (dark) blend(raw, WHITE, 0.24f) else raw
            val tint = if (vivid) blend(accentArgb, lifted, 0.40f) else lifted
            paint.shader = RadialGradient(
                w * fx,
                h * fy,
                blobRadius,
                withAlpha(tint, blobAlpha),
                withAlpha(tint, 0),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(rect, paint)
        }
        paint.shader = null

        // 3) 斜向光带：一条亮面接一条暗面，位置与应用内那条折痕一致（斜 18°，
        //    屏幕上大约落在 46% 高度处）。小部件矮，所以直接用对角渐变近似。
        val sheen = if (dark) 0.17f else 0.46f
        val shade = if (dark) 0.16f else 0.10f
        paint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                withAlpha(WHITE, 0),
                withAlpha(WHITE, (255 * sheen).toInt()),
                withAlpha(WHITE, 0),
                withAlpha(BLACK, (255 * shade).toInt()),
            ),
            floatArrayOf(0.0f, 0.42f, 0.58f, 1.0f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(rect, paint)
        paint.shader = null

        // 4) 顶部柔光 + 底部内阴影。
        //
        //    这两条是「玻璃有厚度」最省事也最有效的表达：上沿把环境光收进来，
        //    下沿压一点暗，中间就鼓起来了。只画一条 1.5dp 的发丝线是不够的——
        //    远看那只是一个白边，读不出体积。
        val topGlow = h * 0.34f
        paint.shader = LinearGradient(
            0f, 0f, 0f, topGlow,
            withAlpha(WHITE, if (dark) 50 else 100),
            withAlpha(WHITE, 0),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), topGlow, paint)
        paint.shader = null

        val bottomShade = h * 0.24f
        paint.shader = LinearGradient(
            0f, h.toFloat(), 0f, h - bottomShade,
            withAlpha(BLACK, if (dark) 70 else 26),
            withAlpha(BLACK, 0),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, h - bottomShade, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // 5) 顶部发丝高光：玻璃上沿被光擦亮的那一条，1.5dp 就够，再宽就像贴了张白纸。
        val highlight = 1.5f * density
        paint.shader = LinearGradient(
            0f, 0f, 0f, highlight,
            withAlpha(WHITE, if (dark) 90 else 180),
            withAlpha(WHITE, 0),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), highlight, paint)
        paint.shader = null

        // 6) 内发光：沿圆角画一圈**模糊的**亮边，一半被裁剪掉，只剩内侧那圈柔光。
        //    这是玻璃「边缘在折射光」的替身——静态位图做不了真折射，但一圈柔光
        //    足以让边缘看起来是有厚度的弧面，而不是一条 1px 的线。
        val glowWidth = 3f * density
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = glowWidth
            color = if (dark) withAlpha(WHITE, 64) else withAlpha(WHITE, 150)
            maskFilter = BlurMaskFilter(glowWidth * 0.9f, BlurMaskFilter.Blur.NORMAL)
        }
        val glowInset = glowWidth / 2f
        canvas.drawRoundRect(
            RectF(glowInset, glowInset, w - glowInset, h - glowInset),
            radius,
            radius,
            glow,
        )

        canvas.restore()

        // 7) 一圈描边：浅色壁纸上没有它，小部件会与桌面糊在一起。
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = if (dark) withAlpha(WHITE, 56) else withAlpha(BLACK, 30)
        }
        val inset = stroke.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, w - inset, h - inset),
            radius,
            radius,
            stroke,
        )
        return bitmap
    }

    /** 文字色：深底白、浅底近黑，与 `GlassColors.content` 同一档。 */
    fun textPrimary(dark: Boolean): Int = if (dark) WHITE else INK

    fun textSecondary(dark: Boolean): Int = if (dark) withAlpha(WHITE, 158) else withAlpha(INK_SOFT, 153)

    /** 进度条着色：直接用强调色，与小部件上的天数徽章同一个来源。 */
    fun accentOf(accent: AccentColor, dark: Boolean): Int = accent.argb(dark).toInt()

    // ---------------------------------------------------------------- 工具
    //
    // 颜色运算全部走位运算而不是 `android.graphics.Color` 的静态方法：
    // `WidgetStyle.colors()` 是纯函数，单测要在 JVM 上直接断言（没有 Android 运行时，
    // 调用 `Color.rgb` 会抛 "not mocked"）。位运算版本在两边行为完全一致。

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val INK = 0xFF1C1C1E.toInt()
    private const val INK_SOFT = 0xFF3C3C43.toInt()

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    private fun red(color: Int): Int = (color shr 16) and 0xFF

    private fun green(color: Int): Int = (color shr 8) and 0xFF

    private fun blue(color: Int): Int = color and 0xFF

    private fun rgb(r: Int, g: Int, b: Int): Int =
        BLACK or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** 按比例把 [color] 往 [target] 混（比例越大越接近 target）。 */
    private fun blend(color: Int, target: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        return rgb(
            (red(color) + (red(target) - red(color)) * t).toInt(),
            (green(color) + (green(target) - green(color)) * t).toInt(),
            (blue(color) + (blue(target) - blue(color)) * t).toInt(),
        )
    }
}
