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

    /**
     * 清除全部（工具条按钮，可撤销）。
     * 破坏性清空（重截换图）走 `replaceImage(keepElements = false)`，语义不同。
     */
    fun clearAll() {
        state = AnnotateOps.clearAll(state)
    }

    /** 矩形橡皮：擦除与矩形相交的笔迹/形状，一步撤销。 */
    fun eraseInRect(rect: PxRect) {
        state = AnnotateOps.eraseInRect(state, rect)
    }

    /** 单点橡皮：移除点中的那一个元素，一步撤销。 */
    fun removeElement(id: Long) {
        state = AnnotateOps.removeElement(state, id)
    }

    /** SELECT 工具点选 / 取消选中元素（不产生撤销步）。 */
    fun selectElement(id: Long?) {
        state = AnnotateOps.selectElement(state, id)
    }

    /** 工具条删除按钮：删除当前选中的元素，一步撤销。 */
    fun removeSelected() {
        state = AnnotateOps.removeSelected(state)
    }

    /** 选区填充马赛克（B6），一步撤销。 */
    fun fillSelectionMosaic(cellSize: Float) {
        state = AnnotateOps.fillSelectionMosaic(state, nextElementId(), cellSize)
    }

    /** 双指缩放/平移（B7），不进撤销。 */
    fun setViewTransform(zoom: Float, pan: Pt) {
        state = AnnotateOps.setViewTransform(state, zoom, pan)
    }

    fun resetViewTransform() {
        state = AnnotateOps.resetViewTransform(state)
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

    /** SHAPE 工具的形状子类型切换。 */
    fun setActiveShape(shape: ShapeKind) {
        state = AnnotateOps.setActiveShape(state, shape)
    }

    /** 橡皮的矩形擦开关（工具条次级按钮）。 */
    fun setEraserRect(enabled: Boolean) {
        state = AnnotateOps.setEraserRect(state, enabled)
    }

    fun updateStyle(tool: ToolKind, style: ToolStyle) {
        state = AnnotateOps.updateStyle(state, tool, style)
    }

    fun moveToolbar(anchor: ToolbarAnchor) {
        state = AnnotateOps.moveToolbar(state, anchor)
    }

    /** 打开标注时应用持久化偏好（每工具风格 + 工具条位置）；不进撤销历史。 */
    fun applySettings(styles: Map<ToolKind, ToolStyle>, anchor: ToolbarAnchor?) {
        state = AnnotateOps.applySettings(state, styles, anchor)
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
