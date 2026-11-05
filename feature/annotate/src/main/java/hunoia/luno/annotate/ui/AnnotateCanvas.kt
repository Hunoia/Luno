package hunoia.luno.annotate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hunoia.luno.annotate.R
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.AnnotateOps
import hunoia.luno.annotate.model.AnnotationElement
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
import hunoia.luno.annotate.model.adjustCorner
import hunoia.luno.annotate.model.bounds
import hunoia.luno.annotate.model.cornerAt
import hunoia.luno.annotate.model.hitElementHandle
import hunoia.luno.annotate.model.mosaicBrushWidth
import hunoia.luno.annotate.model.orientedGeometry
import hunoia.luno.annotate.model.pointInPolygon
import hunoia.luno.annotate.model.pointNearSegment
import hunoia.luno.annotate.model.rotateAround
import hunoia.luno.annotate.model.rotationRadOf
import hunoia.luno.annotate.model.selectionHolds
import hunoia.luno.annotate.model.shapeCrossSegments
import hunoia.luno.annotate.model.shapeOutlinePoints
import hunoia.luno.annotate.model.strokeWidthInImagePx
import hunoia.luno.annotate.model.transformed
import hunoia.luno.annotate.model.translateSelection
import hunoia.luno.annotate.model.translated
import hunoia.luno.annotate.model.withRotation
import hunoia.luno.annotate.render.AnnotateGeometryCache
import hunoia.luno.annotate.render.AnnotateVisuals
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.annotate.render.drawAnnotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * 两个宿主共用的标注画布：手势 → 文档，渲染 → [drawAnnotate]。
 *
 * 只处理画布区域；工具条、穿透开关、导出属于宿主 chrome。
 * 文字编辑直接用 Compose `BasicTextField` 落在画布上，浮层宿主因此要求画布窗
 * 创建即 focusable（计划 D1 / §3.1），图片编辑宿主则天然有 IME。
 */
