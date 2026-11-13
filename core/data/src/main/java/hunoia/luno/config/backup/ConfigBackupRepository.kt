package hunoia.luno.config.backup

import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.store.SettingsStores
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AnnotateSettings
import hunoia.luno.config.model.AppControlSettings
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.config.model.BackupVersions
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.config.model.ClipboardSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GlobalGestureSettings
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TrendConfig
import hunoia.luno.config.model.exportablePinned
import hunoia.luno.config.model.mergePinned
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
        val globalGestureDeferred = async { stores._globalGesture.data.first() }
        val qlaDeferred = async { stores._quickAppLauncherSettings.data.first() }
        val subGestureDeferred = async { stores._subGestureSettings.data.first() }
        val actionLibraryDeferred = async { stores._actionLibrarySettings.data.first() }
        val quickSearchDeferred = async { stores._quickSearchSettings.data.first() }
        val habitDeferred = async { stores._habitConfig.data.first() }
        val todoDeferred = async { stores._todoConfig.data.first() }
        val trendDeferred = async { stores._trendConfig.data.first() }
        val annotateDeferred = async { stores._annotateSettings.data.first() }
        val clipboardSettingsDeferred = async { stores._clipboardSettings.data.first() }
        val clipboardHistoryDeferred = async { stores._clipboardHistory.data.first() }
        val appControlDeferred = async { stores._appControlSettings.data.first() }
        Backup(
            backupFormatVersion = BackupVersions.FORMAT,
            gestureSchemaVersion = BackupVersions.GESTURE,
            tuneSchemaVersion = BackupVersions.TUNE,
            actionSchemaVersion = BackupVersions.ACTION,
            searchSchemaVersion = BackupVersions.SEARCH,
            habitSchemaVersion = BackupVersions.HABIT,
            todoSchemaVersion = BackupVersions.TODO,
            annotateSchemaVersion = BackupVersions.ANNOTATE,
            clipboardSchemaVersion = BackupVersions.CLIPBOARD,
            appControlSchemaVersion = BackupVersions.APP_CONTROL,
            trendSchemaVersion = BackupVersions.TREND,
            initialSettings = initialDeferred.await(),
            advancedSettings = advancedDeferred.await(),
            gestureSettings = gestureDeferred.await(),
            actionSettings = actionDeferred.await(),
            gestureButtons = buttonsDeferred.await(),
            globalGesture = globalGestureDeferred.await(),
            quickAppLauncherSettings = qlaDeferred.await(),
            subGestureSettings = subGestureDeferred.await(),
            actionLibrarySettings = actionLibraryDeferred.await(),
            quickSearchSettings = quickSearchDeferred.await(),
            habitConfig = habitDeferred.await(),
            todoConfig = todoDeferred.await(),
            trendConfig = trendDeferred.await(),
            annotateSettings = annotateDeferred.await(),
            clipboardSettings = clipboardSettingsDeferred.await(),
            clipboardPinned = clipboardHistoryDeferred.await().exportablePinned().ifEmpty { null },
            appControlSettings = appControlDeferred.await(),
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
        launch { backup.globalGesture?.let { v -> writeSection("globalGesture") { stores._globalGesture.updateData { v } } } }
        launch { backup.quickAppLauncherSettings?.let { v -> writeSection("quickAppLauncherSettings") { stores._quickAppLauncherSettings.updateData { v } } } }
        launch { backup.subGestureSettings?.let { v -> writeSection("subGestureSettings") { stores._subGestureSettings.updateData { v } } } }
        launch { backup.actionLibrarySettings?.let { v -> writeSection("actionLibrarySettings") { stores._actionLibrarySettings.updateData { v } } } }
        // 恢复的搜索设置先归一化：旧备份缺引擎 id / 标识重复，落库前补齐，
        // 否则顺序、隐藏、打开方式覆盖会指向不存在的引擎。
        launch { backup.quickSearchSettings?.let { v -> writeSection("quickSearchSettings") { stores._quickSearchSettings.updateData { v.withUniqueEngineTriggers() } } } }
        launch { backup.habitConfig?.let { v -> writeSection("habitConfig") { stores._habitConfig.updateData { v } } } }
        launch { backup.todoConfig?.let { v -> writeSection("todoConfig") { stores._todoConfig.updateData { v } } } }
        launch { backup.trendConfig?.let { v -> writeSection("trendConfig") { stores._trendConfig.updateData { v } } } }
        launch { backup.annotateSettings?.let { v -> writeSection("annotateSettings") { stores._annotateSettings.updateData { v } } } }
        launch { backup.clipboardSettings?.let { v -> writeSection("clipboardSettings") { stores._clipboardSettings.updateData { v.coerced() } } } }
        // 固定项合并而不是覆盖：备份里的固定项是用户资产，不能顺手抹掉本机现有历史。
        launch { backup.clipboardPinned?.let { v -> writeSection("clipboardPinned") { stores._clipboardHistory.updateData { it.mergePinned(v) } } } }
        launch { backup.appControlSettings?.let { v -> writeSection("appControlSettings") { stores._appControlSettings.updateData { v } } } }
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
        launch { stores._globalGesture.updateData { GlobalGestureSettings() } }
        launch { stores._quickAppLauncherSettings.updateData { QuickAppLauncherSettings() } }
        launch { stores._appUsageStats.updateData { AppUsageStats() } }
        launch { stores._clipboardHistory.updateData { ClipboardHistory() } }
        launch { stores._clipboardSettings.updateData { ClipboardSettings() } }
        launch { stores._subGestureSettings.updateData { SubGestureSettings() } }
        launch { stores._actionLibrarySettings.updateData { ActionLibrarySettings() } }
        launch { stores._quickSearchSettings.updateData { QuickSearchSettings() } }
        launch { stores._habitConfig.updateData { HabitConfig() } }
        launch { stores._todoConfig.updateData { TodoConfig() } }
        launch { stores._trendConfig.updateData { TrendConfig() } }
        launch { stores._annotateSettings.updateData { AnnotateSettings() } }
        launch { stores._appControlSettings.updateData { AppControlSettings() } }
    }
}
