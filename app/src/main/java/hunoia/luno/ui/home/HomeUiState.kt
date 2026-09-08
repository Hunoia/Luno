package hunoia.luno.ui.home

import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.SubGesture
import hunoia.luno.shizuku.ShizukuStatus

sealed interface RenameTarget {
    val name: String
    data class GestureButton(val button: hunoia.luno.config.model.GestureButton) : RenameTarget {
        override val name: String get() = button.name
    }
    data class SubGesture(val gesture: hunoia.luno.config.model.SubGesture) : RenameTarget {
        override val name: String get() = gesture.name
    }
}

sealed interface UiEvent {
    data object ScrollToBottom : UiEvent
    data class ScrollToEvent(val offsetY: Int) : UiEvent
}

data class UiState(
    val gestureButtons: List<GestureButton> = emptyList(),
    val subGestures: List<SubGesture> = emptyList(),
    val isGestureSwitchEnabled: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isSubGestureListExpanded: Boolean = false,
    val isGestureButtonListExpanded: Boolean = false,
    val pointer: GestureSettings.Pointer = GestureSettings.Pointer(),
    val excludedAppCount: Int = 0,
    val frozenAppCount: Int = 0,
    val selectedFrozenAppCount: Int = 0,
    val isKeepAliveEnabled: Boolean = false,
    val shizukuStatus: ShizukuStatus = ShizukuStatus(
        installed = false,
        binderAlive = false,
        permissionGranted = false,
        uid = null,
    ),
    val runtimeStatus: HomeRuntimeStatus = HomeRuntimeStatusMapper.map(
        isAccessibilityEnabled = false,
            isGestureSwitchEnabled = false,
        isKeepAliveEnabled = false,
        shizukuStatus = ShizukuStatus(
            installed = false,
            binderAlive = false,
            permissionGranted = false,
            uid = null,
        ),
    ),
    val renameDialogTarget: RenameTarget? = null,
)