@Composable
fun AnnotateCanvas(
    document: AnnotateDocument,
    base: BaseImage?,
    modifier: Modifier = Modifier,
    showSelection: Boolean = true,
    /** 文字编辑器开关：浮层宿主据此隐藏工具条，避免工具条浮在键盘上挡住按键。 */
    onTextEditingChanged: ((Boolean) -> Unit)? = null,
    /** 画布任意落笔（含绘制/拖选/缩放开始）：宿主据此收起样式面板/弹出卡。 */
    onPointerDown: (() -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // contain 基准（不含双指视图变换）；缩放/平移只影响预览，导出永远 identity。
    val baseFit = remember(canvasSize, document.imageWidth, document.imageHeight) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 ||
            document.imageWidth <= 0 || document.imageHeight <= 0
        ) {
            null
        } else {
            ContainFit.of(
                imageWidth = document.imageWidth.toFloat(),
                imageHeight = document.imageHeight.toFloat(),
                viewWidth = canvasSize.width.toFloat(),
                viewHeight = canvasSize.height.toFloat(),
            )
        }
    }

    val state = document.state
    val fit = remember(baseFit, state.viewZoom, state.viewPan) {
        baseFit?.withViewTransform(state.viewZoom, state.viewPan)
    }
    val tool = state.activeTool
    val style = state.styleFor(tool)
    // 指针手势块只在 tool 变化时重建；fit 随缩放变化，用 rememberUpdatedState
    // 让每次新手势读到最新视图变换，而不会在手势中途重启 pointerInput。
    val latestFit by rememberUpdatedState(fit)
    // 直接捕获 style 会拿到旧颜色/线宽；用 rememberUpdatedState 让绘制始终读最新风格。
    val currentStyle by rememberUpdatedState(style)
    // 文字编辑始终用「文字」工具的风格：编辑中切到马赛克/画笔不应改掉字色。
    val textStyle = state.styleFor(ToolKind.TEXT)
    val currentTextStyle by rememberUpdatedState(textStyle)

    var draft by remember { mutableStateOf<AnnotationElement?>(null) }
    // 笔迹草稿的点列表：手势期间只追加（O(1)），不复制整条列表。
    // 旧实现每个 MOVE 都 `points = points + p`，5000 点的笔迹累计复制 ~千万引用，
    // GC 压力直接掉帧（审计 B1）。StrokeElement 挂的是这份活列表，
    // 追加触发绘制快照失效 → 重绘；抬手才 toList() 冻结进文档。
    val draftPoints = remember { mutableStateListOf<Pt>() }
    var draftSelection by remember { mutableStateOf<PxRect?>(null) }
    // 矩形擦的拖拽草稿（图片像素）；抬起即清，只用于画布上的选框预览。
    var draftErase by remember { mutableStateOf<PxRect?>(null) }
    var editor by remember { mutableStateOf<TextEditor?>(null) }
    var textValue by remember { mutableStateOf("") }
    // 双指开始时递增：进行中的单指绘制据此丢弃草稿，避免缩放时留下半截笔迹。
    var viewTransformGeneration by remember { mutableStateOf(0) }
    // 元素几何缓存（图片坐标）：缩放/平移时不重算每个点，元素增删/撤销时清空。
    val geometryCache = remember { AnnotateGeometryCache() }
    LaunchedEffect(state.elements) { geometryCache.clear() }

    val displayState by remember {
        derivedStateOf {
            val current = document.state
            val currentDraft = draft ?: return@derivedStateOf current
            // 元素调整的草稿 id 与文档里的元素相同 → 原地替换；新建元素 → 追加。
            if (current.elements.any { it.id == currentDraft.id }) {
                current.copy(
                    elements = current.elements.map { if (it.id == currentDraft.id) currentDraft else it },
                )
            } else {
                current.copy(elements = current.elements + currentDraft)
            }
        }
    }

    fun commitText() {
        val currentEditor = editor ?: return
        val text = textValue.trim()
        if (text.isNotEmpty()) {
            val existing = currentEditor.existing
            if (existing != null) {
                // 只改文字本身与位置：编辑旧文字不应被当前文字工具的风格覆写（审计 D4）。
                document.replace(existing.copy(text = text, position = currentEditor.position))
            } else {
                document.add(
                    TextElement(
                        id = document.nextElementId(),
                        position = currentEditor.position,
                        text = text,
                        colorArgb = currentTextStyle.colorArgb,
                        textSize = currentTextStyle.textSize,
                    ),
                )
            }
        }
        editor = null
        textValue = ""
    }

    LaunchedEffect(document) {
        // 单实例复用（Activity singleTask 换图）：旧文档的草稿/编辑器不能带到新文档上。
        draft = null
        draftPoints.clear()
        draftSelection = null
        draftErase = null
        editor = null
        textValue = ""
        viewTransformGeneration = 0
        geometryCache.clear()
    }

    LaunchedEffect(tool) {
        // 切到非文字工具时先交卷：否则编辑器会在别的工具下飘着，
        // 后续落笔提交时用的是新工具的风格。
        if (tool != ToolKind.TEXT && editor != null) commitText()
    }

    val editingText = editor != null
    LaunchedEffect(editingText) {
        onTextEditingChanged?.invoke(editingText)
    }
    DisposableEffect(Unit) {
        onDispose { onTextEditingChanged?.invoke(false) }
    }

    // 宿主导出/退出/重截前会调 document.commitPendingText()：
    // 工具栏按钮不一定抢焦点，只靠 onFocusChanged 会漏掉正在输入的文字。
    DisposableEffect(document) {
        document.pendingTextCommit = { commitText() }
        onDispose { document.pendingTextCommit = null }
    }

    /** 双指缩放 + 平移（B7）：只改视图，不写笔迹、不进撤销。 */
    fun applyPinch(
        base: ContainFit,
        previousCentroid: Offset,
        centroid: Offset,
        zoomFactor: Float,
    ) {
        val current = document.state
        val oldFit = base.withViewTransform(current.viewZoom, current.viewPan)
        val focus = oldFit.viewToImage(previousCentroid.asPt())
        val newZoom = (current.viewZoom * zoomFactor)
            .coerceIn(AnnotateOps.MIN_VIEW_ZOOM, AnnotateOps.MAX_VIEW_ZOOM)
        val newScale = base.scale * newZoom
        // 让双指中心下的图片点跟住当前双指中心：同时完成缩放与平移。
        val centeredX = (base.viewWidth - base.imageWidth * newScale) / 2f
        val centeredY = (base.viewHeight - base.imageHeight * newScale) / 2f
        val pan = Pt(
            x = centroid.x - focus.x * newScale - centeredX,
            y = centroid.y - focus.y * newScale - centeredY,
        )
        document.setViewTransform(newZoom, base.clampPan(newZoom, pan))
    }

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            // 第二指针处理器：单指完全不消费（绘制手势照常），双指才接管。
            // 注册在绘制手势之前，Initial pass 保证先看到事件并消费缩放中的 MOVE。
            .pointerInput(document, baseFit) {
                val currentBase = baseFit ?: return@pointerInput
                awaitPointerEventScope {
                    var transforming = false
                    var lastCentroid = Offset.Zero
                    var lastDistance = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            val centroid = pressed
                                .fold(Offset.Zero) { acc, change -> acc + change.position } /
                                pressed.size.toFloat()
                            val distance = (pressed[0].position - pressed[1].position).getDistance()
                            if (!transforming) {
                                transforming = true
                                viewTransformGeneration += 1
                                // 丢掉正在进行的单指草稿：缩放不应留下半截笔迹。
                                draft = null
                                draftPoints.clear()
                                draftSelection = null
                                draftErase = null
                                lastCentroid = centroid
                                lastDistance = distance
                            } else {
                                val factor = if (lastDistance > 1f) distance / lastDistance else 1f
                                applyPinch(currentBase, lastCentroid, centroid, factor)
                                lastCentroid = centroid
                                lastDistance = distance
                            }
                            event.changes.forEach { it.consume() }
                        } else if (transforming) {
                            transforming = false
                        }
                    }
                }
            }
            .pointerInput(document, tool, density) {
                awaitEachGesture {
                    val currentFit = latestFit ?: return@awaitEachGesture
                    // consumed 的 down（落在文字输入框上）不再当作画布手势，
                    // 否则点输入框内部会被父层当成新的落字点。
                    val down = awaitFirstDown(requireUnconsumed = true)
                    // 宿主收面板/弹出卡：落笔即交互开始（样式面板不再遮挡画布）。
                    onPointerDown?.invoke()
                    // 起手前先交卷：画布上旧文字编辑器如果还开着，这次落点应先提交，
                    // 否则会被下面的 TEXT 分支直接覆盖，已输入内容丢失。
                    if (editor != null) commitText()
                    val startImage = currentFit.viewToImage(down.position.asPt()).clampToImage(document)
                    val gestureGeneration = viewTransformGeneration
                    var moved = false

                    when (tool) {
                        ToolKind.TEXT -> {
                            document.selectElement(null)
                            // 真实测量命中（模型层 bounds() 是 textSize*length 估算，混排不准）。
                            val hit = document.state.elements.asReversed()
                                .filterIsInstance<TextElement>()
                                .firstOrNull { it.realHitBounds(currentFit).contains(startImage) }
                            val target = hit?.position ?: startImage
                            editor = TextEditor(position = target, existing = hit)
                            textValue = hit?.text ?: ""
                            drag(down.id) { it.consume() }
                        }

                        ToolKind.NUMBER -> {
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

                        ToolKind.SELECT -> {
                            val threshold = 28f * density
                            val selectedElement = document.state.selectedElementId
                                ?.let { id -> document.state.elements.firstOrNull { it.id == id } }
                            val existingSelection = document.state.selection?.normalized()
                            val selectionCorner = existingSelection
                                ?.let { cornerAt(it, currentFit, down.position.asPt(), threshold) }

                            // 选中元素的有向几何（角/边/旋转柄），用于缩放/旋转命中。
                            var selectedHandle: ElementHandle? = null
                            var selectedBox: PxRect? = null
                            var selectedCenter: Pt? = null
                            var selectedRot = 0f
                            if (selectedElement != null) {
                                val box = elementBounds(selectedElement, currentFit)
                                selectedBox = box
                                selectedRot = selectedElement.rotationRadOf()
                                selectedCenter = box.center
                                val gap = ROTATE_HANDLE_GAP_DP * density / currentFit.scale
                                val geom = orientedGeometry(box, selectedRot, gap)
                                selectedHandle = hitElementHandle(
                                    geom,
                                    currentFit,
                                    down.position.asPt(),
                                    threshold,
                                )
                            }
                            val hovered = if (selectedHandle == null && selectionCorner == null) {
                                hitElementAt(document, startImage, currentFit)
                            } else {
                                null
                            }

                            when {
                                // 1) 选中元素角/边缩放 + 旋转（有向柄）。
                                selectedElement != null && selectedHandle != null &&
                                    selectedBox != null && selectedCenter != null -> {
                                    val box = selectedBox
                                    val center = selectedCenter
                                    val rot = selectedRot
                                    draft = selectedElement
                                    if (selectedHandle == ElementHandle.ROTATE) {
                                        drag(down.id) { change ->
                                            moved = true
                                            val p = currentFit.viewToImage(change.position.asPt())
                                            val dx = p.x - center.x
                                            val dy = p.y - center.y
                                            if (dx * dx + dy * dy > 1f) {
                                                // 顶边外法线方向对应角度 atan2(dx, -dy)。
                                                val newRot = snapRotation(atan2(dx, -dy))
                                                draft = selectedElement.withRotation(newRot)
                                            }
                                            change.consume()
                                        }
                                    } else {
                                        drag(down.id) { change ->
                                            moved = true
                                            val p = currentFit.viewToImage(change.position.asPt())
                                                .clampToImage(document)
                                            if (p.x == p.x && p.y == p.y) {
                                                // 指针逆旋回局部坐标，按角/边柄调整包围盒。
                                                val local = rotateAround(p, center, -rot)
                                                val to = adjustByHandle(box, selectedHandle, local)
                                                if (to.width >= 4f || to.height >= 4f) {
                                                    draft = selectedElement.transformed(box, to)
                                                }
                                            }
                                            change.consume()
                                        }
                                    }
                                    val finished = draft
                                    draft = null
                                    if (moved && finished != null) document.replace(finished)
                                }

                                // 2) 点中元素：选中并拖动（B5）。
                                hovered != null -> {
                                    document.selectElement(hovered.id)
                                    draft = hovered
                                    drag(down.id) { change ->
                                        moved = true
                                        val p = currentFit.viewToImage(change.position.asPt())
                                        draft = hovered.translated(
                                            p.x - startImage.x,
                                            p.y - startImage.y,
                                        )
                                        change.consume()
                                    }
                                    val finished = draft
                                    draft = null
                                    if (moved && finished != null) document.replace(finished)
                                }

                                // 3) 选区四角调整（B2 既有行为）。
                                existingSelection != null && selectionCorner != null -> {
                                    if (document.state.selectedElementId != null) {
                                        document.selectElement(null)
                                    }
                                    var rect = existingSelection!!
                                    drag(down.id) { change ->
                                        moved = true
                                        val p = currentFit.viewToImage(change.position.asPt())
                                        rect = adjustCorner(rect, selectionCorner, p)
                                        draftSelection = rect.normalized()
                                        change.consume()
                                    }
                                    document.setSelection(rect)
                                    draftSelection = null
                                }

                                // 4) 选区内拖动：整体平移选区（B6）。
                                existingSelection != null && selectionHolds(
                                    existingSelection,
                                    currentFit,
                                    down.position.asPt(),
                                ) -> {
                                    if (document.state.selectedElementId != null) {
                                        document.selectElement(null)
                                    }
                                    var rect = existingSelection!!
                                    drag(down.id) { change ->
                                        moved = true
                                        val p = currentFit.viewToImage(change.position.asPt())
                                        rect = translateSelection(
                                            existingSelection,
                                            p.x - startImage.x,
                                            p.y - startImage.y,
                                            document.imageWidth.toFloat(),
                                            document.imageHeight.toFloat(),
                                        )
                                        draftSelection = rect
                                        change.consume()
                                    }
                                    if (moved) document.setSelection(rect)
                                    draftSelection = null
                                }

                                // 5) 空白处拖新选区（B2 既有行为）。
                                else -> {
                                    if (document.state.selectedElementId != null) {
                                        document.selectElement(null)
                                    }
                                    var rect = PxRect.fromPoints(startImage, startImage)
                                    var current = startImage
                                    val ok = drag(down.id) { change ->
                                        moved = true
                                        current = currentFit.viewToImage(change.position.asPt())
                                            .clampToImage(document)
                                        rect = PxRect.fromPoints(startImage, current)
                                        draftSelection = rect.normalized()
                                        change.consume()
                                    }
                                    val finalRect = if (ok) rect else {
                                        PxRect.fromPoints(startImage, current)
                                    }
                                    document.setSelection(finalRect)
                                    draftSelection = null
                                }
                            }
                        }

                        else -> {
                            // 画新内容前清掉旧选中框（NUMBER 自动选中后切其它工具不再挂着框）。
                            document.selectElement(null)
                            // 矩形擦（C1）：橡皮 + 矩形模式拖出选框，松手清除相交的笔迹/形状。
                            if (tool == ToolKind.ERASER && document.state.eraserRect) {
                                var rect = PxRect.fromPoints(startImage, startImage)
                                draftErase = rect.normalized()
                                drag(down.id) { change ->
                                    moved = true
                                    val p = currentFit.viewToImage(change.position.asPt())
                                        .clampToImage(document)
                                    rect = PxRect.fromPoints(startImage, p)
                                    draftErase = rect.normalized()
                                    change.consume()
                                }
                                draftErase = null
                                // 未移动的单击不擦（防误触，同选区的最小尺寸语义）。
                                if (moved && gestureGeneration == viewTransformGeneration) {
                                    document.eraseInRect(rect)
                                }
                                return@awaitEachGesture
                            }
                            // 线宽是屏幕 dp（B9）：落笔瞬间换算成图片像素，
                            // 高分辨率底图 / 缩放下手感一致。
                            val strokeImageWidth = strokeWidthInImagePx(
                                currentStyle.strokeWidth,
                                density,
                                currentFit,
                            )
                            val mosaicWidth = mosaicBrushWidth(currentStyle.mosaicCell)
                            // 距离抽稀阈值（图片像素）：1 视图像素换算回图片像素，
                            // 但不超过线宽/4（保证采样点密到圆头笔帽盖得住），
                            // 且不小于 0.5 图片像素。预览亚像素丢点不改变可见形状，
                            // 导出（scale=1）同样落在原轨迹的笔宽内。
                            // 马赛克笔刷很粗，允许按笔刷直径/4 稀疏采样，避免长涂抹存上万点。
                            val stepImage = if (tool == ToolKind.MOSAIC) {
                                (mosaicWidth / 4f).coerceAtLeast(1f / currentFit.scale)
                            } else {
                                (1f / currentFit.scale)
                                    .coerceAtMost(strokeImageWidth / 4f)
                                    .coerceAtLeast(0.5f)
                            }
                            if (tool == ToolKind.PEN || tool == ToolKind.ERASER ||
                                tool == ToolKind.HIGHLIGHT || tool == ToolKind.MOSAIC
                            ) {
                                // 笔迹/马赛克：点直接追加进活列表（O(1)），元素对象只建一次；
                                // 旧实现每个 MOVE 都 points + p 复制整条列表，长笔迹掉帧。
                                draftPoints.clear()
                                draftPoints.add(startImage)
                            }
                            draft = when (tool) {
                                ToolKind.PEN, ToolKind.ERASER, ToolKind.HIGHLIGHT -> StrokeElement(
                                    id = DRAFT_ID,
                                    points = draftPoints,
                                    colorArgb = currentStyle.colorArgb,
                                    width = strokeImageWidth,
                                    erase = tool == ToolKind.ERASER,
                                    highlight = tool == ToolKind.HIGHLIGHT,
                                )

                                ToolKind.RECT, ToolKind.LINE, ToolKind.ARROW,
                                ToolKind.ELLIPSE, ToolKind.TRIANGLE, ToolKind.CHECK,
                                ToolKind.CROSS, ToolKind.STAR, ToolKind.ROUND_RECT,
                                ToolKind.HEART,
                                -> ShapeElement(
                                    id = DRAFT_ID,
                                    start = startImage,
                                    end = startImage,
                                    colorArgb = currentStyle.colorArgb,
                                    width = strokeImageWidth,
                                    kind = when (tool) {
                                        ToolKind.RECT -> ElementKind.RECT
                                        ToolKind.LINE -> ElementKind.LINE
                                        ToolKind.ARROW -> ElementKind.ARROW
                                        ToolKind.ELLIPSE -> ElementKind.ELLIPSE
                                        ToolKind.TRIANGLE -> ElementKind.TRIANGLE
                                        ToolKind.CHECK -> ElementKind.CHECK
                                        ToolKind.CROSS -> ElementKind.CROSS
                                        ToolKind.STAR -> ElementKind.STAR
                                        ToolKind.ROUND_RECT -> ElementKind.ROUND_RECT
                                        else -> ElementKind.HEART
                                    },
                                )

                                // 马赛克是涂抹笔刷：笔刷宽度随颗粒比例走，
                                // 颗粒越大笔刷越粗；单点也能落一个 dab。
                                ToolKind.MOSAIC -> MosaicElement(
                                    id = DRAFT_ID,
                                    points = draftPoints,
                                    width = mosaicWidth,
                                    cellSize = currentStyle.mosaicCell,
                                )

                                ToolKind.TEXT, ToolKind.SELECT, ToolKind.NUMBER -> null
                            }
                            var lastPt = startImage
                            drag(down.id) { change ->
                                moved = true
                                val p = currentFit.viewToImage(change.position.asPt())
                                    .clampToImage(document)
                                when (val d = draft) {
                                    is StrokeElement -> {
                                        val dx = p.x - lastPt.x
                                        val dy = p.y - lastPt.y
                                        if (dx * dx + dy * dy >= stepImage * stepImage) {
                                            draftPoints.add(p)
                                            lastPt = p
                                        }
                                    }

                                    is ShapeElement -> draft = d.copy(end = p)
                                    is MosaicElement -> {
                                        // 与笔迹同一套距离抽稀；笔刷粗，1px 视图像素足够。
                                        val dx = p.x - lastPt.x
                                        val dy = p.y - lastPt.y
                                        if (dx * dx + dy * dy >= stepImage * stepImage) {
                                            draftPoints.add(p)
                                            lastPt = p
                                        }
                                    }

                                    else -> Unit
                                }
                                change.consume()
                            }
                            val finished = draft
                            draft = null

                            // 双指介入过：本次单指手势作废（缩放不应留下半截笔迹）。
                            if (gestureGeneration != viewTransformGeneration) {
                                draftPoints.clear()
                                return@awaitEachGesture
                            }

                            when (finished) {
                                // 抬手才冻结点列表：必须快照（toList），否则下次手势
                                // draftPoints.clear() 会把已提交的历史笔迹一并清掉。
                                is StrokeElement ->
                                    if (finished.erase && !moved) {
                                        // 单点橡皮（C2）：单击不落橡皮点，改为删掉点击命中的
                                        // 那一个元素（最上层优先）；没命中就什么都不做。
                                        hitElementAt(document, startImage, currentFit)
                                            ?.let { document.removeElement(it.id) }
                                    } else {
                                        document.add(
                                            finished.copy(
                                                id = document.nextElementId(),
                                                points = draftPoints.toList(),
                                            ),
                                        )
                                    }

                                null -> Unit
                                // 马赛克是涂抹笔刷：单击也落一个 dab，抬手快照轨迹。
                                is MosaicElement -> document.add(
                                    finished.copy(
                                        id = document.nextElementId(),
                                        points = draftPoints.toList(),
                                    ).sanitized(),
                                )
                                // 形状：未移动的单击不落元素（与旧行为一致）；
                                // 笔迹单击画点——上面的分支不看 moved，恒落（points 至少有起点）。
                                else -> if (moved) document.add(finished.withId(document.nextElementId()))
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val currentFit = fit ?: return@Canvas
            drawAnnotate(
                state = displayState,
                base = base,
                fit = currentFit,
                showSelection = showSelection,
                selectionOverride = draftSelection,
                scrimAroundImage = base != null,
                geometryCache = geometryCache,
            )
            // 矩形擦拖拽预览（C1）：白色虚线框，抬起即消失。
            draftErase?.let { rect ->
                val view = currentFit.imageRectToView(rect.normalized())
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
                    val box = elementBounds(element, currentFit)
                    if (box.width > 0.5f || box.height > 0.5f) {
                        val rot = element.rotationRadOf()
                        val gap = ROTATE_HANDLE_GAP_DP * density / currentFit.scale
                        val geom = orientedGeometry(box, rot, gap)
                        // 有向框：深色外描边 + 白内芯，白底/深底都可见。
                        val corners = geom.corners.map { currentFit.imageToView(it) }
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
                        // 角柄 + 边中柄（方块）+ 旋转柄（圆），白芯深边。
                        val handle = ELEMENT_HANDLE_DP.dp.toPx()
                        val border = ELEMENT_HANDLE_BORDER_DP.dp.toPx()
                        (geom.corners + geom.edges).forEach { world ->
                            val v = currentFit.imageToView(world)
                            drawRect(
                                color = AnnotateVisuals.ElementHandleStroke,
                                topLeft = Offset(v.x - handle / 2f - border, v.y - handle / 2f - border),
                                size = Size(handle + 2f * border, handle + 2f * border),
                            )
                            drawRect(
                                color = AnnotateVisuals.ElementHandleFill,
                                topLeft = Offset(v.x - handle / 2f, v.y - handle / 2f),
                                size = Size(handle, handle),
                            )
                        }
                        val rv = currentFit.imageToView(geom.rotate)
                        drawCircle(
                            color = AnnotateVisuals.ElementHandleStroke,
                            radius = handle / 2f + border,
                            center = Offset(rv.x, rv.y),
                        )
                        drawCircle(
                            color = AnnotateVisuals.ElementHandleFill,
                            radius = handle / 2f,
                            center = Offset(rv.x, rv.y),
                        )
                    }
                }
        }

        val currentEditor = editor
        if (currentEditor != null && fit != null) {
            val viewPos = fit.imageToView(currentEditor.position)
            val focusRequester = remember { FocusRequester() }
            var hadFocus by remember { mutableStateOf(false) }
            // 编辑器位置由 handle 拖动维护：editor 状态每次 move 更新，
            // pointerInput 用 Unit 键避免拖动中重启。
            val editorState = rememberUpdatedState(editor)
            Row(
                modifier = Modifier.offset { IntOffset(viewPos.x.roundToInt(), viewPos.y.roundToInt()) },
                verticalAlignment = Alignment.Top,
            ) {
                // 拖动把手：编辑中直接挪文字位置（P1），不改变文字内容。
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(Unit) {
                            var startPos = Pt(0f, 0f)
                            var accX = 0f
                            var accY = 0f
                            detectDragGestures(
                                onDragStart = {
                                    startPos = editorState.value?.position ?: Pt(0f, 0f)
                                    accX = 0f
                                    accY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accX += dragAmount.x
                                    accY += dragAmount.y
                                    val currentEditorNow = editorState.value ?: return@detectDragGestures
                                    val currentFitNow = latestFit ?: return@detectDragGestures
                                    editor = currentEditorNow.copy(
                                        position = Pt(
                                            x = (startPos.x + accX / currentFitNow.scale)
                                                .coerceIn(0f, document.imageWidth.toFloat()),
                                            y = (startPos.y + accY / currentFitNow.scale)
                                                .coerceIn(0f, document.imageHeight.toFloat()),
                                        ),
                                    )
                                },
                            )
                        },
                )
                BasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                hadFocus = true
                            } else if (hadFocus) {
                                commitText()
                            }
                        }
                        .onKeyEvent { event ->
                            if (event.key == Key.Escape) {
                                editor = null
                                textValue = ""
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = TextStyle(
                        color = Color(textStyle.colorArgb),
                        fontSize = (textStyle.textSize * fit.scale / density).sp,
                    ),
                    cursorBrush = SolidColor(Color.White),
                    // 多行（D3）：回车换行。结束编辑走旁边的 ✓ 按钮——
                    // IME 的 Done 键在多行下会把换行吞掉，不能当完成入口。
                    singleLine = false,
                )
                // ✓ 完成按钮：浮层窗口 NOT_FOCUSABLE，无法依赖 IME 完成键，
                // 按钮必须与编辑器同窗内联（画布同层）。
                IconButton(
                    onClick = { commitText() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.annotate_text_done),
                        tint = Color.White,
                    )
                }
            }
            LaunchedEffect(currentEditor) {
                focusRequester.requestFocus()
            }
        }
    }
}

