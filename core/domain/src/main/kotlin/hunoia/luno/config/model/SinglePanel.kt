package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 内置单功能面板的稳定 id。它们同时是 `PanelWidget` 的 widgetId，并直接决定面板引用 id
 * （[singlePanelRefId]）。运行时只认这些 id，不依赖配置里是否存在一条记录：
 * 未配置 = 使用默认几何，面板依然存在。
 */
object SinglePanelIds {
    const val CLIPBOARD = "internal.clipboardPage"
    const val APP_DRAWER = "internal.appDrawer"
    const val QUICK_LAUNCH = "internal.quickLaunch"
}

/** 面板引用 id 前缀：`internal.gridPanel` 的 panelId 用它区分单功能面板与网格面板 UUID。 */
const val SINGLE_PANEL_REF_PREFIX = "single:"

fun singlePanelRefId(widgetId: String): String = SINGLE_PANEL_REF_PREFIX + widgetId

fun singlePanelWidgetId(panelId: String): String? =
    panelId.takeIf { it.startsWith(SINGLE_PANEL_REF_PREFIX) }
        ?.removePrefix(SINGLE_PANEL_REF_PREFIX)
        ?.takeIf { it.isNotBlank() }

/**
 * 单功能面板：每个支持整页的功能一块，固定存在、不可新建、不可删除、不可改名。
 *
 * 与 [GridPanel] 是两种独立配置类型：这里没有项列表、页数和名称；只保留面板外观几何与
 * 该功能自己的参数。功能清单由 `PanelWidgetRegistry.singlePanelWidgets` 决定，配置只覆盖默认值。
 *
 * 横竖屏完全分开：[portrait] / [landscape] 各持一份几何，转屏后读另一份。
 */
@Serializable
@Keep
data class SinglePanel(
    /** 固定功能 id（见 [SinglePanelIds]）；一功能一块，全局唯一。 */
    val widgetId: String,
    val portrait: GridPanelGeometry = GridPanelGeometry(),
    val landscape: GridPanelGeometry = GridPanelGeometry(),
    /** 卡片本体透明度：0–1，半透明透出原背景；0 = 纯模糊/完全透明底。 */
    val cardAlpha: Float = GridPanelDefaults.CardAlpha,
    /** 面板与内置部件背景的模糊半径（dp，0 = 关闭）。 */
    val blurRadiusDp: Float = GridPanelDefaults.BlurRadiusDp,
    /** 功能自己的参数（JSON 串，口径同 `GridPanelItem.Kind.Internal.params`）。 */
    val params: String? = null,
) {
    fun geometry(landscape: Boolean): GridPanelGeometry =
        if (landscape) this.landscape else this.portrait

    val refId: String get() = singlePanelRefId(widgetId)
}

@Serializable
@Keep
data class SinglePanelSettings(
    val panels: List<SinglePanel> = emptyList(),
) {
    fun panelByWidgetId(widgetId: String): SinglePanel? =
        panels.firstOrNull { it.widgetId == widgetId }

    fun panelByRefId(panelId: String): SinglePanel? =
        singlePanelWidgetId(panelId)?.let(::panelByWidgetId)

    /** 按 widgetId 更新/新增：一功能只保留一条。 */
    fun withPanel(panel: SinglePanel): SinglePanelSettings = copy(
        panels = if (panels.any { it.widgetId == panel.widgetId }) {
            panels.map { if (it.widgetId == panel.widgetId) panel else it }
        } else {
            panels + panel
        },
    )
}
