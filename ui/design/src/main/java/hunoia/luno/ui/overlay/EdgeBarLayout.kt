package hunoia.luno.ui.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/** 手指拖向的那条边。 */
enum class EdgeBarEdge { TOP, BOTTOM, LEFT, RIGHT }

/**
 * 边缘动作条的纯几何：**屏幕像素坐标**。
 *
 * 摆放（Compose 渲染）与松手判定共用这一份计算，否则会出现「看着拖到某一格上、
 * 松手却没执行」。纯函数，可在 JVM 单测里钉死（`EdgeBarLayoutTest`）。
 *
 * 唯一实现：识屏工具条（`:feature:gesture`）与剪贴板部件的拖拽动作条
 * （`:feature:gridpanel`）都读这里，两边只决定「有哪些格」，不再各写一套几何。
 *
 * 模型：手指拖到某条边附近时该边浮出一条工具条；**浮出后整条锚定在边上的固定位置，
 * 不跟手**（跟手会让格子在指下不停变，等于选不中动作），只有「指到哪一格」随手指变化；
 * 换到另一条边才按当时位置重新锚定，移回屏幕中间即收起。
 */
object EdgeBarLayout {

    /** 单格尺寸：动作条只装图标，48dp 满足浮层命中区基线。 */
    const val SlotDp = 48f
    const val IconDp = 20f
    const val GapDp = 6f
    const val PaddingDp = 6f

    /** 条体贴边距离：留在系统边缘手势 / 圆角之外。 */
    const val EdgeMarginDp = 12f

    /** 判定「拖到这一边」的带宽：手指离边的垂直距离小于它才浮出动作条。 */
    const val EdgeBandDp = 96f

    /** 系统栏 / 导航条 inset（px）：条体与沿边范围都要让开。 */
    data class Insets(
        val top: Float = 0f,
        val bottom: Float = 0f,
        val left: Float = 0f,
        val right: Float = 0f,
    )

    /** 动作条几何：哪条边、整条的范围、每一格的矩形（屏幕像素坐标）。 */
    data class Bar(
        val edge: EdgeBarEdge,
        val bounds: Rect,
        val slots: List<Rect>,
    ) {
        val isVertical: Boolean get() = edge == EdgeBarEdge.LEFT || edge == EdgeBarEdge.RIGHT

        /** 整条平移：几何按屏幕坐标算，渲染前减窗口原点用。 */
        fun translated(dx: Float, dy: Float): Bar = Bar(
            edge = edge,
            bounds = Rect(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy),
            slots = slots.map { Rect(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy) },
        )
    }

    /**
     * 手指落在哪条边的动作条带上；返回 null = 离所有边都远，动作条不出现（松手即取消）。
     *
     * 取距离最近的一条边；等距时按 底 → 顶 → 左 → 右 的顺序选，保证四角处行为确定。
     */
    fun edgeFor(
        finger: Offset,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        bandDp: Float = EdgeBandDp,
        insets: Insets = Insets(),
    ): EdgeBarEdge? {
        if (screenWidth <= 0f || screenHeight <= 0f) return null
        val band = bandDp * density
        val candidates = listOf(
            EdgeBarEdge.BOTTOM to (screenHeight - insets.bottom - finger.y),
            EdgeBarEdge.TOP to (finger.y - insets.top),
            EdgeBarEdge.LEFT to (finger.x - insets.left),
            EdgeBarEdge.RIGHT to (screenWidth - insets.right - finger.x),
        )
        val nearest = candidates.minBy { it.second }
        return if (nearest.second <= band) nearest.first else null
    }

