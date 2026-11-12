package hunoia.luno.runtime.overlay

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureButton

/**
 * 一个手势按钮窗口的标识。[mirrored] 区分同一按钮的主/镜像窗口 —— 镜像窗口的 windowButton
 * 与主窗口字段完全相同，只能靠这个显式标记区分；窗口身份由 (sourceButton.id, mirrored) 决定。
 */
internal data class GestureButtonWindowTarget(
    val sourceButton: GestureButton,
    val windowButton: GestureButton,
    val mirrored: Boolean = false,
)

interface GestureOverlayCallbacks {
    fun onSubGestureModeChanged(inSubGesture: Boolean, center: Offset, radiusPx: Int)
    fun onActionPanelOverlayChanged(show: Boolean)

    /** 请求挂上全屏音量触摸窗：滑动（非停留）手势触发，把调节区扩到全屏，触发的那次拖动结束后任意位置再拖仍能调节。 */
    fun onVolumeScrubOverlayRequested(config: hunoia.luno.config.model.VolumeScrubConfig)

    /**
     * 请求挂上最近应用滚轮窗：状态已 activate（含列表/布局/锚点），窗口渲染共享
     * [hunoia.luno.runtime.recentspicker.RecentsPickerState] 并接管抬手后的滚动/点选。
     */
    fun onRecentsPickerOverlayRequested(state: hunoia.luno.runtime.recentspicker.RecentsPickerState)

    /** 请求收掉最近应用滚轮窗（按住类触发的系统级取消路径）。 */
    fun onRecentsPickerOverlayDismissed()

    /** 指针桥：按住类触发的面板内光标走 start→end/actionAtPosition；其余入口（滑动/面板点选/悬浮球/动作分发）走 show 挂全屏指针窗口。 */
    fun onPointerStart(settings: hunoia.luno.config.model.GestureSettings.Pointer): Boolean
    fun onPointerShow(settings: hunoia.luno.config.model.GestureSettings.Pointer): Boolean
    fun onPointerEnd()
    fun onPointerActionAtPosition(x: Int, y: Int)

    /**
     * ActionPanel 是否正在显示：用于把主 overlay 在隐藏时收成 1x1，避免常驻一块什么都不画的全屏 RGBA_8888 buffer。
     * 与 [onActionPanelOverlayChanged] 是两件事：后者管面板交互时那个临时的可触摸空窗口。
     */
    fun onActionPanelVisibilityChanged(visible: Boolean)

    fun onAction(
        action: hunoia.luno.core.domain.StoredAction,
        sourceButton: GestureButton?,
        touchPosition: androidx.compose.ui.geometry.Offset,
        /** 触发这次动作的识别结果；面板点选等非直接触发为 null。 */
        decision: hunoia.luno.gesture.GestureDecision.Trigger?,
        /**
         * 启动类动作的小窗落点：手势直发 = [touchPosition]，动作面板 = 面板打开时的触点。
         * 与 [touchPosition] 分开，避免影响 `accessibility.tap` 的真实点击位置。
         */
        launchAnchor: androidx.compose.ui.geometry.Offset = touchPosition,
        /** 面板打开时的前台应用快照：全屏浮层会挡住无障碍窗口，执行「当前应用」时用它。 */
        foregroundHint: String? = null,
    )
}
