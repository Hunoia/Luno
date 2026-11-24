package hunoia.luno.runtime.floatball

import kotlin.math.roundToInt

/**
 * 胶囊窗口宽度的唯一口径（纯函数，可 JVM 单测）。
 *
 * 窗口宽 = 画面宽 + 中侧余量（展开态 = 辉光出血 16dp，不含横滑橡皮筋——72dp 橡皮筋
 * 只钳行位移，行在内容区内被裁、窗口边缘用不到它）。内容宽的上限
 * **必须先扣掉这份余量和一条屏缘留白**：否则内容按「屏宽 × 比例」算完，窗口再叠加余量，
 * 窗口就会越出屏幕（贴边侧内容被裁、另一侧的透明像素仍在吃触摸）。
 *
 * 半屏比例 / 250dp 这类内容上限都保留，只是多了「屏宽 − 余量 − 留白」这条硬上限；
 * 窗口宽最后还要在 [BallCapsuleWindow.applyCapsuleGeometry] 末端再夹一次，兜住
 * 旧屏宽遗留、相位中间值与 ROM 上报偏差。
 */
internal object CapsuleWidths {

    /** 屏缘留白（px）：窗口最外沿与物理屏边之间至少留这么多。 */
    fun edgeInsetPx(density: Float): Int = (EdgeInsetDp * density).roundToInt()

    /** 画面（内容）宽的硬上限：再叠加 [centerMarginPx] 也不越屏。 */
    fun maxSurfaceWidthPx(screenWidthPx: Int, centerMarginPx: Int, edgeInsetPx: Int): Int =
        (screenWidthPx - centerMarginPx - edgeInsetPx).coerceAtLeast(1)

    /**
     * 内容宽上限 = min(绝对上限, 屏宽 × 比例, 屏宽 − 中侧余量 − 留白)。
     *
     * @param absoluteCapPx dp 主导的绝对上限（如通知行 250dp / 结果胶囊 180dp）
     * @param widthRatio   窄屏兜底比例（如通知行 0.6 / 结果胶囊 0.48）
     */
    fun surfaceCapPx(
        screenWidthPx: Int,
        centerMarginPx: Int,
        edgeInsetPx: Int,
        absoluteCapPx: Int,
        widthRatio: Float,
    ): Int {
        val screen = screenWidthPx.coerceAtLeast(1)
        val ratioCapPx = (screen * widthRatio).roundToInt()
        return minOf(
            absoluteCapPx,
            ratioCapPx,
            maxSurfaceWidthPx(screen, centerMarginPx, edgeInsetPx),
        ).coerceAtLeast(1)
    }

    /** 窗口宽 = 画面 + 中侧余量，末端硬夹在屏宽内。返回 > 0。 */
    fun windowWidthPx(surfaceWidthPx: Int, innerMarginPx: Int, screenWidthPx: Int): Int {
        val screen = screenWidthPx.coerceAtLeast(1)
        return (surfaceWidthPx + innerMarginPx).coerceIn(1, screen)
    }

    /** 屏缘留白（dp）。 */
    const val EdgeInsetDp = 16f
}
