package hunoia.luno.config.repository

import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.model.QuickLauncherGeometry

internal class QuickLauncherRepository(private val stores: SettingsStores) {

    companion object {
        private const val MAX_LAUNCH_HISTORY = 500
    }

    suspend fun updateQuickAppLauncherLayout(layout: QuickLauncherGeometry, landscape: Boolean) {
        stores._quickAppLauncherSettings.updateData { old ->
            // 只改当前朝向那份几何；启动统计/行为开关不动
            if (landscape) old.copy(landscape = layout) else old.copy(portrait = layout)
        }
    }

    suspend fun recordQuickAppLaunch(appKey: String) {
        stores._quickAppLauncherSettings.updateData { old ->
            val newTimeMap = old.recentLaunchTime + (appKey to System.currentTimeMillis())
            val newCountMap = old.launchCount + (appKey to ((old.launchCount[appKey] ?: 0L) + 1L))
            val trimmedTimeMap = if (newTimeMap.size > MAX_LAUNCH_HISTORY) {
                newTimeMap.entries.sortedBy { it.value }.takeLast(MAX_LAUNCH_HISTORY).associate { it.key to it.value }
            } else {
                newTimeMap
            }
            val trimmedCountMap = newCountMap.filterKeys { it in trimmedTimeMap }
            old.copy(
                recentLaunchTime = trimmedTimeMap,
                launchCount = trimmedCountMap,
            )
        }
    }
}
