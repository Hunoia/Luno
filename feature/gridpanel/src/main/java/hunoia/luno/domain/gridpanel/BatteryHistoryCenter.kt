package hunoia.luno.domain.gridpanel

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.BatteryHistory
import hunoia.luno.config.model.BatterySample
import kotlinx.coroutines.flow.Flow

/**
 * 电量 / 功率历史域门面（gridpanel 部件的唯一入口）。
 *
 * 写入方是 `:service` 的 `BatteryHistoryRecorder`（电池广播事件），部件只读；
 * 历史是瞬态派生数据，不进备份、不走 Dispatcher / SurfaceDispatch（architecture §1.3）。
 */
object BatteryHistoryCenter {

    val batteryHistory: Flow<BatteryHistory> = ConfigProvider.batteryHistory

    suspend fun getBatteryHistory(): BatteryHistory = ConfigProvider.getBatteryHistory()

    /** 面板打开时补一条采样；同值去抖与裁剪由 `BatteryHistory.appended` 收口。 */
    suspend fun record(sample: BatterySample) = ConfigProvider.appendBatterySample(sample)
}
