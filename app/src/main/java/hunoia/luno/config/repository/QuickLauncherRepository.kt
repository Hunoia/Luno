package hunoia.luno.config.repository

import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.model.QuickAppLauncherSettings

internal class QuickLauncherRepository(private val stores: SettingsStores) {

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
            old.copy(
                recentLaunchTime = old.recentLaunchTime + (appKey to System.currentTimeMillis()),
                launchCount = old.launchCount + (appKey to ((old.launchCount[appKey] ?: 0L) + 1L))
            )
        }
    }
}
