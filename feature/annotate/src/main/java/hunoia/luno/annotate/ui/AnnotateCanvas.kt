package hunoia.luno.annotate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import hunoia.luno.annotate.model.ElementKind
import hunoia.luno.annotate.model.MosaicElement
import hunoia.luno.annotate.model.Pt
import hunoia.luno.annotate.model.PxRect
import hunoia.luno.annotate.model.ShapeElement
import hunoia.luno.annotate.model.StrokeElement
import hunoia.luno.annotate.model.TextElement
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.model.bounds
import hunoia.luno.annotate.model.pointNearSegment
import hunoia.luno.annotate.model.transformed
import hunoia.luno.annotate.model.translated
import hunoia.luno.annotate.render.AnnotateVisuals
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.annotate.render.drawAnnotate
import kotlin.math.abs
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
                // 只改文字本身：编辑旧文字不应被当前文字工具的风格覆写（审计 D4）。
                document.replace(existing.copy(text = text))
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
                    // 起手前先交卷：画布上旧文字编辑器如果还开着，这次落点应先提交，
                    // 否则会被下面的 TEXT 分支直接覆盖，已输入内容丢失。
                    if (editor != null) commitText()
                    val startImage = currentFit.viewToImage(down.position.asPt()).clampToImage(document)
                    val gestureGeneration = viewTransformGeneration
                    var moved = false

                    when (tool) {
                        ToolKind.TEXT -> {
                            // 真实测量命中（模型层 bounds() 是 textSize*length 估算，混排不准）。
                            val hit = document.state.elements.asReversed()
                                .filterIsInstance<TextElement>()
                                .firstOrNull { it.realHitBounds(currentFit).contains(startImage) }
                            val target = hit?.position ?: startImage
                            editor = TextEditor(position = target, existing = hit)
                            textValue = hit?.text ?: ""
                            drag(down.id) { it.consume() }
                        }

                        ToolKind.SELECT -> {
                            val threshold = 28f * density
                            val selectedElement = document.state.selectedElementId
                                ?.let { id -> document.state.elements.firstOrNull { it.id == id } }
                            val selectedBounds = selectedElement?.let { elementBounds(it, currentFit) }
                            val elementCorner = selectedBounds
                                ?.let { cornerAt(it, currentFit, down.position, threshold) }
                            val existingSelection = document.state.selection?.normalized()
                            val selectionCorner = existingSelection
                                ?.let { cornerAt(it, currentFit, down.position, threshold) }
                            val hovered = if (elementCorner == null && selectionCorner == null) {
                                hitElementAt(document, startImage, currentFit)
                            } else {
                                null
                            }

                            when {
                                // 1) 选中元素的角柄缩放（B5）。
                                selectedElement != null && elementCorner != null -> {
                                    val from = selectedBounds ?: return@awaitEachGesture
                                    draft = selectedElement
                                    drag(down.id) { change ->
                                        moved = true
                                        val p = currentFit.viewToImage(change.position.asPt())
                                            .clampToImage(document)
                                        if (p.x == p.x && p.y == p.y) {
                                            val to = adjustCorner(from, elementCorner, p)
                                            if (to.width >= 4f || to.height >= 4f) {
                                                draft = selectedElement.transformed(from, to)
                                            }
                                        }
                                        change.consume()
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
                                    down.position,
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

                                ToolKind.RECT, ToolKind.LINE, ToolKind.ARROW -> ShapeElement(
                                    id = DRAFT_ID,
                                    start = startImage,
                                    end = startImage,
                                    colorArgb = currentStyle.colorArgb,
                                    width = strokeImageWidth,
                                    kind = when (tool) {
                                        ToolKind.RECT -> ElementKind.RECT
                                        ToolKind.LINE -> ElementKind.LINE
                                        else -> ElementKind.ARROW
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

                                ToolKind.TEXT, ToolKind.SELECT -> null
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
            // SELECT 选中元素的框 + 四角缩放手柄（B5）。只出现在预览。
            displayState.selectedElementId
                ?.let { id -> displayState.elements.firstOrNull { it.id == id } }
                ?.let { element ->
                    val view = currentFit.imageRectToView(elementBounds(element, currentFit))
                    if (view.width > 0.5f || view.height > 0.5f) {
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(view.left, view.top),
                            size = Size(
                                view.width.coerceAtLeast(1f),
                                view.height.coerceAtLeast(1f),
                            ),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                            ),
                        )
                        val handle = AnnotateVisuals.SelectionHandleSizePx
                        listOf(
                            Offset(view.left, view.top),
                            Offset(view.right, view.top),
                            Offset(view.left, view.bottom),
                            Offset(view.right, view.bottom),
                        ).forEach { corner ->
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(corner.x - handle / 2f, corner.y - handle / 2f),
                                size = Size(handle, handle),
                            )
                        }
                    }
                }
        }

        val currentEditor = editor
        if (currentEditor != null && fit != null) {
            val viewPos = fit.imageToView(currentEditor.position)
            val focusRequester = remember { FocusRequester() }
            var hadFocus by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.offset { IntOffset(viewPos.x.roundToInt(), viewPos.y.roundToInt()) },
                verticalAlignment = Alignment.Top,
            ) {
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

private data class TextEditor(
    val position: Pt,
    val existing: TextElement?,
)

private enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private fun cornerAt(
    rect: PxRect,
    fit: ContainFit,
    viewPoint: Offset,
    threshold: Float,
): Corner? {
    val view = fit.imageRectToView(rect.normalized())
    val corners = listOf(
        Corner.TOP_LEFT to Offset(view.left, view.top),
        Corner.TOP_RIGHT to Offset(view.right, view.top),
        Corner.BOTTOM_LEFT to Offset(view.left, view.bottom),
        Corner.BOTTOM_RIGHT to Offset(view.right, view.bottom),
    )
    return corners.firstOrNull { (_, p) ->
        abs(p.x - viewPoint.x) <= threshold && abs(p.y - viewPoint.y) <= threshold
    }?.first
}

private fun adjustCorner(rect: PxRect, corner: Corner, p: Pt): PxRect = when (corner) {
    Corner.TOP_LEFT -> PxRect(p.x, p.y, rect.right, rect.bottom)
    Corner.TOP_RIGHT -> PxRect(rect.left, p.y, p.x, rect.bottom)
    Corner.BOTTOM_LEFT -> PxRect(p.x, rect.top, rect.right, p.y)
    Corner.BOTTOM_RIGHT -> PxRect(rect.left, rect.top, p.x, p.y)
}

/** 选区整体平移，保持尺寸不缩放：位移按图边夹取。 */
private fun translateSelection(
    rect: PxRect,
    dx: Float,
    dy: Float,
    imageWidth: Float,
    imageHeight: Float,
): PxRect {
    val clampedDx = dx.coerceIn(-rect.left, (imageWidth - rect.right).coerceAtLeast(-rect.left))
    val clampedDy = dy.coerceIn(-rect.top, (imageHeight - rect.bottom).coerceAtLeast(-rect.top))
    return PxRect(
        left = rect.left + clampedDx,
        top = rect.top + clampedDy,
        right = rect.right + clampedDx,
        bottom = rect.bottom + clampedDy,
    )
}

private fun selectionHolds(rect: PxRect, fit: ContainFit, viewPoint: Offset): Boolean =
    fit.imageRectToView(rect.normalized()).contains(viewPoint.asPt())

private fun Pt.clampToImage(document: AnnotateDocument): Pt = Pt(
    x = x.coerceIn(0f, document.imageWidth.toFloat()),
    y = y.coerceIn(0f, document.imageHeight.toFloat()),
)

/**
 * 屏幕 dp 线宽 → 图片像素（B9）。落笔瞬间换算，元素里只存图片像素。
 */
private fun strokeWidthInImagePx(
    styleDp: Float,
    density: Float,
    fit: ContainFit,
): Float = (styleDp * density / fit.scale).coerceIn(0.5f, 512f)

/**
 * 元素命中框（图片像素）。文字用真实测量框；其余走模型层包围盒。
 */
private fun elementBounds(element: AnnotationElement, fit: ContainFit): PxRect = when (element) {
    is TextElement -> element.realHitBounds(fit)
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
    when (el) {
        is StrokeElement -> !el.erase && (
            if (el.points.size == 1) {
                pointNearSegment(at, el.points[0], el.points[0], el.width / 2f)
            } else {
                el.points.zipWithNext().any { (a, b) -> pointNearSegment(at, a, b, el.width / 2f) }
            }
        )

        is ShapeElement ->
            if (el.kind == ElementKind.RECT) {
                val b = el.bounds
                val tl = Pt(b.left, b.top)
                val tr = Pt(b.right, b.top)
                val br = Pt(b.right, b.bottom)
                val bl = Pt(b.left, b.bottom)
                // 点进框内，或落在任一条边的线宽邻域内。
                b.contains(at) ||
                    listOf(tl to tr, tr to br, br to bl, bl to tl)
                        .any { (a, c) -> pointNearSegment(at, a, c, el.width / 2f) }
            } else {
                pointNearSegment(at, el.start, el.end, el.width / 2f)
            }

        is TextElement -> el.realHitBounds(fit).contains(at)
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
}

private fun AnnotationElement.withId(id: Long): AnnotationElement = when (this) {
    is StrokeElement -> copy(id = id)
    is ShapeElement -> copy(id = id)
    is TextElement -> copy(id = id)
    is MosaicElement -> copy(id = id)
}

/**
 * 马赛克涂抹笔刷直径（图片像素）。
 *
 * 不单独给笔刷滑杆：颗粒越大笔刷越粗，视觉上「一把刷过去」的效果稳定；
 * 下限 40 图片像素保证高分辨率截图上也有可见笔触。
 */
private fun mosaicBrushWidth(cellSize: Float): Float =
    maxOf(cellSize * 5f, 40f).coerceAtMost(256f)

private fun Offset.asPt(): Pt = Pt(x, y)
