package hunoia.luno.gridpanel.trend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 趋势详情页的查看偏好（仅进程内）。
 *
 * 范围选择切走再回来仍保留，但它不是业务配置：不落盘、不进备份、不改 schema。
 * 与 `PendingTrendRecord` 一样，放在页面之外的模块级状态里。
 */
object TrendViewState {
    private val _range = MutableStateFlow(TrendRange.DEFAULT)
    val range: StateFlow<TrendRange> = _range.asStateFlow()

    fun selectRange(range: TrendRange) {
        _range.value = range
    }
}
