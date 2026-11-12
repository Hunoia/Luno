package hunoia.luno.domain.gridpanel

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import kotlinx.coroutines.flow.Flow

/** 面板域门面（ui 层唯一入口）：网格 / 单功能共用一个配置域。 */
object GridPanelCenter {

    val actionSettings: Flow<ActionSettings> = ConfigProvider.actionSettings

    suspend fun updateActionSettings(
        transform: suspend (ActionSettings) -> ActionSettings,
    ) = ConfigProvider.updateActionSettings(transform)
}
