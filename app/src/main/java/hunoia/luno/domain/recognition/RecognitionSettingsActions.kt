package hunoia.luno.domain.recognition

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.facade.RecognitionSettingsFacade
import kotlinx.coroutines.flow.Flow

/** 识别器配置门面的实现：唯一写入口仍是 `TuneConfig` / 配置存储。 */
object RecognitionSettingsActions : RecognitionSettingsFacade {

    override val actionSettings: Flow<ActionSettings> = TuneConfig.action

    override suspend fun updateRecognition(
        transform: (RecognitionSettings) -> RecognitionSettings,
    ) {
        TuneConfig.updateAction { it.copy(recognition = transform(it.recognition)) }
    }
}
