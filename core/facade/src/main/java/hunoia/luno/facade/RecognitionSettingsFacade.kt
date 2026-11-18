package hunoia.luno.facade

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.config.model.ScreenPickSettings
import kotlinx.coroutines.flow.Flow

/**
 * 识别器的配置门面：页面只读快照流、只提交变换，不直接触达 `TuneConfig` / DataStore。
 * 实现留在 `:app`，功能模块因此不需要 `:core:data`。
 */
interface RecognitionSettingsFacade {

    /** 动作设置快照；识别器页只消费其中的 `recognition`。 */
    val actionSettings: Flow<ActionSettings>

    /**
     * 识屏自身的参数（二维码 / 松手去向；OCR 文字体系已是 v6 单一模型，不再可选）。
     *
     * 归到识别器门面是因为它们是**输入侧**参数：识别器页要在一处回答
     * 「输入从哪来、怎么取」，而这些设置散在别的页面时用户找不到。
     * 存储仍在 `GestureConfig.initial`（手势域，随备份走），门面只做投影 + 写回。
     */
    val screenPickSettings: Flow<ScreenPickSettings>

    /** 原子更新识别器规则表 / 输出配置（单写者：配置存储）。 */
    suspend fun updateRecognition(transform: (RecognitionSettings) -> RecognitionSettings)

    /** 原子更新识屏参数（二维码 / 松手去向）。 */
    suspend fun updateScreenPick(transform: (ScreenPickSettings) -> ScreenPickSettings)
}
