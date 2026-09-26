package hunoia.luno.config.repository

import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.ActionLibrarySettings
import hunoia.luno.config.model.FrozenAppSettings
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

    suspend fun getFrozenAppSettings(): FrozenAppSettings = stores._frozenAppSettings.data.first()
    suspend fun updateFrozenAppSettings(transform: suspend (FrozenAppSettings) -> FrozenAppSettings) {
        stores._frozenAppSettings.updateData(transform)
    }

    suspend fun getSubGestureSettings(): SubGestureSettings = stores._subGestureSettings.data.first()
    suspend fun updateSubGestureSettings(transform: suspend (SubGestureSettings) -> SubGestureSettings) {
        stores._subGestureSettings.updateData(transform)
    }

    suspend fun getActionLibrarySettings(): ActionLibrarySettings = stores._actionLibrarySettings.data.first()
    suspend fun updateActionLibrarySettings(transform: suspend (ActionLibrarySettings) -> ActionLibrarySettings) {
        stores._actionLibrarySettings.updateData(transform)
    }
}
