package hunoia.luno.domain.floatball

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ClipFloatSettings
import hunoia.luno.config.model.InitialSettings
import kotlinx.coroutines.flow.Flow

/** 悬浮球配置门面（Surface 唯一配置入口）。 */
object FloatBallSettingsCenter {

    val actionSettings: Flow<ActionSettings> = ConfigProvider.actionSettings
    val initialSettings: Flow<InitialSettings> = ConfigProvider.initialSettings
    val advancedSettings: Flow<AdvancedSettings> = ConfigProvider.advancedSettings

    suspend fun getAdvancedSettings(): AdvancedSettings = ConfigProvider.getAdvancedSettings()

    suspend fun updateActionSettings(
        transform: suspend (ActionSettings) -> ActionSettings,
    ) = ConfigProvider.updateActionSettings(transform)

    /** 剪贴板悬浮窗配置（位置 / 缩放 / 透明度）随剪贴板设置同域存取。 */
    suspend fun getClipFloatSettings(): ClipFloatSettings =
        ConfigProvider.getClipboardSettings().clipFloat

    suspend fun updateClipFloatSettings(settings: ClipFloatSettings) {
        ConfigProvider.updateClipboardSettings { it.copy(clipFloat = settings) }
    }
}
