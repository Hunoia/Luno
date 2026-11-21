package hunoia.luno.annotate.ui.gesture

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.input.TextFieldState
import hunoia.luno.annotate.model.AnnotateOps
import hunoia.luno.annotate.model.AnnotationElement
import hunoia.luno.annotate.model.Pt
import hunoia.luno.annotate.model.PxRect

/**
 * 画笔范围环（视图坐标）：[radiusPx] = 实际笔刷半径，落笔/移动时贴触点显示。
 * 纯视图态，抬手即清，不进入文档与撤销栈。
 */
data class BrushCursor(
    val at: Pt,
    val radiusPx: Float,
)

/**
 * 画布手势的瞬态状态（草稿 / 裁剪窗 / 画笔环 / 双指代际）。
 *
 * 全部是**视图态**：不进文档、不进撤销、不落盘。从 AnnotateCanvas 的
 * remember 块收拢到这里，手势路由与绘制层读写同一份事实源；
 * 文字编辑器（[editor] 与其文本源）由 `TextEditorState` 持有，不在这份状态里。
 */
internal class CanvasGestureState {

    /** 进行中的新元素草稿（DRAFT_ID 或被调整元素的 id）。 */
    var draft by mutableStateOf<AnnotationElement?>(null)
        internal set

    /** SELECT 拖动位移：绘制时临时平移，抬手才 commit 一次。 */
    var draftOffset by mutableStateOf<Pt?>(null)
        internal set

    /**
     * 笔迹草稿的点列表：手势期间只追加（O(1)），不复制整条列表。
     * 旧实现每个 MOVE 都 `points = points + p`，5000 点的笔迹累计复制 ~千万引用，
     * GC 压力直接掉帧（审计 B1）。抬手才 toList() 冻结进文档。
     */
    val draftPoints = mutableStateListOf<Pt>()

    /** SELECT 的临时导出选区；抬手后才写入文档。 */
    var draftSelection by mutableStateOf<PxRect?>(null)
        internal set

    /** CROP 的裁剪框（视图坐标）；调整边/角期间底图保持不动。 */
    var cropWindow by mutableStateOf<PxRect?>(null)
        internal set

    /** CROP 的安全区域；裁剪框始终限制在此范围内。 */
    var cropBounds by mutableStateOf<PxRect?>(null)
        internal set

    /** CROP 下使图片完整落入安全区域所需的动态最小缩放。 */
    var cropMinZoom by mutableStateOf(AnnotateOps.MIN_VIEW_ZOOM)
        internal set

    /** 矩形擦的拖拽草稿（图片像素）；抬起即清，只用于画布上的选框预览。 */
    var draftErase by mutableStateOf<PxRect?>(null)
        internal set

    /** 画笔范围环：视图坐标 + 实际笔刷半径（视图 px）。 */
    var brushCursor by mutableStateOf<BrushCursor?>(null)
        internal set

    /** 双指开始时递增：进行中的单指绘制据此丢弃草稿，避免缩放时留下半截笔迹。 */
    var viewTransformGeneration by mutableStateOf(0)
        internal set

    /** 单实例复用（Activity singleTask 换图）：旧文档的草稿/裁剪窗不能带到新文档上。 */
    fun resetForNewDocument() {
        draft = null
        draftOffset = null
        draftPoints.clear()
        draftSelection = null
        cropWindow = null
        cropBounds = null
        cropMinZoom = AnnotateOps.MIN_VIEW_ZOOM
        draftErase = null
        brushCursor = null
        viewTransformGeneration = 0
    }

    /** 双指接管：丢掉正在进行的单指草稿，缩放不应留下半截笔迹。 */
    fun discardActiveDraft() {
        draft = null
        draftOffset = null
        draftPoints.clear()
        draftErase = null
        brushCursor = null
    }
}
