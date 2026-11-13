package com.morrowviento.morrow.ui.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import io.github.neoruaa.glasmio.glass.LiquidContactCapsule
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 两个 capsule 的**平滑并集**几何：从 Glasmio 0.1.3 的 `NavigationNativeUnion`
 * 与 `RoundedMenuField` 逐段移植。
 *
 * ## 为什么必须移植
 *
 * 底栏吸附时要做到「一整块玻璃」，**裁剪轮廓和折射场必须来自同一个 smooth-min 场**。
 * 库的做法（`BottomControlContact.createSurface()`）是：
 *
 * ```
 * NavigationNativeUnion.create(first, second, smoothness)          // 这条 SDF 的零等值线
 *     .let { LiquidContactShape(...).withNativeUnion(it) }        // 拿它的 path 当裁剪轮廓
 *     .withRefractionField(field, band)                            // 同一个场当折射
 * ```
 *
 * 而 `NavigationNativeUnion` 在 0.1.3 里是 `internal`，外部模块无法直接调用
 * （编译报 `INVISIBLE_REFERENCE`），所以这里把它的几何部分原样搬过来：
 * 场函数、连通性判定、轮廓折线化全部保持一致，只是去掉了 native 折线化
 * （真机上那是性能优化，走的也是同一套折线规则）和 multiple 元素分支
 * （我们只有胶囊 + 圆球两个 primitive）。
 *
 * 上一版用库另一套 `liquidContactShape` 当轮廓：那套几何在贴合段是「两个分别闭合的
 * 尖角」或按曲率求切的颈部，与 smooth-min 场对不上，于是融合处出现一条分明的界线，
 * 圆球压到胶囊上时还会把胶囊自己的右端圆弧整条留在圆球里面。
 *
 * ## 数值来自库源码
 *
 * - `RoundedMenuField`：连续圆角的内外距离场（`CornerExpansion / CornerAdjustment`
 *   两个常量与 6 次多项式照抄），`normalMix = 0f` —— 与 `NavigationNativeUnion`
 *   构造字段时一致。
 * - `field()`：`ShaderUtils::sdf_union`，包括它**故意不归一化**的混合梯度，
 *   因为并集半径 `k` 是用两侧法线的点积算的，归一化会改变颈部的宽度。
 * - `createPath()`：沿 x 求零等值线的上下两侧根，按 8px / 0.125px 两个阈值细分，
 *   也就是库在 JVM 上用的同一条折线规则。
 */
