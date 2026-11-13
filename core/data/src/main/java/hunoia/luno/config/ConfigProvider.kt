package hunoia.luno.config

import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.backup.ConfigBackupRepository
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.AnnotateSettings
import hunoia.luno.config.model.AppControlSettings
import hunoia.luno.core.domain.AppLaunchSource
import hunoia.luno.config.model.AppUsageStats
import hunoia.luno.config.model.AutoBackupSettings
import hunoia.luno.config.model.Backup
import hunoia.luno.config.model.BackupDomain
import hunoia.luno.config.model.ClipboardEntry
import hunoia.luno.config.model.ClipboardHistory
import hunoia.luno.config.model.ClipboardSettings
import hunoia.luno.config.model.record
import hunoia.luno.config.model.cleared
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.GlobalGestureSettings
import hunoia.luno.config.model.Habit
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.HabitHistoryRetentionDays
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.QuickAppLauncherSettings
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.config.model.QuickLauncherGeometry
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoDoneRetentionDays
import hunoia.luno.config.model.TodoItem
import hunoia.luno.config.model.TrendConfig
import hunoia.luno.config.model.TrendEntry
import hunoia.luno.config.model.TrendMetric
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
    /** 识别区（全屏手势）：总开关 + 区列表。 */
    val globalGesture: Flow<GlobalGestureSettings> = stores.globalGesture
    val quickAppLauncherSettings: Flow<QuickAppLauncherSettings> = stores.quickAppLauncherSettings
    val appUsageStats: Flow<AppUsageStats> = stores.appUsageStats
    val subGestureSettings: Flow<SubGestureSettings> = stores.subGestureSettings
    val actionLibrarySettings: Flow<ActionLibrarySettings> = stores.actionLibrarySettings
    val quickSearchSettings: Flow<QuickSearchSettings> = stores.quickSearchSettings
    /** 标注偏好（每工具风格 + 工具条位置）：两宿主共用，笔迹不落盘。 */
    val annotateSettings: Flow<AnnotateSettings> = stores.annotateSettings
    /** 剪贴板历史：设备本地派生数据；固定项随备份导出，普通历史不导出。 */
    val clipboardHistory: Flow<ClipboardHistory> = stores.clipboardHistory
    val clipboardSettings: Flow<ClipboardSettings> = stores.clipboardSettings
    /** 定时自动备份状态：设备本地派生数据，不进备份、不被 resetAll 清掉。 */
    val autoBackup: Flow<AutoBackupSettings> = stores.autoBackup
    /** 应用冻结：一键名单（不含系统禁用状态）。 */
    val appControlSettings: Flow<AppControlSettings> = stores.appControlSettings

    /** 习惯配置：习惯定义 + 打卡记录。部件与管理页都订阅它。 */
    val habitConfig: Flow<HabitConfig> = stores.habitConfig

    /** 代办配置：代办列表（一次性任务）。部件与管理页都订阅它。 */
    val todoConfig: Flow<TodoConfig> = stores.todoConfig

    /** 趋势配置：指标定义 + 按指标分组的数值记录。部件与管理页都订阅它。 */
    val trendConfig: Flow<TrendConfig> = stores.trendConfig

    suspend fun getInitialSettings(): InitialSettings = stores._initialSettings.data.first()
    suspend fun getAdvancedSettings(): AdvancedSettings = stores._advancedSettings.data.first()
    suspend fun getGestureSettings(): GestureSettings = stores._gestureSettings.data.first()
    suspend fun getActionSettings(): ActionSettings = stores._actionSettings.data.first()
    suspend fun getGestureButtons(): List<GestureButton> =
        stores._gestureButtons.data.first().map { it.withResolvedName() }
    suspend fun getGlobalGesture(): GlobalGestureSettings = stores._globalGesture.data.first()
    suspend fun getSubGestureSettings(): SubGestureSettings = stores._subGestureSettings.data.first()
    suspend fun getActionLibrarySettings(): ActionLibrarySettings = stores._actionLibrarySettings.data.first()
    suspend fun getAnnotateSettings(): AnnotateSettings = stores._annotateSettings.data.first()
    suspend fun getAppControlSettings(): AppControlSettings = stores._appControlSettings.data.first()
    suspend fun getClipboardSettings(): ClipboardSettings =
        stores._clipboardSettings.data.first().coerced()
    suspend fun getAutoBackup(): AutoBackupSettings = stores._autoBackup.data.first()

    suspend fun updateAutoBackup(transform: suspend (AutoBackupSettings) -> AutoBackupSettings) {
        stores._autoBackup.updateData(transform)
    }

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
    suspend fun updateGlobalGesture(transform: suspend (GlobalGestureSettings) -> GlobalGestureSettings) {
        stores._globalGesture.updateData(transform)
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

    suspend fun updateAnnotateSettings(transform: suspend (AnnotateSettings) -> AnnotateSettings) {
        stores._annotateSettings.updateData(transform)
    }

    suspend fun updateAppControlSettings(transform: suspend (AppControlSettings) -> AppControlSettings) {
        stores._appControlSettings.updateData(transform)
    }

    suspend fun updateQuickAppLauncherLayout(layout: QuickLauncherGeometry, landscape: Boolean) {
        quickLauncherRepository.updateQuickAppLauncherLayout(layout, landscape)
    }

    suspend fun updateClipboardSettings(transform: suspend (ClipboardSettings) -> ClipboardSettings) {
        stores._clipboardSettings.updateData { current -> transform(current).coerced() }
    }

    /** 记录一次成功的 Luno 触发应用启动（包名维度）。 */
    suspend fun recordAppLaunch(packageName: String, source: AppLaunchSource) {
        appUsageRepository.recordLaunch(packageName, source)
    }

    /** 面板首开同步读一次快照。 */
    suspend fun getQuickAppSnapshot(): QuickAppSnapshot = stores.readQuickAppSnapshot()

    /**
     * 记一条剪贴板条目（去重置顶 + 只裁未固定项）。
     * 失败静默：历史是辅助数据，写盘失败不能影响复制/粘贴本身。
     *
     * 返回写入后的历史（失败时 null），调用方据此清理不再被引用的图片副本。
     */
    suspend fun recordClipboardEntry(
        entry: ClipboardEntry,
        maxEntries: Int,
    ): ClipboardHistory? = runCatching {
        stores._clipboardHistory.updateData { it.record(entry, System.currentTimeMillis(), maxEntries) }
    }.getOrNull()

    /** 原子改历史；返回写入后的值（失败时 null）。 */
    suspend fun updateClipboardHistory(
        transform: suspend (ClipboardHistory) -> ClipboardHistory,
    ): ClipboardHistory? = runCatching {
        stores._clipboardHistory.updateData(transform)
    }.getOrNull()

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

    /** 打卡 +1：未达标累加次数，达标移入完成集合；已完成再调是幂等。写后按保留天数惰性裁剪。 */
    suspend fun incrementHabit(habitId: String, day: Long) {
        updateHabitConfig { config ->
            val target = config.habitById(habitId)?.safeDailyTarget ?: return@updateHabitConfig config
            config.withIncrement(habitId, day, target)
                .pruneChecks(LocalDate.now().toEpochDay(), HabitHistoryRetentionDays)
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

    // ---------- 代办域 ----------
    // 切换 / 增删改是应用内状态变更（配置写入），走 domain 门面写回；
    // 不走 Dispatcher / SurfaceDispatch（architecture §1.3 配置写入不是 Action）。

    suspend fun getTodoConfig(): TodoConfig = stores._todoConfig.data.first()

    suspend fun updateTodoConfig(transform: suspend (TodoConfig) -> TodoConfig) {
        stores._todoConfig.updateData(transform)
    }

    /** 切换完成态：done=true 记完成时刻。写后按保留天数惰性裁剪已完成代办。 */
    suspend fun toggleTodo(todoId: String, done: Boolean) {
        updateTodoConfig {
            it.toggleTodo(todoId, done).pruneDone(System.currentTimeMillis(), TodoDoneRetentionDays)
        }
    }

    suspend fun addTodo(todo: TodoItem) {
        updateTodoConfig { it.addTodo(todo) }
    }

    suspend fun updateTodo(todo: TodoItem) {
        updateTodoConfig { it.updateTodo(todo) }
    }

    suspend fun deleteTodo(todoId: String) {
        updateTodoConfig { it.deleteTodo(todoId) }
    }

    // ---------- 趋势域 ----------
    // 记录 / 增删改是应用内状态变更（配置写入），走 domain 门面写回；
    // 不走 Dispatcher / SurfaceDispatch（architecture §1.3 配置写入不是 Action）。

    suspend fun getTrendConfig(): TrendConfig = stores._trendConfig.data.first()

    suspend fun updateTrendConfig(transform: suspend (TrendConfig) -> TrendConfig) {
        stores._trendConfig.updateData(transform)
    }

    suspend fun addTrendMetric(metric: TrendMetric) {
        updateTrendConfig { it.addMetric(metric) }
    }

    suspend fun updateTrendMetric(metric: TrendMetric) {
        updateTrendConfig { it.updateMetric(metric) }
    }

    suspend fun deleteTrendMetric(metricId: String) {
        updateTrendConfig { it.deleteMetric(metricId) }
    }

    /** 追加记录；指标不存在或达到单指标上限时返回 false。 */
    suspend fun addTrendEntry(metricId: String, entry: TrendEntry): Boolean {
        var added = false
        updateTrendConfig { config ->
            config.addEntry(metricId, entry)?.also { added = true } ?: config
        }
        return added
    }

    suspend fun updateTrendEntryValue(metricId: String, entryId: String, value: Double) {
        updateTrendConfig { it.updateEntryValue(metricId, entryId, value) }
    }

    suspend fun deleteTrendEntry(metricId: String, entryId: String) {
        updateTrendConfig { it.deleteEntry(metricId, entryId) }
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
