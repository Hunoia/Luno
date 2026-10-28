package hunoia.luno.domain.floatball

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.InitialSettings
import kotlinx.coroutines.flow.Flow

/** 悬浮球配置门面（Surface 唯一配置入口）。 */
object FloatBallSettingsCenter {

    val actionSettings: Flow<ActionSettings> = ConfigProvider.actionSettings
    val initialSettings: Flow<InitialSettings> = ConfigProvider.initialSettings

    suspend fun updateActionSettings(
        transform: suspend (ActionSettings) -> ActionSettings,
    ) = ConfigProvider.updateActionSettings(transform)
}
