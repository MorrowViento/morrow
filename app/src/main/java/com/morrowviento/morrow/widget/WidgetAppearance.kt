package com.morrowviento.morrow.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import com.morrowviento.morrow.data.backdrop.BackdropImage
import com.morrowviento.morrow.data.settings.AccentPalette
import com.morrowviento.morrow.data.settings.AppSettings
import com.morrowviento.morrow.data.settings.BackdropMode
import com.morrowviento.morrow.ui.glass.GlassOptics
import com.morrowviento.morrow.ui.glass.GlassWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 模糊应用自己的壁纸像素，不采样启动器壁纸；原生文字不进入位图。 */
internal object WidgetAppearance {
    const val MAX_PIXELS = 262_144
    const val CACHE_BYTES = 8 * 1024 * 1024
    private const val WORK_SIDE = 384
    private val cache = LinkedHashMap<RenderKey, Bitmap>(8, 0.75f, true)
    private val renderMutex = Mutex()
    internal val cachedBytes: Int get() = synchronized(cache) { cache.values.sumOf { it.allocationByteCount } }

    internal data class RenderKey(
        val width: Int,
        val height: Int,
        val density: Float,
        val imageIdentity: String?,
        val palette: AccentPalette,
        val dark: Boolean,
        val vivid: Boolean,
        val frost: Float,
        val opaque: Boolean,
    )

