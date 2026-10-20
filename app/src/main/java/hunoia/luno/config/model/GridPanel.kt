package hunoia.luno.config.model

import androidx.annotation.Keep
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
 * [portrait] / [landscape] 是横竖屏各自的几何（列数、卡片宽高占比）；项按
 * [GridPanelItem] 的 x/y/span 落在网格上，可跨格（摆桌面小部件的前提）。
 *
 * 与一墨 PanelPage 的逐面板外观自由度对齐（docs/grid-panel-moting-compare.md §10）：
 * 卡片宽高占比、[pageCount] 简版多页、[cardAlpha] 卡片透明度。
 * （blur/scrim 曾对齐过一轮，真机验证后整体移除 —— 2026-10-19 拍板，字段彻底删除，
 * `ignoreUnknownKeys` 保证旧 JSON 不炸。）
 */
@Serializable
@Keep
data class GridPanel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    /** 固定网格上的项。位置由 [GridPanelItem.x] / [y] / [spanX] / [spanY] 决定。 */
    val items: List<GridPanelItem> = emptyList(),
    /** 竖屏几何（列数/卡宽/卡高）。横竖屏分别设置：横屏下改的就是横屏这份。 */
    val portrait: GridPanelGeometry = GridPanelGeometry(),
    /** 横屏几何。 */
    val landscape: GridPanelGeometry = GridPanelGeometry(),
    /**
     * 简版多页的页数（不设上限）：项按 [GridPanelItem.pageIndex] 分页，
     * 页眉点/横滑切换。锚点随面板（第 0 页布局），不随页变化。
     */
    val pageCount: Int = 1,
    /** 卡片本体透明度：半透明透出原背景（原硬编码 0.75 的可调化）。 */
    val cardAlpha: Float = GridPanelDefaults.CardAlpha,
) {
    /** 当前朝向的几何。 */
    fun geometry(landscape: Boolean): GridPanelGeometry = if (landscape) this.landscape else this.portrait

    companion object {
        fun create(name: String = ""): GridPanel = GridPanel(name = name)
    }
}

/**
 * 面板几何（横竖屏各一份）：列数、卡片宽高占屏比例。卡片**高度由比例决定**，
 * 可见行数 = 高度反推（格子尺寸仍由列数和卡宽决定，方格）；超出靠纵向滚动，
 * 动态行数照旧（实际行数 = max(可见行数, 占用行数) + DynamicRowExtra）。
 */
@Serializable
@Keep
data class GridPanelGeometry(
    val columnCount: Int = GridPanelDefaults.ColumnCount,
    val cardWidthFraction: Float = GridPanelDefaults.CardWidthFraction,
    val cardHeightFraction: Float = GridPanelDefaults.CardHeightFraction,
)

/**
 * 行数不限制：实际行数 = max(显示行数，占用行数) + [DynamicRowExtra]（每页独立算）。
 *
 * 旧版的 gridRows 设置字段已删除（ignoreUnknownKeys 保证旧 JSON 不炸）：
 * 渲染、落盘、编辑页重排都用本函数的结果。
 */
const val DynamicRowExtra = 5

fun GridPanel.effectiveGridRows(pageIndex: Int, visibleRows: Int): Int {
    val display = visibleRows.coerceAtLeast(1)
    val occupied = items.filter { it.pageIndex == pageIndex }.maxOfOrNull { it.bottom } ?: 0
    return maxOf(display, occupied) + DynamicRowExtra
}

/** 按朝向修改几何（横屏改横屏那份，竖屏改竖屏那份）。 */
fun GridPanel.withGeometry(landscape: Boolean, transform: (GridPanelGeometry) -> GridPanelGeometry): GridPanel =
    if (landscape) copy(landscape = transform(this.landscape)) else copy(portrait = transform(this.portrait))

/** 网格面板全局默认值（列数/卡片尺寸的 editable 范围见设置页）。 */
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
