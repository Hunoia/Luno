package hunoia.luno.domain.tune

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.repository.PanelCleaner
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureSettings
import kotlinx.coroutines.flow.Flow

/** 独立调参域门面（ui 层唯一入口）。 */
object TuneConfig {

    val advanced: Flow<AdvancedSettings> = ConfigProvider.advancedSettings
    val gesture: Flow<GestureSettings> = ConfigProvider.gestureSettings
    val action: Flow<ActionSettings> = ConfigProvider.actionSettings

    suspend fun getGesture(): GestureSettings = ConfigProvider.getGestureSettings()
    suspend fun getAction(): ActionSettings = ConfigProvider.getActionSettings()

    suspend fun updateAdvanced(transform: suspend (AdvancedSettings) -> AdvancedSettings) =
        ConfigProvider.updateAdvancedSettings(transform)

    suspend fun updateGesture(transform: suspend (GestureSettings) -> GestureSettings) =
        ConfigProvider.updateGestureSettings(transform)

    suspend fun updateAction(transform: suspend (ActionSettings) -> ActionSettings) =
        ConfigProvider.updateActionSettings(transform)

    /** 引用该面板的处数（面板项 / 触钮槽 / 子手势槽 / 悬浮球）：删面板前提示用。 */
    suspend fun countPanelReferences(panelId: String): Int =
        PanelCleaner.countPanelReferences(panelId)

    /** 删除引用清理：删面板后清掉各处指向它的动作（含长按变体）。 */
    suspend fun cleanPanelReferences(deletedId: String) =
        PanelCleaner.cleanPanelReferences(deletedId)
}
