package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 应用耗电统计部件的每实例参数：排行排序口径 + 是否含系统应用 + 曲线时间范围。
 *
 * 与 [SysMonitorWidgetParams] 同构，走同一套「网格项只在本地生效、单功能面板整页写回」
 * 的规则（见 architecture §9.2）。
 */
@Serializable
@Keep
data class PowerStatsWidgetParams(
    /** [SORT_POWER] / [SORT_NAME]；未知值按耗电排序。 */
    val sort: String = SORT_POWER,
    val includeSystem: Boolean = false,
    /** [RANGE_24H] / [RANGE_7D]；未知值按 24 小时。 */
    val chartRange: String = RANGE_24H,
) {
    fun normalized(): PowerStatsWidgetParams = copy(
        sort = if (sort in Sorts) sort else SORT_POWER,
        chartRange = if (chartRange in Ranges) chartRange else RANGE_24H,
    )

    companion object {
        const val SORT_POWER = "power"
        const val SORT_NAME = "name"

        const val RANGE_24H = "24h"
        const val RANGE_7D = "7d"

        val Sorts = listOf(SORT_POWER, SORT_NAME)
        val Ranges = listOf(RANGE_24H, RANGE_7D)
    }
}
