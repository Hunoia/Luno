package hunoia.luno.runtime.scene

/**
 * 情景识别的防抖调度（纯逻辑，时钟注入）。
 *
 * 无障碍内容事件是高频的（信息流每次滚动、输入框每次键入都会来一条），不能每条都扫。
 * 规则：
 * - **空闲才扫**：距上一条事件 [idleMs] 后扫一次；
 * - **持续有事件也要扫**：一轮突发从首条事件起最长推迟 [maxDeferMs]，否则纯滚动/动画界面
 *   永远扫不上；
 * - **两次扫描间隔不小于 [minIntervalMs]**，与树的遍历成本成反比地限住频率；
 * - **连续无产出就退避**：一次扫描没推出任何东西（同屏去重命中 / 空快照），说明这一屏
 *   没有新内容可看，下一次最少等 [backoffStepsMs] 起步的间隔。信息流挂着不动时，
 *   不再每 1.5s 白跑一次 250ms 的树遍历。任何真实推进（换应用 / 换页面 / 真推出东西）
 *   立刻复位。
 *
 * 对应 gkd 的内容合并（100ms）/follow-up 窗口（3s）/页面稳定（5s）思路：产物是弹卡片而不是
 * 自动点击，所以空闲窗口比 gkd 的内容合并宽，但上限不能太大——信息流/广告动画会让事件一直
 * 不断，上限太长就是「离开界面很久了才弹」。
 */
internal class SceneScanScheduler(
    private val idleMs: Long = IDLE_MS,
    private val maxDeferMs: Long = MAX_DEFER_MS,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val backoffStepsMs: List<Long> = BACKOFF_STEPS_MS,
) {

    private var burstStartMs: Long = 0L
    private var lastEventMs: Long = 0L
    private var lastScanMs: Long = 0L
    private var hasScanned: Boolean = false

    /** 上一次扫描的落地时间（无论有没有产出）；退避的计时起点。 */
    private var lastResultMs: Long = 0L

    /** -1 = 无退避（还没失败过，或刚被真实推进复位）。 */
    private var backoffIndex: Int = -1
    private var lastPackage: String? = null

    /**
     * 收到一个可扫描事件，返回距离该扫描的毫秒数（0 = 立刻扫）。
     *
     * [packageName] 变了 = 换应用，[screenChanged] = 窗口状态变化（开新页 / 换 Activity）：
     * 两者都视为「有新东西可看」，清掉退避，保证导航后照常弹。
     */
    fun onSignal(
        nowMs: Long,
        packageName: String? = null,
        screenChanged: Boolean = false,
    ): Long {
        if (packageName != null && packageName != lastPackage) {
            lastPackage = packageName
            resetBackoff()
        }
        if (screenChanged) resetBackoff()
        if (burstStartMs == 0L) burstStartMs = nowMs
        lastEventMs = nowMs

        var dueMs = lastEventMs + idleMs
        val capMs = burstStartMs + maxDeferMs
        if (dueMs > capMs) dueMs = capMs
        if (hasScanned) {
            val floorMs = lastScanMs + minIntervalMs
            if (dueMs < floorMs) dueMs = floorMs
        }
        backoffStepsMs.getOrNull(backoffIndex)?.let { backoffMs ->
            // 退避可以超过 [maxDeferMs]：那是「这一屏确实没变化」的判断，不是一轮突发。
            val unlockMs = lastResultMs + backoffMs
            if (dueMs < unlockMs) dueMs = unlockMs
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

    /** 真实推进（换应用 / 换页面）：退避与计时起点一起清零，只留 [minIntervalMs] 兜底。 */
    private fun resetBackoff() {
        backoffIndex = -1
        lastResultMs = 0L
    }

    /**
     * 一次扫描真正跑完（不论结果）时调用。
     *
     * [published] = 推出了新内容。false 时按 [backoffStepsMs] 逐级退避；true 清零。
     */
    fun onScanResult(nowMs: Long, published: Boolean) {
        lastResultMs = nowMs
        backoffIndex = if (published) {
            -1
        } else {
            (backoffIndex + 1).coerceAtMost(backoffStepsMs.lastIndex.coerceAtLeast(0))
        }
    }

    companion object {
        /** 界面稳定多久后扫。 */
        const val IDLE_MS = 500L

        /** 一轮突发最长推迟多久（滚动/动画不会永远等下去）。 */
        const val MAX_DEFER_MS = 2_500L

        /** 两次扫描的最小间隔。 */
        const val MIN_INTERVAL_MS = 1_500L

        /** 连续无产出的退避阶梯：3s → 6s → 10s（上限）。 */
        val BACKOFF_STEPS_MS = listOf(3_000L, 6_000L, 10_000L)
    }
}
