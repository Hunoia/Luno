package hunoia.luno.domain.recognition

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.config.model.ScreenPickSettings
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.facade.RecognitionSettingsFacade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 识别器配置门面的实现：唯一写入口仍是 `TuneConfig` / 配置存储。 */
object RecognitionSettingsActions : RecognitionSettingsFacade {

    override val actionSettings: Flow<ActionSettings> = TuneConfig.action

    /** 识屏参数仍存在手势域（`InitialSettings.screenPick`），这里只投影出来给识别器页。 */
    override val screenPickSettings: Flow<ScreenPickSettings> =
        GestureConfig.initial.map { it.screenPick }

    override suspend fun updateRecognition(
        transform: (RecognitionSettings) -> RecognitionSettings,
    ) {
        TuneConfig.updateAction { it.copy(recognition = transform(it.recognition)) }
    }

    override suspend fun updateScreenPick(
        transform: (ScreenPickSettings) -> ScreenPickSettings,
    ) {
        GestureConfig.updateInitial { it.copy(screenPick = transform(it.screenPick)) }
    }
}
