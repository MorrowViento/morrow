package com.morrowviento.morrow.data.backdrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 * `filesDir/backdrop/custom.jpg`，之后只认这个副本，跟相册、跟授权都没关系。
 *
 * 文件名固定（不按时间戳累加）：用户换图时旧图会被覆盖，不会在私有目录里越积越多。
 */
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

    fun file(context: Context, name: String? = FILE_NAME): File = File(dir(context), name ?: FILE_NAME)

    /**
     * 把相册里的图复制进来，返回文件名。
     *
     * 解码时按 [MAX_SIDE] 采样，再以 JPEG 90 重编码：现代手机拍的原图动辄 4000×3000、
     * 8 MB，直接存下来既占空间，每次首帧解码也要几十毫秒（进桌面/切页都会掉帧）。
     */
    fun import(context: Context, uri: Uri): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "无法打开所选图片" }
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "这张图读不出来，换一张试试" }

        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "无法打开所选图片" }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("这张图读不出来，换一张试试")

        val target = File(dir(context), FILE_NAME)
        val temp = File(dir(context), "$FILE_NAME.tmp")
        try {
            temp.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (!temp.renameTo(target)) {
                // 某些机型 rename 跨目录/覆盖会失败，退化成「复制 + 删临时文件」
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } finally {
            bitmap.recycle()
            if (temp.exists()) temp.delete()
        }
        synchronized(this) {
            cachedKey = null
            cachedBitmap = null
        }
        return FILE_NAME
    }

    /**
     * 读回背景图（按最长边 [MAX_SIDE] 采样）。
     *
     * 走进程内缓存：`GlassBackdrop` 每次重组都会问一次，而它只在换图 / 重启后失效。
     */
    fun load(context: Context, name: String?): Bitmap? {
        val file = file(context, name)
        if (!file.isFile) return null
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        synchronized(this) {
            cachedBitmap?.let { if (key == cachedKey && !it.isRecycled) return it }
        }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap != null) {
                synchronized(this) {
                    cachedBitmap = bitmap
                    cachedKey = key
                }
            }
            bitmap
        }.onFailure { Log.w(TAG, "读背景图失败 ${file.name}", it) }.getOrNull()
    }

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

    /**
     * 解码采样倍率：让解码结果的长边**不超过** [MAX_SIDE]。
     *
     * 上一版写成了「长边减半后仍不小于 MAX_SIDE 才继续」——4000×3000 的原图会算出
     * `inSampleSize = 1`，直接解出 48 MB 的位图，低端机上很可能 OOM。
     * 现在的条件是「结果还超就继续减半」，4000 → 2（2000），12000 → 8（1500）。
     */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > MAX_SIDE && sample < 32) {
            sample *= 2
        }
        return sample
    }
}
