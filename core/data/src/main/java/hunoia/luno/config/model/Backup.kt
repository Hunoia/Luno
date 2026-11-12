package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.HabitConfig
import hunoia.luno.config.model.TodoConfig
import kotlinx.serialization.Serializable

/** 备份格式版本与各域 Schema 版本（改某域模型只升对应域版本，不动备份格式）。 */
object BackupVersions {
    const val FORMAT = 2
    const val GESTURE = 1
    const val TUNE = 1
    const val ACTION = 2
    const val SEARCH = 1
    const val HABIT = 2
    /** 2：待办提醒（`TodoItem.reminder`）与全局推迟时长（`TodoConfig.reminderSnoozeMinutes`）。 */
    const val TODO = 2
    const val ANNOTATE = 1
    const val CLIPBOARD = 1
    const val APP_CONTROL = 1
}

/** 备份域：整机备份含全部，单域导出一次一域。 */
enum class BackupDomain {
    GESTURE,
    TUNE,
    ACTION,
    SEARCH,
    HABIT,
    TODO,
    ANNOTATE,
    CLIPBOARD,
    APP_CONTROL,
}

@Serializable
@Keep
data class Backup(
    /** 老备份无此字段，解码默认为 1（前版本时代）。 */
    val backupFormatVersion: Int = 1,
    val gestureSchemaVersion: Int = 1,
    val tuneSchemaVersion: Int = 1,
    val actionSchemaVersion: Int = 1,
    val searchSchemaVersion: Int = 1,
    val habitSchemaVersion: Int = 1,
    val todoSchemaVersion: Int = 1,
    val annotateSchemaVersion: Int = 1,
    val clipboardSchemaVersion: Int = 1,
    val appControlSchemaVersion: Int = 1,
    val initialSettings: InitialSettings? = null,
    val advancedSettings: AdvancedSettings? = null,
    val gestureSettings: GestureSettings? = null,
    val actionSettings: ActionSettings? = null,
    val gestureButtons: List<GestureButton>? = null,
    /** 识别区（全屏手势）：随手势域备份。 */
    val globalGesture: GlobalGestureSettings? = null,
    val quickAppLauncherSettings: QuickAppLauncherSettings? = null,
    val subGestureSettings: SubGestureSettings? = null,
    val actionLibrarySettings: ActionLibrarySettings? = null,
    val quickSearchSettings: QuickSearchSettings? = null,
    val habitConfig: HabitConfig? = null,
    val todoConfig: TodoConfig? = null,
    val annotateSettings: AnnotateSettings? = null,
    /** 剪贴板设置（记录开关 / 未固定上限）：随备份走。 */
    val clipboardSettings: ClipboardSettings? = null,
    /** 仅固定项：普通历史与图片是设备本地派生数据，刻意不进备份。 */
    val clipboardPinned: List<ClipboardEntry>? = null,
    /** 应用冻结：一键冻结名单。当前哪些包被禁用是系统状态，刻意不进备份。 */
    val appControlSettings: AppControlSettings? = null,
)

/**
 * 当前 app 能否完整解析此备份：格式版本与各域 schema 版本均不高于本实现。
 * 拦截「新版本备份导入旧版 app」：配合 ignoreUnknownKeys，未知字段会被静默丢弃，
 * 必须在 precheck 阶段拒绝，而不是恢复完才发现丢数据。
 */
fun Backup.isSupported(): Boolean {
    hunoia.luno.config.backup.BuiltinBackupDomains.ensure()
    return hunoia.luno.config.backup.BackupDomainRegistry.isSupported(this)
}
