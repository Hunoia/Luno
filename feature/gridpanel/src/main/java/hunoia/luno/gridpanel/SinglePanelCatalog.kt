package hunoia.luno.gridpanel

import android.content.Context
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.GridPanel
import hunoia.luno.config.model.GridPanelOrientation
import hunoia.luno.config.model.SinglePanel
import hunoia.luno.config.model.singlePanelWidgetId
import hunoia.luno.gridpanel.internal.PanelWidget
import hunoia.luno.gridpanel.internal.PanelWidgetRegistry

/**
 * 单功能面板的固定清单与默认值。
 *
 * 面板是否存在只由 `PanelWidgetRegistry.singlePanelWidgets` 决定，不依赖 DataStore 里
 * 是否已经写入一条记录：没有配置就用 [SinglePanel] 的默认几何。这样“固定三块面板”
 * 不会因为配置缺失或旧数据不存在而消失。
 */
object SinglePanelCatalog {

    fun widgets(): List<PanelWidget> = PanelWidgetRegistry.singlePanelWidgets

    fun default(widgetId: String): SinglePanel = SinglePanel(widgetId = widgetId)

    fun resolve(settings: ActionSettings, widgetId: String): SinglePanel =
        settings.singlePanels.panelByWidgetId(widgetId) ?: default(widgetId)

    fun all(settings: ActionSettings): List<SinglePanel> =
        widgets().map { resolve(settings, it.id) }

    fun label(context: Context, widgetId: String): String? =
        PanelWidgetRegistry.byId(widgetId)?.let { context.getString(it.labelRes) }

    /**
     * 把单功能面板转成面板引用所需的网格壳：只填 id / 展示名 / 几何 / 外观。
     * 运行时与嵌套面板显示都走这一份，避免各处再写一套 `single:` 解析。
     */
    fun shell(context: Context, panel: SinglePanel): GridPanel? {
        val name = label(context, panel.widgetId) ?: return null
        return GridPanel(
            id = panel.refId,
            name = name,
            portrait = GridPanelOrientation(geometry = panel.portrait),
            landscape = GridPanelOrientation(geometry = panel.landscape),
            cardAlpha = panel.cardAlpha,
            blurRadiusDp = panel.blurRadiusDp,
        )
    }

    fun shells(context: Context, settings: ActionSettings): List<GridPanel> =
        all(settings).mapNotNull { shell(context, it) }

    /** 面板引用 id → 单功能面板；不是单功能引用返回 null。 */
    fun resolveRef(
        context: Context,
        settings: ActionSettings,
        panelId: String,
    ): Pair<SinglePanel, GridPanel>? {
        val widgetId = singlePanelWidgetId(panelId) ?: return null
        val single = resolve(settings, widgetId)
        val shell = shell(context, single) ?: return null
        return single to shell
    }
}
