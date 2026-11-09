package hunoia.luno.runtime.floatball


/** 纯几何锚点，避免窗口层把 Android Rect 带进可单测的布局计算。 */
internal data class BubbleAnchor(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}
