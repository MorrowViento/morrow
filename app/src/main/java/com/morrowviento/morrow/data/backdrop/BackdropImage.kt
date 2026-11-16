package com.morrowviento.morrow.data.backdrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.morrowviento.morrow.data.settings.argbToHsl
import com.morrowviento.morrow.data.settings.hslToArgb
import java.io.File
import kotlin.math.pow

/**
 * 自定义背景图：**取色主色**（纯函数，可在 JVM 上单测）。
 *
 * ## 为什么不能直接求平均色
 *
 * 一张照片的平均色几乎总是「灰蓝 / 土黄」：天空、白墙、阴影占了大半面积，
 * 平均值天然低饱和、偏暗，拿去当强调色会让按钮发脏、选中态看不出选中。
 * 所以这里做三件事：
 *
 * 1. **按色相分桶**（24 个 15° 的桶 + 一个「近无彩」桶），避免分散的杂色互相抵消；
 * 2. 每像素的权重按 `s^1.2 × (0.3 + l)` 算——偏好**鲜艳且不太暗**的部分，
 *    照片里那几块真正有颜色的区域（晚霞、草地、衣服）才能浮上来；
 * 3. 取权重最大的桶做加权平均，得到「这张图的代表色」。
 *
 * 最后一步只做色相与彩度的归一化（见 `AccentPalette.fromSeed`），亮度不采信。
 */
object PaletteExtractor {

    /** 会明显影响结果的像素数下限：太少说明图是纯色或近乎全透明。 */
    private const val MIN_WEIGHT = 1e-3

    /**
     * 从像素数组里取代表色。
     *
     * @return ARGB；样本全透明 / 全黑到无法判断时返回 0（调用方回落到原配色）。
     */
    fun dominantSeed(pixels: IntArray): Long {
        if (pixels.isEmpty()) return 0L
        val buckets = 25
        val weight = DoubleArray(buckets)
        val sumR = DoubleArray(buckets)
        val sumG = DoubleArray(buckets)
        val sumB = DoubleArray(buckets)

        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 96) continue
            val argb = 0xFF000000L or (pixel.toLong() and 0x00FFFFFFL)
            val (hue, saturation, lightness) = argbToHsl(argb)
            // 近无彩的像素单独归一类：它们不该把某个色相桶的「代表色」拉灰
            val index = if (saturation < 0.10f) buckets - 1 else (hue / 15f).toInt().coerceIn(0, 23)
            val w = saturation.toDouble().pow(1.2) * (0.30 + lightness)
            weight[index] += w
            sumR[index] += ((pixel shr 16) and 0xFF) * w
            sumG[index] += ((pixel shr 8) and 0xFF) * w
            sumB[index] += (pixel and 0xFF) * w
        }

        val best = weight.indices.maxByOrNull { weight[it] } ?: return 0L
        if (weight[best] < MIN_WEIGHT) return 0L
        val total = weight[best]
        val r = (sumR[best] / total).toInt().coerceIn(0, 255)
        val g = (sumG[best] / total).toInt().coerceIn(0, 255)
        val b = (sumB[best] / total).toInt().coerceIn(0, 255)

        // 命中的是「近无彩」桶：这张图没有可用色相，给一支冷静的蓝。
        // 不能把灰色当种子——fromSeed 会把它归一化成浑浊的蓝灰，等于没取色。
        if (best == buckets - 1) return hslToArgb(210f, 0.55f, 0.52f)
        return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /** 从一张已解码的位图取代表色（内部缩到 48×48 再算，几万像素足够稳定）。 */
    fun dominantSeed(bitmap: Bitmap): Long {
        val side = 48
        val scaled = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        scaled.getPixels(pixels, 0, side, 0, 0, side, side)
        if (scaled !== bitmap) scaled.recycle()
        return dominantSeed(pixels)
    }
}

/**
 * 背景图的落盘与读回。
 *
 * ## 为什么复制一份到私有目录
 *
 * 相册 uri 的读权限是**临时授予**的：进程重启、用户清理缓存、系统回收授权之后，
 * 同一个 uri 就再也打不开了。如果直接存 uri，用户会遇到「某天打开 App，背景变回纯色
 * 而且怎么都恢复不了」——这种 bug 极难自查。选图当下就复制到
 * `filesDir/backdrop/`，之后只认这个副本，跟相册、跟授权都没关系。
 *
 * 每次使用唯一文件名驱动 UI 换图；设置提交成功后清理旧图，失败时仍保留旧背景。
 * 旧版 custom.jpg 继续可读。
 */
data class BackdropCrop(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun transform(
        imageWidth: Int, imageHeight: Int, aspectRatio: Float,
        focusX: Float, focusY: Float, panX: Float, panY: Float, zoom: Float,
    ): BackdropCrop {
        val base = centered(imageWidth, imageHeight, aspectRatio)
        val nextWidth = (width / zoom).coerceIn(base.width / 8f, base.width)
        val nextHeight = nextWidth / aspectRatio
        return BackdropCrop(
            (left + focusX * (width - nextWidth) - panX * nextWidth)
                .coerceIn(0f, (imageWidth - nextWidth).coerceAtLeast(0f)),
            (top + focusY * (height - nextHeight) - panY * nextHeight)
                .coerceIn(0f, (imageHeight - nextHeight).coerceAtLeast(0f)),
            nextWidth, nextHeight,
        )
    }

    companion object {
        fun centered(imageWidth: Int, imageHeight: Int, aspectRatio: Float): BackdropCrop {
            require(imageWidth > 0 && imageHeight > 0 && aspectRatio.isFinite() && aspectRatio > 0f)
            val width = minOf(imageWidth.toFloat(), imageHeight * aspectRatio)
            val height = minOf(imageHeight.toFloat(), width / aspectRatio)
            return BackdropCrop((imageWidth - width) / 2f, (imageHeight - height) / 2f, width, height)
        }
    }
}

