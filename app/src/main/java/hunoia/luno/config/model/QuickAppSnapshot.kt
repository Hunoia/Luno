package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.quicklaunch.model.AppInfo
import kotlinx.serialization.Serializable

/**
 * 快速启动器应用列表的磁盘快照。
 *
 * 用途：面板首开不必等 PackageManager 全量扫描 —— 那一步是首开延迟的主要来源。
 *
 * **刻意不进备份**：`ConfigBackupRepository` 显式枚举 9 个 store，新增的这个不在其中，
 * 所以 `snapshotAll` / `restoreAll` / `resetAll` 都不会碰它。这是可从系统重建的派生
 * 数据，备份它只会让备份文件随装机应用数量一起膨胀。
 */
@Serializable
@Keep
data class QuickAppSnapshot(
    val apps: List<AppInfo> = emptyList(),
    val disabledPkgs: Set<String> = emptySet(),
    /**
     * 写入时的系统语言标签。
     *
     * `PackageManager.loadLabel` 的结果跟随系统语言，语言一换快照里的 label 就全是旧的。
     * 计划里写的是「监听 ACTION_LOCALE_CHANGED 标脏」，但进程内标脏挡不住「进程已死的
     * 期间换了语言」这种情况 —— 所以把语言记在快照里，读的时候比对。
     */
    val labelLocale: String = "",
)