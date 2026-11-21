package hunoia.luno.annotate.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import hunoia.luno.annotate.model.AnnotateState
import hunoia.luno.annotate.model.AnnotationElement
import hunoia.luno.annotate.model.ContainFit
import hunoia.luno.annotate.model.Pt
import hunoia.luno.annotate.model.PxRect
import hunoia.luno.annotate.model.TextElement
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.model.hitBounds
import hunoia.luno.annotate.model.minSelectionBox
import hunoia.luno.annotate.model.orientedGeometry
import hunoia.luno.annotate.model.rotationRadOf
import hunoia.luno.annotate.render.AnnotateVisuals
import hunoia.luno.annotate.render.drawAnnotateSelection
import hunoia.luno.annotate.ui.gesture.CanvasGestureState
import hunoia.luno.annotate.ui.gesture.ROTATE_HANDLE_GAP_DP
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 画布「视图层」绘制（审计 P1b 从 AnnotateCanvas 拆出）：
 * 选区/裁剪框/矩形擦预览/选中框与柄/画笔范围环。
 *
 * 位置跟随缩放绘制，但线宽/手柄保持屏幕尺寸（世界层之外的一层），
 * `graphicsLayer` 缩放不会把描边/手柄一起放大。全部是纯提示态：
 * 不进文档、不进撤销、不参与导出。
 */

/** 选中柄边长（屏幕 dp），加大易点。 */
private const val ELEMENT_HANDLE_DP = 14f
/** 选中柄深色描边宽（屏幕 dp）。 */
private const val ELEMENT_HANDLE_BORDER_DP = 2f
/** 选中框深色外描边宽（屏幕 dp）。 */
private const val ELEMENT_SELECTION_OUTER_DP = 4f
/** 选中框白色内芯宽（屏幕 dp）。 */
private const val ELEMENT_SELECTION_INNER_DP = 1.5f
/** 角柄命中半径（屏幕 dp）：最小框 48dp 时中心仍留出移动区。 */
internal const val ELEMENT_HANDLE_HIT_DP = 18f
/** 小标注的最小可视选择框边长（屏幕 dp）。 */
internal const val MIN_SELECTION_BOX_DP = 48f
/** 旋转图标直径（屏幕 dp）。 */
private const val ROTATE_HANDLE_ICON_DP = 20f

/** 视图层一帧的绘制入口。调用方保证 [fit] 非空（无 fit 时整层跳过）。 */
internal fun DrawScope.drawAnnotateViewLayer(
    displayState: AnnotateState,
    fit: ContainFit,
    density: Float,
    tool: ToolKind,
    showSelection: Boolean,
    gesture: CanvasGestureState,
    textBounds: (TextElement) -> PxRect,
) {
    if (showSelection) {
        drawAnnotateSelection(displayState, fit, gesture.draftSelection)
    }
    if (tool == ToolKind.CROP) {
        gesture.cropWindow?.let { window ->
            drawCropFrame(window)
        }
    }
    // 矩形擦拖拽预览（C1）：白色虚线框，抬起即消失。
    gesture.draftErase?.let { rect ->
        val view = fit.imageRectToView(rect.normalized())
        if (view.width > 1f && view.height > 1f) {
            drawRect(
                color = Color.White,
                topLeft = Offset(view.left, view.top),
                size = Size(view.width, view.height),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                ),
            )
        }
    }
    // SELECT 选中元素的框（双色）+ 角/边/旋转柄（B5）。只出现在预览。
    displayState.selectedElementId
        ?.let { id -> displayState.elements.firstOrNull { it.id == id } }
        ?.let { element ->
            drawElementSelection(element, fit, density, gesture, textBounds)
        }
    // 画笔范围环：画在最上层，仅触摸期间存在，不参与导出/撤销。
    gesture.brushCursor?.let { cursor ->
        drawBrushCursor(cursor.at, cursor.radiusPx)
    }
}

