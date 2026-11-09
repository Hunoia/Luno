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
    /** SHAPE 工具当前形状子类型（不持久化，每次打开默认 RECT）。 */
    val activeShape: ShapeKind = ShapeKind.RECT,
    /** 橡皮的矩形擦模式（审计 C1）：仅 ERASER 下生效，切走工具时复位。 */
    val eraserRect: Boolean = false,
    val styles: Map<ToolKind, ToolStyle> = DEFAULT_STYLES,
    /**
     * SELECT 工具当前点中的元素（B5）。只影响画布上的选中框与工具条删除按钮，
     * 不是笔迹内容，不进撤销历史；元素被删/撤销掉时自动失效。
     */
    val selectedElementId: Long? = null,
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
                ToolKind.HIGHLIGHT -> ToolStyle(colorArgb = 0xFFFFCC00, strokeWidth = 10f)
                // 序号圆底：默认红色，直径默认 40 图片像素。
                ToolKind.NUMBER -> ToolStyle(colorArgb = 0xFFFF3B30, textSize = 40f, strokeWidth = 3f)
                // 形状默认仅描边，不填充。
                ToolKind.SHAPE -> ToolStyle(shapeFilled = false)
                // 橡皮默认比画笔粗：擦除需要覆盖面积。
                ToolKind.ERASER -> ToolStyle(strokeWidth = 12f)
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

    /** 双指缩放范围（B7）：1 = 全屏适应，最大 5 倍；裁剪态会使用动态安全区域下限。 */
    const val MIN_VIEW_ZOOM = 0.25f
    const val MAX_VIEW_ZOOM = 5f

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
            selectedElementId = state.selectedElementId?.takeIf { id -> prev.any { it.id == id } },
            undoStack = state.undoStack.dropLast(1),
            redoStack = (state.redoStack + listOf(state.elements)).takeLast(UNDO_LIMIT),
        )
    }

    fun redo(state: AnnotateState): AnnotateState {
        val next = state.redoStack.lastOrNull() ?: return state
        return state.copy(
            elements = next,
            selectedElementId = state.selectedElementId?.takeIf { id -> next.any { it.id == id } },
            undoStack = (state.undoStack + listOf(state.elements)).takeLast(UNDO_LIMIT),
            redoStack = state.redoStack.dropLast(1),
        )
    }

    /** 破坏性清空：同时丢弃历史，杜绝撤销回到重截前的错位笔迹。 */
    fun clearElements(state: AnnotateState): AnnotateState =
        state.copy(
            elements = emptyList(),
            selectedElementId = null,
            undoStack = emptyList(),
            redoStack = emptyList(),
        )

    /**
     * 清除全部（工具条按钮）：**可撤销**——与重截路径的破坏性 [clearElements] 不同，
     * 这是用户主动清空，撤销一步就该回来。空文档不进历史。
     */
    fun clearAll(state: AnnotateState): AnnotateState {
        if (state.elements.isEmpty()) return state
        val undo = (state.undoStack + listOf(state.elements)).takeLast(UNDO_LIMIT)
        return state.copy(
            elements = emptyList(),
            selectedElementId = null,
            undoStack = undo,
            redoStack = emptyList(),
        )
    }

    /**
     * 矩形橡皮：移除与 [rect] 相交的笔迹 / 形状（判定在 [erasedBy]，文字与马赛克不动）。
     * 一步撤销；没有命中时原样返回，不产生空历史。
     */
    fun eraseInRect(state: AnnotateState, rect: PxRect): AnnotateState {
        val normalized = rect.normalized()
        val remaining = state.elements.filterNot { it.erasedBy(normalized) }
        if (remaining.size == state.elements.size) return state
        val undo = (state.undoStack + listOf(state.elements)).takeLast(UNDO_LIMIT)
        return state.copy(
            elements = remaining,
            selectedElementId = state.selectedElementId?.takeIf { id -> remaining.any { it.id == id } },
            undoStack = undo,
            redoStack = emptyList(),
        )
    }

    /** 单点橡皮 / 删除：移除 [id] 对应元素，一步撤销。找不到时原样返回。 */
    fun removeElement(state: AnnotateState, id: Long): AnnotateState {
        if (state.elements.none { it.id == id }) return state
        val undo = (state.undoStack + listOf(state.elements)).takeLast(UNDO_LIMIT)
        return state.copy(
            elements = state.elements.filterNot { it.id == id },
            selectedElementId = state.selectedElementId?.takeIf { it != id },
            undoStack = undo,
            redoStack = emptyList(),
        )
    }

    /**
     * SELECT 工具点选元素（B5）。[id] 不在文档里按「取消选中」处理；
     * 选中态不是内容，不进撤销历史。
     */
    fun selectElement(state: AnnotateState, id: Long?): AnnotateState {
        val valid = id?.takeIf { wanted -> state.elements.any { it.id == wanted } }
        if (valid == state.selectedElementId) return state
        return state.copy(selectedElementId = valid)
    }

    /** 删除当前选中的元素（工具条删除按钮），一步撤销。 */
    fun removeSelected(state: AnnotateState): AnnotateState =
        state.selectedElementId?.let { removeElement(state, it) } ?: state

    /**
     * 选区填充马赛克（B6）：把整个选区铺成马赛克块，作为普通元素参与撤销 /
     * 选中 / 删除。没有有效选区时原样返回。
     */
    fun fillSelectionMosaic(state: AnnotateState, id: Long, cellSize: Float): AnnotateState {
        val sel = state.selection
            ?.normalized()
            ?.clampTo(state.imageWidth.toFloat(), state.imageHeight.toFloat())
            ?.takeUnless { it.isEmpty() }
            ?: return state
        return addElement(
            state,
            MosaicElement(
                id = id,
                cellSize = cellSize.coerceIn(4f, 96f),
                rect = sel,
            ),
        )
    }

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
        // 切走橡皮（或重选工具）时退出矩形擦，避免下次进橡皮还是框选模式。
        state.copy(activeTool = tool, eraserRect = false)

    /** SHAPE 工具的形状子类型切换（不进撤销历史）。 */
    fun setActiveShape(state: AnnotateState, shape: ShapeKind): AnnotateState =
        if (shape == state.activeShape) state else state.copy(activeShape = shape)

    fun setEraserRect(state: AnnotateState, enabled: Boolean): AnnotateState =
        state.copy(eraserRect = enabled)

    fun updateStyle(state: AnnotateState, tool: ToolKind, style: ToolStyle): AnnotateState =
        state.copy(styles = state.styles + (tool to style))

    /**
     * 应用持久化偏好（打开标注时）：只覆盖传入的工具风格。
     * 偏好不是笔迹，不进撤销历史。
     */
    fun applySettings(
        state: AnnotateState,
        styles: Map<ToolKind, ToolStyle>,
    ): AnnotateState = state.copy(styles = state.styles + styles)

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
            selectedElementId = state.selectedElementId?.takeIf { id -> kept.any { it.id == id } },
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

    /**
     * 图片编辑原分辨率导出（B8）：把预览坐标系里的全部元素 / 选区按比例映射到
     * 原图尺寸，返回一份**不修改宿主文档**的副本。
     *
     * 线宽、文字大小、马赛克单元随两轴平均比例缩放，导出视觉与预览一致；
     * 撤销历史不参与（导出的副本不会被撤销）。
     */
    fun scaleImage(state: AnnotateState, newWidth: Int, newHeight: Int): AnnotateState {
        if (newWidth <= 0 || newHeight <= 0) return state
        if (newWidth == state.imageWidth && newHeight == state.imageHeight) return state
        val sx = newWidth.toFloat() / state.imageWidth.coerceAtLeast(1)
        val sy = newHeight.toFloat() / state.imageHeight.coerceAtLeast(1)
        val from = PxRect.full(state.imageWidth, state.imageHeight)
        val to = PxRect.full(newWidth, newHeight)
        return state.copy(
            imageWidth = newWidth,
            imageHeight = newHeight,
            elements = state.elements.map { it.transformed(from, to) },
            selection = state.selection?.let { sel ->
                val n = sel.normalized()
                PxRect(
                    left = n.left * sx,
                    top = n.top * sy,
                    right = n.right * sx,
                    bottom = n.bottom * sy,
                ).clampTo(newWidth.toFloat(), newHeight.toFloat())
            },
        )
    }
}
