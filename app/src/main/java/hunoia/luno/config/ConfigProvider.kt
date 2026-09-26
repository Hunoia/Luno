package hunoia.luno.config

import hunoia.luno.config.backup.ConfigBackupRepository
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.ActionLibrarySettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.FrozenAppSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.repository.ActionLibraryRepository
import hunoia.luno.config.repository.QuickLauncherRepository
import hunoia.luno.config.repository.SettingsRepository
import hunoia.luno.config.store.SettingsStores
import kotlinx.coroutines.flow.Flow

object ConfigProvider {

    private val stores by lazy { SettingsStores.create() }
    private val settingsRepository by lazy { SettingsRepository(stores) }
    private val quickLauncherRepository by lazy { QuickLauncherRepository(stores) }
    private val actionLibraryRepository by lazy { ActionLibraryRepository(stores) }
    private val configBackupRepository by lazy { ConfigBackupRepository(stores) }

    val initialSettings: Flow<InitialSettings> = stores.initialSettings
    val advancedSettings: Flow<AdvancedSettings> = stores.advancedSettings
    val gestureSettings: Flow<GestureSettings> = stores.gestureSettings
    val actionSettings: Flow<ActionSettings> = stores.actionSettings
    val gestureButtons: Flow<List<GestureButton>> = stores.gestureButtons
    val quickAppLauncherSettings: Flow<QuickAppLauncherSettings> = stores.quickAppLauncherSettings
    val frozenAppSettings: Flow<FrozenAppSettings> = stores.frozenAppSettings
    val subGestureSettings: Flow<SubGestureSettings> = stores.subGestureSettings
    val actionLibrarySettings: Flow<ActionLibrarySettings> = stores.actionLibrarySettings

    suspend fun getInitialSettings(): InitialSettings = settingsRepository.getInitialSettings()
    suspend fun getAdvancedSettings(): AdvancedSettings = settingsRepository.getAdvancedSettings()
    suspend fun getGestureSettings(): GestureSettings = settingsRepository.getGestureSettings()
    suspend fun getActionSettings(): ActionSettings = settingsRepository.getActionSettings()
    suspend fun getGestureButtons(): List<GestureButton> = settingsRepository.getGestureButtons()
    suspend fun getQuickAppLauncherSettings(): QuickAppLauncherSettings = settingsRepository.getQuickAppLauncherSettings()
    suspend fun getFrozenAppSettings(): FrozenAppSettings = settingsRepository.getFrozenAppSettings()
    suspend fun getSubGestureSettings(): SubGestureSettings = settingsRepository.getSubGestureSettings()
    suspend fun getActionLibrarySettings(): ActionLibrarySettings = settingsRepository.getActionLibrarySettings()

    suspend fun updateInitialSettings(transform: suspend (InitialSettings) -> InitialSettings) {
        settingsRepository.updateInitialSettings(transform)
    }
    suspend fun updateAdvancedSettings(transform: suspend (AdvancedSettings) -> AdvancedSettings) {
        settingsRepository.updateAdvancedSettings(transform)
    }
    suspend fun updateGestureSettings(transform: suspend (GestureSettings) -> GestureSettings) {
        settingsRepository.updateGestureSettings(transform)
    }
    suspend fun updateActionSettings(transform: suspend (ActionSettings) -> ActionSettings) {
        settingsRepository.updateActionSettings(transform)
    }
    suspend fun updateGestureButtons(transform: suspend (List<GestureButton>) -> List<GestureButton>) {
        settingsRepository.updateGestureButtons(transform)
    }
    suspend fun updateQuickAppLauncherSettings(transform: suspend (QuickAppLauncherSettings) -> QuickAppLauncherSettings) {
        settingsRepository.updateQuickAppLauncherSettings(transform)
    }
    suspend fun updateFrozenAppSettings(transform: suspend (FrozenAppSettings) -> FrozenAppSettings) {
        settingsRepository.updateFrozenAppSettings(transform)
    }
    suspend fun updateSubGestureSettings(transform: suspend (SubGestureSettings) -> SubGestureSettings) {
        settingsRepository.updateSubGestureSettings(transform)
    }
    suspend fun updateActionLibrarySettings(transform: suspend (ActionLibrarySettings) -> ActionLibrarySettings) {
        settingsRepository.updateActionLibrarySettings(transform)
    }

    suspend fun updateQuickAppLauncherLayout(layout: QuickAppLauncherSettings) {
        quickLauncherRepository.updateQuickAppLauncherLayout(layout)
    }
    suspend fun resetQuickAppLauncherLayout() {
        quickLauncherRepository.resetQuickAppLauncherLayout()
    }
    suspend fun recordQuickAppLaunch(appKey: String) {
        quickLauncherRepository.recordQuickAppLaunch(appKey)
    }

    suspend fun removeActionLibraryEntry(entryId: String) {
        actionLibraryRepository.removeActionLibraryEntry(entryId)
    }

    suspend fun snapshotAll(): Backup = configBackupRepository.snapshotAll()
    suspend fun restoreAll(backup: Backup) = configBackupRepository.restoreAll(backup)
    suspend fun resetAll() = configBackupRepository.resetAll()
}