/** 裁剪框（P3 视觉升级）：三分线辅助 + 四角 L 形括号，框外压暗（与选区遮罩同浓度）。 */
private fun DrawScope.drawCropFrame(window: PxRect) {
    drawRect(
        color = AnnotateVisuals.SelectionScrim,
        topLeft = Offset.Zero,
        size = Size(size.width, window.top.coerceAtLeast(0f)),
    )
    drawRect(
        color = AnnotateVisuals.SelectionScrim,
        topLeft = Offset(0f, window.bottom),
        size = Size(size.width, (size.height - window.bottom).coerceAtLeast(0f)),
    )
    drawRect(
        color = AnnotateVisuals.SelectionScrim,
        topLeft = Offset(0f, window.top),
        size = Size(window.left.coerceAtLeast(0f), window.height),
    )
    drawRect(
        color = AnnotateVisuals.SelectionScrim,
        topLeft = Offset(window.right, window.top),
        size = Size((size.width - window.right).coerceAtLeast(0f), window.height),
    )
    // 三分线：细白线，半透明（构图辅助，不与边框抢视觉）。
    val thirds = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
    for (i in 1..2) {
        val x = window.left + window.width * i / 3f
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(x, window.top),
            end = Offset(x, window.bottom),
            strokeWidth = 1.dp.toPx(),
            pathEffect = thirds,
        )
        val y = window.top + window.height * i / 3f
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(window.left, y),
            end = Offset(window.right, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = thirds,
        )
    }
    // 边框：白色描边。
    drawRect(
        color = Color.White,
        topLeft = Offset(window.left, window.top),
        size = Size(window.width, window.height),
        style = Stroke(width = 2.dp.toPx()),
    )
    // 四角 L 形括号（加粗短臂）：比方块手柄更明确「这是裁剪框」的视觉语言。
    val arm = 18.dp.toPx()
    val stroke = 3.dp.toPx()
    fun bracket(corner: Offset, dx: Float, dy: Float) {
        drawLine(
            color = Color.White,
            start = Offset(corner.x, corner.y),
            end = Offset(corner.x + dx * arm, corner.y),
            strokeWidth = stroke,
        )
        drawLine(
            color = Color.White,
            start = Offset(corner.x, corner.y),
            end = Offset(corner.x, corner.y + dy * arm),
            strokeWidth = stroke,
        )
    }
    bracket(Offset(window.left, window.top), dx = 1f, dy = 1f)
    bracket(Offset(window.right, window.top), dx = -1f, dy = 1f)
    bracket(Offset(window.left, window.bottom), dx = 1f, dy = -1f)
    bracket(Offset(window.right, window.bottom), dx = -1f, dy = -1f)
}

/** SELECT 选中框（有向双色框）+ 角柄（方块）+ 旋转柄（弧形箭头）。 */
private fun DrawScope.drawElementSelection(
    element: AnnotationElement,
    fit: ContainFit,
    density: Float,
    gesture: CanvasGestureState,
    textBounds: (TextElement) -> PxRect,
) {
    val offset = gesture.draftOffset
    val rawBox = hitBounds(element, fit, textBounds)
    val movedBox = if (offset != null && element.id == gesture.draft?.id) {
        PxRect(
            rawBox.left + offset.x,
            rawBox.top + offset.y,
            rawBox.right + offset.x,
            rawBox.bottom + offset.y,
        )
    } else {
        rawBox
    }
    // 小标注用 48dp 最小可视框，角柄不会在屏幕上挤成一团。
    val box = minSelectionBox(movedBox, fit, MIN_SELECTION_BOX_DP * density)
    if (box.width <= 0.5f && box.height <= 0.5f) return
    val rot = element.rotationRadOf()
    val gap = ROTATE_HANDLE_GAP_DP * density / fit.scale
    val geom = orientedGeometry(box, rot, gap)
    // 有向框：深色外描边 + 白内芯，白底/深底都可见。
    val corners = geom.corners.map { fit.imageToView(it) }
    val frame = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        for (i in 1 until 4) lineTo(corners[i].x, corners[i].y)
        close()
    }
    drawPath(
        frame,
        color = AnnotateVisuals.ElementSelectionOuter,
        style = Stroke(width = ELEMENT_SELECTION_OUTER_DP.dp.toPx()),
    )
    drawPath(
        frame,
        color = AnnotateVisuals.ElementSelectionInner,
        style = Stroke(width = ELEMENT_SELECTION_INNER_DP.dp.toPx()),
    )
    // 角柄（圆角方块）+ 旋转柄（弧形箭头），白芯深边。
    val handle = ELEMENT_HANDLE_DP.dp.toPx()
    val border = ELEMENT_HANDLE_BORDER_DP.dp.toPx()
    val handleRadius = CornerRadius(3.dp.toPx())
    geom.corners.forEach { world ->
        val v = fit.imageToView(world)
        drawRoundRect(
            color = AnnotateVisuals.ElementHandleStroke,
            topLeft = Offset(v.x - handle / 2f - border, v.y - handle / 2f - border),
            size = Size(handle + 2f * border, handle + 2f * border),
            cornerRadius = handleRadius,
        )
        drawRoundRect(
            color = AnnotateVisuals.ElementHandleFill,
            topLeft = Offset(v.x - handle / 2f, v.y - handle / 2f),
            size = Size(handle, handle),
            cornerRadius = handleRadius,
        )
    }
    // 旋转柄：弧形箭头（不再是圆点），旋转语义直观。
    drawRotateHandle(geom.rotate, fit, handle, border)
}

