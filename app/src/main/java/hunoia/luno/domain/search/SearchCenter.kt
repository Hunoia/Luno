package hunoia.luno.domain.search

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.config.model.QuickSearchSettings
import kotlinx.coroutines.flow.Flow

/** 搜索启动域门面（ui 层唯一入口）。 */
object SearchCenter {

    val launcher: Flow<QuickAppLauncherSettings> = ConfigProvider.quickAppLauncherSettings
    val search: Flow<QuickSearchSettings> = ConfigProvider.quickSearchSettings

    suspend fun updateLauncher(transform: suspend (QuickAppLauncherSettings) -> QuickAppLauncherSettings) =
        ConfigProvider.updateQuickAppLauncherSettings(transform)

    suspend fun updateSearch(transform: suspend (QuickSearchSettings) -> QuickSearchSettings) =
        ConfigProvider.updateQuickSearchSettings(transform)

    suspend fun updateLauncherLayout(layout: QuickAppLauncherSettings) =
        ConfigProvider.updateQuickAppLauncherLayout(layout)

    suspend fun getSnapshot(): QuickAppSnapshot = ConfigProvider.getQuickAppSnapshot()
}
