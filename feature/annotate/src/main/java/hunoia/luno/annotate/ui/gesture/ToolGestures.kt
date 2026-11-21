package hunoia.luno.annotate.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import hunoia.luno.annotate.StickerPickCenter
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.AnnotateOps
import hunoia.luno.annotate.model.AnnotationElement
import hunoia.luno.annotate.model.BlurElement
import hunoia.luno.annotate.model.ContainFit
import hunoia.luno.annotate.model.ElementHandle
import hunoia.luno.annotate.model.ElementKind
import hunoia.luno.annotate.model.MosaicElement
import hunoia.luno.annotate.model.NumberElement
import hunoia.luno.annotate.model.Pt
import hunoia.luno.annotate.model.PxRect
import hunoia.luno.annotate.model.ShapeElement
import hunoia.luno.annotate.model.StrokeElement
import hunoia.luno.annotate.model.TextElement
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.model.adjustByHandle
import hunoia.luno.annotate.model.adjustCropWindow
import hunoia.luno.annotate.model.blurBrushWidth
import hunoia.luno.annotate.model.clampPanToCropWindow
import hunoia.luno.annotate.model.hitBounds
import hunoia.luno.annotate.model.hitCropHandle
import hunoia.luno.annotate.model.hitElementHandle
import hunoia.luno.annotate.model.hitTopElement
import hunoia.luno.annotate.model.maxCropScale
import hunoia.luno.annotate.model.minSelectionBox
import hunoia.luno.annotate.model.mosaicBrushWidth
import hunoia.luno.annotate.model.orientedGeometry
import hunoia.luno.annotate.model.rotateAround
import hunoia.luno.annotate.model.rotationRadOf

import hunoia.luno.annotate.model.scaleCropWindow
import hunoia.luno.annotate.model.snapShapeEnd
import hunoia.luno.annotate.model.strokeWidthInImagePx
import hunoia.luno.annotate.model.transformed
import hunoia.luno.annotate.model.translated
import hunoia.luno.annotate.model.withElementId
import hunoia.luno.annotate.model.withRotation
import hunoia.luno.annotate.ui.ELEMENT_HANDLE_HIT_DP
import hunoia.luno.annotate.ui.MIN_SELECTION_BOX_DP
import hunoia.luno.annotate.ui.TextEditorState
import hunoia.luno.annotate.ui.realHitBounds
import hunoia.luno.annotate.ui.snapRotation
import kotlin.math.atan2
import kotlin.math.min

/**
 * 按工具的手势路由（审计 P1b 从 AnnotateCanvas 拆出）。
 *
 * 一个 `awaitEachGesture` 落点 → 按当前工具分派到对应处理器；处理器通过
 * [GestureHost] 读写文档 / 瞬态手势状态。工具语义与拆分前逐行一致：
 * 切工具重启 pointerInput（canvas 的 key），状态对象在重组间保持。
 */
internal const val DRAFT_ID = -1L

/**
 * 手势处理器共享的宿主上下文。
 * [baseFit] / [currentFit] 用 lambda 现读：转屏 / 换图后 fit 变化，
 * 长生命周期的手势闭包不能捕获启动那一份。
 */
internal class GestureHost(
    val document: AnnotateDocument,
    val gesture: CanvasGestureState,
    val text: TextEditorState,
    val density: Float,
    /** contain 基准（不含双指视图变换）。 */
    val baseFit: () -> ContainFit?,
    /** 叠加视图变换后的当前 fit。 */
    val currentFit: () -> ContainFit?,
    /** 画布任意落笔（含绘制/拖选/缩放开始）：宿主据此收起样式面板/弹出卡。 */
    val onPointerDown: (() -> Unit)?,
    /** 贴纸工具落点后发起系统选图（宿主接线：浮层 / 图片编辑页各自 startActivity）。 */
    val onPickSticker: (() -> Unit)? = null,
)

/**
 * 单次落点的工具分派入口。在 canvas 的 `pointerInput(document, tool, density)` 里
 * 每个 awaitEachGesture 调一次。
 */
