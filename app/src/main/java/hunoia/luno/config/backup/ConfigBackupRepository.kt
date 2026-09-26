package hunoia.luno.config.backup

import hunoia.luno.BuildConfig
import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.ActionLibrarySettings
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.SubGestureSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class ConfigBackupRepository(private val stores: SettingsStores) {

    suspend fun snapshotAll(): Backup = coroutineScope {
        val initialDeferred = async { stores._initialSettings.data.first() }
        val advancedDeferred = async { stores._advancedSettings.data.first() }
        val gestureDeferred = async { stores._gestureSettings.data.first() }
        val actionDeferred = async { stores._actionSettings.data.first() }
        val buttonsDeferred = async { stores._gestureButtons.data.first() }
        val qlaDeferred = async { stores._quickAppLauncherSettings.data.first() }
        val subGestureDeferred = async { stores._subGestureSettings.data.first() }
        val actionLibraryDeferred = async { stores._actionLibrarySettings.data.first() }
        Backup(
            initialSettings = initialDeferred.await(),
            advancedSettings = advancedDeferred.await(),
            gestureSettings = gestureDeferred.await(),
            actionSettings = actionDeferred.await(),
            gestureButtons = buttonsDeferred.await(),
            quickAppLauncherSettings = qlaDeferred.await(),
            subGestureSettings = subGestureDeferred.await(),
            actionLibrarySettings = actionLibraryDeferred.await(),
            timestamp = System.currentTimeMillis(),
            version = BuildConfig.VERSION_NAME,
        )
    }

    suspend fun restoreAll(backup: Backup) = coroutineScope {
        launch { backup.initialSettings?.let { v -> writeSection("initialSettings") { stores._initialSettings.updateData { v } } } }
        launch { backup.advancedSettings?.let { v -> writeSection("advancedSettings") { stores._advancedSettings.updateData { v } } } }
        launch { backup.gestureSettings?.let { v -> writeSection("gestureSettings") { stores._gestureSettings.updateData { v } } } }
        launch { backup.actionSettings?.let { v -> writeSection("actionSettings") { stores._actionSettings.updateData { v } } } }
        launch { backup.gestureButtons?.let { v -> writeSection("gestureButtons") { stores._gestureButtons.updateData { v } } } }
        launch { backup.quickAppLauncherSettings?.let { v -> writeSection("quickAppLauncherSettings") { stores._quickAppLauncherSettings.updateData { v } } } }
        launch { backup.subGestureSettings?.let { v -> writeSection("subGestureSettings") { stores._subGestureSettings.updateData { v } } } }
        launch { backup.actionLibrarySettings?.let { v -> writeSection("actionLibrarySettings") { stores._actionLibrarySettings.updateData { v } } } }
    }

    private suspend fun writeSection(section: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            throw IllegalStateException("restore failed: $section write failed: ${e::class.simpleName} ${e.message}", e)
        }
    }

    suspend fun resetAll() = coroutineScope {
        launch { stores._initialSettings.updateData { InitialSettings() } }
        launch { stores._advancedSettings.updateData { AdvancedSettings() } }
        launch { stores._gestureSettings.updateData { GestureSettings() } }
        launch { stores._actionSettings.updateData { ActionSettings() } }
        launch { stores._gestureButtons.updateData { GestureButton.Defaults } }
        launch { stores._quickAppLauncherSettings.updateData { QuickAppLauncherSettings() } }
        launch { stores._subGestureSettings.updateData { SubGestureSettings() } }
        launch { stores._actionLibrarySettings.updateData { ActionLibrarySettings() } }
    }
}
