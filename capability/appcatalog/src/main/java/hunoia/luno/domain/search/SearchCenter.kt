package hunoia.luno.domain.search

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickLauncherGeometry
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.quickaccess.query.SystemAppsVisibility
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 搜索启动域门面（ui 层唯一入口）。 */
object SearchCenter {

    val launcher: Flow<QuickAppLauncherSettings> = ConfigProvider.quickAppLauncherSettings
    val appUsage: Flow<AppUsageStats> = ConfigProvider.appUsageStats
    val search: Flow<QuickSearchSettings> = ConfigProvider.quickSearchSettings
    /** 启动器/搜索面（快速启动面板 / 应用抽屉 / 快速搜索）两类系统包开关；全局开关。 */
    val showSystemApps: Flow<SystemAppsVisibility> =
        ConfigProvider.actionSettings.map {
            SystemAppsVisibility(it.showUILessSystemComponents, it.showNoEntrySystemApps)
        }

    /** 搜索面板展示动作库条目所需的只读投影，避免 quickaccess 直连配置存储。 */
    val actionLibrary: Flow<ActionLibrarySettings> = ConfigProvider.actionLibrarySettings

    suspend fun updateLauncher(transform: suspend (QuickAppLauncherSettings) -> QuickAppLauncherSettings) =
        ConfigProvider.updateQuickAppLauncherSettings(transform)

    suspend fun updateSearch(transform: suspend (QuickSearchSettings) -> QuickSearchSettings) =
        ConfigProvider.updateQuickSearchSettings(transform)

    suspend fun updateLauncherLayout(layout: QuickLauncherGeometry, landscape: Boolean) =
        ConfigProvider.updateQuickAppLauncherLayout(layout, landscape)

    suspend fun getSnapshot(): QuickAppSnapshot = ConfigProvider.getQuickAppSnapshot()
}
