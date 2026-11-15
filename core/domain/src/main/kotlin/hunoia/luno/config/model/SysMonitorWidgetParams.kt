package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 资源监控部件的每实例参数：应用列表排序口径 + 是否含系统应用。
 *
 * 经 `SinglePanel.params` / `GridPanelItem.Kind.Internal.params`（JSON 串）落盘，
 * 见 architecture §9.2。网格项没有参数写入通道（`onUpdateParams == null`），
 * 部件内的排序 / 过滤切换只在本地生效；单功能面板整页会写回。
 */
@Serializable
@Keep
data class SysMonitorWidgetParams(
    /** [SORT_MEM] / [SORT_CPU] / [SORT_NAME]；未知值按内存排序。 */
    val sort: String = SORT_MEM,
    val includeSystem: Boolean = false,
) {
    fun normalized(): SysMonitorWidgetParams =
        if (sort in Sorts) this else copy(sort = SORT_MEM)

    companion object {
        const val SORT_MEM = "mem"
        const val SORT_CPU = "cpu"
        const val SORT_NAME = "name"

        val Sorts = listOf(SORT_MEM, SORT_CPU, SORT_NAME)
    }
}
