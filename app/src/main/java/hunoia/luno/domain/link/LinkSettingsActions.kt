package hunoia.luno.domain.link

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.LinkDispatchSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.facade.LinkSettingsFacade
import kotlinx.coroutines.flow.Flow

/** 链接配置门面的实现：唯一写入口仍是 `TuneConfig` / 配置存储。 */
object LinkSettingsActions : LinkSettingsFacade {

    override val actionSettings: Flow<ActionSettings> = TuneConfig.action

    override suspend fun updateLinkDispatch(
        transform: (LinkDispatchSettings) -> LinkDispatchSettings,
    ) {
        TuneConfig.updateAction { it.copy(linkDispatch = transform(it.linkDispatch)) }
    }
}
