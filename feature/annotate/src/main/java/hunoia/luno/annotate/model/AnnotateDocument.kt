package hunoia.luno.annotate.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 宿主持有的可变文档包装。所有写入口都走 [AnnotateOps]，
 * 保证撤销语义、选区 clamp 与「破坏性清空不进历史」只有一份实现。
 */
class AnnotateDocument(initial: AnnotateState) {

    var state by mutableStateOf(initial)
        private set

    private var nextId: Long = (initial.elements.maxOfOrNull { it.id } ?: 0L) + 1L

    val imageWidth: Int get() = state.imageWidth
    val imageHeight: Int get() = state.imageHeight

    fun nextElementId(): Long = nextId++

    fun add(element: AnnotationElement) {
        state = AnnotateOps.addElement(state, element)
    }

    fun replace(element: AnnotationElement) {
        state = AnnotateOps.replaceElement(state, element)
    }

    fun undo() {
        state = AnnotateOps.undo(state)
    }

    fun redo() {
        state = AnnotateOps.redo(state)
    }

    fun clearElements() {
        state = AnnotateOps.clearElements(state)
    }

    fun setSelection(rect: PxRect?) {
        state = AnnotateOps.setSelection(state, rect)
    }

    fun clearSelection() {
        state = AnnotateOps.clearSelection(state)
    }

    fun selectTool(tool: ToolKind) {
        state = AnnotateOps.selectTool(state, tool)
    }

    fun updateStyle(tool: ToolKind, style: ToolStyle) {
        state = AnnotateOps.updateStyle(state, tool, style)
    }

    fun moveToolbar(anchor: ToolbarAnchor) {
        state = AnnotateOps.moveToolbar(state, anchor)
    }

    fun replaceImage(width: Int, height: Int, keepElements: Boolean) {
        state = AnnotateOps.replaceImage(state, width, height, keepElements)
    }

    fun exportCropRect(): PxRect = AnnotateOps.exportCropRect(state)

    /**
     * 画布内联文字编辑器注册的「未提交文本」回调。
     * 导出/退出/重截前先调 [commitPendingText]，否则正在编辑的文字（还没失焦）
     * 会被漏掉：工具栏按钮不一定抢焦点。
     */
    internal var pendingTextCommit: (() -> Unit)? = null

    fun commitPendingText() {
        pendingTextCommit?.invoke()
    }
}
