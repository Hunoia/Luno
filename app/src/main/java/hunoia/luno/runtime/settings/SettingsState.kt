package hunoia.luno.runtime.settings

import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.InitialSettings
import hunoia.luno.config.model.SubGestureSettings

data class SettingsState(
    val initialSettings: InitialSettings = InitialSettings(),
    val advancedSettings: AdvancedSettings = AdvancedSettings(),
    val gestureSettings: GestureSettings = GestureSettings(),
    val actionSettings: ActionSettings = ActionSettings(),
    val gestureButtons: List<GestureButton> = emptyList(),
    val subGestureSettings: SubGestureSettings = SubGestureSettings(),
)
