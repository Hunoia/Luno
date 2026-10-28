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
    /** 卡片离屏幕边缘的最小距离（dp）：影响位置 clamp 与卡片圆角（圆角 = 屏幕圆角 − 此值）。 */
    val screenMarginDp: Float = GridPanelDefaults.ScreenMarginDp,
)

/**
 * 页数派生：= 最大 [GridPanelItem.pageIndex] + 1（空面板 1 页）。页数不再是显式设置项，
 * 编辑态滑到虚页落项即建页；尾部无内容页随项删除自动消失。
 */
val GridPanel.derivedPageCount: Int
    get() = (items.maxOfOrNull { it.pageIndex } ?: 0) + 1

/** 行数不设限：实际行数 = max(显示行数，占用行数) + [extraRows]（编辑态 [EditRowExtra]）。 */
const val DynamicRowExtra = 0

/** 编辑态在滚动区末尾追加的空行数：给拖拽留落点，避免底部项被加项 FAB 遮挡。 */
const val EditRowExtra = 2

fun GridPanel.effectiveGridRows(pageIndex: Int, visibleRows: Int, extraRows: Int = DynamicRowExtra): Int {
    val display = visibleRows.coerceAtLeast(1)
    val occupied = items.filter { it.pageIndex == pageIndex }.maxOfOrNull { it.bottom } ?: 0
    return maxOf(display, occupied) + extraRows
}

/** 只改对应朝向那份几何。 */
fun GridPanel.withGeometry(landscape: Boolean, transform: (GridPanelGeometry) -> GridPanelGeometry): GridPanel =
    if (landscape) copy(landscape = transform(this.landscape)) else copy(portrait = transform(this.portrait))

/** 网格面板默认值与可调范围（范围值供设置页用）。 */

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