internal suspend fun AwaitPointerEventScope.routeToolGesture(
    tool: ToolKind,
    host: GestureHost,
) {
    val fit = host.currentFit() ?: return
    // consumed 的 down（落在文字输入框上）不再当作画布手势，
    // 否则点输入框内部会被父层当成新的落字点。
    val down = awaitFirstDown(requireUnconsumed = true)
    // 宿主收面板/弹出卡：落笔即交互开始（样式面板不再遮挡画布）。
    host.onPointerDown?.invoke()
    // 起手前先交卷：画布上旧文字编辑器如果还开着，这次落点应先提交，
    // 否则会被下面的 TEXT 分支直接覆盖，已输入内容丢失。
    if (host.text.isEditing) host.text.commit(host.document)
    val startImage = fit.viewToImage(down.position.asPt()).clampToImage(host.document)
    val gestureGeneration = host.gesture.viewTransformGeneration
    when (tool) {
        ToolKind.TEXT -> handleTextDown(host, fit, down, startImage)
        ToolKind.NUMBER -> handleNumberDown(host, down, startImage)
        ToolKind.SELECT -> handleSelectDown(host, fit, down, startImage)
        ToolKind.CROP -> handleCropDown(host, fit, down)
        ToolKind.STICKER -> handleStickerDown(host, down, startImage)
        else -> handleDrawDown(host, tool, fit, down, startImage, gestureGeneration)
    }
}

// ---------------------------------------------------------------------------
// STICKER：落点记位 + 发起选图（位图交付后由画布观察插入）
// ---------------------------------------------------------------------------

private suspend fun AwaitPointerEventScope.handleStickerDown(
    host: GestureHost,
    down: PointerInputChange,
    startImage: Pt,
) {
    host.document.selectElement(null)
    StickerPickCenter.tapPoint = startImage
    host.onPickSticker?.invoke()
    drag(down.id) { it.consume() }
}

// ---------------------------------------------------------------------------
// TEXT / NUMBER
// ---------------------------------------------------------------------------

private suspend fun AwaitPointerEventScope.handleTextDown(
    host: GestureHost,
    fit: ContainFit,
    down: PointerInputChange,
    startImage: Pt,
) {
    val document = host.document
    document.selectElement(null)
    // 真实测量命中（模型层 bounds() 是 textSize*length 估算，混排不准）。
    val hit = document.state.elements.asReversed()
        .filterIsInstance<TextElement>()
        .firstOrNull { it.realHitBounds(fit).contains(startImage) }
    val target = hit?.position ?: startImage
    host.text.start(target, hit, hit?.text ?: "")
    drag(down.id) { it.consume() }
}

private suspend fun AwaitPointerEventScope.handleNumberDown(
    host: GestureHost,
    down: PointerInputChange,
    startImage: Pt,
) {
    val document = host.document
    // 序号：落点即放，自动递增编号（渲染按列表顺序推导），
    // 生成后立即选中，可拖动/旋转/删除。
    val style = document.state.styleFor(ToolKind.NUMBER)
    val id = document.nextElementId()
    document.add(
        NumberElement(
            id = id,
            position = startImage,
            colorArgb = style.colorArgb,
            textSize = style.textSize,
        ),
    )
    document.selectElement(id)
    drag(down.id) { it.consume() }
}

// ---------------------------------------------------------------------------
// SELECT：点选 / 拖动 / 缩放 / 旋转
// ---------------------------------------------------------------------------