    /** 同比缩小且同时限制长边和总像素，保持圆角在宿主坐标中的 dp 大小。 */
    internal fun boundedSize(width: Int, height: Int, maxPixels: Int = MAX_PIXELS): Pair<Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val scale = minOf(1.0, 1024.0 / maxOf(w, h), sqrt(maxPixels.coerceIn(1, MAX_PIXELS).toDouble() / (w.toDouble() * h)))
        return (w * scale).toInt().coerceAtLeast(1) to (h * scale).toInt().coerceAtLeast(1)
    }

    suspend fun render(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        settings: AppSettings,
        colors: WidgetColors,
        maxPixels: Int = MAX_PIXELS,
    ): Bitmap {
        val imageMode = settings.backdrop.effectiveMode == BackdropMode.IMAGE
        val identity = if (imageMode) withContext(Dispatchers.IO) {
            BackdropImage.file(context, settings.backdrop.image).let {
                "${it.absolutePath}:${it.lastModified()}:${it.length()}"
            }
        } else null
        val (w, h) = boundedSize(widthPx, heightPx, maxPixels)
        val scaledDensity = density * minOf(w.toFloat() / widthPx.coerceAtLeast(1), h.toFloat() / heightPx.coerceAtLeast(1))
        val key = RenderKey(w, h, scaledDensity, identity, settings.resolvedAccent,
            colors.dark, colors.vivid, settings.glassFrost, settings.usesOpaqueGlass)
        synchronized(cache) { cache[key]?.let { return it } }
        return withContext(Dispatchers.Default) {
            // 用挂起锁串行工作图；不能持有 cache monitor 渲染，否则主线程查询也会被阻塞。
            renderMutex.withLock {
                synchronized(cache) { cache[key] } ?: run {
                    val image = if (imageMode) withContext(Dispatchers.IO) {
                        BackdropImage.load(context, settings.backdrop.image)
                    } else null
                    val bitmap = draw(key, image)
                    synchronized(cache) {
                        cache[key] = bitmap
                        while (cache.values.sumOf { it.allocationByteCount } > CACHE_BYTES) {
                            cache.remove(cache.keys.first()) // 不 recycle 已借给 Compose/RemoteViews 的图。
                        }
                    }
                    bitmap
                }
            }
        }
    }

    private fun draw(key: RenderKey, image: Bitmap?): Bitmap {
        val scale = minOf(1f, WORK_SIDE.toFloat() / maxOf(key.width, key.height))
        val w = (key.width * scale).roundToInt().coerceAtLeast(1)
        val h = (key.height * scale).roundToInt().coerceAtLeast(1)
        val base = if (key.dark) BLACK else 0xFFF2F2F7.toInt()
        val work = if (image == null) {
            GlassWallpaper.renderUncached(w, h, Color(base), key.dark, key.palette.blobs(key.dark)).asAndroidBitmap()
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(base)
                val cover = maxOf(w.toFloat() / image.width, h.toFloat() / image.height)
                val dw = image.width * cover
                val dh = image.height * cover
                canvas.drawBitmap(image, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                canvas.drawColor(withAlpha(base, if (key.dark) 0.30f else 0.16f))
            }
        }
        try {
            blur(work, (GlassOptics.frostRadiusDp(key.frost) * key.density * scale / 2f).roundToInt().coerceAtLeast(1))
            val result = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val rect = RectF(0f, 0f, key.width.toFloat(), key.height.toFloat())
            val path = GlassOptics.cardPath(key.width, key.height, key.density)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(work, null, rect, paint)
            val elevated = if (key.dark) INK else WHITE
            canvas.drawColor(withAlpha(elevated, GlassOptics.veilAlpha(key.frost, key.opaque)))
            canvas.drawColor(withAlpha(key.palette.argb(key.dark).toInt(),
                if (key.vivid) 0.18f else GlassOptics.accentAlpha(key.dark)))
            paint.shader = LinearGradient(0f, 0f, 0f, key.height.toFloat(),
                intArrayOf(withAlpha(WHITE, if (key.dark) 0.10f else 0.16f), 0, withAlpha(BLACK, 0.04f)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = key.density.coerceAtLeast(0.5f)
            paint.color = withAlpha(WHITE, if (key.dark) 0.22f else 0.38f)
            canvas.drawPath(path, paint)
            canvas.restore()
            return result
        } finally {
            work.recycle() // 仅本次渲染拥有的工作图；不动 BackdropImage 的共享源图。
        }
    }

    /** 三次可分离盒滤波近似高斯，预乘 alpha 避免透明像素 RGB 产生暗边。 */
    internal fun blur(bitmap: Bitmap, radius: Int) {
        if (radius <= 0) return
        val w = bitmap.width
        val h = bitmap.height
        var pixels = IntArray(w * h)
        var buffer = IntArray(pixels.size)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        pixels.indices.forEach { i ->
            val p = pixels[i]
            val a = p ushr 24
            pixels[i] = (a shl 24) or ((((p ushr 16) and 255) * a / 255) shl 16) or
                ((((p ushr 8) and 255) * a / 255) shl 8) or ((p and 255) * a / 255)
        }
        repeat(3) {
            filter(pixels, buffer, w, h, radius, horizontal = true)
            val swap = pixels; pixels = buffer; buffer = swap
            filter(pixels, buffer, w, h, radius, horizontal = false)
            val swapBack = pixels; pixels = buffer; buffer = swapBack
        }
        pixels.indices.forEach { i ->
            val p = pixels[i]
            val a = p ushr 24
            pixels[i] = if (a == 0) 0 else (a shl 24) or
                (((((p ushr 16) and 255) * 255 / a).coerceAtMost(255)) shl 16) or
                (((((p ushr 8) and 255) * 255 / a).coerceAtMost(255)) shl 8) or
                (((p and 255) * 255 / a).coerceAtMost(255))
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun filter(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val length = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        val window = radius * 2 + 1
        repeat(lines) { line ->
            val start = if (horizontal) line * w else line
            var a = 0; var r = 0; var g = 0; var b = 0
            fun add(p: Int, sign: Int) {
                a += (p ushr 24) * sign; r += ((p ushr 16) and 255) * sign
                g += ((p ushr 8) and 255) * sign; b += (p and 255) * sign
            }
            for (i in -radius..radius) add(src[start + i.coerceIn(0, length - 1) * step], 1)
            repeat(length) { i ->
                dst[start + i * step] = ((a / window) shl 24) or ((r / window) shl 16) or
                    ((g / window) shl 8) or (b / window)
                add(src[start + (i - radius).coerceIn(0, length - 1) * step], -1)
                add(src[start + (i + radius + 1).coerceIn(0, length - 1) * step], 1)
            }
        }
    }

    fun textPrimary(dark: Boolean): Int = if (dark) WHITE else INK
    fun textSecondary(dark: Boolean): Int = if (dark) 0x9EFFFFFF.toInt() else 0x993C3C43.toInt()
    fun separator(dark: Boolean): Int = if (dark) 0x1EFFFFFF else 0x1A000000
    fun accentOf(accent: AccentPalette, dark: Boolean): Int = accent.argb(dark).toInt()
    fun rowTint(colors: WidgetColors, frost: Float, opaque: Boolean): Int =
        androidx.core.graphics.ColorUtils.compositeColors(
            withAlpha(colors.accent, GlassOptics.accentAlpha(colors.dark)),
            withAlpha(if (colors.dark) INK else WHITE, GlassOptics.veilAlpha(frost, opaque)),
        )
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val INK = 0xFF1C1C1E.toInt()
    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24)
}
