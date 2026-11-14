package hunoia.luno.runtime.scene

/**
 * 情景识别的防抖调度（纯逻辑，时钟注入）。
 *
 * 无障碍内容事件是高频的（信息流每次滚动、输入框每次键入都会来一条），不能每条都扫。
 * 规则：
 * - **空闲才扫**：距上一条事件 [idleMs] 后扫一次；
 * - **持续有事件也要扫**：一轮突发从首条事件起最长推迟 [maxDeferMs]，否则纯滚动/动画界面
 *   永远扫不上；
 * - **两次扫描间隔不小于 [minIntervalMs]**，与树的遍历成本成反比地限住频率。
 *
 * 对应 gkd 的内容合并（100ms）/follow-up 窗口（3s）/页面稳定（5s）思路，这里取更保守的空闲
 * 时间，因为产物是弹卡片而不是自动点击。
 */
internal class SceneScanScheduler(
    private val idleMs: Long = IDLE_MS,
    private val maxDeferMs: Long = MAX_DEFER_MS,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
) {

    private var burstStartMs: Long = 0L
    private var lastEventMs: Long = 0L
    private var lastScanMs: Long = 0L
    private var hasScanned: Boolean = false

    /**
     * 收到一个可扫描事件，返回距离该扫描的毫秒数（0 = 立刻扫）。
     */
    fun onSignal(nowMs: Long): Long {
        if (burstStartMs == 0L) burstStartMs = nowMs
        lastEventMs = nowMs

        var dueMs = lastEventMs + idleMs
        val capMs = burstStartMs + maxDeferMs
        if (dueMs > capMs) dueMs = capMs
        if (hasScanned) {
            val floorMs = lastScanMs + minIntervalMs
            if (dueMs < floorMs) dueMs = floorMs
        }
        return (dueMs - nowMs).coerceAtLeast(0)
    }

    /**
     * 扫描开始执行时调用。结束当前突发：之后来的事件另起一轮，
     * 扫描期间不断的事件因此变成「扫完再扫一次」，不会排队。
     */
    fun onScanStarted(nowMs: Long) {
        hasScanned = true
        lastScanMs = nowMs
        burstStartMs = 0L
    }

    companion object {
        /** 界面稳定多久后扫。 */
        const val IDLE_MS = 700L

        /** 一轮突发最长推迟多久（滚动/动画不会永远等下去）。 */
        const val MAX_DEFER_MS = 4_000L

        /** 两次扫描的最小间隔。 */
        const val MIN_INTERVAL_MS = 1_500L
    }
}
