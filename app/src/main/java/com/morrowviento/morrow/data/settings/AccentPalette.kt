package com.morrowviento.morrow.data.settings

import androidx.compose.runtime.Immutable

/**
 * 一套「配色」的完整描述：强调色 + 壁纸四团色斑（明暗各一组）。
 *
 * ## 为什么要从 enum 升成数据类
 *
 * 之前配色是 [AccentColor] 这个 enum，七套值写死在源码里。现在多了一档
 * 「从背景图取色」——颜色要到运行时才知道，enum 装不下。于是把 enum 降级成
 * **预设表**，真正被界面与玻璃层消费的是本类：[AccentColor.palette] 把预设摊平成它，
 * `AccentPalette.fromSeed` 把任意一张图的种子色摊平成它。两条来源产出同一种值，
 * 下游（`GlassTheme` / `GlassBackdrop` / 小部件）就不必关心颜色是选的还是取来的。
 *
 * ## equal / hashCode 必须按内容比
 *
 * 这是本类最容易踩的一处：`data class` 对 `LongArray` 用的是**引用**相等，
 * 而 `resolvedAccent` 每次调用都可能产出新实例。若相等判断退化成引用比较，
 * 上层 `key(darkTheme, accent)` 会认为「每帧都换了配色」——整棵主题子树每帧重建，
 * 于是换一次明暗之后就永久卡顿。因此这里手写按内容的 equals / hashCode。
 */
@Immutable
data class AccentPalette(
    /** 稳定标识：预设的 `prefValue`，取色档固定为 [AUTO_ID]。 */
    val id: String,
    val label: String,
    /** 浅色主题下的强调色（ARGB，`0xAARRGGBB`）。 */
    val light: Long,
    /** 深色主题下的强调色（同色系提亮一档）。 */
    val dark: Long,
    /** 壁纸四团色斑（暖 / 冷 / 天青 / 深），顺序与 `GlassBackdrop` 一一对应。 */
    val blobsLight: LongArray,
    val blobsDark: LongArray,
) {
    fun argb(isDark: Boolean): Long = if (isDark) dark else light

    fun blobs(isDark: Boolean): LongArray = if (isDark) blobsDark else blobsLight

    /** 取色档：七套预设之外的第八种来源。 */
    val isAuto: Boolean get() = id == AUTO_ID

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccentPalette) return false
        return id == other.id &&
            label == other.label &&
            light == other.light &&
            dark == other.dark &&
            blobsLight.contentEquals(other.blobsLight) &&
            blobsDark.contentEquals(other.blobsDark)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + light.hashCode()
        result = 31 * result + dark.hashCode()
        result = 31 * result + blobsLight.contentHashCode()
        result = 31 * result + blobsDark.contentHashCode()
        return result
    }

    companion object {
        const val AUTO_ID = "auto"

        /**
         * 由一张背景图的主色（种子 ARGB）推导整套配色。
         *
         * 规则刻意保守：**只借色相与彩度，亮度自己定**。
         *
         * 直接拿照片平均色当强调色会得到一堆「灰蓝 / 土黄」——照片里大面积是天空、
         * 墙面、阴影，平均值天然低饱和、偏暗；直接铺到界面上，按钮就脏了。
         * 所以这里只取 hue，饱和度夹到 0.5 以上，亮度压到能承载白字的区间。
         *
         * 四团色斑按色相错开（暖 -26°/冷 +18°/天青 -8°/深 +34°），复刻手工配好的
         * 那七套的冷暖关系：两团近色相邻、一团跳开、最后一团收暗。
         */
        fun fromSeed(seed: Long): AccentPalette {
            val (rawHue, rawSaturation, _) = argbToHsl(seed)
            // 近乎无彩的图（黑白、雾天、室内灰墙）给一支冷静的蓝：
            // 从灰色里「取」不出颜色，硬取只会得到一片浑浊。
            val hue = if (rawSaturation < 0.12f) 210f else rawHue
            val saturation = rawSaturation.coerceIn(0.55f, 0.92f)

            return AccentPalette(
                id = AUTO_ID,
                label = "取色",
                light = hslToArgb(hue, saturation, 0.52f),
                dark = hslToArgb(hue, (saturation * 0.96f).coerceIn(0.50f, 0.90f), 0.64f),
                blobsLight = longArrayOf(
                    hslToArgb(hueShift(hue, -26f), 0.44f, 0.86f),
                    hslToArgb(hueShift(hue, 18f), 0.34f, 0.80f),
                    hslToArgb(hueShift(hue, -8f), 0.36f, 0.78f),
                    hslToArgb(hueShift(hue, 34f), 0.20f, 0.62f),
                ),
                // 深色那组必须整组比浅色暗一档：夜里色斑一亮，整页就被刷白了
                blobsDark = longArrayOf(
                    hslToArgb(hueShift(hue, -26f), 0.42f, 0.20f),
                    hslToArgb(hueShift(hue, 18f), 0.32f, 0.17f),
                    hslToArgb(hueShift(hue, -8f), 0.34f, 0.16f),
                    hslToArgb(hueShift(hue, 34f), 0.22f, 0.19f),
                ),
            )
        }
    }
}

// ---------------------------------------------------------------- 颜色运算
//
// 全部用纯 Kotlin 位运算 + HSL 手算，不碰 `android.graphics.Color`：
// `AccentPalette.fromSeed` 要被 JVM 单测直接断言（没有 Android 运行时，
// 调 `Color.colorToHSV` 会抛 "not mocked"）。

private fun hueShift(hue: Float, delta: Float): Float {
    var value = (hue + delta) % 360f
    if (value < 0f) value += 360f
    return value
}

/** HSL（h 0..360 / s 0..1 / l 0..1）→ ARGB。 */
internal fun hslToArgb(hue: Float, saturation: Float, lightness: Float): Long {
    val h = ((hue % 360f) + 360f) % 360f / 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun channel(t: Float): Int {
        var value = t
        if (value < 0f) value += 1f
        if (value > 1f) value -= 1f
        val result = when {
            value < 1f / 6f -> p + (q - p) * 6f * value
            value < 1f / 2f -> q
            value < 2f / 3f -> p + (q - p) * (2f / 3f - value) * 6f
            else -> p
        }
        return (result * 255f + 0.5f).toInt().coerceIn(0, 255)
    }
    val r = channel(h + 1f / 3f)
    val g = channel(h)
    val b = channel(h - 1f / 3f)
    return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}

/** ARGB → (h 0..360, s 0..1, l 0..1)。 */
internal fun argbToHsl(argb: Long): Triple<Float, Float, Float> {
    val r = ((argb shr 16) and 0xFF).toInt() / 255f
    val g = ((argb shr 8) and 0xFF).toInt() / 255f
    val b = (argb and 0xFF).toInt() / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)
    val delta = max - min
    val s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val h = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return Triple(hueShift(h, 0f), s.coerceIn(0f, 1f), l)
}
