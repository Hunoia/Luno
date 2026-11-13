package hunoia.luno.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 待处理的「从趋势面板部件 / 提醒通知打开详情页」请求（主进程内）。
 *
 * 网格面板是独立窗口（服务侧 overlay），只能带 Intent extra 跨进程拉起
 * [hunoia.luno.ui.MainActivity]；Activity 收到后写入这里，导航图消费一次即清空。
 * 用进程内 StateFlow 而不是塞进 SavedState：它是一次性导航意图，不该在进程重建后重放。
 */
object PendingTrendRecord {

    /** [metricId] 非空 = 进该指标详情；空 = 只打开列表页。[openRecord] = 详情页自动弹快速记录。 */
    data class PendingOpen(val metricId: String? = null, val openRecord: Boolean = false)

    private val _open = MutableStateFlow<PendingOpen?>(null)
    val open: StateFlow<PendingOpen?> = _open.asStateFlow()

    fun request(metricId: String?, openRecord: Boolean) {
        _open.value = PendingOpen(metricId?.takeIf { it.isNotBlank() }, openRecord)
    }

    fun consume() {
        _open.value = null
    }
}
