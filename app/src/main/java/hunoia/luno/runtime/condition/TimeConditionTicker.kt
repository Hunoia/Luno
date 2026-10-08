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
) {
    private var job: Job? = null

    fun sync(rules: List<AutomationRule>) {
        // 只看启用规则：纯禁用规则不该让分钟 ticker 空转。
        if (rules.any { it.enabled && (it.condition.hasTimeRange() || it.condition.hasScheduledTime()) }) {
            if (job?.isActive != true) start()
        } else {
            stop()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
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
                // 定时触发：分钟级精度，Doze 下可能推迟（doc §3.2 已声明）。
                if (rules.any { it.condition.hasScheduledTime() }) {
                    val weekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
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
                if (rules.none { it.condition.hasTimeRange() }) {
                    lastSignature = null
                    continue
                }
                val signature = rules
                    .flatMap { rule -> rule.condition.timeRanges() }
                    .map { it.matchesMinute(minute) }
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
