package hunoia.luno.config

import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.config.backup.ConfigBackupRepository
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.config.model.QuickSearchSettings
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
    val automationLog: Flow<List<hunoia.luno.runtime.automation.AutomationEvent>> = stores.automationLog
    val quickSearchSettings: Flow<QuickSearchSettings> = stores.quickSearchSettings

    suspend fun getInitialSettings(): InitialSettings = stores._initialSettings.data.first()
    suspend fun getAdvancedSettings(): AdvancedSettings = stores._advancedSettings.data.first()
    suspend fun getGestureSettings(): GestureSettings = stores._gestureSettings.data.first()
    suspend fun getActionSettings(): ActionSettings = stores._actionSettings.data.first()
    suspend fun getGestureButtons(): List<GestureButton> = stores._gestureButtons.data.first()
    suspend fun getQuickAppLauncherSettings(): QuickAppLauncherSettings = stores._quickAppLauncherSettings.data.first()
    suspend fun getSubGestureSettings(): SubGestureSettings = stores._subGestureSettings.data.first()
    suspend fun getNewActionLibrarySettings(): NewActionLibrarySettings = stores._newActionLibrarySettings.data.first()
    suspend fun getAutomationRules(): List<AutomationRule> = stores._automationRules.data.first()
    suspend fun getQuickSearchSettings(): QuickSearchSettings = stores._quickSearchSettings.data.first()

    suspend fun updateInitialSettings(transform: suspend (InitialSettings) -> InitialSettings) {
        stores._initialSettings.updateData(transform)
    }
    suspend fun updateAdvancedSettings(transform: suspend (AdvancedSettings) -> AdvancedSettings) {
        stores._advancedSettings.updateData(transform)
    }
    suspend fun updateActionSettings(transform: suspend (ActionSettings) -> ActionSettings) {
        stores._actionSettings.updateData(transform)
    }
    suspend fun updateGestureSettings(transform: suspend (GestureSettings) -> GestureSettings) {
        stores._gestureSettings.updateData(transform)
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

    /** 执行历史追加：全局环形 100，写盘失败吞掉（调试数据不影响规则执行）。 */
    suspend fun appendAutomationEvent(event: hunoia.luno.runtime.automation.AutomationEvent) {
        appendAutomationEvents(listOf(event))
    }

    /**
     * 批量追加：一次 updateData 写入整批。原来「每条事件一次全量重写」在规则竞争时
     * 会变成 N 次序列化 + N 次写盘，这里是写放大的主要来源。
     */
    suspend fun appendAutomationEvents(events: List<hunoia.luno.runtime.automation.AutomationEvent>) {
        if (events.isEmpty()) return
        runCatching {
            stores._automationLog.updateData { old ->
                (old + events).takeLast(hunoia.luno.runtime.automation.ExecutionLog.MAX_EVENTS)
            }
        }
    }

    suspend fun updateQuickSearchSettings(transform: suspend (QuickSearchSettings) -> QuickSearchSettings) {
        stores._quickSearchSettings.updateData(transform)
    }

    suspend fun updateQuickAppLauncherLayout(layout: QuickAppLauncherSettings) {
        quickLauncherRepository.updateQuickAppLauncherLayout(layout)
    }
    suspend fun recordQuickAppLaunch(appKey: String) {
        quickLauncherRepository.recordQuickAppLaunch(appKey)
    }

    /** 面板首开同步读一次快照。 */
    suspend fun getQuickAppSnapshot(): QuickAppSnapshot = stores.readQuickAppSnapshot()

    /**
     * 预热链扫完列表后回写快照。只写派生的列表数据，不含任何用户配置。
     *
     * 内容没变就不写：启动预热每次都重扫一遍包列表，绝大多数时候结果与上次完全一致，
     * 却是「全量序列化 + 全量写盘」。DataStore 首次读之后数据在内存里，比一次写便宜得多。
     */
    suspend fun writeQuickAppSnapshot(snapshot: QuickAppSnapshot) {
        val current = runCatching { stores.readQuickAppSnapshot() }.getOrNull()
        if (current == snapshot) return
        stores._quickAppSnapshot.updateData { snapshot }
    }

    suspend fun snapshotAll(): Backup = configBackupRepository.snapshotAll()
    suspend fun restoreAll(backup: Backup) = configBackupRepository.restoreAll(backup)
    suspend fun resetAll() = configBackupRepository.resetAll()
    suspend fun snapshotDomain(domain: BackupDomain): Backup = configBackupRepository.snapshotDomain(domain)
    suspend fun restoreDomain(backup: Backup) = configBackupRepository.restoreDomain(backup)
}