    /**
     * 动作条几何。未到边缘（[edgeFor] 为 null）、没有格子、屏幕尺寸无效时返回 null。
     *
     * **沿边位置锚定在 [anchorAxis]，不跟手**：锚点由调用方在「条体首次浮出」时锁定一次；
     * 为 null 时退回按手指居中，只用于无状态的一次性计算。
     *
     * [slotMainPx] 是单格主轴尺寸；0 用 [SlotDp]。一屏放不下时整条等比缩小，绝不溢出屏外。
     */
    fun bar(
        finger: Offset,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        count: Int,
        insets: Insets = Insets(),
        slotMainPx: Float = 0f,
        bandDp: Float = EdgeBandDp,
        anchorAxis: Float? = null,
    ): Bar? {
        if (count <= 0 || screenWidth <= 0f || screenHeight <= 0f) return null
        val edge = edgeFor(finger, screenWidth, screenHeight, density, bandDp, insets) ?: return null
        val cross = SlotDp * density
        val pad = PaddingDp * density
        val depth = cross + pad * 2
        var main = if (slotMainPx > 0f) slotMainPx else cross
        var gap = GapDp * density
        val margin = EdgeMarginDp * density
        val vertical = edge == EdgeBarEdge.LEFT || edge == EdgeBarEdge.RIGHT
        val axisStart = if (vertical) insets.top else insets.left
        val axisEnd = if (vertical) screenHeight - insets.bottom else screenWidth - insets.right
        // 可用主轴长度要两头都留出贴边距，否则缩小后仍会顶出屏外。
        val axisRoom = (axisEnd - axisStart - margin * 2).coerceAtLeast(0f)

        // 放不下：主轴尺寸与间距一体等比缩小，保证整条留在屏内。
        val natural = count * main + (count - 1) * gap
        if (axisRoom > 0f && natural + pad * 2 > axisRoom) {
            val scale = ((axisRoom - pad * 2) / natural).coerceIn(0.05f, 1f)
            main *= scale
            gap *= scale
        }
        val total = count * main + (count - 1) * gap + pad * 2

        // 沿边定位：锚定在浮出那一刻的手指位置（不随手指滑动），贴到安全区就夹住。
        val anchor = anchorAxis ?: if (vertical) finger.y else finger.x
        val axisMin = axisStart + margin
        val axisMax = (axisEnd - margin - total).coerceAtLeast(axisMin)
        val axisPos = (anchor - total / 2f).coerceIn(axisMin, axisMax)

        val bounds = when (edge) {
            EdgeBarEdge.LEFT -> Rect(
                insets.left + margin,
                axisPos,
                insets.left + margin + depth,
                axisPos + total,
            )
            EdgeBarEdge.RIGHT -> Rect(
                screenWidth - insets.right - margin - depth,
                axisPos,
                screenWidth - insets.right - margin,
                axisPos + total,
            )
            EdgeBarEdge.TOP -> Rect(
                axisPos,
                insets.top + margin,
                axisPos + total,
                insets.top + margin + depth,
            )
            EdgeBarEdge.BOTTOM -> Rect(
                axisPos,
                screenHeight - insets.bottom - margin - depth,
                axisPos + total,
                screenHeight - insets.bottom - margin,
            )
        }

        val slots = List(count) { index ->
            val shift = pad + index * (main + gap)
            when (edge) {
                EdgeBarEdge.LEFT, EdgeBarEdge.RIGHT -> Rect(
                    bounds.left + pad,
                    bounds.top + shift,
                    bounds.right - pad,
                    bounds.top + shift + main,
                )
                EdgeBarEdge.TOP, EdgeBarEdge.BOTTOM -> Rect(
                    bounds.left + shift,
                    bounds.top + pad,
                    bounds.left + shift + main,
                    bounds.bottom - pad,
                )
            }
        }
        return Bar(edge = edge, bounds = bounds, slots = slots)
    }

    /**
     * 松手落在哪一格：整条范围内取主轴中点最近的一格（格间缝隙也算，不逼用户压线）。
     * 未进入条体范围返回 null。
     */
    fun hit(bar: Bar?, point: Offset): Int? {
        if (bar == null || bar.slots.isEmpty()) return null
        if (!bar.bounds.contains(point)) return null
        val vertical = bar.isVertical
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        bar.slots.forEachIndexed { index, rect ->
            val distance = if (vertical) {
                abs(point.y - rect.center.y)
            } else {
                abs(point.x - rect.center.x)
            }
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
    }
}

/** 动作条几何的简称（[EdgeBarLayout.Bar]），调用方不必多记一层嵌套。 */
typealias EdgeBar = EdgeBarLayout.Bar
