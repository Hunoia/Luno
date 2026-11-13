package hunoia.luno.domain.gridpanel

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.TrendConfig
import hunoia.luno.config.model.TrendEntry
import hunoia.luno.config.model.TrendMetric
import kotlinx.coroutines.flow.Flow

/**
 * 趋势记录域门面（gridpanel 部件与管理页的唯一入口）。
 *
 * 记录 / 增删改是应用内状态变更（配置写入），走 domain 门面写回；
 * 不走 Dispatcher / SurfaceDispatch（architecture §1.3 配置写入不是 Action）。
 */
object TrendCenter {

    val trendConfig: Flow<TrendConfig> = ConfigProvider.trendConfig

    suspend fun getTrendConfig(): TrendConfig = ConfigProvider.getTrendConfig()

    /** 追加一条记录；指标不存在或达到单指标上限时返回 false。 */
    suspend fun addEntry(metricId: String, entry: TrendEntry): Boolean =
        ConfigProvider.addTrendEntry(metricId, entry)

    /** 只改数值，时间不可改（趋势不支持补录）。 */
    suspend fun updateEntryValue(metricId: String, entryId: String, value: Double) =
        ConfigProvider.updateTrendEntryValue(metricId, entryId, value)

    suspend fun deleteEntry(metricId: String, entryId: String) =
        ConfigProvider.deleteTrendEntry(metricId, entryId)

    suspend fun addMetric(metric: TrendMetric) = ConfigProvider.addTrendMetric(metric)

    suspend fun updateMetric(metric: TrendMetric) = ConfigProvider.updateTrendMetric(metric)

    suspend fun deleteMetric(metricId: String) = ConfigProvider.deleteTrendMetric(metricId)
}
