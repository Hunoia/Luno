package hunoia.luno.runtime.floatball

import kotlin.math.max

/**
 * 悬浮球贴边定位的纯几何。
 *
 * 归一化落点 x = 球窗顶边 / 屏宽，横竖屏各存一份。转屏后必须按**目标朝向自己那份**
 * 落点判侧别：拿旧朝向的像素 `posX` 去比新屏宽，会在屏宽变化时翻转侧别
 * （竖屏贴右 → 横屏变左），再叠加上一朝向的静置平移，就表现为「球跑到另一侧 /
 * 偏离边缘 / 半颗球被裁」。
 */
internal object BallPlacement {

    /** 归一化落点是否贴左缘（按球心判，与 [isOnLeft] 同口径）。 */
    fun isLeftEdge(xRatio: Float, screenWidthPx: Int, ballPx: Int): Boolean {
        if (screenWidthPx <= 0) return true
        val topLeft = xRatio * screenWidthPx
        return topLeft + ballPx / 2f < screenWidthPx / 2f
    }

    /** 指定侧别的贴边球窗顶边（px）：左缘 0 / 右缘屏宽 − 球径。 */
    fun edgeX(left: Boolean, screenWidthPx: Int, ballPx: Int): Float =
        if (left) 0f else max(0, screenWidthPx - ballPx).toFloat()

    /** 归一化落点对应的贴边球窗顶边（px）：左缘 0 / 右缘屏宽 − 球径。 */
    fun edgeX(xRatio: Float, screenWidthPx: Int, ballPx: Int): Float =
        edgeX(isLeftEdge(xRatio, screenWidthPx, ballPx), screenWidthPx, ballPx)

    /** 当前球窗顶边是否在左缘（球心判，和贴边/停靠/静置外移同口径）。 */
    fun isOnLeft(posX: Float, screenWidthPx: Int, ballPx: Int): Boolean =
        posX * 2f + ballPx < screenWidthPx

    /** 静置横向外移量：左缘为负（向屏外），右缘为正。 */
    fun idleShiftPx(left: Boolean, ballPx: Int, outRatio: Float): Float =
        (if (left) -1f else 1f) * outRatio * ballPx
}
