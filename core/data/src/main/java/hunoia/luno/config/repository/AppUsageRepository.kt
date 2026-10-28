package hunoia.luno.config.repository

import hunoia.luno.config.model.AppLaunchSource
import hunoia.luno.config.model.recordLaunch
import hunoia.luno.config.store.SettingsStores

internal class AppUsageRepository(private val stores: SettingsStores) {

    suspend fun recordLaunch(packageName: String, source: AppLaunchSource) {
        if (packageName.isBlank()) return
        stores._appUsageStats.updateData { old ->
            old.recordLaunch(packageName, System.currentTimeMillis(), source)
        }
    }
}
