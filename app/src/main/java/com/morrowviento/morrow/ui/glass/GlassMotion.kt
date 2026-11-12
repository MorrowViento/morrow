package com.morrowviento.morrow.ui.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * Glasmio 的动效常数，集中在一处，业务层只引用这里。
 *
 * 为什么不是「自己调参数」：库内部用的是 UIKit 的三组曲线，参数是反推出来的，
 * 随手改成 `tween(300)` 就再也对不上系统的观感。
 *
 * - 导航（页面推入 / 弹出）：临界阻尼弹簧，ω = 2π / 0.3s。库内对应
 *   `IosNavigationStiffness`，此处按同一公式算出 438.6 —— 库把这个常量标了
 *   `internal`，拿不到就只能复算，公式与取值在注释里，升级 Glasmio 时对照一遍即可。
 * - 被覆盖页的视差位移是自身宽度的 30%（`IosNavigationParallax`），推入页从右侧整宽进入。
 * - 弹窗 / 弹层（sheet、alert）直接用库的 [IosSheetPresentationMotion]（公开的
 *   `FloatAnimationSpec`，UIKit 的 presenting 曲线），不要自己写 tween。
 */
object GlassMotion {

    /** iOS 标准临界阻尼弹簧：ζ = 1，ω = 2π / 0.3s。 */
    private const val NAVIGATION_OMEGA = 2.0 * Math.PI / 0.3

    /** 导航弹簧刚度（= ω² ≈ 438.6）。与 Glasmio `IosNavigationStiffness` 同源。 */
    private const val NAVIGATION_STIFFNESS = (NAVIGATION_OMEGA * NAVIGATION_OMEGA).toFloat()

    /** 被覆盖页面后退的比例：1 / 3.33 ≈ 30%（UIKit 的导航视差）。 */
    const val NAVIGATION_PARALLAX = 0.3f

    /** 页面推入 / 弹出的弹簧。 */
    fun <T> navigation(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = NAVIGATION_STIFFNESS)

    /** 位移用（IntOffset 版本，给 slideInHorizontally 用）。 */
    val navigationSlide: FiniteAnimationSpec<IntOffset>
        get() = spring(dampingRatio = 1f, stiffness = NAVIGATION_STIFFNESS)

    /**
     * 弹层 / 弹窗的呈现曲线。
     *
     * 库里的 `IosSheetPresentationMotion` 是 `internal`，取不到；按它公布的参数复算：
     * `IosSheetMotion.ResponseSeconds = 0.3441`、`DampingRatio = 1`（临界阻尼），
     * 刚度 = (2π / 0.3441)² ≈ 333.5。这条曲线只用于我们自己的淡入/遮罩，弹层本身
     * 由 `GlasmioBottomSheet` 内部驱动，不经过这里。
     */
    val sheet: AnimationSpec<Float>
        get() = spring(dampingRatio = 1f, stiffness = 333.5f)

    /** 推入过程中的淡入：只走前 120ms，剩下的交给位移，避免整段发灰。 */
    val enterFade = tween<Float>(120)

    /** 遮罩淡入淡出。 */
    val scrim = tween<Float>(200)
}

/**
 * 整页推入 / 弹出（日历、设置）。
 *
 * 此前这里是 `fadeIn()` / `fadeOut()`：两个页面叠着互相淡出，看着像一次「换壁纸」，
 * 而不是 iOS 那种「新页面从右边推上来、旧页退到后面」。现在按库的导航动效做：
 * 进入页整宽从右滑入，退出页整宽滑回右侧，都用同一条临界阻尼弹簧。
 *
 * [reduceMotion] 打开时退化为瞬时切换（不做任何位移/淡出），与设置项文案一致。
 */
@Composable
fun GlassPage(
    visible: Boolean,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = LocalReduceMotion.current,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceMotion) {
            EnterTransition.None
        } else {
            slideInHorizontally(GlassMotion.navigationSlide) { full -> full } +
                fadeIn(GlassMotion.enterFade)
        },
        exit = if (reduceMotion) {
            ExitTransition.None
        } else {
            slideOutHorizontally(GlassMotion.navigationSlide) { full -> full }
        },
    ) {
        content()
    }
}
