package com.morrowviento.morrow.ui.glass

/**
 * 全窗口背景宿主：壁纸采样层 + 玻璃控件内容层。
 *
 * 与主题/材质分开：这里是「背景怎么画、采样怎么传」的框架，换壁纸实现不会牵动面板材质。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.morrowviento.morrow.data.backdrop.BackdropImage
import com.morrowviento.morrow.data.settings.AccentPalette
import com.morrowviento.morrow.data.settings.BackdropMode
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 页面底色（同时充当玻璃的采样源）。
 *
 * **为什么必须是一张「有内容的背景」**：玻璃透不透，肉眼判断的唯一依据是
 * 「背后的东西有没有被糊开、有没有被边缘折射弯」。上一版这里只有一个近乎纯色的
 * 深色渐变，采样出来的每个像素都一样——那么无论透明度是 0 还是 1、模糊是 0dp 还是
 * 24dp，面板看起来都是同一块平板，唯一还能动的是材质自己画的内阴影与边缘光，
 * 于是就有了「调厚度只看到边缘发光变了」的反馈。
 *
 * 所以这里改成一幅**丝缎壁纸**（和 Glasmio 示例里那张壁纸同一路数）：
 *
 * 1. 底色：库的 `groupedBackground`；
 * 2. 四团大色斑：暖象牙（左上）、冷钢蓝（右上）、天青（左下）、冷灰（右下）。
 *    四角四种色、中间留一片静区，卡片压在哪个位置都能采到不同的颜色；
 * 3. 两条倾斜光带：一条宽（柔和亮面 + 相邻暗面，像布料折了一下），一条发丝级。
 *    发丝高光是整幅背景里唯一的高频细节，模糊半径一小一大，它就从「一道锐利的光」
 *    变成「一片发白」——**厚度的差异靠它被看见**。
 *
 * 不再有「顶部一团白柔光」：那团光在浅色下等于把整页刷白（半径给到
 * `maxDimension * 0.9` 时能盖住九成屏幕），卡片压上去就完全看不出透的是什么。
 * 这也是上一版「看起来完全不透明」的另一半原因。
 *
 * 渐变一律淡到「同色 alpha=0」而不是 `Color.Transparent`：Compose 的渐变按非预乘
 * 插值，淡向黑色透明会在色斑边缘留一圈脏灰。
 *
 * 绘制走 `drawWithCache` + **预渲染**：整幅图按屏幕尺寸与配色只画一次（见
 * `GlassWallpaper`），之后每帧只是一次 `drawImage`。壁纸是静态的，没有任何理由
 * 每帧重跑六层渐变——那正是列表滚动与页面切换时卡顿的填充率来源。
 *
 * 4.1.0：预渲染挪到后台线程（换明暗/换配色时那一帧不再僵住），并支持
 * [BackdropMode.IMAGE]——用户挑的照片按 cover 铺满，上面压一层极淡的底色纱
 * 保证文字对比度。照片本身也走 `drawImage`，每帧只有「一张图 + 一个纯色矩形」。
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val colors = GlasmioTheme.colors
    val dark = LocalGlassDark.current
    val base = colors.groupedBackground
    val backdrop = LocalBackdropSettings.current

    // 四团色斑来自当前配色方案（见 `AccentPalette`）。这是「换主题整页换色」的
    // 落点：卡片、底栏、弹层都是玻璃，它们透出来的正是这几团色；只换强调色的话，
    // 屏幕上只有图标与按钮变，底子还是同一片底色。
    val palette = LocalAccentPalette.current
    val blobs = remember(palette, dark) { palette.blobs(dark) }

    // 自定义图片：只在真的用图片档时才去读盘。解码与取色都在 IO 线程，
    // 完成后主线程只是换一个 ImageBitmap 引用。
    val context = LocalContext.current
    var photoLoading by remember(backdrop.image, backdrop.effectiveMode) {
        mutableStateOf(backdrop.effectiveMode == BackdropMode.IMAGE)
    }
    val photo by produceState<ImageBitmap?>(initialValue = null, backdrop.image, backdrop.effectiveMode) {
        value = if (backdrop.effectiveMode == BackdropMode.IMAGE) {
            withContext(Dispatchers.IO) { BackdropImage.load(context, backdrop.image)?.asImageBitmap() }
        } else {
            null
        }
        photoLoading = false
    }

    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    // 首帧先用上一次渲染出来的那张（进程内可能已经在别的页面画过），
    // 让「刚进应用」和「刚换主题」这两条路径都不出现空白帧。
    var wallpaper by remember { mutableStateOf(GlassWallpaper.placeholder()) }

    // 图片真的解码出来了才不需要丝缎壁纸。写成布尔量而不是直接读 photo：
    // 它要当 LaunchedEffect 的 key（图刚就绪时要把还没画的丝缎补上）。
    val needSatin = backdrop.effectiveMode != BackdropMode.IMAGE || (!photoLoading && photo == null)

    LaunchedEffect(sizePx, base, dark, blobs, needSatin) {
        if (sizePx.width <= 0 || sizePx.height <= 0) return@LaunchedEffect
        // 图片档根本不用丝缎壁纸：白画一张 2.6M 像素的图纯属浪费
        if (!needSatin) return@LaunchedEffect
        val width = sizePx.width
        val height = sizePx.height
        val hit = GlassWallpaper.cached(width, height, base, dark, blobs)
        if (hit != null) {
            wallpaper = hit
            return@LaunchedEffect
        }
        // 冷启动第一张同步画：此刻既没有缓存也没有上一张，异步的话第一帧只能铺纯底色，
        // 用户会先看到一下「整页灰白」再变成壁纸。这一下发生在启动期（本来就在跑
        // 各种初始化），而换明暗 / 换配色时才走后台——那才是用户反复触发、要求流畅的路径。
        wallpaper = if (GlassWallpaper.placeholder() == null) {
            GlassWallpaper.render(width, height, base, dark, blobs)
        } else {
            // 不清空 wallpaper：新图就绪前继续显示旧的那张，比闪一下纯底色自然得多
            withContext(Dispatchers.Default) {
                GlassWallpaper.render(width, height, base, dark, blobs)
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { if (it != sizePx) sizePx = it }
            .drawWithCache {
                // 位图按整像素生成，而 `size` 常带小数（dp→px 的余数）：
                // 贴图时显式给 dstSize，否则右 / 下边缘会留一条 1px 没盖住的缝。
                val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                // 照片上的那层纱：不是装饰，是**可读性**的兜底。亮色的照片会把
                // 玻璃上的白字吞掉，压一层底色纱后对比度回到可用范围。
                val veil = base.copy(alpha = if (dark) 0.30f else 0.16f)
                onDrawBehind {
                    val photoBitmap = photo
                    val satin = wallpaper
                    when {
                        // 图片模式：cover 裁切（短边铺满，长边居中裁掉），不做拉伸
                        backdrop.effectiveMode == BackdropMode.IMAGE && photoBitmap != null -> {
                            val iw = photoBitmap.width.toFloat()
                            val ih = photoBitmap.height.toFloat()
                            if (iw > 0f && ih > 0f && dstSize.width > 0 && dstSize.height > 0) {
                                val scale = maxOf(dstSize.width / iw, dstSize.height / ih)
                                val srcW = (dstSize.width / scale).roundToInt().coerceIn(1, photoBitmap.width)
                                val srcH = (dstSize.height / scale).roundToInt().coerceIn(1, photoBitmap.height)
                                val srcX = (photoBitmap.width - srcW) / 2
                                val srcY = (photoBitmap.height - srcH) / 2
                                drawImage(
                                    image = photoBitmap,
                                    srcOffset = IntOffset(srcX, srcY),
                                    srcSize = IntSize(srcW, srcH),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = dstSize,
                                    // 照片通常比屏幕大，缩小时用双线性 + mipmap：
                                    // 默认的 Low 会把细密纹理（树叶、砖墙）抖成噪点
                                    filterQuality = FilterQuality.Medium,
                                )
                            } else {
                                drawImage(
                                    image = photoBitmap,
                                    dstOffset = IntOffset.Zero,
                                    dstSize = dstSize,
                                    filterQuality = FilterQuality.Medium,
                                )
                            }
                            drawRect(veil)
                        }
                        // 丝缎：等首张位图就绪时先铺一块纯底色，避免透出系统黑
                        satin != null -> drawImage(satin, dstOffset = IntOffset.Zero, dstSize = dstSize)
                        else -> drawRect(base)
                    }
                }
            },
    )
}

/**
 * 玻璃背景宿主（自定义内容版）。
 *
 * sampledContent 只放**不透明背景**（壁纸）；overlayContent 放全部玻璃控件，
 * 并从参数拿到合成后的 [GlassContentBackdrop]，把它挂在内容层、传给底栏。
 *
 * 玻璃控件**不得**放入 sampledContent 或内容层之外，否则会自我采样形成递归。
 */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    sampledContent: @Composable BoxScope.() -> Unit,
    overlayContent: @Composable BoxScope.(GlassContentBackdrop) -> Unit,
) {
    GlasmioBackdropHost(
        modifier = modifier,
        sampledContent = { sampledContent() },
        overlayContent = { wallpaperLayer ->
            val contentLayer = rememberLayerBackdrop()
            val combined = rememberCombinedBackdrop(wallpaperLayer, contentLayer)
            val handle = remember(contentLayer, combined) {
                GlassContentBackdrop(contentLayer, combined)
            }
            overlayContent(handle)
        },
    )
}

/**
 * 玻璃背景宿主（纯色背景版）。
 *
 * Glasmio 0.1.2 无 sampledColor 便捷重载，故用纯色 Box 充当被采样内容。
 */
@Composable
fun GlassBackdropHost(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    overlayContent: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    GlasmioBackdropHost(
        modifier = modifier,
        sampledContent = { Box(Modifier.fillMaxSize().background(backgroundColor)) },
        overlayContent = overlayContent,
    )
}
