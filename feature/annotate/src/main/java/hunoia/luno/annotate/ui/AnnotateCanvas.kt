package hunoia.luno.annotate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import hunoia.luno.annotate.model.AnnotateDocument
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

    val fit = remember(canvasSize, document.imageWidth, document.imageHeight) {
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
    // 指针手势块只在 fit/tool 变化时重建，直接捕获 style 会拿到旧颜色/线宽；
    // 用 rememberUpdatedState 让绘制始终读最新风格。
    val currentStyle by rememberUpdatedState(style)
    // 文字编辑始终用「文字」工具的风格：编辑中切到马赛克/画笔不应改掉字色。
    val textStyle = state.styleFor(ToolKind.TEXT)
    val currentTextStyle by rememberUpdatedState(textStyle)

    var draft by remember { mutableStateOf<AnnotationElement?>(null) }
    var draftSelection by remember { mutableStateOf<PxRect?>(null) }
    var editor by remember { mutableStateOf<TextEditor?>(null) }
    var textValue by remember { mutableStateOf("") }

    val displayState by remember {
        derivedStateOf {
            val current = document.state
            draft?.let { current.copy(elements = current.elements + it) } ?: current
        }
    }

    fun commitText() {
        val currentEditor = editor ?: return
        val text = textValue.trim()
        if (text.isNotEmpty()) {
            val existing = currentEditor.existing
            if (existing != null) {
                document.replace(
                    existing.copy(
                        text = text,
                        colorArgb = currentTextStyle.colorArgb,
                        textSize = currentTextStyle.textSize,
                    ),
                )
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
        draftSelection = null
        editor = null
        textValue = ""
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

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(document, fit, tool, density) {
                val currentFit = fit ?: return@pointerInput
                awaitEachGesture {
                    // consumed 的 down（落在文字输入框上）不再当作画布手势，
                    // 否则点输入框内部会被父层当成新的落字点。
                    val down = awaitFirstDown(requireUnconsumed = true)
                    // 起手前先交卷：画布上旧文字编辑器如果还开着，这次落点应先提交，
                    // 否则会被下面的 TEXT 分支直接覆盖，已输入内容丢失。
                    if (editor != null) commitText()
                    val startImage = currentFit.viewToImage(down.position.asPt()).clampToImage(document)
                    var moved = false

                    when (tool) {
                        ToolKind.TEXT -> {
                            val hit = document.state.elements.asReversed()
                                .filterIsInstance<TextElement>()
                                .firstOrNull { it.bounds().contains(startImage) }
                            val target = hit?.position ?: startImage
                            editor = TextEditor(position = target, existing = hit)
                            textValue = hit?.text ?: ""
                            drag(down.id) { it.consume() }
                        }

                        ToolKind.SELECT -> {
                            val threshold = 28f * density
                            val existing = document.state.selection?.normalized()
                            val corner = existing?.let { cornerAt(it, currentFit, down.position, threshold) }
                            var rect = existing ?: PxRect.fromPoints(startImage, startImage)
                            var current = startImage
                            val ok = drag(down.id) { change ->
                                moved = true
                                current = currentFit.viewToImage(change.position.asPt()).clampToImage(document)
                                rect = if (corner != null) {
                                    adjustCorner(rect, corner, current)
                                } else {
                                    PxRect.fromPoints(startImage, current)
                                }
                                draftSelection = rect.normalized()
                                change.consume()
                            }
                            val finalRect = if (corner != null || ok) rect else PxRect.fromPoints(startImage, current)
                            document.setSelection(finalRect)
                            draftSelection = null
                        }

                        else -> {
                            var points = listOf(startImage)
                            draft = when (tool) {
                                ToolKind.PEN, ToolKind.ERASER -> StrokeElement(
                                    id = DRAFT_ID,
                                    points = points,
                                    colorArgb = currentStyle.colorArgb,
                                    width = currentStyle.strokeWidth,
                                    erase = tool == ToolKind.ERASER,
                                )

                                ToolKind.RECT, ToolKind.ARROW -> ShapeElement(
                                    id = DRAFT_ID,
                                    start = startImage,
                                    end = startImage,
                                    colorArgb = currentStyle.colorArgb,
                                    width = currentStyle.strokeWidth,
                                    kind = if (tool == ToolKind.RECT) ElementKind.RECT else ElementKind.ARROW,
                                )

                                ToolKind.MOSAIC -> MosaicElement(
                                    id = DRAFT_ID,
                                    rect = PxRect.fromPoints(startImage, startImage),
                                    cellSize = currentStyle.mosaicCell,
                                )

                                ToolKind.TEXT, ToolKind.SELECT -> null
                            }
                            drag(down.id) { change ->
                                moved = true
                                val p = currentFit.viewToImage(change.position.asPt()).clampToImage(document)
                                draft = when (val d = draft) {
                                    is StrokeElement -> {
                                        points = points + p
                                        d.copy(points = points)
                                    }

                                    is ShapeElement -> d.copy(end = p)
                                    is MosaicElement -> d.copy(rect = PxRect.fromPoints(startImage, p))
                                    else -> d
                                }
                                change.consume()
                            }
                            val finished = draft
                            draft = null
                            if (finished != null && (moved || finished is StrokeElement && finished.points.size > 0)) {
                                document.add(finished.withId(document.nextElementId()))
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
        }

        val currentEditor = editor
        if (currentEditor != null && fit != null) {
            val viewPos = fit.imageToView(currentEditor.position)
            val focusRequester = remember { FocusRequester() }
            var hadFocus by remember { mutableStateOf(false) }
            BasicTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier
                    .offset { IntOffset(viewPos.x.roundToInt(), viewPos.y.roundToInt()) }
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
                // 单行：IME 的完成键才会出现并触发 commitText；多行会让回车变成换行，
                // 用户找不到“结束编辑”的入口。
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitText() }),
            )
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
    selection: PxRect,
    fit: ContainFit,
    viewPoint: Offset,
    threshold: Float,
): Corner? {
    val view = fit.imageRectToView(selection)
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

private fun Pt.clampToImage(document: AnnotateDocument): Pt = Pt(
    x = x.coerceIn(0f, document.imageWidth.toFloat()),
    y = y.coerceIn(0f, document.imageHeight.toFloat()),
)

private fun AnnotationElement.withId(id: Long): AnnotationElement = when (this) {
    is StrokeElement -> copy(id = id)
    is ShapeElement -> copy(id = id)
    is TextElement -> copy(id = id)
    is MosaicElement -> copy(id = id)
}

private fun Offset.asPt(): Pt = Pt(x, y)
