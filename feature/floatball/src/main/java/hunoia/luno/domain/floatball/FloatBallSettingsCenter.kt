package hunoia.luno.domain.floatball

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.model.ActivityFloatSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ClipFloatSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.WebCardSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** 悬浮球配置门面（Surface 唯一配置入口）。 */
object FloatBallSettingsCenter {

    val actionSettings: Flow<ActionSettings> = ConfigProvider.actionSettings
    val initialSettings: Flow<InitialSettings> = ConfigProvider.initialSettings
    val advancedSettings: Flow<AdvancedSettings> = ConfigProvider.advancedSettings

    /** 动作库条目（动作动效页的第二个 tab）：只读快照，编辑入口在动作库。 */
    fun latestActionLibrarySettings(): ActionLibrarySettings =
        ConfigProvider.latestActionLibrarySettings()

    suspend fun getAdvancedSettings(): AdvancedSettings = ConfigProvider.getAdvancedSettings()

    suspend fun updateActionSettings(
        transform: suspend (ActionSettings) -> ActionSettings,
    ) = ConfigProvider.updateActionSettings(transform)

    /** 剪贴板悬浮窗配置（位置 / 尺寸 / 停靠侧）随剪贴板设置同域存取。 */
    suspend fun getClipFloatSettings(): ClipFloatSettings =
        ConfigProvider.getClipboardSettings().clipFloat

    suspend fun updateClipFloatSettings(settings: ClipFloatSettings) {
        ConfigProvider.updateClipboardSettings { it.copy(clipFloat = settings) }
    }

    /** 当前界面悬浮窗配置随悬浮球设置（`ActionSettings.floatBall.activityFloat`）同域存取。 */
    suspend fun getActivityFloatSettings(): ActivityFloatSettings =
        ConfigProvider.getActionSettings().floatBall.activityFloat

    suspend fun updateActivityFloatSettings(settings: ActivityFloatSettings) {
        ConfigProvider.updateActionSettings {
            it.copy(floatBall = it.floatBall.copy(activityFloat = settings))
        }
    }

    /** 搜索小窗尺寸（横竖屏各一份，存在 `QuickSearchSettings.webCard`）。 */
    suspend fun getWebCardSettings(): WebCardSettings =
        ConfigProvider.quickSearchSettings.first().webCard

    suspend fun updateWebCardSettings(settings: WebCardSettings) {
        ConfigProvider.updateQuickSearchSettings { it.copy(webCard = settings) }
    }
}