object BackdropImage {

    private const val TAG = "MorrowBackdrop"

    /** 背景图的最长边上限（px）。 */
    private const val MAX_SIDE = 2400

    private const val FILE_NAME = "custom.jpg"

    /**
     * 进程内解码缓存：同一张图只解一次，换主题时不必重新读盘。
     *
     * **不 recycle 旧的位图**。它此刻可能还被 `GlassBackdrop` 拿在手里画（换图时
     * 组合还引用着上一张），`recycle()` 会让下一帧的 `drawImage` 抛
     * "Canvas: trying to use a recycled bitmap"——偶发闪退，且只在「刚换完图」出现，
     * 极难复现。交给 GC 回收，最多多占几十 MB 一小会儿。
     */
    private var cachedKey: String? = null
    private var cachedBitmap: Bitmap? = null

    fun dir(context: Context): File = File(context.filesDir, "backdrop").apply { mkdirs() }

    fun file(context: Context, name: String? = FILE_NAME): File {
        val safeName = name ?: FILE_NAME
        require(safeName != "." && safeName != ".." && safeName.matches(Regex("[A-Za-z0-9._-]+"))) { "无效的背景文件名" }
        return File(dir(context), safeName)
    }

    /**
     * 把相册里的图复制进来，返回独立文件名与同次解码的取色结果。
     *
     * 解码时按 [MAX_SIDE] 采样，再以 JPEG 90 重编码：现代手机拍的原图动辄 4000×3000、
     * 8 MB，直接存下来既占空间，每次首帧解码也要几十毫秒（进桌面/切页都会掉帧）。
     */
    data class Imported(val name: String, val seed: Long)

    fun prepare(context: Context, uri: Uri): Bitmap =
        decode(ImageDecoder.createSource(context.contentResolver, uri))

    fun import(context: Context, uri: Uri): Imported = publish(context, prepare(context, uri))

    fun importCropped(context: Context, source: Bitmap, crop: BackdropCrop): Imported {
        require(listOf(crop.left, crop.top, crop.width, crop.height).all { it.isFinite() })
        require(crop.left >= 0f && crop.top >= 0f && crop.width > 0f && crop.height > 0f)
        require(crop.left + crop.width <= source.width + 0.01f && crop.top + crop.height <= source.height + 0.01f)
        val bitmap = Bitmap.createBitmap(
            crop.width.toInt().coerceAtLeast(1), crop.height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888,
        )
        try {
            val matrix = android.graphics.Matrix().apply {
                setRectToRect(
                    RectF(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height),
                    RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                    android.graphics.Matrix.ScaleToFit.FILL,
                )
            }
            Canvas(bitmap).drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
        return publish(context, bitmap)
    }

    private fun publish(context: Context, bitmap: Bitmap): Imported {
        val target = File(dir(context), "backdrop-${java.util.UUID.randomUUID()}.jpg")
        val temp = File(dir(context), "${target.name}.tmp")
        var published = false
        try {
            val seed = PaletteExtractor.dominantSeed(bitmap)
            temp.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) { "无法保存所选图片" }
            }
            check(temp.renameTo(target)) { "无法保存所选图片" }
            synchronized(this) {
                cachedKey = cacheKey(target)
                cachedBitmap = bitmap
            }
            published = true
            return Imported(target.name, seed)
        } finally {
            temp.delete()
            if (!published) {
                target.delete()
                bitmap.recycle()
            }
        }
    }

    /**
     * 读回背景图（按最长边 [MAX_SIDE] 采样）。
     *
     * 走进程内缓存：`GlassBackdrop` 每次重组都会问一次，而它只在换图 / 重启后失效。
     */
    fun load(context: Context, name: String?): Bitmap? = runCatching {
        val file = file(context, name)
        if (!file.isFile) return null
        val key = cacheKey(file)
        synchronized(this) {
            cachedBitmap?.let { if (key == cachedKey && !it.isRecycled) return it }
        }
        val bitmap = decode(ImageDecoder.createSource(file))
        synchronized(this) {
            cachedBitmap = bitmap
            cachedKey = key
        }
        bitmap
    }.onFailure { Log.w(TAG, "读背景图失败", it) }.getOrNull()

    /** 取主色。失败返回 0（调用方保留原配色）。 */
    fun seedOf(context: Context, name: String?): Long {
        val bitmap = load(context, name) ?: return 0L
        return runCatching { PaletteExtractor.dominantSeed(bitmap) }.getOrDefault(0L)
    }

    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
        synchronized(this) {
            cachedKey = null
            cachedBitmap = null
        }
    }

    fun remove(context: Context, name: String) {
        file(context, name).delete()
        synchronized(this) {
            if (cachedKey?.startsWith(file(context, name).absolutePath + ":") == true) {
                cachedKey = null
                cachedBitmap = null
            }
        }
    }

    /** 设置提交成功后才回收旧文件，不 recycle 已借给 UI 的位图。 */
    fun retain(context: Context, name: String) {
        dir(context).listFiles()?.filter { it.name != name }?.forEach { it.delete() }
    }

    private fun cacheKey(file: File) = "${file.absolutePath}:${file.lastModified()}:${file.length()}"

    private fun decode(source: ImageDecoder.Source): Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > MAX_SIDE) {
            val ratio = MAX_SIDE.toDouble() / longest
            decoder.setTargetSize(
                (info.size.width * ratio).toInt().coerceAtLeast(1),
                (info.size.height * ratio).toInt().coerceAtLeast(1),
            )
        }
    }
}
