package hunoia.luno.config.store

import androidx.datastore.core.DataStore
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.core.AppContext
import kotlinx.coroutines.flow.Flow

class SettingsStores(
    internal val _initialSettings: DataStore<InitialSettings>,
    internal val _advancedSettings: DataStore<AdvancedSettings>,
    internal val _gestureSettings: DataStore<GestureSettings>,
    internal val _actionSettings: DataStore<ActionSettings>,
    internal val _gestureButtons: DataStore<List<GestureButton>>,
    internal val _quickAppLauncherSettings: DataStore<QuickAppLauncherSettings>,
    internal val _subGestureSettings: DataStore<SubGestureSettings>,
    internal val _newActionLibrarySettings: DataStore<NewActionLibrarySettings>,
) {
    val initialSettings: Flow<InitialSettings> = _initialSettings.data
    val advancedSettings: Flow<AdvancedSettings> = _advancedSettings.data
    val gestureSettings: Flow<GestureSettings> = _gestureSettings.data
    val actionSettings: Flow<ActionSettings> = _actionSettings.data
    val gestureButtons: Flow<List<GestureButton>> = _gestureButtons.data
    val quickAppLauncherSettings: Flow<QuickAppLauncherSettings> = _quickAppLauncherSettings.data
    val subGestureSettings: Flow<SubGestureSettings> = _subGestureSettings.data
    val newActionLibrarySettings: Flow<NewActionLibrarySettings> = _newActionLibrarySettings.data

    companion object {
        fun create(): SettingsStores {
            val ctx = AppContext.get()
            return SettingsStores(
                _initialSettings = ctx.dataStore(DataStoreFiles.INITIAL_SETTINGS, InitialSettings()),
                _advancedSettings = ctx.dataStore(DataStoreFiles.ADVANCED_SETTINGS, AdvancedSettings()),
                _gestureSettings = ctx.dataStore(DataStoreFiles.GESTURE_SETTINGS, GestureSettings()),
                _actionSettings = ctx.dataStore(DataStoreFiles.ACTION_SETTINGS, ActionSettings()),
                _gestureButtons = ctx.dataStore(DataStoreFiles.GESTURE_BUTTONS, GestureButton.Defaults),
                _quickAppLauncherSettings = ctx.dataStore(DataStoreFiles.QUICK_APP_LAUNCHER, QuickAppLauncherSettings()),
                _subGestureSettings = ctx.dataStore(DataStoreFiles.SUB_GESTURE_SETTINGS, SubGestureSettings()),
                _newActionLibrarySettings = ctx.dataStore(DataStoreFiles.NEW_ACTION_LIBRARY_SETTINGS, NewActionLibrarySettings()),
            )
        }
    }
}