/**
 * 画笔范围环：外深内白，半径 = 实际笔刷半径（视图 px）。
 * 半径很小时额外画中心点，极细笔刷也能看出落点。
 */
private fun DrawScope.drawBrushCursor(at: Pt, radiusPx: Float) {
    val center = Offset(at.x, at.y)
    val dark = AnnotateVisuals.ElementHandleStroke
    val light = AnnotateVisuals.ElementHandleFill
    val edge = 1.dp.toPx()
    drawCircle(
        color = dark,
        radius = radiusPx + edge,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )
    drawCircle(
        color = light,
        radius = radiusPx,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )
    if (radiusPx < 6.dp.toPx()) {
        drawCircle(color = dark, radius = 2.dp.toPx(), center = center)
        drawCircle(color = light, radius = 1.dp.toPx(), center = center)
    }
}

/**
 * 旋转柄：标准环形箭头（白芯深边），无填充背景。
 * 约 270° 圆弧 + 末端三角箭头，缺口在右上，箭头指向顺时针方向。
 */
private fun DrawScope.drawRotateHandle(
    world: Pt,
    fit: ContainFit,
    handle: Float,
    border: Float,
) {
    val v = fit.imageToView(world)
    val center = Offset(v.x, v.y)
    val radius = ROTATE_HANDLE_ICON_DP.dp.toPx() / 2f
    val strokeWidth = 2.dp.toPx()
    val arcStart = 0f
    val arcSweep = 270f

    // 深色外弧 + 白内弧（与角柄同一对比策略）。
    fun arc(color: Color, r: Float) {
        drawArc(
            color = color,
            startAngle = arcStart,
            sweepAngle = arcSweep,
            useCenter = false,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(2f * r, 2f * r),
            style = Stroke(width = strokeWidth),
        )
    }
    arc(AnnotateVisuals.ElementHandleStroke, radius + border)
    arc(AnnotateVisuals.ElementHandleFill, radius)

    // 箭头：弧末端处的小三角，指向顺时针切线方向（顶端收尾，缺口在右上）。
    val endRad = Math.toRadians((arcStart + arcSweep).toDouble())
    val end = Offset(
        center.x + (radius + border) * cos(endRad).toFloat(),
        center.y + (radius + border) * sin(endRad).toFloat(),
    )
    // 切线方向 = 径向顺时针转 90°（y 向下，正 sweep 即顺时针，箭头指向继续旋转方向）。
    val tangent = Offset(
        x = -sin(endRad).toFloat(),
        y = cos(endRad).toFloat(),
    )
    val normal = Offset(-tangent.y, tangent.x)
    val arrowSize = handle * 0.6f
    val tip = Offset(
        end.x + tangent.x * arrowSize * 0.5f,
        end.y + tangent.y * arrowSize * 0.5f,
    )
    val base = Offset(
        end.x - tangent.x * arrowSize * 0.5f,
        end.y - tangent.y * arrowSize * 0.5f,
    )
    val left = Offset(base.x + normal.x * arrowSize * 0.55f, base.y + normal.y * arrowSize * 0.55f)
    val right = Offset(base.x - normal.x * arrowSize * 0.55f, base.y - normal.y * arrowSize * 0.55f)
    val arrow = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(arrow, AnnotateVisuals.ElementHandleStroke)
    // 内芯箭头略小，留深色边。
    val innerScale = radius / (radius + border)
    val inner = Path().apply {
        moveTo(center.x + (tip.x - center.x) * innerScale, center.y + (tip.y - center.y) * innerScale)
        lineTo(center.x + (left.x - center.x) * innerScale, center.y + (left.y - center.y) * innerScale)
        lineTo(center.x + (right.x - center.x) * innerScale, center.y + (right.y - center.y) * innerScale)
        close()
    }
    drawPath(inner, AnnotateVisuals.ElementHandleFill)
}

/** 旋转吸附：距 90° 的倍数 5° 内吸附（横平竖直常用），否则自由。 */
internal fun snapRotation(rad: Float): Float {
    val snap = (Math.PI / 2).toFloat()
    val nearest = ((rad / snap).let { if (it >= 0) (it + 0.5f).toInt() else (it - 0.5f).toInt() }) * snap
    if (abs(rad - nearest) < (5.0 * Math.PI / 180.0)) return nearest
    return rad
}
