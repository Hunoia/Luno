package hunoia.luno.facade

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.LinkDispatchSettings
import kotlinx.coroutines.flow.Flow

/**
 * 链接分发的配置门面：页面只读快照流、只提交变换，不直接触达 `TuneConfig` / DataStore。
 * 实现留在 `:app`，`:feature:link` 因此不再需要 `:core:data`。
 */
interface LinkSettingsFacade {

    /** 动作设置快照；链接页只消费其中的 `linkDispatch`。 */
    val actionSettings: Flow<ActionSettings>

    /** 原子更新链接规则表（单写者：配置存储）。 */
    suspend fun updateLinkDispatch(transform: (LinkDispatchSettings) -> LinkDispatchSettings)
}
