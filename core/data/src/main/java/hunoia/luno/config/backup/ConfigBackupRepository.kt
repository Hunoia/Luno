package hunoia.luno.config.backup

import android.util.Log
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.config.model.BackupVersions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GlobalVariables
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.withUniqueEngineTriggers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
        val automationRulesDeferred = async { stores._automationRules.data.first() }
        val globalVariablesDeferred = async { stores._automationVariables.data.first() }
        val quickSearchDeferred = async { stores._quickSearchSettings.data.first() }
        Backup(
            backupFormatVersion = BackupVersions.FORMAT,
            gestureSchemaVersion = BackupVersions.GESTURE,
            tuneSchemaVersion = BackupVersions.TUNE,
            actionSchemaVersion = BackupVersions.ACTION,
            automationSchemaVersion = BackupVersions.AUTOMATION,
            searchSchemaVersion = BackupVersions.SEARCH,
            initialSettings = initialDeferred.await(),
            advancedSettings = advancedDeferred.await(),
            gestureSettings = gestureDeferred.await(),
            actionSettings = actionDeferred.await(),
            gestureButtons = buttonsDeferred.await(),
            quickAppLauncherSettings = qlaDeferred.await(),
            subGestureSettings = subGestureDeferred.await(),
            actionLibrarySettings = actionLibraryDeferred.await(),
            automationRules = automationRulesDeferred.await(),
            globalVariables = globalVariablesDeferred.await(),
            quickSearchSettings = quickSearchDeferred.await(),
        )
    }

    suspend fun restoreAll(backup: Backup) = withRollback {
        writeAll(backup)
    }

    private suspend fun writeAll(backup: Backup) = coroutineScope {
        launch { backup.initialSettings?.let { v -> writeSection("initialSettings") { stores._initialSettings.updateData { v } } } }
        launch { backup.advancedSettings?.let { v -> writeSection("advancedSettings") { stores._advancedSettings.updateData { v } } } }
        launch { backup.gestureSettings?.let { v -> writeSection("gestureSettings") { stores._gestureSettings.updateData { v } } } }
        launch { backup.actionSettings?.let { v -> writeSection("actionSettings") { stores._actionSettings.updateData { v } } } }
        launch { backup.gestureButtons?.let { v -> writeSection("gestureButtons") { stores._gestureButtons.updateData { v } } } }
        launch { backup.quickAppLauncherSettings?.let { v -> writeSection("quickAppLauncherSettings") { stores._quickAppLauncherSettings.updateData { v } } } }
        launch { backup.subGestureSettings?.let { v -> writeSection("subGestureSettings") { stores._subGestureSettings.updateData { v } } } }
        launch { backup.actionLibrarySettings?.let { v -> writeSection("actionLibrarySettings") { stores._actionLibrarySettings.updateData { v } } } }
        launch { backup.automationRules?.let { v -> writeSection("automationRules") { stores._automationRules.updateData { hunoia.luno.config.model.AutomationRuleNormalizer.normalize(v) } } } }
        launch { backup.globalVariables?.let { v -> writeSection("globalVariables") { stores._automationVariables.updateData { v.normalized() } } } }
        // 恢复的搜索设置先归一化：旧备份缺引擎 id / 标识重复，落库前补齐，
        // 否则顺序、隐藏、打开方式覆盖会指向不存在的引擎。
        launch { backup.quickSearchSettings?.let { v -> writeSection("quickSearchSettings") { stores._quickSearchSettings.updateData { v.withUniqueEngineTriggers() } } } }
    }

    /**
     * 恢复是跨 10 个 DataStore 的多写，不存在真正的原子提交。失败时把操作前的
     * 全量快照写回去，避免用户停在「一半新配置、一半旧配置」的混合状态。
     */
    private suspend fun withRollback(block: suspend () -> Unit) {
        val previous = snapshotAll()
        try {
            block()
        } catch (e: Exception) {
            withContext(NonCancellable) {
                runCatching { writeAll(previous) }
                    .onFailure { Log.e("LunoBackup", "restore rollback failed", it) }
            }
            throw e
        }
    }

    /** 单域快照：域内包含哪些段由 [BackupDomainRegistry] 注册的投影决定。 */
    suspend fun snapshotDomain(domain: BackupDomain): Backup {
        BuiltinBackupDomains.ensure()
        return BackupDomainRegistry.snapshot(snapshotAll(), domain)
    }

    /** 单域恢复：只写非 null 段。 */
    suspend fun restoreDomain(backup: Backup) = restoreAll(backup)

    private suspend fun writeSection(section: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            throw IllegalStateException("restore failed: $section write failed: ${e::class.simpleName} ${e.message}", e)
        }
    }

    suspend fun resetAll() = withRollback {
        resetStores()
    }

    private suspend fun resetStores() = coroutineScope {
        launch { stores._initialSettings.updateData { InitialSettings() } }
        launch { stores._advancedSettings.updateData { AdvancedSettings() } }
        launch { stores._gestureSettings.updateData { GestureSettings() } }
        launch { stores._actionSettings.updateData { ActionSettings() } }
        launch { stores._gestureButtons.updateData { GestureButton.Defaults } }
        launch { stores._quickAppLauncherSettings.updateData { QuickAppLauncherSettings() } }
        launch { stores._appUsageStats.updateData { AppUsageStats() } }
        launch { stores._clipboardHistory.updateData { hunoia.luno.config.model.ClipboardHistory() } }
        launch { stores._subGestureSettings.updateData { SubGestureSettings() } }
        launch { stores._actionLibrarySettings.updateData { ActionLibrarySettings() } }
        launch { stores._automationRules.updateData { AutomationRule.Defaults } }
        launch { stores._automationVariables.updateData { GlobalVariables() } }
        launch { stores._quickSearchSettings.updateData { QuickSearchSettings() } }
    }
}