private const val DRAFT_ID = -1L

/** 旋转柄距选中框顶边的间距（屏幕 dp）。 */
private const val ROTATE_HANDLE_GAP_DP = 28f
/** 选中柄边长（屏幕 dp），加大易点。 */
private const val ELEMENT_HANDLE_DP = 14f
/** 选中柄深色描边宽（屏幕 dp）。 */
private const val ELEMENT_HANDLE_BORDER_DP = 2f
/** 选中框深色外描边宽（屏幕 dp）。 */
private const val ELEMENT_SELECTION_OUTER_DP = 4f
/** 选中框白色内芯宽（屏幕 dp）。 */
private const val ELEMENT_SELECTION_INNER_DP = 1.5f

/** 旋转吸附：距 90° 的倍数 5° 内吸附（横平竖直常用），否则自由。 */
private fun snapRotation(rad: Float): Float {
    val snap = (PI / 2).toFloat()
    val nearest = ((rad / snap).let { if (it >= 0) (it + 0.5f).toInt() else (it - 0.5f).toInt() }) * snap
    if (abs(rad - nearest) < (5.0 * PI / 180.0)) return nearest
    return rad
}

private data class TextEditor(
    val position: Pt,
    val existing: TextElement?,
)

private fun Pt.clampToImage(document: AnnotateDocument): Pt = Pt(
    x = x.coerceIn(0f, document.imageWidth.toFloat()),
    y = y.coerceIn(0f, document.imageHeight.toFloat()),
)

