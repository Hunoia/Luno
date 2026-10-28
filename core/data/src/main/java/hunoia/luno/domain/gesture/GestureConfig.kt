package hunoia.luno.domain.gesture

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.repository.SubGestureCleaner
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.SubGestureSettings
import kotlinx.coroutines.flow.Flow

/**
 * 手势配置域门面（ui 层唯一入口）。
 * 方向：ui → domain → config，ui 不得直调 config。
 */
object GestureConfig {

    val initial: Flow<InitialSettings> = ConfigProvider.initialSettings
    val buttons: Flow<List<GestureButton>> = ConfigProvider.gestureButtons
    val subGestures: Flow<SubGestureSettings> = ConfigProvider.subGestureSettings

    suspend fun getButtons(): List<GestureButton> = ConfigProvider.getGestureButtons()
    suspend fun getSubGestures(): SubGestureSettings = ConfigProvider.getSubGestureSettings()

    suspend fun updateInitial(transform: suspend (InitialSettings) -> InitialSettings) =
        ConfigProvider.updateInitialSettings(transform)

    suspend fun updateButtons(transform: suspend (List<GestureButton>) -> List<GestureButton>) =
        ConfigProvider.updateGestureButtons(transform)

    suspend fun updateSubGestures(transform: suspend (SubGestureSettings) -> SubGestureSettings) =
        ConfigProvider.updateSubGestureSettings(transform)

    /** 删除引用清理：删子手势后清掉按钮/子手势里指向它的动作。 */
    suspend fun cleanSubGestureReferences(deletedId: String) =
        SubGestureCleaner.cleanSubGestureReferences(deletedId)
}
