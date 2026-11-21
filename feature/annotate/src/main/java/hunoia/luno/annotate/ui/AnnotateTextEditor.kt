package hunoia.luno.annotate.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import hunoia.luno.annotate.R
import hunoia.luno.annotate.model.AnnotateDocument
import hunoia.luno.annotate.model.ContainFit
import hunoia.luno.annotate.model.Pt
import hunoia.luno.annotate.model.TextElement
import hunoia.luno.annotate.model.ToolKind
import hunoia.luno.ui.component.PressIconButton
import kotlin.math.roundToInt

/**
 * 画布内联文字编辑器（审计 P1b 从 AnnotateCanvas 拆出）。
 *
 * 文字编辑直接用 Compose `BasicTextField` 落在画布上；浮层窗口 NOT_FOCUSABLE，
 * 完成按钮必须与编辑器同窗内联。提交协议见 [TextEditorState.commit]。
 */

/** 进行中的一次文字编辑：落点位置 + 被编辑的旧元素（null = 新建）。 */
internal data class TextEditor(
    val position: Pt,
    val existing: TextElement?,
)

/**
 * 文字编辑状态：编辑器（位置/目标）与文本源（TextFieldState 即事实源）。
 *
 * 提交点散布多处（落笔前、切工具、导出/退出/重截前、失焦）——全部收敛到
 * [commit]，避免任何一路漏掉正在输入、尚未失焦的文字。
 */
internal class TextEditorState {

    var editor by mutableStateOf<TextEditor?>(null)
        internal set

    val isEditing: Boolean get() = editor != null

    /** 文本源：TextFieldState 即事实源（foundation 1.13 起 value 重载弃用）。 */
    val textFieldState = TextFieldState()

    /** 开始一次编辑：[existing] 为 null 时是新建。 */
    fun start(position: Pt, existing: TextElement?, text: String) {
        editor = TextEditor(position = position, existing = existing)
        textFieldState.setTextAndPlaceCursorAtEnd(text)
    }

    /** 编辑中拖动把手改位置（P1），不改变文字内容。 */
    fun updatePosition(position: Pt) {
        editor = editor?.copy(position = position)
    }

    /** Escape 等取消路径：丢弃内容，不落元素。 */
    fun cancel() {
        editor = null
        textFieldState.clearText()
    }

    /**
     * 交卷：空文本不落元素；编辑旧文字只改文字本身与位置，**不**被当前文字
     * 工具的风格覆写（审计 D4）；新建元素用文档当前的文字工具风格。
     */
    fun commit(document: AnnotateDocument) {
        val current = editor ?: return
        val text = textFieldState.text.toString().trim()
        if (text.isNotEmpty()) {
            val style = document.state.styleFor(ToolKind.TEXT)
            val existing = current.existing
            if (existing != null) {
                document.replace(existing.copy(text = text, position = current.position))
            } else {
                document.add(
                    TextElement(
                        id = document.nextElementId(),
                        position = current.position,
                        text = text,
                        colorArgb = style.colorArgb,
                        textSize = style.textSize,
                    ),
                )
            }
        }
        editor = null
        textFieldState.clearText()
    }
}

/**
 * 内联编辑器本体：拖动把手 + 文本框 + 完成勾。位置跟随 [fit] 映射到视图坐标。
 */
@Composable
internal fun AnnotateInlineTextEditor(
    document: AnnotateDocument,
    textState: TextEditorState,
    fit: ContainFit,
    density: Density,
    modifier: Modifier = Modifier,
) {
    val currentEditor = textState.editor ?: return
    val textStyle = document.state.styleFor(ToolKind.TEXT)
    val viewPos = fit.imageToView(currentEditor.position)
    val focusRequester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    // 编辑器位置由 handle 拖动维护：editor 状态每次 move 更新，
    // pointerInput 用 Unit 键避免拖动中重启。
    val editorState = rememberUpdatedState(currentEditor)
    val fitState = rememberUpdatedState(fit)
    Row(
        modifier = modifier.offset { IntOffset(viewPos.x.roundToInt(), viewPos.y.roundToInt()) },
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
                            val editor = editorState.value ?: return@detectDragGestures
                            val currentFit = fitState.value
                            textState.updatePosition(
                                Pt(
                                    x = (startPos.x + accX / currentFit.scale)
                                        .coerceIn(0f, document.imageWidth.toFloat()),
                                    y = (startPos.y + accY / currentFit.scale)
                                        .coerceIn(0f, document.imageHeight.toFloat()),
                                ),
                            )
                        },
                    )
                },
        )
        BasicTextField(
            state = textState.textFieldState,
            modifier = Modifier
                .widthIn(min = 48.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        textState.commit(document)
                    }
                }
                .onKeyEvent { event ->
                    if (event.key == Key.Escape) {
                        textState.cancel()
                        true
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(
                color = Color(textStyle.colorArgb),
                // 图片像素 → 视图 px → sp；toSp() 会带上系统字体缩放，
                // 与画布上按 fit.scale 绘制的字号对齐。
                fontSize = with(density) {
                    (textStyle.textSize * fit.scale).toSp()
                },
            ),
            // 光标跟随文字颜色；浅色底图上不再是一根看不见的白线。
            cursorBrush = SolidColor(Color(textStyle.colorArgb)),
            // 多行（D3）：回车换行。结束编辑走旁边的 ✓ 按钮——
            // IME 的 Done 键在多行下会把换行吞掉，不能当完成入口。
            lineLimits = TextFieldLineLimits.MultiLine(),
        )
        // ✓ 完成按钮：浮层窗口 NOT_FOCUSABLE，无法依赖 IME 完成键，
        // 按钮必须与编辑器同窗内联（画布同层）。
        PressIconButton(
            onClick = { textState.commit(document) },
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
