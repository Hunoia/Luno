package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.AppInfo
import kotlinx.serialization.Serializable

/**
 * 快速启动器应用列表的磁盘快照：面板首开不必等 PackageManager 全量扫描。
 * 刻意不进备份：这是可从系统重建的派生数据，备份只会让文件随装机应用数量膨胀。
 */
@Serializable
@Keep
data class QuickAppSnapshot(
    val apps: List<AppInfo> = emptyList(),
    val disabledPkgs: Set<String> = emptySet(),
    /**
     * 写入时的系统语言标签：`loadLabel` 跟随系统语言，语言一换快照里的 label 就全是旧的。
     * 进程内标脏挡不住「进程已死期间换了语言」，所以记下语言、读时比对。
     */
    val labelLocale: String = "",
)