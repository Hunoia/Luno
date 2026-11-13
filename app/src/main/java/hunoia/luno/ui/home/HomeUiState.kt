package hunoia.luno.ui.home

import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GlobalGestureZone
import hunoia.luno.config.model.SubGesture
import hunoia.luno.facade.PrivilegedPermissionStatus
import hunoia.luno.shizuku.ShizukuStatus

sealed interface RenameTarget {
    val name: String
    data class GestureButton(val button: hunoia.luno.config.model.GestureButton) : RenameTarget {
        override val name: String get() = button.name
    }
    data class SubGesture(val gesture: hunoia.luno.config.model.SubGesture) : RenameTarget {
        override val name: String get() = gesture.name
    }
    data class GlobalZone(val zone: GlobalGestureZone) : RenameTarget {
        // 默认全屏区没有持久化名称：弹窗预填展示名，允许用户直接确认。
        override val name: String
            get() = zone.name.ifBlank { if (zone.id == "1") "全屏" else "" }
    }
}

sealed interface UiEvent {
    data object ScrollToBottom : UiEvent
}

data class UiState(
    val gestureButtons: List<GestureButton> = emptyList(),
    val subGestures: List<SubGesture> = emptyList(),
    val globalZones: List<GlobalGestureZone> = emptyList(),
    val isGestureSwitchEnabled: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isKeepAliveEnabled: Boolean = false,
    val shizukuStatus: ShizukuStatus = ShizukuStatus(
        binderAlive = false,
        permissionGranted = false,
    ),
    val runtimeStatus: HomeRuntimeStatus = HomeRuntimeStatusMapper.map(
        isAccessibilityEnabled = false,
            isGestureSwitchEnabled = false,
        isKeepAliveEnabled = false,
        shizukuStatus = ShizukuStatus(
            binderAlive = false,
            permissionGranted = false,
        ),
    ),
    /** 各特权权限的当前授权快照；主页只从中数缺口，明细在授权页。 */
    val privilegedPermissionStatus: List<PrivilegedPermissionStatus> = emptyList(),
    /** 主页「一键授权」进行中：按钮置忙，避免重复触发。 */
    val isGrantingPermissions: Boolean = false,
    val renameDialogTarget: RenameTarget? = null,
)