private suspend fun AwaitPointerEventScope.handleSelectDown(
    host: GestureHost,
    fit: ContainFit,
    down: PointerInputChange,
    startImage: Pt,
) {
    val document = host.document
    val gesture = host.gesture
    val density = host.density
    // 纯元素选择：点选/拖动/缩放/旋转，不再管理导出裁剪框（CROP 接管）。
    val selectedElement = document.state.selectedElementId
        ?.let { id -> document.state.elements.firstOrNull { it.id == id } }

    // 选中元素的有向几何（四角 + 旋转柄），用于缩放/旋转命中。
    // 小标注用 48dp 最小可视框撑开角柄；缩放时按可视框比例映射回真实元素。
    var selectedHandle: ElementHandle? = null
    var selectedBox: PxRect? = null
    var selectedCenter: Pt? = null
    var selectedInsideBox = false
    var selectedRot = 0f
    if (selectedElement != null) {
        val box = minSelectionBox(
            hitBoundsOf(selectedElement, fit),
            fit,
            MIN_SELECTION_BOX_DP * density,
        )
        selectedBox = box
        selectedRot = selectedElement.rotationRadOf()
        selectedCenter = box.center
        // 本体优先：可视框内（角柄以外）都可以直接拖走。
        // 小标注的真实笔迹可能只有几 dp，靠它命中会退回「点不中」的老问题；
        // 48dp 可视框就是它的拖拽目标。
        val localStart = if (selectedRot == 0f) startImage else {
            rotateAround(startImage, box.center, -selectedRot)
        }
        selectedInsideBox = box.contains(localStart)
        val gap = ROTATE_HANDLE_GAP_DP * density / fit.scale
        val geom = orientedGeometry(box, selectedRot, gap)
        selectedHandle = hitElementHandle(
            geom,
            fit,
            down.position.asPt(),
            ELEMENT_HANDLE_HIT_DP * density,
        )
    }
    // 旋转柄在框外；角柄优先缩放/旋转，其余可视框区域直接移动。
    val movable = when {
        selectedHandle == null && selectedInsideBox -> selectedElement
        selectedHandle == null ->
            hitTopElement(document.state.elements, startImage, fit) { it.realHitBounds(fit) }
        else -> null
    }
    var moved = false

    when {
        // 1) 选中元素四角缩放 + 旋转（有向柄）。
        selectedElement != null && selectedHandle != null &&
            selectedBox != null && selectedCenter != null -> {
            val box = selectedBox
            val center = selectedCenter
            val rot = selectedRot
            val handle = selectedHandle
            gesture.draft = selectedElement
            if (handle == ElementHandle.ROTATE) {
                drag(down.id) { change ->
                    moved = true
                    val p = fit.viewToImage(change.position.asPt())
                    val dx = p.x - center.x
                    val dy = p.y - center.y
                    if (dx * dx + dy * dy > 1f) {
                        // 顶边外法线方向对应角度 atan2(dx, -dy)。
                        val newRot = snapRotation(atan2(dx, -dy))
                        gesture.draft = selectedElement.withRotation(newRot)
                    }
                    change.consume()
                }
            } else {
                drag(down.id) { change ->
                    moved = true
                    val p = fit.viewToImage(change.position.asPt())
                        .clampToImage(document)
                    if (p.x == p.x && p.y == p.y) {
                        // 指针逆旋回局部坐标，按角柄调整包围盒。
                        val local = rotateAround(p, center, -rot)
                        val to = adjustByHandle(box, handle, local)
                        if (to.width >= 4f || to.height >= 4f) {
                            gesture.draft = selectedElement.transformed(box, to)
                        }
                    }
                    change.consume()
                }
            }
            val finished = gesture.draft
            gesture.draft = null
            if (moved && finished != null) document.replace(finished)
        }

        // 2) 点中元素（含已选中小标注的本体）：选中并拖动（B5）。
        movable != null -> {
            document.selectElement(movable.id)
            gesture.draft = movable
            gesture.draftOffset = Pt(0f, 0f)
            drag(down.id) { change ->
                moved = true
                val p = fit.viewToImage(change.position.asPt())
                gesture.draftOffset = Pt(
                    p.x - startImage.x,
                    p.y - startImage.y,
                )
                change.consume()
            }
            val offset = gesture.draftOffset
            gesture.draft = null
            gesture.draftOffset = null
            if (moved && offset != null) {
                document.replace(movable.translated(offset.x, offset.y))
            }
        }

        // 3) 空白处拖出导出选区；SELECT 不是多标注框选工具。
        else -> {
            document.selectElement(null)
            var rect = PxRect.fromPoints(startImage, startImage)
            var current = startImage
            val completed = drag(down.id) { change ->
                moved = true
                current = fit.viewToImage(change.position.asPt())
                    .clampToImage(document)
                rect = PxRect.fromPoints(startImage, current).normalized()
                gesture.draftSelection = rect
                change.consume()
            }
            val finalRect = if (completed) rect else {
                PxRect.fromPoints(startImage, current).normalized()
            }
            document.setSelection(finalRect)
            gesture.draftSelection = null
        }
    }
}

// ---------------------------------------------------------------------------
// CROP：标准图片编辑器式裁剪（窗口固定，图片在窗口下移动/缩放）
// ---------------------------------------------------------------------------

