package com.morrowviento.morrow.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 丝缎壁纸的**预渲染器**：把整幅底图一次性画进一张位图，之后每帧只贴一次图。
 *
 * ## 为什么值得单独做一层
 *
 * 壁纸是**静态**的——四团色斑、两条光带、一层底色，全部只跟「屏幕尺寸 / 明暗 / 配色方案」
 * 有关，与滚动位置、动画进度毫无关系。但在改这里之前，它在 `drawWithCache` 的
 * `onDrawBehind` 里**每一帧**重新跑一遍：
 *
 * - 4 个 `radialGradient`（每个都要在 GPU 上按像素算距离场）
 * - 2 个 `verticalGradient` 铺在**3 倍屏幕大小**的矩形上，再旋转 18°
 *
 * 也就是每帧约 6 次全屏级（其中一个还是 3×3 倍面积）的渐变填充。玻璃面板还要从这层
 * **再采样一次**做模糊，等于同一批像素被算了两遍。列表滚动、页面切换、底栏拖动时
 * 这些填充全部压在填充率上，是那阵「有点卡」的主要来源之一。
 *
 * 现在：进组合后第一次绘制时渲染一张位图，之后 `drawImage` 一次搞定。观感逐像素不变
 * （渲染用的是同一份 `CanvasDrawScope` 代码），填充率开销从「6 层 shader」降到「1 次贴图」。
 *
 * ## 为什么是「单槽缓存」而不是给每个调用方各自 remember
 *
 * 首页、日历页、设置页、小部件配置页各有一个 `GlassBackdrop`，但它们**同一时刻只有
 * 一个在屏幕上**，尺寸与配色也几乎总是同一个。用一张进程级单槽缓存，四个实例共享同一张
 * 位图：切页不重建、内存里也只常驻一份（1080×2400 约 10 MB）。key 不匹配时才重画。
 *
 * 这里刻意**不降分辨率**：整幅图里唯一的细节是那条发丝级高光，而「玻璃厚度差异」正是
 * 靠它被看得清 / 被糊掉来体现的（见 `GlassBackdrop` 的注释）。半分辨率会把它直接抹掉。
 */
internal object GlassWallpaper {

    /** 上一次渲染的 key；[cacheImage] 与之配对。0 表示还没画过。 */
    private var cacheKey = 0L
    private var cacheImage: ImageBitmap? = null

