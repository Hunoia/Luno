package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.NewActionLibrarySettings
import hunoia.luno.config.model.GestureButton
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class Backup(
    val initialSettings: InitialSettings? = null,
    val advancedSettings: AdvancedSettings? = null,
    val gestureSettings: GestureSettings? = null,
    val actionSettings: ActionSettings? = null,
    val gestureButtons: List<GestureButton>? = null,
    val quickAppLauncherSettings: QuickAppLauncherSettings? = null,
    val subGestureSettings: SubGestureSettings? = null,
    val newActionLibrarySettings: NewActionLibrarySettings? = null,
    val timestamp: Long? = null,
    val version: String? = null
)
