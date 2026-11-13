package com.morrowviento.morrow.data.settings

import androidx.compose.runtime.Immutable

/**
 * 页面底色（同时是玻璃的采样源）来源。
 *
 * 两档的意义：
 * - [SATIN]：内置的丝缎壁纸，按配色方案渲染四团色斑与光带（默认，老用户升级后不变）；
 * - [IMAGE]：用户从相册挑的一张图。玻璃照旧实时采它、糊它、折射它——这正是
 *   「自定义背景」在液态玻璃应用里唯一说得通的实现方式：换的是**被采样的底**，
 *   不是给卡片贴一张图。
 */
enum class BackdropMode(val prefValue: String, val label: String) {
    SATIN("satin", "丝缎"),
    IMAGE("image", "图片");

    companion object {
        fun fromPref(raw: String?): BackdropMode =
            entries.firstOrNull { it.prefValue == raw } ?: SATIN
    }
}

/**
 * 背景设置。
 *
 * [image] 存的是**私有目录里的文件名**，不是相册 uri：相册 uri 的生命周期由系统
 * 授予决定（重启、清理缓存后可能失效），一旦失效，用户会看到「背景突然变回纯色」
 * 而且再也回不来了。选图时立刻复制一份到 `filesDir/backdrop/`，之后只认这个副本。
 *
 * [autoTint] 打开时，整套配色（强调色 + 壁纸色斑）由图片主色推导，见
 * `AccentPalette.fromSeed`。关闭时回到用户手动选的 [AppSettings.accent]。
 */
@Immutable
data class BackdropSettings(
    val mode: BackdropMode = BackdropMode.SATIN,
    /** 私有目录内的文件名（不含目录）；null 表示没选过图。 */
    val image: String? = null,
    /** 是否用图片主色生成主题色。 */
    val autoTint: Boolean = false,
    /**
     * 图片主色（ARGB）。
     *
     * 存下来而不是每次渲染时重算：取色要解码整张图 + 遍历像素，放在组合期做会掉帧；
     * 而且主题色在导入备份、卸载重装后都要能还原——种子只有 4 个字节，存进
     * DataStore 与备份文件都毫无压力，图片本身不进备份。
     */
    val tintSeed: Long = 0L,
) {
    val hasImage: Boolean get() = !image.isNullOrBlank()

    /**
     * 实际生效的模式。
     *
     * 选了图片档却没有图（导入备份、文件被系统清理）时回落到丝缎——否则整个应用
     * 会退成一片纯色，看起来像「背景功能坏了」。
     */
    val effectiveMode: BackdropMode
        get() = if (mode == BackdropMode.IMAGE && hasImage) BackdropMode.IMAGE else BackdropMode.SATIN

    /** 自动取色是否真的可用（选了图 + 开了开关 + 种子有效）。 */
    val tintActive: Boolean
        get() = autoTint && hasImage && tintSeed != 0L
}
