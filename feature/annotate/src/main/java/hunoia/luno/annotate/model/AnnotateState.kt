package hunoia.luno.annotate.model

/**
 * 标注文档的不可变快照。宿主只持有 [AnnotateDocument]（Compose state 包装），
 * 所有变换集中在 [AnnotateOps]，便于 JVM 单测。
 */
data class AnnotateState(
    val imageWidth: Int,
    val imageHeight: Int,
    val elements: List<AnnotationElement> = emptyList(),
    /** 导出裁剪区；null 表示全图。永远存图片像素坐标。 */
    val selection: PxRect? = null,
    val activeTool: ToolKind = ToolKind.PEN,
    val styles: Map<ToolKind, ToolStyle> = DEFAULT_STYLES,
    val toolbarAnchor: ToolbarAnchor? = null,
    val undoStack: List<List<AnnotationElement>> = emptyList(),
    val redoStack: List<List<AnnotationElement>> = emptyList(),
) {
    val hasElements: Boolean get() = elements.isNotEmpty()

    fun styleFor(tool: ToolKind): ToolStyle = styles[tool] ?: ToolStyle()

    companion object {
        val DEFAULT_STYLES: Map<ToolKind, ToolStyle> = ToolKind.entries.associateWith { tool ->
            when (tool) {
                ToolKind.MOSAIC -> ToolStyle(colorArgb = 0xFF000000, mosaicCell = 24f)
                ToolKind.TEXT -> ToolStyle(textSize = 48f, strokeWidth = 4f)
                else -> ToolStyle()
            }
        }

        fun of(imageWidth: Int, imageHeight: Int): AnnotateState =
            AnnotateState(imageWidth = imageWidth, imageHeight = imageHeight)
    }
}

/**
 * 纯变换。撤销是元素级快照（上限 [UNDO_LIMIT] 步），不存底图、不落盘。
 *
 * 约定：
 * - 新增元素 / 改选区进撤销栈；
 * - 关闭穿透重截后的「清空笔迹」是破坏性路径，不进撤销栈（避免撤销回到错位笔迹）；
 * - 选择工具的画框只改 [AnnotateState.selection]，不产生元素。
 */
object AnnotateOps {

    const val UNDO_LIMIT = 30

    fun addElement(state: AnnotateState, element: AnnotationElement): AnnotateState {
        val undo = (state.undoStack + listOf(state.elements)).takeLast(UNDO_LIMIT)
        return state.copy(
            elements = state.elements + element,
            undoStack = undo,
            redoStack = emptyList(),
        )
    }

    /** 原地替换（文字二次编辑）：保持图层位置不变，一步撤销。 */
    fun replaceElement(state: AnnotateState, element: AnnotationElement): AnnotateState {
        if (state.elements.none { it.id == element.id }) return addElement(state, element)
        val undo = (state.undoStack + listOf(state.elements)).takeLast(UNDO_LIMIT)
        return state.copy(
            elements = state.elements.map { if (it.id == element.id) element else it },
            undoStack = undo,
            redoStack = emptyList(),
        )
    }

    fun undo(state: AnnotateState): AnnotateState {
        val prev = state.undoStack.lastOrNull() ?: return state
        return state.copy(
            elements = prev,
            undoStack = state.undoStack.dropLast(1),
            redoStack = (state.redoStack + listOf(state.elements)).takeLast(UNDO_LIMIT),
        )
    }

    fun redo(state: AnnotateState): AnnotateState {
        val next = state.redoStack.lastOrNull() ?: return state
        return state.copy(
            elements = next,
            undoStack = (state.undoStack + listOf(state.elements)).takeLast(UNDO_LIMIT),
            redoStack = state.redoStack.dropLast(1),
        )
    }

    /** 破坏性清空：同时丢弃历史，杜绝撤销回到重截前的错位笔迹。 */
    fun clearElements(state: AnnotateState): AnnotateState =
        state.copy(elements = emptyList(), undoStack = emptyList(), redoStack = emptyList())

    /** 选区先归一化再 clamp；过小视为清除。 */
    fun setSelection(state: AnnotateState, rect: PxRect?): AnnotateState {
        val clamped = rect
            ?.normalized()
            ?.clampTo(state.imageWidth.toFloat(), state.imageHeight.toFloat())
            ?.takeUnless { it.isEmpty() }
        return state.copy(selection = clamped)
    }

    fun clearSelection(state: AnnotateState): AnnotateState = state.copy(selection = null)

    fun selectTool(state: AnnotateState, tool: ToolKind): AnnotateState =
        state.copy(activeTool = tool)

    fun updateStyle(state: AnnotateState, tool: ToolKind, style: ToolStyle): AnnotateState =
        state.copy(styles = state.styles + (tool to style))

    fun moveToolbar(state: AnnotateState, anchor: ToolbarAnchor): AnnotateState =
        state.copy(toolbarAnchor = anchor.sanitized())

    /**
     * 关闭穿透后换底图。笔迹坐标不变（D6「保留」路径）；选区按新尺寸重新 clamp。
     * [keepElements] 为 false 时等价于破坏性清空。
     */
    fun replaceImage(
        state: AnnotateState,
        imageWidth: Int,
        imageHeight: Int,
        keepElements: Boolean,
    ): AnnotateState {
        val kept = if (keepElements) state.elements else emptyList()
        val newSelection = if (keepElements) {
            state.selection?.clampTo(imageWidth.toFloat(), imageHeight.toFloat())
                ?.takeUnless { it.isEmpty() }
        } else {
            // 破坏性路径连选区一起重置：重截后旧裁剪区已无意义。
            null
        }
        return state.copy(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            elements = kept,
            selection = newSelection,
            undoStack = if (keepElements) state.undoStack else emptyList(),
            redoStack = if (keepElements) state.redoStack else emptyList(),
        )
    }

    /** 导出裁剪区：无有效选区就是全图（D8 / §3.4）。 */
    fun exportCropRect(state: AnnotateState): PxRect {
        val full = PxRect.full(state.imageWidth, state.imageHeight)
        val sel = state.selection?.normalized()?.clampTo(full.right, full.bottom) ?: return full
        return if (sel.isEmpty()) full else sel
    }
}
