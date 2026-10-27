package hunoia.luno.runtime.condition

import hunoia.luno.config.model.AutomationRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class TimeConditionTicker(
    private val scope: CoroutineScope,
    private val rulesProvider: () -> List<AutomationRule>,
    private val onTimeSignatureChanged: () -> Unit,
    /** 定时命中：调用方先置 scheduledTimeHit 脉冲（幂等），再照常求值消费。 */
    private val markScheduledHit: () -> Unit = {},
    /** 间隔命中：key 为 ruleId:分钟；调用方置脉冲后按 INTERVAL 域求值。 */
    private val markIntervalHit: (Set<String>) -> Unit = {},
) {
    private var job: Job? = null

    /** 每个 interval 项的上次触发时间（elapsedRealtime），规则删除后自动修剪。 */
    private val lastFiredAt = mutableMapOf<String, Long>()

    fun sync(rules: List<AutomationRule>) {
        // 只看启用规则：纯禁用规则不该让分钟 ticker 空转。
        if (rules.any {
                it.enabled && (
                    it.condition.hasTimeRange() ||
                        it.condition.hasScheduledTime() ||
                        it.condition.hasIntervalCondition()
                    )
            }
        ) {
            if (job?.isActive != true) start()
        } else {
            stop()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        lastFiredAt.clear()
    }

    private fun start() {
        job = scope.launch {
            var lastSignature: List<Boolean>? = null
            while (isActive) {
                delay(millisUntilNextMinuteBoundary())
                if (!isActive) break
                // 禁用规则不参与定时/时间窗签名（与 sync 的启用过滤一致）。
                val rules = rulesProvider().filter { it.enabled }
                val minute = nowMinuteOfDay()
                val weekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                // 定时触发：分钟级精度，Doze 下可能推迟。
                if (rules.any { it.condition.hasScheduledTime() }) {
                    val due = rules
                        .flatMap { rule -> rule.condition.scheduledItems() }
                        .any { item ->
                            minute in item.scheduledMinutes &&
                                (item.scheduledWeekdays.isEmpty() || weekday in item.scheduledWeekdays)
                        }
                    if (due) {
                        markScheduledHit()
                        onTimeSignatureChanged()
                    }
                }
                if (rules.any { it.condition.hasIntervalCondition() }) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    val due = mutableSetOf<String>()
                    val activeKeys = mutableSetOf<String>()
                    for (rule in rules) {
                        for (item in rule.condition.intervalItems()) {
                            val key = intervalDueKey(rule.id, item) ?: continue
                            activeKeys.add(key)
                            val last = lastFiredAt[key]
                            if (last == null) {
                                // 首次看到该间隔项只播种，不立即触发；新建规则也遵循同一语义。
                                lastFiredAt[key] = now
                                continue
                            }
                            val intervalMs = (item.intervalMinutes ?: continue) * 60_000L
                            if (now - last >= intervalMs) {
                                due.add(key)
                                lastFiredAt[key] = now
                            }
                        }
                    }
                    // 规则删除/禁用/改间隔后清理旧 key，避免 map 长期增长。
                    lastFiredAt.keys.retainAll(activeKeys)
                    if (due.isNotEmpty()) {
                        markIntervalHit(due)
                    }
                }
                if (rules.none { it.condition.hasTimeRange() }) {
                    lastSignature = null
                    continue
                }
                // 签名含星期：跨午夜且窗口跨天时，分钟连续而星期已切换，也要触发刷新。
                val signature = rules
                    .flatMap { rule -> rule.condition.timeRanges() }
                    .map { it.matches(minute, weekday) }
                if (lastSignature != null && signature != lastSignature) {
                    onTimeSignatureChanged()
                }
                lastSignature = signature
            }
        }
    }

    private fun millisUntilNextMinuteBoundary(): Long {
        val remainder = System.currentTimeMillis() % 60_000L
        return if (remainder == 0L) 60_000L else 60_000L - remainder
    }
}
