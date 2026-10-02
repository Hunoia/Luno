package hunoia.luno.config.repository

import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.SubGestureSettings
import kotlinx.coroutines.flow.first

internal class SettingsRepository(private val stores: SettingsStores) {

    suspend fun getInitialSettings(): InitialSettings = stores._initialSettings.data.first()
    suspend fun updateInitialSettings(transform: suspend (InitialSettings) -> InitialSettings) {
        stores._initialSettings.updateData(transform)
    }

    suspend fun getAdvancedSettings(): AdvancedSettings = stores._advancedSettings.data.first()
    suspend fun updateAdvancedSettings(transform: suspend (AdvancedSettings) -> AdvancedSettings) {
        stores._advancedSettings.updateData(transform)
    }

    suspend fun getGestureSettings(): GestureSettings = stores._gestureSettings.data.first()
    suspend fun updateGestureSettings(transform: suspend (GestureSettings) -> GestureSettings) {
        stores._gestureSettings.updateData(transform)
    }

    suspend fun getActionSettings(): ActionSettings = stores._actionSettings.data.first()
    suspend fun updateActionSettings(transform: suspend (ActionSettings) -> ActionSettings) {
        stores._actionSettings.updateData(transform)
    }

    suspend fun getGestureButtons(): List<GestureButton> = stores._gestureButtons.data.first()
    suspend fun updateGestureButtons(transform: suspend (List<GestureButton>) -> List<GestureButton>) {
        stores._gestureButtons.updateData(transform)
    }

    suspend fun getQuickAppLauncherSettings(): QuickAppLauncherSettings = stores._quickAppLauncherSettings.data.first()
    suspend fun updateQuickAppLauncherSettings(transform: suspend (QuickAppLauncherSettings) -> QuickAppLauncherSettings) {
        stores._quickAppLauncherSettings.updateData(transform)
    }

    suspend fun getSubGestureSettings(): SubGestureSettings = stores._subGestureSettings.data.first()
    suspend fun updateSubGestureSettings(transform: suspend (SubGestureSettings) -> SubGestureSettings) {
        stores._subGestureSettings.updateData(transform)
    }

    suspend fun getNewActionLibrarySettings(): NewActionLibrarySettings = stores._newActionLibrarySettings.data.first()
    suspend fun updateNewActionLibrarySettings(transform: suspend (NewActionLibrarySettings) -> NewActionLibrarySettings) {
        stores._newActionLibrarySettings.updateData(transform)
    }

    suspend fun getAutomationRules(): List<AutomationRule> = stores._automationRules.data.first()
    suspend fun updateAutomationRules(transform: suspend (List<AutomationRule>) -> List<AutomationRule>) {
        stores._automationRules.updateData(transform)
    }
}
