package hunoia.luno.config

import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.config.backup.ConfigBackupRepository
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.repository.QuickLauncherRepository
import hunoia.luno.config.store.SettingsStores
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

object ConfigProvider {

    private val stores by lazy { SettingsStores.create() }
    private val quickLauncherRepository by lazy { QuickLauncherRepository(stores) }
    private val configBackupRepository by lazy { ConfigBackupRepository(stores) }

    val initialSettings: Flow<InitialSettings> = stores.initialSettings
    val advancedSettings: Flow<AdvancedSettings> = stores.advancedSettings
    val gestureSettings: Flow<GestureSettings> = stores.gestureSettings
    val actionSettings: Flow<ActionSettings> = stores.actionSettings
    val gestureButtons: Flow<List<GestureButton>> = stores.gestureButtons
    val quickAppLauncherSettings: Flow<QuickAppLauncherSettings> = stores.quickAppLauncherSettings
    val subGestureSettings: Flow<SubGestureSettings> = stores.subGestureSettings
    val newActionLibrarySettings: Flow<NewActionLibrarySettings> = stores.newActionLibrarySettings
    val automationRules: Flow<List<AutomationRule>> = stores.automationRules

    suspend fun getInitialSettings(): InitialSettings = stores._initialSettings.data.first()
    suspend fun getAdvancedSettings(): AdvancedSettings = stores._advancedSettings.data.first()
    suspend fun getGestureSettings(): GestureSettings = stores._gestureSettings.data.first()
    suspend fun getActionSettings(): ActionSettings = stores._actionSettings.data.first()
    suspend fun getGestureButtons(): List<GestureButton> = stores._gestureButtons.data.first()
    suspend fun getQuickAppLauncherSettings(): QuickAppLauncherSettings = stores._quickAppLauncherSettings.data.first()
    suspend fun getSubGestureSettings(): SubGestureSettings = stores._subGestureSettings.data.first()
    suspend fun getNewActionLibrarySettings(): NewActionLibrarySettings = stores._newActionLibrarySettings.data.first()
    suspend fun getAutomationRules(): List<AutomationRule> = stores._automationRules.data.first()

    suspend fun updateInitialSettings(transform: suspend (InitialSettings) -> InitialSettings) {
        stores._initialSettings.updateData(transform)
    }
    suspend fun updateAdvancedSettings(transform: suspend (AdvancedSettings) -> AdvancedSettings) {
        stores._advancedSettings.updateData(transform)
    }
    suspend fun updateGestureSettings(transform: suspend (GestureSettings) -> GestureSettings) {
        stores._gestureSettings.updateData(transform)
    }
    suspend fun updateActionSettings(transform: suspend (ActionSettings) -> ActionSettings) {
        stores._actionSettings.updateData(transform)
    }
    suspend fun updateGestureButtons(transform: suspend (List<GestureButton>) -> List<GestureButton>) {
        stores._gestureButtons.updateData(transform)
    }
    suspend fun updateQuickAppLauncherSettings(transform: suspend (QuickAppLauncherSettings) -> QuickAppLauncherSettings) {
        stores._quickAppLauncherSettings.updateData(transform)
    }
    suspend fun updateSubGestureSettings(transform: suspend (SubGestureSettings) -> SubGestureSettings) {
        stores._subGestureSettings.updateData(transform)
    }
    suspend fun updateNewActionLibrarySettings(transform: suspend (NewActionLibrarySettings) -> NewActionLibrarySettings) {
        stores._newActionLibrarySettings.updateData(transform)
    }

    suspend fun updateAutomationRules(transform: suspend (List<AutomationRule>) -> List<AutomationRule>) {
        stores._automationRules.updateData(transform)
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

    suspend fun snapshotAll(): Backup = configBackupRepository.snapshotAll()
    suspend fun restoreAll(backup: Backup) = configBackupRepository.restoreAll(backup)
    suspend fun resetAll() = configBackupRepository.resetAll()
}
