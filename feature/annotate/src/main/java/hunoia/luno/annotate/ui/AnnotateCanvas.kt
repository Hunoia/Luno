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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import hunoia.luno.annotate.model.bounds
import hunoia.luno.annotate.model.hitElementHandle
import hunoia.luno.annotate.model.minSelectionBox
import hunoia.luno.annotate.model.mosaicBrushWidth
import hunoia.luno.annotate.model.orientedGeometry
import hunoia.luno.annotate.model.pointInPolygon
import hunoia.luno.annotate.model.pointNearSegment
import hunoia.luno.annotate.model.rotateAround
import hunoia.luno.annotate.model.rotationRadOf
import hunoia.luno.annotate.model.shapeCrossSegments
import hunoia.luno.annotate.model.shapeOutlinePoints
import hunoia.luno.annotate.model.strokeWidthInImagePx
import hunoia.luno.annotate.model.transformed
import hunoia.luno.annotate.model.translated
import hunoia.luno.annotate.model.withRotation
import hunoia.luno.annotate.render.AnnotateGeometryCache
import hunoia.luno.annotate.render.AnnotateVisuals
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.annotate.render.drawAnnotate
import hunoia.luno.annotate.render.drawAnnotateSelection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import hunoia.luno.ui.component.PressIconButton

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
    /** 裁剪态“恢复整图”请求：宿主自增 token，画布据此重置裁剪窗口。 */
    cropResetToken: Int = 0,
) {
    val localDensity = LocalDensity.current
    val density = localDensity.density
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
    val tool = state.activeTool
    val style = state.styleFor(tool)
    // 视图变换不参与组合：捏合时只更新 graphicsLayer 与手势读取，
    // 避免每帧重组内容/重绘世界层。
    fun currentViewFit(): ContainFit? = baseFit?.let { document.viewTransform.applyTo(it) }
    // 直接捕获 style 会拿到旧颜色/线宽；用 rememberUpdatedState 让绘制始终读最新风格。
    val currentStyle by rememberUpdatedState(style)
    // 文字编辑始终用「文字」工具的风格：编辑中切到马赛克/画笔不应改掉字色。
    val textStyle = state.styleFor(ToolKind.TEXT)
    val currentTextStyle by rememberUpdatedState(textStyle)

    var draft by remember { mutableStateOf<AnnotationElement?>(null) }
    // SELECT 拖动位移：不复制元素 points（长笔迹拖动会每帧复制整条列表），
    // 绘制时临时平移，抬手才 commit 一次。
    var draftOffset by remember { mutableStateOf<Pt?>(null) }
    // 笔迹草稿的点列表：手势期间只追加（O(1)），不复制整条列表。
    // 旧实现每个 MOVE 都 `points = points + p`，5000 点的笔迹累计复制 ~千万引用，
    // GC 压力直接掉帧（审计 B1）。StrokeElement 挂的是这份活列表，
    // 追加触发绘制快照失效 → 重绘；抬手才 toList() 冻结进文档。
    val draftPoints = remember { mutableStateListOf<Pt>() }
    // SELECT 的临时导出选区；抬手后才写入文档。
    var draftSelection by remember { mutableStateOf<PxRect?>(null) }
    /** CROP 的裁剪框（视图坐标）；调整边/角期间底图保持不动。 */
    var cropWindow by remember { mutableStateOf<PxRect?>(null) }
    /** CROP 的安全区域；裁剪框始终限制在此范围内。 */
    var cropBounds by remember { mutableStateOf<PxRect?>(null) }
    /** CROP 下使图片完整落入安全区域所需的动态最小缩放。 */
    var cropMinZoom by remember { mutableStateOf(AnnotateOps.MIN_VIEW_ZOOM) }
    // 矩形擦的拖拽草稿（图片像素）；抬起即清，只用于画布上的选框预览。
    var draftErase by remember { mutableStateOf<PxRect?>(null) }
    // 画笔范围环：视图坐标 + 实际笔刷半径（视图 px）。纯提示态，不写文档、不进撤销。
    var brushCursor by remember { mutableStateOf<BrushCursor?>(null) }
    var editor by remember { mutableStateOf<TextEditor?>(null) }
    var textValue by remember { mutableStateOf("") }
    // 双指开始时递增：进行中的单指绘制据此丢弃草稿，避免缩放时留下半截笔迹。
    var viewTransformGeneration by remember { mutableStateOf(0) }
    // 元素几何缓存（图片坐标）：缩放/平移时不重算每个点。
    // 缓存以元素对象引用为键（IdentityHashMap），撤销/重做时未变元素缓存仍有效，
    // 容量由 AnnotateGeometryCache.MAX_ENTRIES 上限自动管理，不再全量清空。
    val geometryCache = remember { AnnotateGeometryCache() }

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
        draftOffset = null
        draftPoints.clear()
        draftSelection = null
        cropWindow = null
        cropBounds = null
        cropMinZoom = AnnotateOps.MIN_VIEW_ZOOM
        draftErase = null
        brushCursor = null
        editor = null
        textValue = ""
        viewTransformGeneration = 0
        geometryCache.clear()
    }

    LaunchedEffect(tool) {
        // 切到非文字工具时先交卷：否则编辑器会在别的工具下飘着，
        // 后续落笔提交时用的是新工具的风格。
        if (tool != ToolKind.TEXT && editor != null) commitText()
        // 切工具不能把旧笔刷范围环留在屏幕上。
        brushCursor = null
    }

    LaunchedEffect(baseFit, canvasSize, density) {
        if (baseFit != null) {
            document.updateSafeViewZoom(
                zoomToFitBounds(baseFit, cropSafeBounds(canvasSize, density)),
            )
        }
    }

    LaunchedEffect(tool, baseFit, canvasSize, cropResetToken) {
        if (tool == ToolKind.CROP && baseFit != null) {
            // 裁剪框使用固定安全边距，不依赖工具条当前位置；图片按安全区域动态适配。
            val safeBounds = cropSafeBounds(canvasSize, density)
            val safeZoom = zoomToFitBounds(baseFit, safeBounds)
            cropMinZoom = safeZoom
            document.setViewTransform(safeZoom, Pt(0f, 0f))
            document.clearSelection()
            val safeFit = baseFit.withViewTransform(safeZoom, Pt(0f, 0f))
            val imageWindow = safeFit.imageRectToView(
                PxRect.full(document.imageWidth, document.imageHeight),
            )
            cropBounds = safeBounds
            cropWindow = imageWindow
            document.setSelection(safeFit.viewRectToImage(imageWindow))
        } else if (tool != ToolKind.CROP) {
            cropWindow = null
            cropBounds = null
            cropMinZoom = AnnotateOps.MIN_VIEW_ZOOM
        }
    }

    // 裁剪窗口是视图坐标；图片缩放/平移时，导出 selection 必须跟随窗口同步。
    // cropWindow 在调整大小时只是临时预览，因此不把它作为 effect key，避免拖动中提交 selection。
    LaunchedEffect(tool) {
        if (tool != ToolKind.CROP) return@LaunchedEffect
        snapshotFlow { document.viewTransform }.collect { transform ->
            if (transform.zoom < cropMinZoom) {
                document.setViewTransform(cropMinZoom, Pt(0f, 0f))
                return@collect
            }
            val currentBase = baseFit
            val window = cropWindow
            if (currentBase != null && window != null) {
                document.setSelection(
                    currentBase.withViewTransform(transform.zoom, transform.pan)
                        .viewRectToImage(window),
                )
            }
        }
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
        // 每次调用时读最新状态：工具/裁剪下限可能在捏合过程中变化（如刚切到 CROP），
        // 闭包捕获旧值会用错缩放下限或裁剪窗口。
        val currentTool = document.state.activeTool
        val currentCropMinZoom = cropMinZoom
        val currentCropWindow = cropWindow
        val transform = document.viewTransform
        val oldFit = base.withViewTransform(transform.zoom, transform.pan)
        val focus = oldFit.viewToImage(previousCentroid.asPt())
        val newZoom = (transform.zoom * zoomFactor)
            .coerceIn(
                if (currentTool == ToolKind.CROP) currentCropMinZoom else AnnotateOps.MIN_VIEW_ZOOM,
                AnnotateOps.MAX_VIEW_ZOOM,
            )
        val newScale = base.scale * newZoom
        // 让双指中心下的图片点跟住当前双指中心：同时完成缩放与平移。
        val centeredX = (base.viewWidth - base.imageWidth * newScale) / 2f
        val centeredY = (base.viewHeight - base.imageHeight * newScale) / 2f
        val pan = Pt(
            x = centroid.x - focus.x * newScale - centeredX,
            y = centroid.y - focus.y * newScale - centeredY,
        )
        val clamped = if (currentTool == ToolKind.CROP) {
            clampPanToCropWindow(base, newZoom, pan, currentCropWindow)
        } else {
            base.clampPan(newZoom, pan)
        }
        document.setViewTransform(newZoom, clamped)
        if (currentTool == ToolKind.CROP) {
            currentCropWindow?.let { window ->
                document.setSelection(
                    base.withViewTransform(newZoom, clamped).viewRectToImage(window),
                )
            }
        }
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
                                draftOffset = null
                                draftPoints.clear()
                                draftErase = null
                                brushCursor = null
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
                    val currentFit = currentViewFit() ?: return@awaitEachGesture
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
                                    elementBounds(selectedElement, currentFit),
                                    currentFit,
                                    MIN_SELECTION_BOX_DP * density,
                                )
                                selectedBox = box
                                selectedRot = selectedElement.rotationRadOf()
                                selectedCenter = box.center
                                // 本体优先：可视框内（角柄以外）都可以直接拖走。
                                // 小标注的真实笔迹可能只有几 dp，靠它命中会退回“点不中”的老问题；
                                // 48dp 可视框就是它的拖拽目标。
                                val localStart = if (selectedRot == 0f) startImage else {
                                    rotateAround(startImage, box.center, -selectedRot)
                                }
                                selectedInsideBox = box.contains(localStart)
                                val gap = ROTATE_HANDLE_GAP_DP * density / currentFit.scale
                                val geom = orientedGeometry(box, selectedRot, gap)
                                selectedHandle = hitElementHandle(
                                    geom,
                                    currentFit,
                                    down.position.asPt(),
                                    ELEMENT_HANDLE_HIT_DP * density,
                                )
                            }
                            // 旋转柄在框外；角柄优先缩放/旋转，其余可视框区域直接移动。
                            val movable = when {
                                selectedHandle == null && selectedInsideBox -> selectedElement
                                selectedHandle == null ->
                                    hitElementAt(document, startImage, currentFit)
                                else -> null
                            }

                            when {
                                // 1) 选中元素四角缩放 + 旋转（有向柄）。
                                selectedElement != null && selectedHandle != null &&
                                    selectedBox != null && selectedCenter != null -> {
                                    val box = selectedBox
                                    val center = selectedCenter
                                    val rot = selectedRot
                                    val handle = selectedHandle
                                    draft = selectedElement
                                    if (handle == ElementHandle.ROTATE) {
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
                                                // 指针逆旋回局部坐标，按角柄调整包围盒。
                                                val local = rotateAround(p, center, -rot)
                                                val to = adjustByHandle(box, handle, local)
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

                                // 2) 点中元素（含已选中小标注的本体）：选中并拖动（B5）。
                                movable != null -> {
                                    document.selectElement(movable.id)
                                    draft = movable
                                    draftOffset = Pt(0f, 0f)
                                    drag(down.id) { change ->
                                        moved = true
                                        val p = currentFit.viewToImage(change.position.asPt())
                                        draftOffset = Pt(
                                            p.x - startImage.x,
                                            p.y - startImage.y,
                                        )
                                        change.consume()
                                    }
                                    val offset = draftOffset
                                    draft = null
                                    draftOffset = null
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
                                        current = currentFit.viewToImage(change.position.asPt())
                                            .clampToImage(document)
                                        rect = PxRect.fromPoints(startImage, current).normalized()
                                        draftSelection = rect
                                        change.consume()
                                    }
                                    val finalRect = if (completed) rect else {
                                        PxRect.fromPoints(startImage, current).normalized()
                                    }
                                    document.setSelection(finalRect)
                                    draftSelection = null
                                }
                            }
                        }

                        ToolKind.CROP -> {
                            // 标准图片编辑器式裁剪：窗口固定，图片在窗口下移动/缩放。
                            document.selectElement(null)
                            val base = baseFit ?: return@awaitEachGesture
                            val window = cropWindow ?: currentFit.imageRectToView(
                                PxRect.full(document.imageWidth, document.imageHeight),
                            )
                            // 裁剪框只受固定安全区域限制；图片像素选区在提交时统一 clamp。
                            val imageBounds = cropBounds ?: currentFit.imageRectToView(
                                PxRect.full(document.imageWidth, document.imageHeight),
                            )
                            val handle = hitCropHandle(window, down.position, 28f * density)
                            val minSize = (48f * density).coerceAtMost(
                                min(imageBounds.width, imageBounds.height),
                            )

                            if (handle != null) {
                                val oldWindow = window
                                val startZoom = document.viewTransform.zoom
                                val startFit = currentFit
                                var nextWindow = oldWindow
                                val released = drag(down.id) { change ->
                                    moved = true
                                    // 调整期间只更新临时窗口预览；selection、zoom、pan 在抬手后一次性提交。
                                    nextWindow = adjustCropWindow(
                                        oldWindow,
                                        handle,
                                        change.position,
                                        imageBounds,
                                        minSize,
                                    )
                                    cropWindow = nextWindow
                                    change.consume()
                                }

                                if (released && moved) {
                                    // 拖动期间图片保持不动；抬手后把选区与框作为整体同步放大。
                                    val factor = maxOf(
                                        oldWindow.width / nextWindow.width,
                                        oldWindow.height / nextWindow.height,
                                    )
                                    val zoom = (startZoom * factor)
                                        .coerceIn(cropMinZoom, AnnotateOps.MAX_VIEW_ZOOM)
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
                                        .coerceIn(cropMinZoom, AnnotateOps.MAX_VIEW_ZOOM)
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
                                    cropWindow = finalWindow
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
                                val generation = viewTransformGeneration
                                drag(down.id) { change ->
                                    if (generation == viewTransformGeneration) {
                                        moved = true
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
                            // 画笔范围环：半径 = 实际落笔半径（视图 px），只在触摸期间显示。
                            val brushTool = tool == ToolKind.PEN || tool == ToolKind.ERASER ||
                                tool == ToolKind.HIGHLIGHT || tool == ToolKind.MOSAIC
                            val brushRadiusPx = when (tool) {
                                ToolKind.MOSAIC -> mosaicWidth / 2f * currentFit.scale
                                ToolKind.PEN, ToolKind.ERASER, ToolKind.HIGHLIGHT ->
                                    currentStyle.strokeWidth * density / 2f
                                else -> 0f
                            }
                            if (brushTool) {
                                brushCursor = BrushCursor(down.position.asPt(), brushRadiusPx)
                            }
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
                            if (brushTool) {
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

                                // 形状已合并：子类型读 activeShape，填充读 SHAPE 风格。
                                ToolKind.SHAPE -> ShapeElement(
                                    id = DRAFT_ID,
                                    start = startImage,
                                    end = startImage,
                                    colorArgb = currentStyle.colorArgb,
                                    width = strokeImageWidth,
                                    kind = document.state.activeShape.elementKind,
                                    filled = currentStyle.shapeFilled,
                                )

                                // 马赛克是涂抹笔刷：笔刷宽度随颗粒比例走，
                                // 颗粒越大笔刷越粗；单点也能落一个 dab。
                                ToolKind.MOSAIC -> MosaicElement(
                                    id = DRAFT_ID,
                                    points = draftPoints,
                                    width = mosaicWidth,
                                    cellSize = currentStyle.mosaicCell,
                                )

                                    ToolKind.TEXT, ToolKind.SELECT, ToolKind.CROP,
                                    ToolKind.NUMBER -> null
                            }
                            var lastPt = startImage
                            drag(down.id) { change ->
                                moved = true
                                if (brushTool) {
                                    brushCursor = BrushCursor(
                                        change.position.asPt(),
                                        brushRadiusPx,
                                    )
                                }
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
                            // 抬手/取消即收起范围环；不是在文档里画东西。
                            brushCursor = null
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
        // 背景（图片外留白）：放在世界层之外，缩放时不会露出未绘制区域。
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color = AnnotateVisuals.CanvasGutter, size = size)
        }
        // 世界层：底图 + 标注按 baseFit 绘制一次；缩放/平移只改 layer 变换。
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = document.viewTransform.zoom
                    scaleY = document.viewTransform.zoom
                    translationX = document.viewTransform.pan.x
                    translationY = document.viewTransform.pan.y
                    clip = true
                },
        ) {
            val currentBase = baseFit ?: return@Canvas
            drawAnnotate(
                state = displayState,
                base = base,
                fit = currentBase,
                showSelection = false,
                geometryCache = geometryCache,
                draftId = if (draftOffset != null) draft?.id else null,
                draftOffset = draftOffset,
            )
        }
        // 视图层：选区/裁剪/选中框/矩形擦预览。位置跟随缩放，但线宽/手柄保持屏幕尺寸。
        Canvas(Modifier.fillMaxSize()) {
            val currentFit = currentViewFit() ?: return@Canvas
            if (showSelection) {
                drawAnnotateSelection(displayState, currentFit, draftSelection)
            }
            if (tool == ToolKind.CROP) {
                cropWindow?.let { window ->
                    // 裁剪框外压暗；调整完成后裁剪框与选区内容同步放大。
                    drawRect(
                        color = Color.Black.copy(alpha = 0.42f),
                        topLeft = Offset.Zero,
                        size = Size(size.width, window.top.coerceAtLeast(0f)),
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.42f),
                        topLeft = Offset(0f, window.bottom),
                        size = Size(size.width, (size.height - window.bottom).coerceAtLeast(0f)),
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.42f),
                        topLeft = Offset(0f, window.top),
                        size = Size(window.left.coerceAtLeast(0f), window.height),
                    )
                    drawRect(
                        color = Color.Black.copy(alpha = 0.42f),
                        topLeft = Offset(window.right, window.top),
                        size = Size((size.width - window.right).coerceAtLeast(0f), window.height),
                    )
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(window.left, window.top),
                        size = Size(window.width, window.height),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    val handle = 12.dp.toPx()
                    listOf(
                        Offset(window.left, window.top),
                        Offset(window.right, window.top),
                        Offset(window.left, window.bottom),
                        Offset(window.right, window.bottom),
                    ).forEach { point ->
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(point.x - handle / 2f, point.y - handle / 2f),
                            size = Size(handle, handle),
                        )
                    }
                }
            }
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
                    val offset = draftOffset
                    val rawBox = elementBounds(element, currentFit)
                    val movedBox = if (offset != null && element.id == draft?.id) {
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
                    val box = minSelectionBox(movedBox, currentFit, MIN_SELECTION_BOX_DP * density)
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
                        // 角柄（方块）+ 旋转柄（弧形箭头），白芯深边。
                        val handle = ELEMENT_HANDLE_DP.dp.toPx()
                        val border = ELEMENT_HANDLE_BORDER_DP.dp.toPx()
                        geom.corners.forEach { world ->
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
                        // 旋转柄：弧形箭头（不再是圆点），旋转语义直观。
                        drawRotateHandle(geom.rotate, currentFit, handle, border)
                    }
                }
            // 画笔范围环：画在最上层，仅触摸期间存在，不参与导出/撤销。
            brushCursor?.let { cursor ->
                drawBrushCursor(cursor.at, cursor.radiusPx)
            }
        }

        val currentEditor = editor
        // 只在编辑中读取视图变换：未编辑时捏合不应让整个 Canvas 组合失效。
        val editorFit = if (currentEditor != null) currentViewFit() else null
        if (currentEditor != null && editorFit != null) {
            val viewPos = editorFit.imageToView(currentEditor.position)
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
                                    val currentFitNow = currentViewFit() ?: return@detectDragGestures
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
                        // 图片像素 → 视图 px → sp；toSp() 会带上系统字体缩放，
                        // 与画布上按 fit.scale 绘制的字号对齐。
                        fontSize = with(localDensity) {
                            (textStyle.textSize * editorFit.scale).toSp()
                        },
                    ),
                    // 光标跟随文字颜色；浅色底图上不再是一根看不见的白线。
                    cursorBrush = SolidColor(Color(textStyle.colorArgb)),
                    // 多行（D3）：回车换行。结束编辑走旁边的 ✓ 按钮——
                    // IME 的 Done 键在多行下会把换行吞掉，不能当完成入口。
                    singleLine = false,
                )
                // ✓ 完成按钮：浮层窗口 NOT_FOCUSABLE，无法依赖 IME 完成键，
                // 按钮必须与编辑器同窗内联（画布同层）。
                PressIconButton(
                    onClick = { commitText() },
                    modifier = Modifier.size(40.dp),
                ) {
                    // 勾跟随文字颜色 + 1px 反色描边：不加底，浅色/深色底图都可见。
                    val checkColor = Color(textStyle.colorArgb)
                    TextDoneIcon(
                        color = checkColor,
                        outline = textIconOutline(checkColor),
                        contentDescription = stringResource(R.string.annotate_text_done),
                    )
                }
            }
            LaunchedEffect(currentEditor) {
                focusRequester.requestFocus()
            }
        }
    }
}

