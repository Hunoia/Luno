package hunoia.luno.runtime.floatball

import kotlin.math.pow

/**
 * 持机侧判定：近期触钮侧别事件的**衰减加权多数票**。
 *
 * 每个事件按时间衰减（半衰期 [halfLifeMs]）计入所属侧权重，对侧要翻转需同时
 * 满足：权重占比 ≥ [flipShare] 且绝对权重 ≥ [minWeight]（约两票新鲜事件）。
 *
 * - 换手：对侧连续两三次触发即累积到位，切过去；
 * - 单次跨手误触：被本侧残余权重稀释，占比到不了门槛；
 * - 双手交替：两侧权重相近，占比在 0.5 附近徘徊，永不切换（无需冷却）；
 * - 久置：旧票自然衰减并被 [windowMs] 裁剪，等效过期后两票即切，无需专门过期分支；
 * - 手动停靠球是确证：直接采用并清空票池。
 *
 * 纯逻辑无 Android 依赖，时间由调用方注入（uptime ms），便于单测。
 */
internal class HandSideHysteresis(
    private val halfLifeMs: Float = 60_000f,
    private val windowMs: Long = 5 * 60_000L,
    private val flipShare: Float = 0.7f,
    private val minWeight: Float = 1.5f,
) {

    /** 当前持机侧（true = 左）；null = 尚未播种。 */
    var currentLeft: Boolean? = null
        private set

    /** (事件时刻, 左侧?) 票池，按 [windowMs] 裁剪。 */
    private val events = mutableListOf<Pair<Long, Boolean>>()

    /** 用球停靠边播种（只播一次）。 */
    fun seed(leftSide: Boolean) {
        if (currentLeft == null) currentLeft = leftSide
    }

    /** 触钮侧别事件；返回 true 表示发生切换。 */
    fun onSideEvent(leftSide: Boolean, nowMs: Long): Boolean {
        events.removeAll { nowMs - it.first > windowMs }
        events.add(nowMs to leftSide)
        val current = currentLeft ?: run {
            currentLeft = leftSide
            return false
        }
        var leftW = 0f
        var rightW = 0f
        for ((t, eventLeft) in events) {
            val w = 0.5f.pow((nowMs - t) / halfLifeMs)
            if (eventLeft) leftW += w else rightW += w
        }
        val winnerLeft = leftW >= rightW
        val winnerW = if (winnerLeft) leftW else rightW
        val total = leftW + rightW
        if (winnerLeft == current || winnerW < minWeight ||
            total <= 0f || winnerW / total < flipShare
        ) {
            return false
        }
        currentLeft = winnerLeft
        return true
    }

    /** 手动停靠：用户亲手放的位置是确证，直接采用并清空票池；返回是否变化。 */
    fun onManualPark(leftSide: Boolean): Boolean {
        val changed = currentLeft != leftSide
        currentLeft = leftSide
        events.clear()
        return changed
    }
}
