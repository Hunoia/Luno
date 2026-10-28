package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * Luno 触发的应用启动统计（包名维度）。
 *
 * 只统计“用户通过 Luno 打开应用”的成功启动；最近应用/系统使用时长是另一套数据，
 * 不在这里混用。统计独立于启动器几何配置，写入不会触发布局设置的订阅者。
 */
@Serializable
@Keep
data class AppUsageStats(
    /** packageName -> 累计启动次数（随 [recentLaunchTime] 的 500 条窗口一起裁剪）。 */
    val launchCount: Map<String, Long> = emptyMap(),
    /** packageName -> 最近一次启动时间（epoch millis）。 */
    val recentLaunchTime: Map<String, Long> = emptyMap(),
    /** packageName -> 最近一次启动来源（诊断用，不参与排序）。 */
    val lastSource: Map<String, AppLaunchSource> = emptyMap(),
)

/**
 * 纯函数记录：成功启动后调用。
 * - 同一包名 [minIntervalMs] 内的重复事件只记一次，避免双击/转发造成虚高；
 *   被去抖的那次不改次数/时间，但仍记最新来源（诊断字段不参与排序）；
 * - 只保留最近 [maxHistory] 个包名，计数同步裁掉窗口外的键。
 */
fun AppUsageStats.recordLaunch(
    packageName: String,
    now: Long,
    source: AppLaunchSource = AppLaunchSource.UNKNOWN,
    maxHistory: Int = 500,
    minIntervalMs: Long = 300L,
): AppUsageStats {
    if (packageName.isBlank()) return this
    val last = recentLaunchTime[packageName] ?: 0L
    // now < last（时钟回拨）不当作去抖窗口，否则该包要等时钟追上来才能再计。
    val debounced = last > 0L && now >= last && now - last < minIntervalMs
    if (debounced) {
        if (lastSource[packageName] == source) return this
        return copy(lastSource = lastSource + (packageName to source))
    }

    val recent = recentLaunchTime + (packageName to now)
    val trimmedRecent = if (recent.size > maxHistory) {
        recent.entries
            .sortedBy { it.value }
            .takeLast(maxHistory)
            .associate { it.key to it.value }
    } else {
        recent
    }
    val counts = launchCount + (packageName to ((launchCount[packageName] ?: 0L) + 1L))
    return AppUsageStats(
        launchCount = counts.filterKeys { it in trimmedRecent },
        recentLaunchTime = trimmedRecent,
        lastSource = (lastSource + (packageName to source)).filterKeys { it in trimmedRecent },
    )
}
