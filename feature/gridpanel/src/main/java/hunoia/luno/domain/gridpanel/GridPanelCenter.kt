package hunoia.luno.domain.gridpanel

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.SinglePanel
import kotlinx.coroutines.flow.Flow

/** 面板域门面（ui 层唯一入口）：网格 / 单功能共用一个配置域。 */
object GridPanelCenter {

    val actionSettings: Flow<ActionSettings> = ConfigProvider.actionSettings

    suspend fun updateActionSettings(
        transform: suspend (ActionSettings) -> ActionSettings,
    ) = ConfigProvider.updateActionSettings(transform)

    /** 单功能面板配置更新：一功能一块，缺省时从默认几何起步。 */
    suspend fun updateSinglePanel(
        widgetId: String,
        transform: (SinglePanel) -> SinglePanel,
    ) = ConfigProvider.updateActionSettings { settings ->
        val current = settings.singlePanels.panelByWidgetId(widgetId) ?: SinglePanel(widgetId)
        settings.copy(singlePanels = settings.singlePanels.withPanel(transform(current)))
    }
}
