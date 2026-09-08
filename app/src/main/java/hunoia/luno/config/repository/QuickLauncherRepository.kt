package hunoia.luno.config.repository

import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.model.QuickAppLauncherSettings

internal class QuickLauncherRepository(private val stores: SettingsStores) {

    companion object {
        private const val MAX_LAUNCH_HISTORY = 500
    }

    suspend fun updateQuickAppLauncherLayout(layout: QuickAppLauncherSettings) {
        stores._quickAppLauncherSettings.updateData { old ->
            old.copy(
                panelHeightFraction = layout.panelHeightFraction,
                contentHeightFraction = layout.contentHeightFraction,
                candidateRows = layout.candidateRows,
                panelWidthFraction = layout.panelWidthFraction,
                panelHorizontalBias = layout.panelHorizontalBias,
                gridColumns = layout.gridColumns,
                keyHeightDp = layout.keyHeightDp,
            )
        }
    }

    suspend fun resetQuickAppLauncherLayout() {
        stores._quickAppLauncherSettings.updateData { old ->
            old.copy(
                panelHeightFraction = QuickAppLauncherSettings().panelHeightFraction,
                contentHeightFraction = QuickAppLauncherSettings().contentHeightFraction,
                candidateRows = QuickAppLauncherSettings().candidateRows,
                panelWidthFraction = QuickAppLauncherSettings().panelWidthFraction,
                panelHorizontalBias = QuickAppLauncherSettings().panelHorizontalBias,
                gridColumns = QuickAppLauncherSettings().gridColumns,
                keyHeightDp = QuickAppLauncherSettings().keyHeightDp,
            )
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