internal class MergedCapsuleUnion private constructor(
    val first: LiquidContactCapsule,
    val second: LiquidContactCapsule?,
    val smoothness: Float,
) {
    /** 单个 capsule 的距离场：半径为 `min(w, h) / 2` 的连续圆角矩形。 */
    private class Element(capsule: LiquidContactCapsule) {
        val bounds = capsule.bounds
        private val radius = capsule.size.minDimension * .5f
        val radii = Offset(
            radius * bounds.width / capsule.size.width,
            radius * bounds.height / capsule.size.height,
        )
        private val field = RoundedMenuField(bounds, radii)

        fun distance(x: Float, y: Float): Float = field.distance(x, y)
        fun gradient(x: Float, y: Float): Offset = field.gradient(x, y)
    }

    private val a = Element(first)
    private val b = second?.let(::Element)

    val bounds: Rect = second?.bounds?.let {
        Rect(
            min(first.bounds.left, it.left),
            min(first.bounds.top, it.top),
            max(first.bounds.right, it.right),
            max(first.bounds.bottom, it.bottom),
        )
    } ?: first.bounds

    private val centerY: Float = second?.bounds?.let { (first.bounds.center.y + it.center.y) * .5f }
        ?: first.bounds.center.y

    private val sample = FloatArray(3)

    /**
     * 两片是否已经连成一片：相邻两片中点处的场值 <= 0 即连通。
     *
     * 注意这是**并集半径 k 的**判定，不是几何上「有没有重叠」——smooth-min 在
     * `gap < k / 2` 时把两片拉出连接，中点场值正好在 `gap = k / 2` 归零。
     */
    val isConnected: Boolean = second?.let {
        distance((first.bounds.right + it.bounds.left) * .5f, centerY) <= 0f
    } ?: false

    /**
     * 轮廓：零等值线的折线近似。`by lazy` —— 每个实例只算一次，
     * 拖动时每帧一个新实例（几何变了），静止时不会重复计算。
     */
    val path: Path by lazy(LazyThreadSafetyMode.NONE) { createPath() }

    /** ShaderUtils::sdf_union，包含它未归一化的混合梯度（并集半径靠这个点积）。 */
    private fun field(x: Float, y: Float, output: FloatArray): FloatArray {
        val ad = a.distance(x, y)
        val an = a.gradient(x, y)
        val other = b
        if (other == null) {
            output[0] = ad
            output[1] = an.x
            output[2] = an.y
            return output
        }
        val bd = other.distance(x, y)
        val bn = other.gradient(x, y)
        val radius = smoothness * (.5f - .5f * (an.x * bn.x + an.y * bn.y)).coerceIn(0f, 1f)
        // 有限最近场极限定义了 native fast-math 的 k == 0 分支。
        val h = if (radius <= .000001f) {
            if (ad < bd) 1f else 0f
        } else {
            (.5f + .5f * (bd - ad) / radius).coerceIn(0f, 1f)
        }
        output[0] = bd + (ad - bd) * h - radius * h * (1f - h)
        output[1] = bn.x + (an.x - bn.x) * h
        output[2] = bn.y + (an.y - bn.y) * h
        return output
    }

    private fun distance(x: Float, y: Float): Float =
        if (b == null) a.distance(x, y) else field(x, y, sample)[0]

    /** 在 [outside] → [inside] 之间二分出零等值线的 x。 */
    private fun rootX(outside: Float, inside: Float): Float {
        var lo = outside
        var hi = inside
        repeat(20) {
            val mid = (lo + hi) * .5f
            if (distance(mid, centerY) > 0f) lo = mid else hi = mid
        }
        return (lo + hi) * .5f
    }

    /** 库在 JVM 上的前景轮廓近似：沿 x 求上下两条零等值线，再按曲率细分。 */
    private fun createPath(): Path = Path().apply {
        val guard = smoothness * .25f + 1f
        val top = bounds.top - guard
        val bottom = bounds.bottom + guard
        val edgeRoots = HashMap<Long, Float>()
        fun edgeY(x: Float, outer: Float): Float {
            // 父级四分点与子级中点探测常常得到完全相同的位模式，只复用这些精确根，
            // 保证每一次二分步进与每个顶点都与库一致。
            val key = (x.toRawBits().toLong() shl 32) or (outer.toRawBits().toLong() and 0xffffffffL)
            return edgeRoots.getOrPut(key) {
                var lo = outer
                var hi = centerY
                repeat(20) {
                    val mid = (lo + hi) * .5f
                    if (distance(x, mid) > 0f) lo = mid else hi = mid
                }
                (lo + hi) * .5f
            }
        }

        fun append(left: Float, right: Float) {
            val points = ArrayList<Offset>(64)
            fun subdivide(x0: Float, y0: Float, x1: Float, y1: Float, outer: Float, depth: Int) {
                val xm = (x0 + x1) * .5f
                val ym = edgeY(xm, outer)
                // 颈部的拐点可能正好落在中弦上而两侧没有，所以要再检查两个四分点。
                val refine = abs(x1 - x0) > 8f || abs(ym - (y0 + y1) * .5f) > .125f ||
                    abs(edgeY(x0 + (x1 - x0) * .25f, outer) - (y0 + (y1 - y0) * .25f)) > .125f ||
                    abs(edgeY(x0 + (x1 - x0) * .75f, outer) - (y0 + (y1 - y0) * .75f)) > .125f
                if (depth < 16 && refine) {
                    subdivide(x0, y0, xm, ym, outer, depth + 1)
                    subdivide(xm, ym, x1, y1, outer, depth + 1)
                } else {
                    points.add(Offset(x1, y1))
                }
            }

            val stops = buildList {
                add(left)
                add(right)
                for (element in listOfNotNull(a, b)) {
                    add(element.bounds.center.x)
                    add(element.bounds.left + element.radii.x)
                    add(element.bounds.right - element.radii.x)
                    add(element.bounds.left)
                    add(element.bounds.right)
                }
                second?.let { add((first.bounds.right + it.bounds.left) * .5f) }
            }.filter { it >= left && it <= right }.distinct().sorted()

            moveTo(left, edgeY(left, top))
            for (outer in floatArrayOf(top, bottom)) {
                points.clear()
                val ordered = if (outer == top) stops else stops.asReversed()
                for (i in 0 until ordered.lastIndex) {
                    val x0 = ordered[i]
                    val x1 = ordered[i + 1]
                    subdivide(x0, edgeY(x0, outer), x1, edgeY(x1, outer), outer, 0)
                }
                for (point in points) lineTo(point.x, point.y)
                if (outer == top) lineTo(right, edgeY(right, bottom))
            }
            close()
        }

        if (b == null || isConnected) {
            append(
                rootX(bounds.left - guard, a.bounds.center.x),
                rootX(bounds.right + guard, b?.bounds?.center?.x ?: a.bounds.center.x),
            )
        } else {
            // 缝的中点已经在场外（上面的连通性判定），从两侧各自收口。
            val gap = (a.bounds.right + b.bounds.left) * .5f
            append(rootX(a.bounds.left - guard, a.bounds.center.x), rootX(gap, a.bounds.center.x))
            append(rootX(gap, b.bounds.center.x), rootX(b.bounds.right + guard, b.bounds.center.x))
        }
    }

    companion object {
        /**
         * 与库的 `NavigationNativeUnion.create(first, second, smoothness)` 等价，
         * 非法几何 / 两片垂直错开超过 1px 时返回 null（库同样返回 null，
         * 由调用方退回别的轮廓）。
         */
        fun create(
            first: LiquidContactCapsule,
            second: LiquidContactCapsule?,
            smoothness: Float,
        ): MergedCapsuleUnion? {
            fun valid(c: LiquidContactCapsule) = c.size.width > 0f && c.size.height > 0f &&
                c.bounds.width > 0f && c.bounds.height > 0f && c.size.width.isFinite() &&
                c.size.height.isFinite() && c.bounds.left.isFinite() && c.bounds.top.isFinite() &&
                c.bounds.right.isFinite() && c.bounds.bottom.isFinite()
            if (!valid(first) || second?.let { !valid(it) } == true) return null
            if (!smoothness.isFinite() || smoothness <= 0f) return null
            if (second != null && abs(first.bounds.center.y - second.bounds.center.y) > 1f) return null
            val left = if (second == null || first.bounds.center.x <= second.bounds.center.x) first else second
            val right = if (second == null) null else if (left === first) second else first
            return MergedCapsuleUnion(left, right, smoothness)
        }
    }
}

