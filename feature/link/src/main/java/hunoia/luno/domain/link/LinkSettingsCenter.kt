package hunoia.luno.domain.link

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.LinkDispatchSettings

/** 链接分发域门面（ui 层唯一入口）。 */
object LinkSettingsCenter {

    suspend fun linkDispatch(): LinkDispatchSettings =
        runCatching { ConfigProvider.getActionSettings().linkDispatch }
            .getOrDefault(LinkDispatchSettings())
}