private enum class CropHandle {
    LEFT, RIGHT, TOP, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
}

private fun hitCropHandle(rect: PxRect, point: Offset, radius: Float): CropHandle? {
    val x = point.x
    val y = point.y
    fun near(a: Float, b: Float): Boolean = abs(a - b) <= radius
    return when {
        near(x, rect.left) && near(y, rect.top) -> CropHandle.TOP_LEFT
        near(x, rect.right) && near(y, rect.top) -> CropHandle.TOP_RIGHT
        near(x, rect.left) && near(y, rect.bottom) -> CropHandle.BOTTOM_LEFT
        near(x, rect.right) && near(y, rect.bottom) -> CropHandle.BOTTOM_RIGHT
        near(x, rect.left) && y in rect.top..rect.bottom -> CropHandle.LEFT
        near(x, rect.right) && y in rect.top..rect.bottom -> CropHandle.RIGHT
        near(y, rect.top) && x in rect.left..rect.right -> CropHandle.TOP
        near(y, rect.bottom) && x in rect.left..rect.right -> CropHandle.BOTTOM
        else -> null
    }
}

private fun adjustCropWindow(
    original: PxRect,
    handle: CropHandle,
    point: Offset,
    bounds: PxRect,
    minSize: Float,
): PxRect {
    var left = original.left
    var top = original.top
    var right = original.right
    var bottom = original.bottom
    val minWidth = minSize.coerceAtMost(bounds.width)
    val minHeight = minSize.coerceAtMost(bounds.height)
    when (handle) {
        CropHandle.LEFT, CropHandle.TOP_LEFT, CropHandle.BOTTOM_LEFT ->
            left = point.x.coerceIn(bounds.left, right - minWidth)
        CropHandle.RIGHT, CropHandle.TOP_RIGHT, CropHandle.BOTTOM_RIGHT ->
            right = point.x.coerceIn(left + minWidth, bounds.right)
        else -> Unit
    }
    when (handle) {
        CropHandle.TOP, CropHandle.TOP_LEFT, CropHandle.TOP_RIGHT ->
            top = point.y.coerceIn(bounds.top, bottom - minHeight)
        CropHandle.BOTTOM, CropHandle.BOTTOM_LEFT, CropHandle.BOTTOM_RIGHT ->
            bottom = point.y.coerceIn(top + minHeight, bounds.bottom)
        else -> Unit
    }
    return PxRect(left, top, right, bottom)
}

