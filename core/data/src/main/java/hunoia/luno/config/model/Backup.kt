package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.model.GestureButton
import kotlinx.serialization.Serializable

/** 备份格式版本与各域 Schema 版本（改某域模型只升对应域版本，不动备份格式）。 */
object BackupVersions {
    const val FORMAT = 2
    const val GESTURE = 1
    const val TUNE = 1
    const val ACTION = 1
    const val AUTOMATION = 3
    const val SEARCH = 1
}

/** 备份域：整机备份含全部，单域导出一次一域。 */
enum class BackupDomain {
    GESTURE,
    TUNE,
    ACTION,
    AUTOMATION,
    SEARCH,
}

@Serializable
@Keep
data class Backup(
    /** 老备份无此字段，解码默认为 1（前版本时代）。 */
    val backupFormatVersion: Int = 1,
    val gestureSchemaVersion: Int = 1,
    val tuneSchemaVersion: Int = 1,
    val actionSchemaVersion: Int = 1,
    val automationSchemaVersion: Int = 1,
    val searchSchemaVersion: Int = 1,
    val initialSettings: InitialSettings? = null,
    val advancedSettings: AdvancedSettings? = null,
    val gestureSettings: GestureSettings? = null,
    val actionSettings: ActionSettings? = null,
    val gestureButtons: List<GestureButton>? = null,
    val quickAppLauncherSettings: QuickAppLauncherSettings? = null,
    val subGestureSettings: SubGestureSettings? = null,
    val actionLibrarySettings: ActionLibrarySettings? = null,
    val automationRules: List<AutomationRule>? = null,
    val globalVariables: GlobalVariables? = null,
    val quickSearchSettings: QuickSearchSettings? = null,
)

/**
 * 当前 app 能否完整解析此备份：格式版本与各域 schema 版本均不高于本实现。
 * 拦截「新版本备份导入旧版 app」：配合 ignoreUnknownKeys，未知字段会被静默丢弃，
 * 必须在 precheck 阶段拒绝，而不是恢复完才发现丢数据。
 */
fun Backup.isSupported(): Boolean =
    backupFormatVersion <= BackupVersions.FORMAT &&
        gestureSchemaVersion <= BackupVersions.GESTURE &&
        tuneSchemaVersion <= BackupVersions.TUNE &&
        actionSchemaVersion <= BackupVersions.ACTION &&
        automationSchemaVersion <= BackupVersions.AUTOMATION &&
        searchSchemaVersion <= BackupVersions.SEARCH
