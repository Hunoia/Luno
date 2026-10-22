package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.ActionLibrarySettings
import hunoia.luno.config.model.GestureButton
import kotlinx.serialization.Serializable

/** 备份格式版本与各域 Schema 版本（改某域模型只升对应域版本，不动备份格式）。 */
object BackupVersions {
    const val FORMAT = 2
    const val GESTURE = 1
    const val TUNE = 1
    const val ACTION = 1
    const val AUTOMATION = 1
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
    val quickSearchSettings: QuickSearchSettings? = null,
)