private suspend fun AwaitPointerEventScope.handleCropDown(
    host: GestureHost,
    fit: ContainFit,
    down: PointerInputChange,
) {
    val document = host.document
    val gesture = host.gesture
    document.selectElement(null)
    val base = host.baseFit() ?: return
    val window = gesture.cropWindow ?: fit.imageRectToView(
        PxRect.full(document.imageWidth, document.imageHeight),
    )
    // 裁剪框只受固定安全区域限制；图片像素选区在提交时统一 clamp。
    val imageBounds = gesture.cropBounds ?: fit.imageRectToView(
        PxRect.full(document.imageWidth, document.imageHeight),
    )
    val handle = hitCropHandle(window, down.position.asPt(), 28f * host.density)
    val minSize = (48f * host.density).coerceAtMost(
        min(imageBounds.width, imageBounds.height),
    )

    if (handle != null) {
        val oldWindow = window
        val startZoom = document.viewTransform.zoom
        val startFit = fit
        var nextWindow = oldWindow
        var moved = false
        val released = drag(down.id) { change ->
            moved = true
            // 调整期间只更新临时窗口预览；selection、zoom、pan 在抬手后一次性提交。
            nextWindow = adjustCropWindow(
                oldWindow,
                handle,
                change.position.asPt(),
                imageBounds,
                minSize,
            )
            gesture.cropWindow = nextWindow
            change.consume()
        }

        if (released && moved) {
            // 拖动期间图片保持不动；抬手后把选区与框作为整体同步放大。
            val factor = maxOf(
                oldWindow.width / nextWindow.width,
                oldWindow.height / nextWindow.height,
            )
            val zoom = (startZoom * factor)
                .coerceIn(gesture.cropMinZoom, AnnotateOps.MAX_VIEW_ZOOM)
            val desiredFactor = zoom / startZoom
            // 框与图片的几何一律用「未夹取」的图片区域计算：拖动到图片外时
            // 该区域中心仍与拖动位置一致，抬手后框与图片保持同步放大，
            // 不会因选区被夹取到图片边缘而中心跳变、产生上下偏移。
            // 导出用的选区在下方 setSelection 时才夹取到图片范围。
            val cropImg = startFit.viewRectToImage(nextWindow).normalized()
            val cropCenter = startFit.imageToView(cropImg.center)
            val appliedFactor = desiredFactor.coerceAtMost(
                maxCropScale(nextWindow, cropCenter, imageBounds),
            )
            val appliedZoom = (startZoom * appliedFactor)
                .coerceIn(gesture.cropMinZoom, AnnotateOps.MAX_VIEW_ZOOM)
            val finalWindow = scaleCropWindow(
                nextWindow,
                cropCenter,
                appliedFactor,
            )
            // 平移保持图片区域中心与裁剪框中心一致。
            val transformed = base.withViewTransform(appliedZoom, Pt(0f, 0f))
            val target = transformed.imageToView(cropImg.center)
            val requestedPan = Pt(
                x = finalWindow.center.x - target.x,
                y = finalWindow.center.y - target.y,
            )
            // 裁剪态允许图片移出屏幕；只能保证裁剪框内仍由图片覆盖，
            // 不能再用屏幕边界二次夹取，否则会改变框内图片区域。
            val pan = requestedPan
            gesture.cropWindow = finalWindow
            document.setViewTransform(appliedZoom, pan)
            document.setSelection(
                cropImg.clampTo(
                    document.imageWidth.toFloat(),
                    document.imageHeight.toFloat(),
                ),
            )
        }
    } else if (window.contains(down.position.asPt())) {
        // 裁剪框内单指拖动图片；裁剪框本身保持在安全区域内。
        var last = down.position
        val generation = gesture.viewTransformGeneration
        drag(down.id) { change ->
            if (generation == gesture.viewTransformGeneration) {
                val dx = change.position.x - last.x
                val dy = change.position.y - last.y
                val pan = document.viewTransform.pan + Pt(dx, dy)
                val clamped = clampPanToCropWindow(
                    base,
                    document.viewTransform.zoom,
                    pan,
                    window,
                )
                document.setViewTransform(document.viewTransform.zoom, clamped)
                document.setSelection(
                    base.withViewTransform(
                        document.viewTransform.zoom,
                        clamped,
                    ).viewRectToImage(window),
                )
                last = change.position
                change.consume()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 绘画类：笔迹 / 形状 / 马赛克 / 橡皮（含矩形擦）
// ---------------------------------------------------------------------------

private suspend fun AwaitPointerEventScope.handleDrawDown(
    host: GestureHost,
    tool: ToolKind,
    fit: ContainFit,
    down: PointerInputChange,
    startImage: Pt,
    gestureGeneration: Int,
) {
    val document = host.document
    val gesture = host.gesture
    val density = host.density
    var moved = false
    // 画新内容前清掉旧选中框（NUMBER 自动选中后切其它工具不再挂着框）。
    document.selectElement(null)
    // 矩形擦（C1）：橡皮 + 矩形模式拖出选框，松手清除相交的笔迹/形状。
    if (tool == ToolKind.ERASER && document.state.eraserRect) {
        var rect = PxRect.fromPoints(startImage, startImage)
        gesture.draftErase = rect.normalized()
        drag(down.id) { change ->
            moved = true
            val p = fit.viewToImage(change.position.asPt())
                .clampToImage(document)
            rect = PxRect.fromPoints(startImage, p)
            gesture.draftErase = rect.normalized()
            change.consume()
        }
        gesture.draftErase = null
        // 未移动的单击不擦（防误触，同选区的最小尺寸语义）。
        if (moved && gestureGeneration == gesture.viewTransformGeneration) {
            document.eraseInRect(rect)
        }
        return
    }
    // 线宽是屏幕 dp（B9）：落笔瞬间换算成图片像素，
    // 高分辨率底图 / 缩放下手感一致。
    val style = document.state.styleFor(tool)
    val strokeImageWidth = strokeWidthInImagePx(
        style.strokeWidth,
        density,
        fit,
    )
    val mosaicWidth = mosaicBrushWidth(style.mosaicCell)
    val blurWidth = blurBrushWidth(style.blurRadius)
    // 画笔范围环：半径 = 实际落笔半径（视图 px），只在触摸期间显示。
    val brushTool = tool == ToolKind.PEN || tool == ToolKind.ERASER ||
        tool == ToolKind.HIGHLIGHT || tool == ToolKind.MOSAIC || tool == ToolKind.BLUR
    val brushRadiusPx = when (tool) {
        ToolKind.MOSAIC -> mosaicWidth / 2f * fit.scale
        ToolKind.BLUR -> blurWidth / 2f * fit.scale
        ToolKind.PEN, ToolKind.ERASER, ToolKind.HIGHLIGHT ->
            style.strokeWidth * density / 2f
        else -> 0f
    }
    if (brushTool) {
        gesture.brushCursor = BrushCursor(down.position.asPt(), brushRadiusPx)
    }
    // 距离抽稀阈值（图片像素）：1 视图像素换算回图片像素，
    // 但不超过线宽/4（保证采样点密到圆头笔帽盖得住），
    // 且不小于 0.5 图片像素。预览亚像素丢点不改变可见形状，
    // 导出（scale=1）同样落在原轨迹的笔宽内。
    // 马赛克 / 模糊笔刷很粗，允许按笔刷直径/4 稀疏采样，避免长涂抹存上万点。
    val stepImage = if (tool == ToolKind.MOSAIC || tool == ToolKind.BLUR) {
        val brushWidth = if (tool == ToolKind.MOSAIC) mosaicWidth else blurWidth
        (brushWidth / 4f).coerceAtLeast(1f / fit.scale)
    } else {
        (1f / fit.scale)
            .coerceAtMost(strokeImageWidth / 4f)
            .coerceAtLeast(0.5f)
    }
    if (brushTool) {
        // 笔迹/马赛克：点直接追加进活列表（O(1)），元素对象只建一次；
        // 旧实现每个 MOVE 都 points + p 复制整条列表，长笔迹掉帧。
        gesture.draftPoints.clear()
        gesture.draftPoints.add(startImage)
    }
    gesture.draft = when (tool) {
        ToolKind.PEN, ToolKind.ERASER, ToolKind.HIGHLIGHT -> StrokeElement(
            id = DRAFT_ID,
            points = gesture.draftPoints,
            colorArgb = style.colorArgb,
            width = strokeImageWidth,
            erase = tool == ToolKind.ERASER,
            highlight = tool == ToolKind.HIGHLIGHT,
        )

        // 形状已合并：子类型读 activeShape，填充读 SHAPE 风格。
        ToolKind.SHAPE -> ShapeElement(
            id = DRAFT_ID,
            start = startImage,
            end = startImage,
            colorArgb = style.colorArgb,
            width = strokeImageWidth,
            kind = document.state.activeShape.elementKind,
            filled = style.shapeFilled,
        )

        // 马赛克是涂抹笔刷：笔刷宽度随颗粒比例走，
        // 颗粒越大笔刷越粗；单点也能落一个 dab。
        ToolKind.MOSAIC -> MosaicElement(
            id = DRAFT_ID,
            points = gesture.draftPoints,
            width = mosaicWidth,
            cellSize = style.mosaicCell,
        )

        // 模糊与马赛克同一套涂抹轨迹，取样底图换成预模糊副本。
        ToolKind.BLUR -> BlurElement(
            id = DRAFT_ID,
            points = gesture.draftPoints,
            width = blurWidth,
            radius = style.blurRadius,
        )

        ToolKind.TEXT, ToolKind.SELECT, ToolKind.CROP,
        ToolKind.NUMBER, ToolKind.STICKER,
        -> null
    }
    var lastPt = startImage
    drag(down.id) { change ->
        moved = true
        if (brushTool) {
            gesture.brushCursor = BrushCursor(
                change.position.asPt(),
                brushRadiusPx,
            )
        }
        val p = fit.viewToImage(change.position.asPt())
            .clampToImage(document)
        when (val d = gesture.draft) {
            is StrokeElement -> {
                val dx = p.x - lastPt.x
                val dy = p.y - lastPt.y
                if (dx * dx + dy * dy >= stepImage * stepImage) {
                    gesture.draftPoints.add(p)
                    lastPt = p
                }
            }

            is ShapeElement -> gesture.draft = d.copy(end = snapShapeEnd(d.start, p, d.kind))
            is MosaicElement -> {
                // 与笔迹同一套距离抽稀；笔刷粗，1px 视图像素足够。
                val dx = p.x - lastPt.x
                val dy = p.y - lastPt.y
                if (dx * dx + dy * dy >= stepImage * stepImage) {
                    gesture.draftPoints.add(p)
                    lastPt = p
                }
            }

            else -> Unit
        }
        change.consume()
    }
    // 抬手/取消即收起范围环；不是在文档里画东西。
    gesture.brushCursor = null
    val finished = gesture.draft
    gesture.draft = null

    // 双指介入过：本次单指手势作废（缩放不应留下半截笔迹）。
    if (gestureGeneration != gesture.viewTransformGeneration) {
        gesture.draftPoints.clear()
        return
    }

    when (finished) {
        // 抬手才冻结点列表：必须快照（toList），否则下次手势
        // draftPoints.clear() 会把已提交的历史笔迹一并清掉。
        is StrokeElement ->
            if (finished.erase && !moved) {
                // 单点橡皮（C2）：单击不落橡皮点，改为删掉点击命中的
                // 那一个元素（最上层优先）；没命中就什么都不做。
                hitTopElement(document.state.elements, startImage, fit) { it.realHitBounds(fit) }
                    ?.let { document.removeElement(it.id) }
            } else {
                document.add(
                    finished.copy(
                        id = document.nextElementId(),
                        points = gesture.draftPoints.toList(),
                    ),
                )
            }

        null -> Unit
        // 马赛克是涂抹笔刷：单击也落一个 dab，抬手快照轨迹。
        is MosaicElement -> document.add(
            finished.copy(
                id = document.nextElementId(),
                points = gesture.draftPoints.toList(),
            ).sanitized(),
        )
        // 模糊同马赛克：单击也落一个 dab，抬手快照轨迹。
        is BlurElement -> document.add(
            finished.copy(
                id = document.nextElementId(),
                points = gesture.draftPoints.toList(),
            ).sanitized(),
        )
        // 形状：未移动的单击不落元素（与旧行为一致）；
        // 笔迹单击画点——上面的分支不看 moved，恒落（points 至少有起点）。
        else -> if (moved) document.add(finished.withElementId(document.nextElementId()))
    }
}

// ---------------------------------------------------------------------------
// 共享小工具
// ---------------------------------------------------------------------------

/** 元素命中框：文字用真实测量框；其余走模型层包围盒。 */
private fun hitBoundsOf(element: AnnotationElement, fit: ContainFit) =
    hitBounds(element, fit) { it.realHitBounds(fit) }

private fun Pt.clampToImage(document: AnnotateDocument): Pt = Pt(
    x = x.coerceIn(0f, document.imageWidth.toFloat()),
    y = y.coerceIn(0f, document.imageHeight.toFloat()),
)

internal fun Offset.asPt(): Pt = Pt(x, y)

/** 旋转柄距选中框顶边的间距（屏幕 dp）。与视图层绘制共用一个值。 */
internal const val ROTATE_HANDLE_GAP_DP = 28f