/**
 * 元素命中框（图片像素）。文字用真实测量框；其余走模型层包围盒。
 */
private fun elementBounds(element: AnnotationElement, fit: ContainFit): PxRect = when (element) {
    is TextElement -> element.realHitBounds(fit)
    is NumberElement -> element.badgeBounds
    else -> element.bounds()
}

/**
 * 单点橡皮（C2）/ SELECT 点选的命中测试：从最上层往下找第一个被 [at] 点中的元素。
 *
 * 笔迹按线宽半径逐段判；矩形形状含内部（点进框里就中），直线/箭头只判主干；
 * 文字用真实测量框（多行由 `realHitBounds` 按 `\n` 分行）；马赛克按轨迹包围盒。
 * 橡皮笔迹自身不参与命中——点在已擦区域上不该把橡皮轨迹删掉（内容回不来）。
 */
private fun hitElementAt(
    document: AnnotateDocument,
    at: Pt,
    fit: ContainFit,
): AnnotationElement? = document.state.elements.asReversed().firstOrNull { el ->
    // 旋转元素：把命中点逆旋回局部坐标系再判（旋转是刚体变换，尺寸/位置不变）。
    val rot = el.rotationRadOf()
    val local = if (rot == 0f) at else {
        val box = elementBounds(el, fit)
        rotateAround(at, box.center, -rot)
    }
    hitElementLocal(el, local, fit)
}

