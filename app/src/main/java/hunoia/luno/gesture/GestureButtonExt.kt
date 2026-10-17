package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import hunoia.luno.bridge.ui.rootSize
import hunoia.luno.config.model.GestureButton

data class GestureTouchTarget(
    val sourceButton: GestureButton,
    val effectiveButton: GestureButton,
    val bounds: Rect,
    val isMirror: Boolean,
)

fun List<GestureButton>.find(offset: Offset, imePadding: Int = 0): GestureButton? {
    return find { it.contains(offset, imePadding) }
}

fun List<GestureButton>.findTouchTarget(
    offset: Offset,
    imePadding: Int = 0,
    hint: TouchWindowHint? = null,
): GestureTouchTarget? {
    // 命中测试优先采信 WMS 结果：按钮区域重叠时，几何首个命中与实际收到 DOWN 的窗口
    // 顺序相反（WMS 取最后添加的窗口），不采信就会把动作派给另一个按钮。
    if (hint != null) {
        for (button in this) {
            if (button.id != hint.buttonId) continue
            val windowButton = if (hint.mirrored) button.mirroredButton() ?: button else button
            return GestureTouchTarget(
                sourceButton = button,
                effectiveButton = windowButton,
                bounds = windowButton.bounds(imePadding),
                isMirror = hint.mirrored,
            )
        }
    }
    for (button in this) {
        if (button.contains(offset, imePadding)) {
            return GestureTouchTarget(
                sourceButton = button,
                effectiveButton = button,
                bounds = button.bounds(imePadding),
                isMirror = false,
            )
        }
        val mirroredButton = button.mirroredButton() ?: continue
        if (button.mirrorHorizontal && mirroredButton.contains(offset, imePadding)) {
            return GestureTouchTarget(
                sourceButton = button,
                effectiveButton = mirroredButton,
                bounds = mirroredButton.bounds(imePadding),
                isMirror = true,
            )
        }
    }
    return null
}

fun GestureButton.contains(offset: Offset, imePadding: Int = 0): Boolean {
    val bounds = bounds(imePadding)
    return bounds.contains(offset)
}

fun GestureButton.bounds(imePadding: Int = 0): Rect {
    val topLeft = Offset(
        x = rootSize.width * this.bounds.x,
        y = rootSize.height * this.bounds.y - imePadding,
    )
    val boundsSize = Size(
        width = rootSize.width * this.bounds.width,
        height = rootSize.height * this.bounds.height,
    )
    return Rect(topLeft, boundsSize)
}

val GestureButton.fraction: Float get() = bounds.height

fun GestureButton.mirroredButton(): GestureButton? {
    if (!mirrorHorizontal) return null
    return copy(bounds = bounds.copy(x = 1f - bounds.x - bounds.width), mirrorHorizontal = false)
}
