package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.StoredAction
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 网格面板：触发时在触点附近弹出的网格快捷中心（一墨 PanelPage 的 Luno 版骨架）。
 *
 * 与弧形动作面板（[ActionPanel]）并存、分工不同：弧形面板是「待选菜单」，
 * 本面板是「快捷中心」——点项即执行，编辑在面板内完成（见 docs/grid-panel.md）。
 *
 * 数据必须挂在 [ActionSettings.actionGridPanels] 下才能顺着既有通路到达
 * `:service` 运行时，挪走会让面板静默失效（理由同 [ActionPanel] 类注释）。
 *
 * [items] 是内联动作而非动作库引用，理由同弧形面板：`__longPress` 长按变体
 * 藏在 `StoredAction.params` 里，面板项逐项带变体零成本。
 *
 * [columnCount] 每面板各自的列数；[aspectRatio] 卡片宽高比（宽/高，每面板各自可调），
 * 卡片宽取屏宽固定比例、高按比例推算，项超出滚动。
 */
@Serializable
@Keep
data class GridPanel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val items: List<StoredAction> = emptyList(),
    val columnCount: Int = GridPanelDefaults.ColumnCount,
    /** 卡片宽高比（宽/高）。0.9 ≈ 微竖长卡片。 */
    val aspectRatio: Float = GridPanelDefaults.AspectRatio,
) {
    companion object {
        fun create(name: String = ""): GridPanel = GridPanel(name = name)
    }
}

/** 网格面板全局默认值（列数/宽高比的 editable 范围见设置页）。 */
object GridPanelDefaults {
    const val ColumnCount = 4
    const val AspectRatio = 0.9f
    const val MinColumnCount = 2
    const val MaxColumnCount = 6
    const val MinAspectRatio = 0.5f
    const val MaxAspectRatio = 1.8f
}

/** 全局网格面板列表。 */
@Serializable
@Keep
data class GridPanelSettings(
    val panels: List<GridPanel> = emptyList(),
) {
    fun panelById(id: String): GridPanel? = panels.firstOrNull { it.id == id }

    /** 按 id 更新/新增：已有则替换，没有则追加到末尾。 */
    fun withPanel(panel: GridPanel): GridPanelSettings = copy(
        panels = if (panels.any { it.id == panel.id }) {
            panels.map { if (it.id == panel.id) panel else it }
        } else {
            panels + panel
        }
    )

    fun withoutPanel(id: String): GridPanelSettings =
        copy(panels = panels.filterNot { it.id == id })
}
