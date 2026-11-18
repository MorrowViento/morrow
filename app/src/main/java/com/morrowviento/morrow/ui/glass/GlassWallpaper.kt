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
 * 现在：渲染一次，之后 `drawImage` 一次搞定。观感逐像素不变（渲染用的是同一份
 * `CanvasDrawScope` 代码），填充率开销从「6 层 shader」降到「1 次贴图」。
 *
 * ## 为什么渲染必须挪到后台线程（4.1.0）
 *
 * 上一版是「在绘制阶段同步渲染」。第一次进页面时它只发生一次，看不出来；但**切换明暗 /
 * 换配色**会让 key 变，于是正好在最需要流畅的那一帧里，主线程上多出 2.6 M 像素 × 6 层
 * 渐变的工作量——一次换主题就卡三四帧，用户感受是「点一下深色，界面先僵一下再变」。
 *
 * 现在由 [GlassBackdrop] 在 `LaunchedEffect` 里用 `Dispatchers.Default` 调 [render]，
 * 主线程只负责贴图。等待期间显示上一次那张（颜色差一档，几十毫秒），不会闪白也不会僵住。
 *
 * ## 为什么是「多槽缓存」
 *
 * 首页、日历页、设置页、小部件配置页各有一个 `GlassBackdrop`，但同一时刻只有一个在屏幕上。
 * 用进程级缓存按 key 命中：切页不重建；浅色 / 深色来回切时两套位图都留着，**第二次切换
 * 完全不需要渲染**——而「切明暗卡顿」正是用户最常重复的那个动作。
 *
 * 这里刻意**不降分辨率**：整幅图里唯一的细节是那条发丝级高光，而「玻璃厚度差异」正是
 * 靠它被看得清 / 被糊掉来体现的（见 `GlassBackdrop` 的注释）。半分辨率会把它直接抹掉。
 */
internal object GlassWallpaper {

    /**
     * 缓存的槽位数。
     *
     * 4 张 1080×2400 的 ARGB 位图约 40 MB——在低端机上偏多，但它们只在真的被用过之后
     * 才占位（一台手机通常只经历「浅色 / 深色」两套 × 最多几套配色），而且可以随时被
     * 淘汰。给 3 槽足以覆盖「来回切明暗」这个最常用路径。
     */
    private const val CACHE_SLOTS = 3

    /** 按访问顺序淘汰的位图缓存；key 见 [keyOf]。 */
    private val cache = object : LinkedHashMap<Long, ImageBitmap>(CACHE_SLOTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ImageBitmap>?): Boolean =
            size > CACHE_SLOTS
    }

    /** 最近一次渲染出来的那张：换主题等待新图时先拿它顶上，避免闪一下空白。 */
    @Volatile
    private var lastRendered: ImageBitmap? = null

    /**
     * 命中缓存则直接返回，否则 null。
     *
     * 组合期调它（不能在这里渲染）：命中时首帧就有图，不用等一次异步。
     */
    fun cached(widthPx: Int, heightPx: Int, base: Color, dark: Boolean, blobs: LongArray): ImageBitmap? {
        val key = keyOf(widthPx, heightPx, base, dark, blobs)
        synchronized(cache) { return cache[key] }
    }

    /** 上一张渲染结果（任何 key）。 */
    fun placeholder(): ImageBitmap? = lastRendered

    /**
     * 渲染（必要时）。**可以在任意线程调用**：`ImageBitmap` 在 Android 上就是一张
     * 软件位图，`CanvasDrawScope` 只是往它的 `android.graphics.Canvas` 上画，
     * 没有线程亲和性。
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        base: Color,
        dark: Boolean,
        blobs: LongArray,
    ): ImageBitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val key = keyOf(w, h, base, dark, blobs)
        synchronized(cache) { cache[key]?.let { return it } }
        val rendered = renderUncached(w, h, base, dark, blobs)
        synchronized(cache) {
            cache[key] = rendered
            lastRendered = rendered
        }
        return rendered
    }

    // ---------------------------------------------------------------- 绘制

    /** 小部件离线绘制不写首页缓存或 lastRendered。 */
    fun renderUncached(
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
     * （理论上会撞，但撞了只是「显示上一次的配色」——而配色只有七套 × 两态 + 取色档，
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