/** 按同一比例放大裁剪框，并以 [anchor] 为中心。 */
private fun scaleCropWindow(rect: PxRect, anchor: Pt, factor: Float): PxRect {
    val width = rect.width * factor
    val height = rect.height * factor
    return PxRect(
        left = anchor.x - width / 2f,
        top = anchor.y - height / 2f,
        right = anchor.x + width / 2f,
        bottom = anchor.y + height / 2f,
    )
}

/** 计算以固定锚点放大后仍落在安全区域内的最大倍数。 */
private fun maxCropScale(rect: PxRect, anchor: Pt, bounds: PxRect): Float {
    var result = Float.POSITIVE_INFINITY
    if (rect.left < anchor.x) {
        result = minOf(result, (anchor.x - bounds.left) / (anchor.x - rect.left))
    }
    if (rect.right > anchor.x) {
        result = minOf(result, (bounds.right - anchor.x) / (rect.right - anchor.x))
    }
    if (rect.top < anchor.y) {
        result = minOf(result, (anchor.y - bounds.top) / (anchor.y - rect.top))
    }
    if (rect.bottom > anchor.y) {
        result = minOf(result, (bounds.bottom - anchor.y) / (rect.bottom - anchor.y))
    }
    return result.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
}

/** 裁剪框的固定安全区域：左右/顶部 48dp，底部 96dp。 */
private fun cropSafeBounds(size: IntSize, density: Float): PxRect {
    val horizontal = 48f * density
    val top = 48f * density
    val bottom = 96f * density
    return PxRect(
        left = horizontal,
        top = top,
        right = (size.width - horizontal).coerceAtLeast(horizontal),
        bottom = (size.height - bottom).coerceAtLeast(top),
    )
}

