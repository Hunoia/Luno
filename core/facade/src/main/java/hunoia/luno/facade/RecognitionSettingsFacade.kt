package hunoia.luno.facade

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.RecognitionSettings
import kotlinx.coroutines.flow.Flow

/**
 * 识别器的配置门面：页面只读快照流、只提交变换，不直接触达 `TuneConfig` / DataStore。
 * 实现留在 `:app`，功能模块因此不需要 `:core:data`。
 */
interface RecognitionSettingsFacade {

    /** 动作设置快照；识别器页只消费其中的 `recognition`。 */
    val actionSettings: Flow<ActionSettings>

    /** 原子更新识别器规则表 / 输出配置（单写者：配置存储）。 */
    suspend fun updateRecognition(transform: (RecognitionSettings) -> RecognitionSettings)
}
