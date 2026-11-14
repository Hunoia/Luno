package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 应用抽屉部件的每实例参数：列数（横竖屏各一份，口径同 `SinglePanel` 的几何）。
 *
 * 经 `SinglePanel.params` / `GridPanelItem.Kind.Internal.params`（JSON 串）落盘，
 * 见 architecture §9.2；网格细胞里的小抽屉按自身 span 排版，不读也不写这份参数。
 */
@Serializable
@Keep
data class AppDrawerWidgetParams(
    val portraitColumns: Int? = null,
    val landscapeColumns: Int? = null,
) {
    /** 当前方向记忆的列数；`null` = 还没手动调过，由宿主给的默认列数兜底。 */
    fun columns(landscape: Boolean): Int? =
        if (landscape) landscapeColumns else portraitColumns

    fun withColumns(landscape: Boolean, columns: Int): AppDrawerWidgetParams =
        if (landscape) copy(landscapeColumns = columns) else copy(portraitColumns = columns)
}