/** 计算让整张图片落入安全区域所需的 contain 缩放倍数。 */
private fun zoomToFitBounds(base: ContainFit, bounds: PxRect): Float = minOf(
    bounds.width / (base.imageWidth * base.scale).coerceAtLeast(1f),
    bounds.height / (base.imageHeight * base.scale).coerceAtLeast(1f),
).coerceIn(0.25f, 1f)

/** 使图片始终覆盖裁剪框；裁剪态允许图片移出屏幕。 */
private fun clampPanToCropWindow(
    base: ContainFit,
    zoom: Float,
    pan: Pt,
    window: PxRect?,
): Pt {
    if (window == null) return pan
    val centered = base.withViewTransform(zoom, Pt(0f, 0f))
    val lowX = window.right - (centered.offsetX + centered.imageWidth * centered.scale)
    val highX = window.left - centered.offsetX
    val lowY = window.bottom - (centered.offsetY + centered.imageHeight * centered.scale)
    val highY = window.top - centered.offsetY
    return Pt(
        x = if (lowX <= highX) pan.x.coerceIn(lowX, highX) else (lowX + highX) / 2f,
        y = if (lowY <= highY) pan.y.coerceIn(lowY, highY) else (lowY + highY) / 2f,
    )
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
/** 角柄命中半径（屏幕 dp）：最小框 48dp 时中心仍留出移动区。 */
private const val ELEMENT_HANDLE_HIT_DP = 18f
/** 小标注的最小可视选择框边长（屏幕 dp）。 */
private const val MIN_SELECTION_BOX_DP = 48f
/** 旋转图标直径（屏幕 dp）。 */
private const val ROTATE_HANDLE_ICON_DP = 20f

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

/**
 * 画笔范围环（视图坐标）：[radiusPx] = 实际笔刷半径，落笔/移动时贴触点显示。
 * 纯视图态，抬手即清，不进入文档与撤销栈。
 */
private data class BrushCursor(
    val at: Pt,
    val radiusPx: Float,
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
    hitElementBody(el, at, fit)
}

/** 单个元素的本体命中（已处理旋转）。 */
private fun hitElementBody(
    element: AnnotationElement,
    at: Pt,
    fit: ContainFit,
): Boolean {
    // 旋转元素：把命中点逆旋回局部坐标系再判（旋转是刚体变换，尺寸/位置不变）。
    val rot = element.rotationRadOf()
    val local = if (rot == 0f) at else {
        val box = elementBounds(element, fit)
        rotateAround(at, box.center, -rot)
    }
    return hitElementLocal(element, local, fit)
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

        ElementKind.LINE, ElementKind.ARROW, ElementKind.DASHED_LINE ->
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

/** 文字色 → 反色描边（只描边，不加背景）。 */
private fun textIconOutline(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black.copy(alpha = 0.72f)
    else Color.White.copy(alpha = 0.86f)

/**
 * 文字完成勾：跟随文字色，四周 1dp 反色描边。
 * 不用底色/遮罩，浅色图和深色图上都能看清。
 */
@Composable
private fun TextDoneIcon(
    color: Color,
    outline: Color,
    contentDescription: String,
) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        val edge = 1.dp
        listOf(
            Pair(-edge, 0.dp), Pair(edge, 0.dp),
            Pair(0.dp, -edge), Pair(0.dp, edge),
            Pair(-edge, -edge), Pair(edge, -edge),
            Pair(-edge, edge), Pair(edge, edge),
        ).forEach { (x, y) ->
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = outline,
                modifier = Modifier
                    .size(24.dp)
                    .offset(x = x, y = y),
            )
        }
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
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
