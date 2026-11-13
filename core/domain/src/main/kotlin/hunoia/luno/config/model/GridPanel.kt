package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 网格面板：触发时在触点附近弹出的网格快捷中心。
 *
 * 与单功能面板（[SinglePanel]）是两种独立配置类型：网格面板有项列表、可多页、可新建多个；
 * 单功能面板每个功能一块，配置在 [SinglePanelSettings]。两者共用 `grid-panel` Surface 与
 * 触发引用，但不再用同一个容器类型互相切换。
 *
 * 必须挂在 [ActionSettings.actionGridPanels] 下才能到达 `:service` 运行时，挪走会静默失效。
 *
 * 横竖屏**完全分开**：[portrait] / [landscape] 各持一份几何与部件列表，
 * 转屏后面板显示另一朝向的独立布局。
 */
@Serializable
@Keep
data class GridPanel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /**
     * 底栏图标：图标库 key（见 `IconPalette`）或自定义文字（首个字符当作字标）。
     * `null` = 没设过：底栏回退到名称首字，名称也空时用默认网格图标。
     */
    val icon: String? = null,
    val portrait: GridPanelOrientation = GridPanelOrientation(),
    val landscape: GridPanelOrientation = GridPanelOrientation(),
    /** 卡片本体透明度：0–1，半透明透出原背景；0 = 纯模糊/完全透明底。 */
    val cardAlpha: Float = GridPanelDefaults.CardAlpha,
    /**
     * 面板与内置部件背景的模糊半径（dp，0 = 关闭）。
     * 默认开启；旧配置没有该字段时按默认值反序列化（不做迁移）。
     */
    val blurRadiusDp: Float = GridPanelDefaults.BlurRadiusDp,
) {
    fun orientation(landscape: Boolean): GridPanelOrientation =
        if (landscape) this.landscape else this.portrait

    companion object {
        fun create(name: String = ""): GridPanel = GridPanel(name = name)
    }
}

/**
 * 单朝向的面板配置：几何 + 部件列表。横竖屏各一份，互不影响。
 */
@Serializable
@Keep
data class GridPanelOrientation(
    val geometry: GridPanelGeometry = GridPanelGeometry(),
    val items: List<GridPanelItem> = emptyList(),
) {
    /** 页数派生：= 最大 [GridPanelItem.pageIndex] + 1（空面板 1 页）。页数不再是显式设置项，
     *  编辑态滑到虚页落项即建页；尾部无内容页随项删除自动消失。 */
    val derivedPageCount: Int
        get() = (items.maxOfOrNull { it.pageIndex } ?: 0) + 1

    fun effectiveGridRows(pageIndex: Int, visibleRows: Int, extraRows: Int = DynamicRowExtra): Int {
        val display = visibleRows.coerceAtLeast(1)
        val occupied = items.filter { it.pageIndex == pageIndex }.maxOfOrNull { it.bottom } ?: 0
        return maxOf(display, occupied) + extraRows
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
    /** 跟随触发点的三个边距：横向贴触发侧，竖向跟随触发点（上下保留最小间距）。 */
    val placement: SurfacePlacement = SurfacePlacement(),
)

/** 行数不设限：实际行数 = max(显示行数，占用行数) + [extraRows]（编辑态 [EditRowExtra]）。 */
const val DynamicRowExtra = 0

/** 编辑态在滚动区末尾追加的空行数：给拖拽留落点，避免底部项被加项 FAB 遮挡。 */
const val EditRowExtra = 2

/** 只改对应朝向那份配置。 */
fun GridPanel.withOrientation(
    landscape: Boolean,
    transform: (GridPanelOrientation) -> GridPanelOrientation,
): GridPanel =
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
