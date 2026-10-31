package hunoia.luno.runtime.floatball

import kotlin.math.min

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

internal data class BubblePlacement(
    val x: Int,
    val y: Int,
    val sideRight: Boolean,
    val maxWidthPx: Int,
)

/**
 * 共享气泡的锚点计算：贴在球所在侧，纵向跟随球心，始终留在安全区内。
 * 纯函数便于 JVM 单测；像素单位由调用方统一换算。
 */
internal object BubbleGeometry {

    fun place(
        ball: BubbleAnchor,
        bubbleWidthPx: Int,
        bubbleHeightPx: Int,
        screenWidthPx: Int,
        screenHeightPx: Int,
        safeTopPx: Int,
        safeBottomPx: Int,
        marginPx: Int,
        gapPx: Int,
        minWidthPx: Int,
        maxWidthPx: Int,
    ): BubblePlacement {
        val sideRight = ball.centerX >= screenWidthPx / 2
        val available = if (sideRight) {
            ball.left - gapPx - marginPx
        } else {
            screenWidthPx - ball.right - gapPx - marginPx
        }
        val widthLimit = min(maxWidthPx, available.coerceAtLeast(minWidthPx))
        val effectiveWidth = bubbleWidthPx.takeIf { it > 0 } ?: widthLimit

        val rawX = if (sideRight) {
            ball.left - gapPx - effectiveWidth
        } else {
            ball.right + gapPx
        }
        val maxX = (screenWidthPx - effectiveWidth - marginPx).coerceAtLeast(marginPx)
        val x = rawX.coerceIn(marginPx, maxX)

        val minY = safeTopPx + marginPx
        val maxY = (safeBottomPx - bubbleHeightPx - marginPx).coerceAtLeast(minY)
        val y = (ball.centerY - bubbleHeightPx / 2).coerceIn(minY, maxY)

        return BubblePlacement(
            x = x,
            y = y,
            sideRight = sideRight,
            maxWidthPx = widthLimit,
        )
    }
}
