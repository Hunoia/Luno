package hunoia.luno.runtime.condition

import hunoia.luno.config.model.VisibilityRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimeConditionTicker(
    private val scope: CoroutineScope,
    private val rulesProvider: () -> List<VisibilityRule>,
    private val onTimeSignatureChanged: () -> Unit,
) {
    private var job: Job? = null

    fun sync(rules: List<VisibilityRule>) {
        if (rules.any { it.condition.hasTimeRange() }) {
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
                val rules = rulesProvider()
                if (rules.none { it.condition.hasTimeRange() }) {
                    lastSignature = null
                    continue
                }
                val signature = rules
                    .flatMap { rule -> rule.condition.timeRanges() }
                    .map { it.matchesMinute(nowMinuteOfDay()) }
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
