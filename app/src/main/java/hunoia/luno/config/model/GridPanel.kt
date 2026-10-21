package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 网格面板：触发时在触点附近弹出的网格快捷中心，点项即执行。
 * 与弧形面板（[ActionPanel]）的「待选菜单」分工不同。必须挂在
 * [ActionSettings.actionGridPanels] 下才能到达 `:service` 运行时，挪走会静默失效。
 * [items] 是内联动作而非动作库引用（长按变体随项零成本）。
 */
@Serializable
@Keep
data class GridPanel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val items: List<GridPanelItem> = emptyList(),
    val portrait: GridPanelGeometry = GridPanelGeometry(),
    val landscape: GridPanelGeometry = GridPanelGeometry(),
    /** 页数（不设上限）：项按 [GridPanelItem.pageIndex] 分页；锚点随面板（第 0 页布局），不随页变化。 */
    val pageCount: Int = 1,
    /** 卡片本体透明度：0.3–1，半透明透出原背景。 */
    val cardAlpha: Float = GridPanelDefaults.CardAlpha,
) {
    fun geometry(landscape: Boolean): GridPanelGeometry = if (landscape) this.landscape else this.portrait

    companion object {
        fun create(name: String = ""): GridPanel = GridPanel(name = name)
    }
}

/**
 * 面板几何（横竖屏各一份）：列数、卡片宽高占屏比例。卡片高度由比例决定，
 * 可见行数由此反推；格子保持方格，超出靠纵向滚动。
 */
@Serializable
@Keep
data class GridPanelGeometry(
    val columnCount: Int = GridPanelDefaults.ColumnCount,
    val cardWidthFraction: Float = GridPanelDefaults.CardWidthFraction,
    val cardHeightFraction: Float = GridPanelDefaults.CardHeightFraction,
)

/** 行数不设限：实际行数 = max(显示行数，占用行数) + [DynamicRowExtra]（每页独立算）。 */
const val DynamicRowExtra = 5

fun GridPanel.effectiveGridRows(pageIndex: Int, visibleRows: Int): Int {
    val display = visibleRows.coerceAtLeast(1)
    val occupied = items.filter { it.pageIndex == pageIndex }.maxOfOrNull { it.bottom } ?: 0
    return maxOf(display, occupied) + DynamicRowExtra
}

/** 只改对应朝向那份几何。 */
fun GridPanel.withGeometry(landscape: Boolean, transform: (GridPanelGeometry) -> GridPanelGeometry): GridPanel =
    if (landscape) copy(landscape = transform(this.landscape)) else copy(portrait = transform(this.portrait))

/** 网格面板默认值与可调范围（范围值供设置页用）。 */
object GridPanelDefaults {
    const val ColumnCount = 4
    const val MinColumnCount = 2
    const val MaxColumnCount = 6
    const val CardHeightFraction = 0.62f
    const val MinCardHeightFraction = 0.30f
    const val MaxCardHeightFraction = 0.90f

    const val CardWidthFraction = 0.72f
    const val MinCardWidthFraction = 0.5f
    const val MaxCardWidthFraction = 0.9f
    const val CardAlpha = 0.75f
    const val MinCardAlpha = 0.3f
    const val MaxCardAlpha = 1f
}

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