/** 未旋转元素的命中（[at] 已在元素局部坐标系）。 */
private fun hitElementLocal(
    el: AnnotationElement,
    at: Pt,
    fit: ContainFit,
): Boolean = when (el) {
    is StrokeElement -> !el.erase && (
        if (el.points.size == 1) {
            pointNearSegment(at, el.points[0], el.points[0], el.width / 2f)
        } else {
            el.points.zipWithNext().any { (a, b) -> pointNearSegment(at, a, b, el.width / 2f) }
        }
    )

    is ShapeElement -> when (el.kind) {
        ElementKind.RECT -> {
            val b = el.bounds
            val tl = Pt(b.left, b.top)
            val tr = Pt(b.right, b.top)
            val br = Pt(b.right, b.bottom)
            val bl = Pt(b.left, b.bottom)
            // 点进框内，或落在任一条边的线宽邻域内。
            b.contains(at) ||
                listOf(tl to tr, tr to br, br to bl, bl to tl)
                    .any { (a, c) -> pointNearSegment(at, a, c, el.width / 2f) }
        }

        ElementKind.LINE, ElementKind.ARROW ->
            pointNearSegment(at, el.start, el.end, el.width / 2f)

        ElementKind.CROSS ->
            shapeCrossSegments(el.start, el.end).any { (a, b) ->
                pointNearSegment(at, a, b, el.width / 2f)
            }

        ElementKind.CHECK -> {
            val pts = shapeOutlinePoints(el.kind, el.start, el.end)
            pts.zipWithNext().any { (a, b) -> pointNearSegment(at, a, b, el.width / 2f) }
        }

        ElementKind.ELLIPSE, ElementKind.TRIANGLE,
        ElementKind.STAR, ElementKind.ROUND_RECT, ElementKind.HEART,
        -> {
            val pts = shapeOutlinePoints(el.kind, el.start, el.end)
            val nearOutline = pts.zipWithNext().any { (a, b) ->
                pointNearSegment(at, a, b, el.width / 2f)
            } || pts.size >= 2 && pointNearSegment(at, pts.last(), pts.first(), el.width / 2f)
            // 点进形状内部也能选中（空心形状点中心是常见操作）。
            nearOutline || pointInPolygon(at, pts)
        }

        else -> false
    }

    is TextElement -> el.realHitBounds(fit).contains(at)
    is NumberElement -> el.badgeBounds.contains(at)
    is MosaicElement -> {
        val nearTrail = when {
            el.points.isEmpty() -> false
            el.points.size == 1 -> pointNearSegment(at, el.points[0], el.points[0], el.width / 2f)
            else -> el.points.zipWithNext().any { (a, b) ->
                pointNearSegment(at, a, b, el.width / 2f)
            }
        }
        nearTrail || el.rect?.normalized()?.contains(at) == true
    }
}

private fun AnnotationElement.withId(id: Long): AnnotationElement = when (this) {
    is StrokeElement -> copy(id = id)
    is ShapeElement -> copy(id = id)
    is TextElement -> copy(id = id)
    is MosaicElement -> copy(id = id)
    is NumberElement -> copy(id = id)
}

private fun Offset.asPt(): Pt = Pt(x, y)