    /**
     * 取（必要时重画）当前尺寸与配色下的壁纸位图。
     *
     * @param blobs 四团色斑的 ARGB（`0xAARRGGBB`），顺序：暖 / 冷 / 天青 / 深。
     */
    fun image(
        widthPx: Int,
        heightPx: Int,
        base: Color,
        dark: Boolean,
        blobs: LongArray,
    ): ImageBitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val key = keyOf(w, h, base, dark, blobs)
        cacheImage?.let { if (key == cacheKey) return it }
        val rendered = render(w, h, base, dark, blobs)
        cacheKey = key
        cacheImage = rendered
        return rendered
    }

    // ---------------------------------------------------------------- 绘制

    private fun render(
        w: Int,
        h: Int,
        base: Color,
        dark: Boolean,
        blobs: LongArray,
    ): ImageBitmap {
        val width = w.toFloat()
        val height = h.toFloat()
        val image = ImageBitmap(w, h)
        val canvas = Canvas(image)

        val sheen = Color.White.copy(alpha = if (dark) 0.20f else 0.60f)
        // 浅色下折痕的暗面用中性冷灰：白色在浅底上不可能更亮
        val shade = (if (dark) Color.Black else Color(0xFF3F4956))
            .copy(alpha = if (dark) 0.70f else 0.35f)
        // 发丝线在浅色下走暗色，白线在 #F2F2F7 上等于隐形
        val edge = if (dark) sheen else shade.copy(alpha = 0.30f)

        val gradients = arrayOf(
            Brush.radialGradient(
                listOf(Color(blobs[0]), Color(blobs[0]).copy(alpha = 0f)),
                center = Offset(width * 0.08f, height * 0.05f),
                radius = height * 0.55f,
            ),
            Brush.radialGradient(
                listOf(Color(blobs[1]), Color(blobs[1]).copy(alpha = 0f)),
                center = Offset(width * 0.98f, height * 0.30f),
                radius = height * 0.62f,
            ),
            Brush.radialGradient(
                listOf(Color(blobs[2]), Color(blobs[2]).copy(alpha = 0f)),
                center = Offset(width * 0.18f, height * 0.90f),
                radius = height * 0.65f,
            ),
            Brush.radialGradient(
                listOf(Color(blobs[3]), Color(blobs[3]).copy(alpha = 0f)),
                center = Offset(width * 0.92f, height * 1.00f),
                radius = height * 0.55f,
            ),
        )
        // 光带画在「3 倍屏幕大小、以屏幕为中心」的一块矩形上（旋转后仍能盖满四角），
        // 所以屏幕上的 0..1 只落在这块矩形的中间三分之一：bandY(p) = (p + 1) / 3。
        // 写死 0..1 的话光带会整条跑到屏幕外。
        fun bandY(p: Float): Float = (p + 1f) / 3f
        // 宽光带：一条柔和的亮面 + 一条相邻的暗面，形成「布料折了一下」的体积
        val broad = Brush.verticalGradient(
            bandY(0.00f) to sheen.copy(alpha = 0f),
            bandY(0.26f) to sheen.copy(alpha = 0f),
            bandY(0.33f) to sheen.copy(alpha = sheen.alpha * 0.62f),
            bandY(0.40f) to sheen.copy(alpha = 0f),
            bandY(0.56f) to shade.copy(alpha = shade.alpha * 0.75f),
            bandY(0.63f) to shade.copy(alpha = 0f),
            bandY(0.80f) to sheen.copy(alpha = sheen.alpha * 0.30f),
            bandY(0.88f) to sheen.copy(alpha = 0f),
        )
        // 发丝高光：屏幕上只有一两个像素宽，模糊一开就整条化掉——
        // 「通透」和「厚重」的差别，一半是靠它被看清 / 被糊掉
        val hairline = Brush.verticalGradient(
            bandY(0.000f) to edge.copy(alpha = 0f),
            bandY(0.462f) to edge.copy(alpha = 0f),
            bandY(0.4655f) to edge,
            bandY(0.469f) to edge.copy(alpha = 0f),
            bandY(0.700f) to edge.copy(alpha = 0f),
            bandY(0.7030f) to edge.copy(alpha = edge.alpha * 0.8f),
            bandY(0.7060f) to edge.copy(alpha = 0f),
        )
        val bandLeft = Offset(-width, -height)
        val bandSize = Size(width * 3f, height * 3f)

        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(width, height),
        ) {
            drawRect(base)
            for (index in gradients.indices) drawRect(gradients[index])
            // 倾斜 18°：光带斜穿屏幕，不会被卡片整块盖住而失去参照
            rotate(-18f, pivot = Offset(width * 0.5f, height * 0.5f)) {
                drawRect(broad, topLeft = bandLeft, size = bandSize)
                drawRect(hairline, topLeft = bandLeft, size = bandSize)
            }
        }
        return image
    }

    // ---------------------------------------------------------------- key

    /**
     * 缓存 key。
     *
     * 尺寸 + 明暗 + 底色 + 四团色斑就是这幅图的全部输入。混进 64 位里足够区分
     * （理论上会撞，但撞了只是「显示上一次的配色」——而配色只有七套 × 两态，
     * 实际输入组合只有十几种，撞不上）。
     */
    private fun keyOf(w: Int, h: Int, base: Color, dark: Boolean, blobs: LongArray): Long {
        var key = w.toLong() * 73856093L
        key = key xor (h.toLong() * 19349663L)
        // `Color.value` 是 ULong：`toLong()` 是位重解释，正好当 hash 用
        key = key xor (base.value.toLong() * 83492791L)
        if (dark) key = key xor 0x5DEECE66DL
        for (index in blobs.indices) {
            key = key * 31L + blobs[index]
        }
        // 0 留作「还没画过」的哨兵
        return if (key == 0L) 1L else key
    }
}
