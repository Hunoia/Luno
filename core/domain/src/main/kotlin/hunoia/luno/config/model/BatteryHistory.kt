package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** 电池充放电状态（与 `BatteryManager` 的 status 一一对应）。 */
@Serializable
@Keep
enum class BatteryPowerStatus {
    CHARGING,
    FULL,
    DISCHARGING,
    UNKNOWN,
}

/**
 * 一条电量 / 功率采样。
 *
 * 只记录「当时看到的值」，不做任何推算：电量来自 sticky `ACTION_BATTERY_CHANGED`，
 * 功率来自 `BatteryManager` 的 `CURRENT_NOW × VOLTAGE`（设备不支持时为 null）。
 * 这是瞬态派生数据，不进备份；时间轴由 [BatteryHistory] 自己维护。
 */
@Serializable
@Keep
data class BatterySample(
    /** epoch millis。 */
    val atMillis: Long,
    /** 0..100。 */
    val levelPct: Int,
    val status: BatteryPowerStatus = BatteryPowerStatus.UNKNOWN,
    /** 瞬时功率（mW）；读不到为 null，不要用 0 假装有值。 */
    val powerMw: Float? = null,
) {
    fun normalized(): BatterySample = copy(
        levelPct = levelPct.coerceIn(0, 100),
        powerMw = powerMw?.takeIf { it.isFinite() && it >= 0f },
    )
}

/** 电量 / 功率历史：按时间升序的采样列表。 */
@Serializable
@Keep
data class BatteryHistory(
    val samples: List<BatterySample> = emptyList(),
) {
    /** 最近一条；没有采样时为 null。 */
    val latest: BatterySample? get() = samples.lastOrNull()

    /**
     * 取 [sinceMillis] 之后的采样，用于曲线的时间范围裁剪。
     * 时间升序由 [appended] 保证，这里只按时间过滤，不重排。
     */
    fun since(sinceMillis: Long): List<BatterySample> =
        samples.filter { it.atMillis >= sinceMillis }
}

/** 历史保留窗口：7 天。 */
const val BatteryHistoryRetentionMs: Long = 7L * 24 * 60 * 60 * 1000

/**
 * 采样条数上限：7 天 × 每 5 分钟一条的最坏情况。
 * 电池广播通常几分钟才来一次，这个上限只是防御性边界，避免异常 ROM 刷爆 DataStore。
 */
const val BatteryHistoryMaxSamples: Int = 7 * 24 * 12

/** 同电量 / 同状态的最小记录间隔：5 分钟。状态变化不受此限制。 */
const val BatterySampleMinIntervalMs: Long = 5L * 60 * 1000

/**
 * 追加一条采样（纯函数）。
 *
 * 口径：
 *  - 同电量 + 同状态且距上一条不足 [minIntervalMs] → 不记，避免广播抖动把曲线画成锯齿；
 *  - 电量或状态变化 → 立即记，不受最小间隔限制；
 *  - 时钟回拨（`now < last.atMillis`）仍按时间升序插入，不丢数据；
 *  - 写后按 [retentionMs] 与 [maxSamples] 双重裁剪。
 */
fun BatteryHistory.appended(
    sample: BatterySample,
    now: Long = sample.atMillis,
    minIntervalMs: Long = BatterySampleMinIntervalMs,
    retentionMs: Long = BatteryHistoryRetentionMs,
    maxSamples: Int = BatteryHistoryMaxSamples,
): BatteryHistory {
    val next = sample.normalized()
    val last = samples.lastOrNull()
    if (last != null &&
        last.levelPct == next.levelPct &&
        last.status == next.status &&
        next.atMillis >= last.atMillis &&
        next.atMillis - last.atMillis < minIntervalMs
    ) {
        return this
    }

    // 时钟回拨时插入到正确位置，保持升序；正常追加走快路径。
    val inserted = if (last == null || next.atMillis >= last.atMillis) {
        samples + next
    } else {
        val index = samples.indexOfFirst { it.atMillis > next.atMillis }
        if (index < 0) {
            samples + next
        } else {
            samples.toMutableList().also { it.add(index, next) }
        }
    }

    val cutoff = now - retentionMs
    val kept = inserted.filter { it.atMillis >= cutoff }
    val capped = if (kept.size > maxSamples) kept.takeLast(maxSamples) else kept
    return if (capped == samples) this else BatteryHistory(capped)
}
