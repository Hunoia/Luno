package hunoia.luno.runtime.floatball

import hunoia.luno.bridge.ui.SystemBarsSafeArea
import kotlin.math.max

/**
 * 悬浮球贴边定位的纯几何。
 *
 * 归一化落点 x = 球窗顶边 / 屏宽，横竖屏各存一份。转屏后必须按**目标朝向自己那份**
 * 落点判侧别：拿旧朝向的像素 `posX` 去比新屏宽，会在屏宽变化时翻转侧别
 * （竖屏贴右 → 横屏变左），再叠加上一朝向的静置平移，就表现为「球跑到另一侧 /
 * 偏离边缘 / 半颗球被裁」。
 *
 * 安全区由 [SystemBarsSafeArea] 表达：只约束上下（可见状态栏 / 导航栏），左右贴
 * 物理屏边。横屏时状态栏可能只作为覆盖层压在屏幕顶部（LP 原点为 0），光靠 LP
 * 原点夹不住，必须按绝对屏坐标的安全区再夹一次。
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

    /** 归一化落点对应的贴边球窗顶边（px）。 */
    fun edgeX(xRatio: Float, screenWidthPx: Int, ballPx: Int): Float =
        edgeX(isLeftEdge(xRatio, screenWidthPx, ballPx), screenWidthPx, ballPx)

    /** 当前球窗顶边是否在左缘（球心判，和贴边/停靠/静置外移同口径）。 */
    fun isOnLeft(posX: Float, screenWidthPx: Int, ballPx: Int): Boolean =
        posX * 2f + ballPx < screenWidthPx

    /**
     * 球心可落范围（屏幕绝对坐标，px）。
     *
     * `minY` 取「LP 原点」与「状态栏安全区」的较大者：竖屏 LP 原点就是状态栏下沿；
     * 横屏 LP 原点可能为 0，此时只能靠安全区把球挡在状态栏之外。
     */
    fun bounds(
        screenWidthPx: Int,
        screenHeightPx: Int,
        ballPx: Int,
        safe: SystemBarsSafeArea = SystemBarsSafeArea(),
        lpOriginY: Int = 0,
    ): BallBounds {
        val maxX = max(0, screenWidthPx - ballPx).toFloat()
        val minY = max(lpOriginY, safe.top).toFloat()
        val maxY = max(minY, (screenHeightPx - ballPx - safe.bottom).toFloat())
        return BallBounds(minX = 0f, maxX = maxX, minY = minY, maxY = maxY)
    }

    /** 静置横向外移量：左缘为负（向屏外），右缘为正。 */
    fun idleShiftPx(left: Boolean, ballPx: Int, outRatio: Float): Float =
        (if (left) -1f else 1f) * outRatio * ballPx

    /**
     * 键盘让位抬升量（px）：悬浮球与胶囊共用同一公式。
     *
     * [base] 是未让位的落点（屏幕绝对坐标，球窗顶边），[size] 是球 / 收起圆直径，
     * [imeBottomPx] 是键盘顶部到屏幕底的距离（`WindowInsets.Type.ime().bottom`，
     * display 绝对参考系），[minY] 是可落范围上界（安全区 / LP 原点）。
     *
     * 抬升量 = 球底 + 边距越过键盘顶的部分；封顶在球顶正好到 [minY]——不把球顶上
     * 状态栏。只算量、不落窗口：调用方各自把「显示位置 = 基准 − 抬升」写进窗口。
     */
    fun imeLiftAmount(
        base: Int,
        size: Int,
        marginPx: Int,
        imeBottomPx: Int,
        minY: Int,
        screenHeightPx: Int,
    ): Int {
        if (imeBottomPx <= 0) return 0
        val overflow = base + size + marginPx - (screenHeightPx - imeBottomPx)
        return max(0, overflow).coerceAtMost(max(0, base - minY))
    }
}

/** 球窗可落范围（屏幕绝对坐标，px）。 */
internal data class BallBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
)
