package hunoia.luno.config

import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.backup.ConfigBackupRepository
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AppLaunchSource
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.config.model.record
import hunoia.luno.config.model.remove
import hunoia.luno.config.model.cleared
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitHistoryRetentionDays
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
import java.time.LocalDate
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
    val quickSearchSettings: Flow<QuickSearchSettings> = stores.quickSearchSettings
    /** 剪贴板历史：设备本地派生数据，只供剪贴板页面部件订阅。 */
    val clipboardHistory: Flow<ClipboardHistory> = stores.clipboardHistory

    /** 习惯配置：习惯定义 + 打卡记录。部件与管理页都订阅它。 */
    val habitConfig: Flow<HabitConfig> = stores.habitConfig

    suspend fun getInitialSettings(): InitialSettings = stores._initialSettings.data.first()
    suspend fun getAdvancedSettings(): AdvancedSettings = stores._advancedSettings.data.first()
    suspend fun getGestureSettings(): GestureSettings = stores._gestureSettings.data.first()
    suspend fun getActionSettings(): ActionSettings = stores._actionSettings.data.first()
    suspend fun getGestureButtons(): List<GestureButton> =
        stores._gestureButtons.data.first().map { it.withResolvedName() }
    suspend fun getSubGestureSettings(): SubGestureSettings = stores._subGestureSettings.data.first()
    suspend fun getActionLibrarySettings(): ActionLibrarySettings = stores._actionLibrarySettings.data.first()
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

    // ---------- 习惯域 ----------
    // 打卡 / 增删改是应用内状态变更（配置写入），走 domain 门面写回；
    // 不走 Dispatcher / SurfaceDispatch（architecture §1.3 配置写入不是 Action）。

    suspend fun getHabitConfig(): HabitConfig = stores._habitConfig.data.first()

    suspend fun updateHabitConfig(transform: suspend (HabitConfig) -> HabitConfig) {
        stores._habitConfig.updateData(transform)
    }

    /** 部件打卡：只打卡不可取消；今天已打再调是幂等。写后按保留天数惰性裁剪。 */
    suspend fun toggleHabit(habitId: String, day: Long) {
        updateHabitConfig {
            it.withCheck(habitId, day).pruneChecks(LocalDate.now().toEpochDay(), HabitHistoryRetentionDays)
        }
    }

    /** 管理页补 / 改历史（近 30 天）：统一写入 / 清除。写后按保留天数惰性裁剪。 */
    suspend fun setHabitCheck(habitId: String, day: Long, checked: Boolean) {
        updateHabitConfig {
            it.setChecked(habitId, day, checked).pruneChecks(LocalDate.now().toEpochDay(), HabitHistoryRetentionDays)
        }
    }

    suspend fun addHabit(habit: Habit) {
        updateHabitConfig { it.addHabit(habit) }
    }

    suspend fun updateHabit(habit: Habit) {
        updateHabitConfig { it.updateHabit(habit) }
    }

    suspend fun deleteHabit(habitId: String) {
        updateHabitConfig { it.deleteHabit(habitId) }
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
