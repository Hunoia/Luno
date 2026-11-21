package hunoia.luno.annotate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import hunoia.luno.annotate.StickerPickCenter
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.AnnotateOps
import hunoia.luno.annotate.model.ContainFit
import hunoia.luno.annotate.model.Pt
import hunoia.luno.annotate.model.PxRect
import hunoia.luno.annotate.model.StickerElement
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.annotate.model.clampPanToCropWindow
import hunoia.luno.annotate.model.cropSafeBounds
import hunoia.luno.annotate.model.zoomToFitBounds
import hunoia.luno.annotate.render.AnnotateGeometryCache
import hunoia.luno.annotate.render.AnnotateVisuals
import hunoia.luno.annotate.render.BaseImage
import hunoia.luno.annotate.render.drawAnnotate
import hunoia.luno.annotate.ui.gesture.CanvasGestureState
import hunoia.luno.annotate.ui.gesture.GestureHost
import hunoia.luno.annotate.ui.gesture.asPt
import hunoia.luno.annotate.ui.gesture.routeToolGesture

/**
 * 两个宿主共用的标注画布：手势 → 文档，渲染 → [drawAnnotate]。
 *
 * 只处理画布区域；工具条、穿透开关、导出属于宿主 chrome。
 * 职责拆分（审计 P1b）：
 * - 手势瞬态状态 → [CanvasGestureState]；按工具的手势处理器 → `ui/gesture/ToolGestures`；
 * - 内联文字编辑器 → [AnnotateInlineTextEditor]（提交协议收口在 `TextEditorState.commit`）；
 * - 选区/裁剪框/选中框/画笔环绘制 → `drawAnnotateViewLayer`；
 * - 本文件只做组合：三层 Canvas（留白 / 世界 / 视图）、双指变换与各 effect 的接线。
 *
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
    /** 贴纸工具落点后发起系统选图（宿主各自 startActivity）。 */
    onPickSticker: (() -> Unit)? = null,
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
    // 长生命周期的手势 / effect 闭包经 State 现读最新 fit，不捕获启动那一份。
    val baseFitState = rememberUpdatedState(baseFit)

    val tool = document.state.activeTool
    val gesture = remember { CanvasGestureState() }
    val textState = remember { TextEditorState() }
    // 元素几何缓存（图片坐标）：缩放/平移时不重算每个点。
    // 缓存以元素对象引用为键（IdentityHashMap），撤销/重做时未变元素缓存仍有效，
    // 容量由 AnnotateGeometryCache.MAX_ENTRIES 上限自动管理，不再全量清空。
    val geometryCache = remember { AnnotateGeometryCache() }

    val displayState by remember {
        derivedStateOf {
            val current = document.state
            val currentDraft = gesture.draft ?: return@derivedStateOf current
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

    fun currentViewFit(): ContainFit? = baseFitState.value?.let { document.viewTransform.applyTo(it) }

    // 回调是普通参数：长生命周期的 effect / 手势闭包会捕获启动那一份。
    // 宿主换了实例时必须现读当前回调，否则收尾仍通知旧宿主。
    val currentOnPointerDown by rememberUpdatedState(onPointerDown)
    val currentOnTextEditingChanged by rememberUpdatedState(onTextEditingChanged)
    val currentOnPickSticker by rememberUpdatedState(onPickSticker)

    // 手势宿主：持稳定对象，fit / 回调一律经 lambda 现读。
    val host = GestureHost(
        document = document,
        gesture = gesture,
        text = textState,
        density = density,
        baseFit = { baseFitState.value },
        currentFit = { currentViewFit() },
        onPointerDown = { currentOnPointerDown?.invoke() },
        onPickSticker = { currentOnPickSticker?.invoke() },
    )

    LaunchedEffect(document) {
        // 单实例复用（Activity singleTask 换图）：旧文档的草稿/编辑器不能带到新文档上。
        gesture.resetForNewDocument()
        textState.cancel()
        geometryCache.clear()
    }

    // 贴纸交付观察（P2e）：选图完成 → 落点（或画布中心）插入贴纸元素并选中。
    // 插入逻辑只在这份实现（两个宿主共用）；落点由 STICKER 手势发起选图前写入。
    LaunchedEffect(document) {
        snapshotFlow { StickerPickCenter.pending }.collect { bitmap ->
            val picked = bitmap ?: return@collect
            StickerPickCenter.consume()
            val tap = StickerPickCenter.tapPoint
            val centerX = tap?.x ?: (document.imageWidth / 2f)
            val centerY = tap?.y ?: (document.imageHeight / 2f)
            val aspect = if (picked.width > 0) {
                picked.height.toFloat() / picked.width.toFloat()
            } else {
                1f
            }
            val width = (document.imageWidth * 0.25f).coerceAtLeast(48f)
            val height = (width * aspect).coerceAtLeast(48f)
            val rect = PxRect(
                left = centerX - width / 2f,
                top = centerY - height / 2f,
                right = centerX + width / 2f,
                bottom = centerY + height / 2f,
            ).clampTo(document.imageWidth.toFloat(), document.imageHeight.toFloat())
            if (rect.isEmpty(minSize = 8f)) return@collect
            val id = document.nextElementId()
            document.add(StickerElement(id = id, rect = rect, bitmap = picked))
            document.selectElement(id)
        }
    }

    LaunchedEffect(tool) {
        // 切到非文字工具时先交卷：否则编辑器会在别的工具下飘着，
        // 后续落笔提交时用的是新工具的风格。
        if (tool != ToolKind.TEXT && textState.isEditing) textState.commit(document)
        // 切工具不能把旧笔刷范围环留在屏幕上。
        gesture.brushCursor = null
    }

    LaunchedEffect(baseFit, canvasSize, density) {
        if (baseFit != null) {
            document.updateSafeViewZoom(
                zoomToFitBounds(
                    baseFit,
                    cropSafeBounds(canvasSize.width.toFloat(), canvasSize.height.toFloat(), density),
                ),
            )
        }
    }

    LaunchedEffect(tool, baseFit, canvasSize, cropResetToken) {
        if (tool == ToolKind.CROP && baseFit != null) {
            // 裁剪框使用固定安全边距，不依赖工具条当前位置；图片按安全区域动态适配。
            val safeBounds = cropSafeBounds(canvasSize.width.toFloat(), canvasSize.height.toFloat(), density)
            val safeZoom = zoomToFitBounds(baseFit, safeBounds)
            gesture.cropMinZoom = safeZoom
            document.setViewTransform(safeZoom, Pt(0f, 0f))
            document.clearSelection()
            val safeFit = baseFit.withViewTransform(safeZoom, Pt(0f, 0f))
            val imageWindow = safeFit.imageRectToView(
                PxRect.full(document.imageWidth, document.imageHeight),
            )
            gesture.cropBounds = safeBounds
            gesture.cropWindow = imageWindow
            document.setSelection(safeFit.viewRectToImage(imageWindow))
        } else if (tool != ToolKind.CROP) {
            gesture.cropWindow = null
            gesture.cropBounds = null
            gesture.cropMinZoom = AnnotateOps.MIN_VIEW_ZOOM
        }
    }

    // 裁剪窗口是视图坐标；图片缩放/平移时，导出 selection 必须跟随窗口同步。
    // cropWindow 在调整大小时只是临时预览，因此不把它作为 effect key，避免拖动中提交 selection。
    // baseFit 必须进 key：转屏/画布尺寸变化后仍按旧 fit 换算 selection 的话，
    // 裁剪框与导出的选区就错位。
    LaunchedEffect(tool, baseFit) {
        if (tool != ToolKind.CROP) return@LaunchedEffect
        snapshotFlow { document.viewTransform }.collect { transform ->
            if (transform.zoom < gesture.cropMinZoom) {
                document.setViewTransform(gesture.cropMinZoom, Pt(0f, 0f))
                return@collect
            }
            val currentBase = baseFit
            val window = gesture.cropWindow
            if (currentBase != null && window != null) {
                document.setSelection(
                    currentBase.withViewTransform(transform.zoom, transform.pan)
                        .viewRectToImage(window),
                )
            }
        }
    }

    val editingText = textState.isEditing
    LaunchedEffect(editingText) {
        currentOnTextEditingChanged?.invoke(editingText)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnTextEditingChanged?.invoke(false) }
    }

    // 宿主导出/退出/重截前会调 document.commitPendingText()：
    // 工具栏按钮不一定抢焦点，只靠 onFocusChanged 会漏掉正在输入的文字。
    DisposableEffect(document) {
        document.pendingTextCommit = { textState.commit(document) }
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
        val currentCropMinZoom = gesture.cropMinZoom
        val currentCropWindow = gesture.cropWindow
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
                                gesture.viewTransformGeneration += 1
                                // 丢掉正在进行的单指草稿：缩放不应留下半截笔迹。
                                gesture.discardActiveDraft()
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
            // 按工具的单指手势：路由见 ToolGestures.routeToolGesture。
            .pointerInput(document, tool, density) {
                awaitEachGesture {
                    routeToolGesture(tool, host)
                }
            },
    ) {
        // 背景（图片外留白）：放在世界层之外，缩放时不会露出未绘制区域。
        // 只在有底图时画：穿透态 base == null，铺黑会把整个屏幕挡住
        // （overlay 窗口本身是透明的，黑来自这里）。
        if (base != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(color = AnnotateVisuals.CanvasGutter, size = size)
            }
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
                draftId = if (gesture.draftOffset != null) gesture.draft?.id else null,
                draftOffset = gesture.draftOffset,
            )
        }
        // 视图层：选区/裁剪/选中框/矩形擦预览。位置跟随缩放，但线宽/手柄保持屏幕尺寸。
        Canvas(Modifier.fillMaxSize()) {
            val currentFit = currentViewFit() ?: return@Canvas
            drawAnnotateViewLayer(
                displayState = displayState,
                fit = currentFit,
                density = density,
                tool = tool,
                showSelection = showSelection,
                gesture = gesture,
                textBounds = { it.realHitBounds(currentFit) },
            )
        }

        // 内联文字编辑器（编辑中才组合）。
        if (textState.isEditing) {
            val editorFit = currentViewFit()
            if (editorFit != null) {
                AnnotateInlineTextEditor(
                    document = document,
                    textState = textState,
                    fit = editorFit,
                    density = localDensity,
                )
            }
        }
    }
}
