package hunoia.luno.config.repository

import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.model.QuickLauncherGeometry

internal class QuickLauncherRepository(private val stores: SettingsStores) {

    suspend fun updateQuickAppLauncherLayout(layout: QuickLauncherGeometry, landscape: Boolean) {
        stores._quickAppLauncherSettings.updateData { old ->
            // 只改当前朝向那份几何；行为开关不动
            if (landscape) old.copy(landscape = layout) else old.copy(portrait = layout)
        }
    }
}
