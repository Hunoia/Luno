package hunoia.luno.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.rootSize
import hunoia.luno.config.model.GestureButton

data class GestureTouchTarget(
    val sourceButton: GestureButton,
    val effectiveButton: GestureButton,
    val bounds: Rect,
    val isMirror: Boolean,
)

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

fun GestureButton.mirroredButton(): GestureButton? {
    if (!mirrorHorizontal) return null
    return copy(bounds = bounds.copy(x = 1f - bounds.x - bounds.width), mirrorHorizontal = false)
}

/** 按钮是否配置了至少一个动作；全空时不值得占拦截区，也不该参与来源仲裁。 */
fun GestureButton.hasAnyActions(): Boolean =
    tapActions.isSet() ||
        doubleTapActions.isSet() ||
        longPressActions.isSet() ||
        slideActions.actions.values.any { it.isSet() } ||
        slideHoldActions.actions.values.any { it.isSet() } ||
        longSlideActions.actions.values.any { it.isSet() } ||
        longSlideHoldActions.actions.values.any { it.isSet() }

private fun hunoia.luno.core.domain.StoredAction?.isSet(): Boolean =
    this != null && typeId != hunoia.luno.action.model.Action.NONE_TYPE_ID

/**
 * 触钮**实际生效的触摸区域**（归一化坐标）。
 *
 * 触摸来源配成内核触摸流且拦截宽度 < 触发宽度时，按钮只保留贴边一条：
 * 无障碍按钮窗与拦截窗都按这个矩形摆放，所以条带之外的像素照常落到下层应用。
 * 未配内核触摸流时原样返回，行为与接入 Shizuku 之前完全一致。
 */
fun GestureButton.effectiveWindowButton(
    screenWidthPx: Int = rootSize.width,
    density: Float = DensityProvider.density,
): GestureButton {
    if (touchInput.mode == hunoia.luno.config.model.TouchInputMode.ONLY_ACCESSIBILITY) return this
    val widthDp = touchInput.interceptWidthDp
    if (widthDp <= 0 || screenWidthPx <= 0) return this
    val strip = (widthDp * density / screenWidthPx).coerceAtMost(bounds.width)
    if (strip >= bounds.width) return this
    val hugLeft = (bounds.x + bounds.width / 2f) < 0.5f
    val x = if (hugLeft) bounds.x else bounds.x + bounds.width - strip
    return copy(bounds = bounds.copy(x = x, width = strip))
}
