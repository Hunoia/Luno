package hunoia.luno.config

import hunoia.luno.action.model.ActionLibrarySettings
import hunoia.luno.config.backup.ConfigBackupRepository
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AppLaunchSource
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.config.model.record
import hunoia.luno.config.model.remove
import hunoia.luno.config.model.cleared
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.config.model.QuickLauncherGeometry
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.withResolvedName
import hunoia.luno.config.model.withUniqueEngineTriggers
import hunoia.luno.config.repository.AppUsageRepository
import hunoia.luno.config.repository.QuickLauncherRepository
import hunoia.luno.config.store.SettingsStores
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object ConfigProvider {

    private val stores by lazy { SettingsStores.create() }
    private val quickLauncherRepository by lazy { QuickLauncherRepository(stores) }
    private val appUsageRepository by lazy { AppUsageRepository(stores) }
    private val configBackupRepository by lazy { ConfigBackupRepository(stores) }

    val initialSettings: Flow<InitialSettings> = stores.initialSettings
    val advancedSettings: Flow<AdvancedSettings> = stores.advancedSettings
    val gestureSettings: Flow<GestureSettings> = stores.gestureSettings
    val actionSettings: Flow<ActionSettings> = stores.actionSettings
    val gestureButtons: Flow<List<GestureButton>> =
        stores.gestureButtons.map { buttons -> buttons.map { it.withResolvedName() } }
    val quickAppLauncherSettings: Flow<QuickAppLauncherSettings> = stores.quickAppLauncherSettings
    val appUsageStats: Flow<AppUsageStats> = stores.appUsageStats
    val subGestureSettings: Flow<SubGestureSettings> = stores.subGestureSettings
    val actionLibrarySettings: Flow<ActionLibrarySettings> = stores.actionLibrarySettings
    val automationRules: Flow<List<AutomationRule>> = stores.automationRules
    val automationLog: Flow<List<hunoia.luno.runtime.automation.AutomationEvent>> = stores.automationLog
    val quickSearchSettings: Flow<QuickSearchSettings> = stores.quickSearchSettings
    /** 剪贴板历史：设备本地派生数据，只供剪贴板页面部件订阅。 */
    val clipboardHistory: Flow<ClipboardHistory> = stores.clipboardHistory

    suspend fun getInitialSettings(): InitialSettings = stores._initialSettings.data.first()
    suspend fun getAdvancedSettings(): AdvancedSettings = stores._advancedSettings.data.first()
    suspend fun getGestureSettings(): GestureSettings = stores._gestureSettings.data.first()
    suspend fun getActionSettings(): ActionSettings = stores._actionSettings.data.first()
    suspend fun getGestureButtons(): List<GestureButton> =
        stores._gestureButtons.data.first().map { it.withResolvedName() }
    suspend fun getSubGestureSettings(): SubGestureSettings = stores._subGestureSettings.data.first()
    suspend fun getActionLibrarySettings(): ActionLibrarySettings = stores._actionLibrarySettings.data.first()
    suspend fun getAutomationRules(): List<AutomationRule> = stores._automationRules.data.first()
    suspend fun getQuickSearchSettings(): QuickSearchSettings =
        stores._quickSearchSettings.data.first().withUniqueEngineTriggers()

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
    suspend fun updateActionLibrarySettings(transform: suspend (ActionLibrarySettings) -> ActionLibrarySettings) {
        stores._actionLibrarySettings.updateData(transform)
    }

    suspend fun updateAutomationRules(transform: suspend (List<AutomationRule>) -> List<AutomationRule>) {
        stores._automationRules.updateData(transform)
    }

    /** 执行历史追加：全局环形 100，写盘失败吞掉（调试数据不影响规则执行）。 */
    suspend fun appendAutomationEvent(event: hunoia.luno.runtime.automation.AutomationEvent) {
        appendAutomationEvents(listOf(event))
    }

    /**
     * 批量追加：一次 updateData 写入整批，避免 N 次序列化 + N 次写盘。
     */
    suspend fun appendAutomationEvents(events: List<hunoia.luno.runtime.automation.AutomationEvent>) {
        if (events.isEmpty()) return
        runCatching {
            stores._automationLog.updateData { old ->
                val merged = old + events
                // 跳过事件限流：只保留最近 MAX_SKIP_EVENTS 条，超出的从最旧开始丢。
                val skipIndices = merged.indices.filter { !merged[it].fired }
                val dropCount = (skipIndices.size -
                    hunoia.luno.runtime.automation.ExecutionLog.MAX_SKIP_EVENTS).coerceAtLeast(0)
                val dropped = skipIndices.take(dropCount).toSet()
                val kept = merged.filterIndexed { index, _ -> index !in dropped }
                kept.takeLast(hunoia.luno.runtime.automation.ExecutionLog.MAX_EVENTS)
            }
        }
    }

    suspend fun updateQuickSearchSettings(transform: suspend (QuickSearchSettings) -> QuickSearchSettings) {
        stores._quickSearchSettings.updateData { current ->
            // 先归一化再交给 transform：transform 常按引擎 id 匹配/改写，
            // 拿到的必须是界面看到的那份（空 id 在归一化里补齐），否则改不动。
            transform(current.withUniqueEngineTriggers()).withUniqueEngineTriggers()
        }
    }

    suspend fun updateQuickAppLauncherLayout(layout: QuickLauncherGeometry, landscape: Boolean) {
        quickLauncherRepository.updateQuickAppLauncherLayout(layout, landscape)
    }

    /** 记录一次成功的 Luno 触发应用启动（包名维度）。 */
    suspend fun recordAppLaunch(packageName: String, source: AppLaunchSource) {
        appUsageRepository.recordLaunch(packageName, source)
    }

    /** 面板首开同步读一次快照。 */
    suspend fun getQuickAppSnapshot(): QuickAppSnapshot = stores.readQuickAppSnapshot()

    /**
     * 记一条剪贴板文本历史（去重置顶 + 环形裁剪）。
     * 失败静默：历史是辅助数据，写盘失败不能影响复制/粘贴本身。
     */
    suspend fun recordClipboardText(text: String) {
        if (text.isBlank()) return
        runCatching {
            stores._clipboardHistory.updateData { it.record(text, System.currentTimeMillis()) }
        }
    }

    suspend fun removeClipboardEntry(id: String) {
        if (id.isBlank()) return
        runCatching { stores._clipboardHistory.updateData { it.remove(id) } }
    }

    suspend fun clearClipboardHistory() {
        runCatching { stores._clipboardHistory.updateData { it.cleared() } }
    }

    /**
     * 回写派生快照（不含任何用户配置）。内容没变就不写：预热每次都重扫，结果通常
     * 与上次一致，而一次全量序列化 + 写盘远贵于内存里的 DataStore 读。
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
