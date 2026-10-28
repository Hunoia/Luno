package hunoia.luno.domain.search

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickLauncherGeometry
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.core.domain.ActionLibrarySettings
import kotlinx.coroutines.flow.Flow

/** 搜索启动域门面（ui 层唯一入口）。 */
object SearchCenter {

    val launcher: Flow<QuickAppLauncherSettings> = ConfigProvider.quickAppLauncherSettings
    val appUsage: Flow<AppUsageStats> = ConfigProvider.appUsageStats
    val search: Flow<QuickSearchSettings> = ConfigProvider.quickSearchSettings

    /** 搜索面板展示动作库条目所需的只读投影，避免 quicklaunch 直连配置存储。 */
    val actionLibrary: Flow<ActionLibrarySettings> = ConfigProvider.actionLibrarySettings

    suspend fun getSearch(): QuickSearchSettings = ConfigProvider.getQuickSearchSettings()

    suspend fun updateLauncher(transform: suspend (QuickAppLauncherSettings) -> QuickAppLauncherSettings) =
        ConfigProvider.updateQuickAppLauncherSettings(transform)

    suspend fun updateSearch(transform: suspend (QuickSearchSettings) -> QuickSearchSettings) =
        ConfigProvider.updateQuickSearchSettings(transform)

    suspend fun updateLauncherLayout(layout: QuickLauncherGeometry, landscape: Boolean) =
        ConfigProvider.updateQuickAppLauncherLayout(layout, landscape)

    suspend fun getSnapshot(): QuickAppSnapshot = ConfigProvider.getQuickAppSnapshot()
}
