package hunoia.luno.annotate.model

import kotlin.math.abs
import kotlin.math.max

/**
 * 画布手势的纯几何逻辑（B5/B6/B9）：与 Compose/UI 解耦，可 JVM 单测。
 *
 * 命中测试的**文字**部分依赖 `android.graphics.Paint` 量字宽，留在 `AnnotateCanvas`
 * 的 `realHitBounds` 里；其余几何全部集中在这里。
 */

/** 元素选中柄：四角（双向缩放）+ 旋转柄。边中点柄已删除（4 角拖拽语义）。 */
internal enum class ElementHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    ROTATE,
}

/**
 * 旋转元素的有向选中几何（图片像素坐标）。
 * [corners] 顺序 = TL, TR, BR, BL；[rotate] 在顶边中点外法线方向 [rotateGap] 处。
 */
internal data class OrientedGeometry(
    val center: Pt,
    val rot: Float,
    val corners: List<Pt>,
    val rotate: Pt,
)

/** 由未旋转包围盒 [box] + 旋转角 [rot] 生成有向几何。 */
internal fun orientedGeometry(box: PxRect, rot: Float, rotateGap: Float): OrientedGeometry {
    val c = box.center
    fun r(p: Pt) = rotateAround(p, c, rot)
    return OrientedGeometry(
        center = c,
        rot = rot,
        corners = listOf(
            r(Pt(box.left, box.top)), r(Pt(box.right, box.top)),
            r(Pt(box.right, box.bottom)), r(Pt(box.left, box.bottom)),
        ),
        rotate = r(Pt(c.x, box.top - rotateGap)),
    )
}

/**
 * 最小可视选择框（图片像素）。
 *
 * 把 [box] 映射到视图，短边不足 [minViewSize] 时以中心为轴对称撑开，再映射回图片坐标。
 * 视图空间保证小标注的四个角柄在屏幕上始终有足够间距，不会挤在同一点上。
 * 中心不变，缩放时仍以元素中心为原点；旋转/缩放只影响可视框，真实元素尺寸由
 * `transformed` 按可视框比例映射回去。
 */
internal fun minSelectionBox(box: PxRect, fit: ContainFit, minViewSize: Float): PxRect {
    val view = fit.imageRectToView(box)
    if (view.width >= minViewSize && view.height >= minViewSize) return box
    val width = max(view.width, minViewSize)
    val height = max(view.height, minViewSize)
    val c = view.center
    return fit.viewRectToImage(
        PxRect(c.x - width / 2f, c.y - height / 2f, c.x + width / 2f, c.y + height / 2f),
    )
}

/** 视图坐标点命中的选中柄（角 > 旋转），未命中返回 null。 */
internal fun hitElementHandle(
    geom: OrientedGeometry,
    fit: ContainFit,
    viewPoint: Pt,
    threshold: Float,
): ElementHandle? {
    fun hit(p: Pt): Boolean {
        val v = fit.imageToView(p)
        return abs(v.x - viewPoint.x) <= threshold && abs(v.y - viewPoint.y) <= threshold
    }
    // corners 顺序 = TL, TR, BR, BL（与 [orientedGeometry] 一致）。
    val corners = listOf(
        ElementHandle.TOP_LEFT, ElementHandle.TOP_RIGHT,
        ElementHandle.BOTTOM_RIGHT, ElementHandle.BOTTOM_LEFT,
    )
    corners.forEachIndexed { i, h -> if (hit(geom.corners[i])) return h }
    if (hit(geom.rotate)) return ElementHandle.ROTATE
    return null
}

/** 按角柄把 [box] 调整到 [p]（[p] 已转进局部未旋转坐标系）。 */
internal fun adjustByHandle(box: PxRect, handle: ElementHandle, p: Pt): PxRect = when (handle) {
    ElementHandle.TOP_LEFT -> PxRect(p.x, p.y, box.right, box.bottom)
    ElementHandle.TOP_RIGHT -> PxRect(box.left, p.y, p.x, box.bottom)
    ElementHandle.BOTTOM_LEFT -> PxRect(p.x, box.top, box.right, p.y)
    ElementHandle.BOTTOM_RIGHT -> PxRect(box.left, box.top, p.x, p.y)
    ElementHandle.ROTATE -> box
}

/**
 * 屏幕 dp 线宽 → 图片像素（B9）。落笔瞬间换算，元素里只存图片像素。
 */
internal fun strokeWidthInImagePx(
    styleDp: Float,
    density: Float,
    fit: ContainFit,
): Float = (styleDp * density / fit.scale).coerceIn(0.5f, 512f)

/**
 * 马赛克涂抹笔刷直径（图片像素）。
 *
 * 不单独给笔刷滑杆：颗粒越大笔刷越粗，视觉上「一把刷过去」的效果稳定；
 * 下限 40 图片像素保证高分辨率截图上也有可见笔触。
 */
internal fun mosaicBrushWidth(cellSize: Float): Float =
    max(cellSize * 5f, 40f).coerceAtMost(256f)