/**
 * 连续圆角的距离场：Glasmio `LiquidMenuContour.kt` 的 `RoundedMenuField` 原样移植。
 *
 * 只有 `normalMix = 0f` 这一支会用到（`NavigationNativeUnion` 构造字段时的取值），
 * 所以这里直接固化了那一支。
 */
private class RoundedMenuField(
    bounds: Rect,
    radii: Offset,
) {
    private val centerX = bounds.center.x
    private val centerY = bounds.center.y
    private val radiusX = radii.x.coerceAtLeast(0.01f)
    private val radiusY = radii.y.coerceAtLeast(0.01f)
    private val radius = min(radiusX, radiusY)
    private val inverseAxisX = radius / radiusX
    private val inverseAxisY = radius / radiusY
    private val halfWidth = bounds.width / 2f * inverseAxisX
    private val halfHeight = bounds.height / 2f * inverseAxisY
    private val expandedRadius = radius * CornerExpansion
    private val curvatureX = (2.8915570351735034f -
        1.4457785175867517f * (halfWidth * 2f) / expandedRadius).coerceIn(0f, 1f)
    private val curvatureY = (2.8915570351735034f -
        1.4457785175867517f * (halfHeight * 2f) / expandedRadius).coerceIn(0f, 1f)
    private val baseRadius = radius * (CornerExpansion - CornerAdjustment * max(curvatureX, curvatureY))
    private val circular = curvatureX == 1f && curvatureY == 1f

    fun distance(x: Float, y: Float): Float {
        val qx = abs((x - centerX) * inverseAxisX) - halfWidth
        val qy = abs((y - centerY) * inverseAxisY) - halfHeight
        val circularX = qx + baseRadius
        val circularY = qy + baseRadius
        val interior = min(max(circularX, circularY), 0f)
        if (circular) {
            val dx = max(circularX, 0f)
            val dy = max(circularY, 0f)
            return interior + sqrt(dx * dx + dy * dy) - radius
        }
        val ux = (qx + expandedRadius) / expandedRadius
        val uy = (qy + expandedRadius) / expandedRadius
        val zx = max(ux, 0f)
        val zy = max(uy, 0f)
        val maximum = max(zx, zy)
        val t = if (maximum > 0f) min(zx, zy) / maximum else 0f
        val length = sqrt(zx * zx + zy * zy)
        val polynomial = ((((-0.9260540008544922f * t + 3.1560099124908447f) * t -
            3.6412200927734375f) * t + 1.268030047416687f) * t + 0.2685309946537018f) * t * t
        val continuous = length + 1f - 1f / (1f - polynomial * min(length, 1f))
        val ax = max(CornerExpansion * ux - CornerAdjustment, 0f)
        val ay = max(CornerExpansion * uy - CornerAdjustment, 0f)
        val correction = 0.3458344340324402f - continuous +
            0.6541655659675598f * sqrt(ax * ax + ay * ay)
        val weight = ((if (zx < zy) 1f else -1f) * (t - 1f) + 0.5f).coerceIn(0f, 1f)
        val corner = continuous - 1f + correction * (curvatureX + (curvatureY - curvatureX) * weight)
        return interior + expandedRadius * corner
    }

    /** 未归一化的梯度：并集半径用两侧法线的点积，归一化会改变颈部宽度。 */
    fun gradient(x: Float, y: Float): Offset {
        val localX = (x - centerX) * inverseAxisX
        val localY = (y - centerY) * inverseAxisY
        val qx = abs(localX) - halfWidth + baseRadius
        val qy = abs(localY) - halfHeight + baseRadius
        var nx = max(qx, 0f)
        var ny = max(qy, 0f)
        val cornerLength = sqrt(nx * nx + ny * ny)
        if (cornerLength > 0.000001f) {
            nx /= cornerLength
            ny /= cornerLength
        } else if (qx > qy) {
            nx = 1f
            ny = 0f
        } else {
            nx = 0f
            ny = 1f
        }
        if (localX < 0f) nx = -nx
        if (localY < 0f) ny = -ny
        var ovalX = localX
        var ovalY = localY * halfWidth / halfHeight.coerceAtLeast(0.000001f)
        val ovalLength = sqrt(ovalX * ovalX + ovalY * ovalY)
        if (ovalLength > 0.000001f) {
            ovalX /= ovalLength
            ovalY /= ovalLength
        } else {
            ovalX = nx
            ovalY = ny
        }
        // normalMix == 0：梯度完全取圆角法线（NavigationNativeUnion 的取值）。
        val length = sqrt(nx * nx + ny * ny)
        if (length <= 0.000001f) return Offset.Zero
        // CASDF 在归一化之后施加仿射法线逆变换，其模长携带距离尺度，并集的点积要用到。
        return Offset(nx / length * inverseAxisX, ny / length * inverseAxisY)
    }

    private companion object {
        const val CornerExpansion = 1.5286649465560913f
        const val CornerAdjustment = 0.5286649465560913f
    }
}
