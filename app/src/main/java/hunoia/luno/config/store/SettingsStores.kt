package hunoia.luno.config.store

import androidx.datastore.core.DataStore
import hunoia.luno.action.model.ActionLibrarySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.withUniqueEngineTriggers
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.core.AppContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsStores(
    internal val _initialSettings: DataStore<InitialSettings>,
    internal val _advancedSettings: DataStore<AdvancedSettings>,
    internal val _gestureSettings: DataStore<GestureSettings>,
    internal val _actionSettings: DataStore<ActionSettings>,
    internal val _gestureButtons: DataStore<List<GestureButton>>,
    internal val _quickAppLauncherSettings: DataStore<QuickAppLauncherSettings>,
    internal val _subGestureSettings: DataStore<SubGestureSettings>,
    internal val _actionLibrarySettings: DataStore<ActionLibrarySettings>,
    internal val _automationRules: DataStore<List<AutomationRule>>,
    internal val _automationLog: DataStore<List<hunoia.luno.runtime.automation.AutomationEvent>>,
    internal val _quickAppSnapshot: DataStore<QuickAppSnapshot>,
    internal val _quickSearchSettings: DataStore<QuickSearchSettings>,
    internal val _appUsageStats: DataStore<AppUsageStats>,
    internal val _clipboardHistory: DataStore<ClipboardHistory>,
) {
    val initialSettings: Flow<InitialSettings> = _initialSettings.data
    val advancedSettings: Flow<AdvancedSettings> = _advancedSettings.data
    val gestureSettings: Flow<GestureSettings> = _gestureSettings.data
    val actionSettings: Flow<ActionSettings> = _actionSettings.data
    val gestureButtons: Flow<List<GestureButton>> = _gestureButtons.data
    val quickAppLauncherSettings: Flow<QuickAppLauncherSettings> = _quickAppLauncherSettings.data
    val subGestureSettings: Flow<SubGestureSettings> = _subGestureSettings.data
    val actionLibrarySettings: Flow<ActionLibrarySettings> = _actionLibrarySettings.data
    val automationRules: Flow<List<AutomationRule>> = _automationRules.data
    val automationLog: Flow<List<hunoia.luno.runtime.automation.AutomationEvent>> = _automationLog.data
    val quickSearchSettings: Flow<QuickSearchSettings> =
        _quickSearchSettings.data.map { it.withUniqueEngineTriggers() }
    val appUsageStats: Flow<AppUsageStats> = _appUsageStats.data
    val clipboardHistory: Flow<ClipboardHistory> = _clipboardHistory.data

    /** 只读不订阅：面板首开同步取一次就够，不需要一条常驻的 flow。 */
    suspend fun readQuickAppSnapshot(): QuickAppSnapshot = _quickAppSnapshot.data.first()

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
                _actionLibrarySettings = ctx.dataStore(DataStoreFiles.ACTION_LIBRARY_SETTINGS, ActionLibrarySettings()),
                _automationRules = ctx.dataStore(DataStoreFiles.AUTOMATION_RULES, AutomationRule.Defaults),
                _automationLog = ctx.dataStore(DataStoreFiles.AUTOMATION_LOG, emptyList()),
                _quickAppSnapshot = ctx.dataStore(DataStoreFiles.QUICK_APP_SNAPSHOT, QuickAppSnapshot()),
                _quickSearchSettings = ctx.dataStore(DataStoreFiles.QUICK_SEARCH_SETTINGS, QuickSearchSettings()),
                _appUsageStats = ctx.dataStore(DataStoreFiles.APP_USAGE_STATS, AppUsageStats()),
                _clipboardHistory = ctx.dataStore(DataStoreFiles.CLIPBOARD_HISTORY, ClipboardHistory()),
            )
        }
    }
}
