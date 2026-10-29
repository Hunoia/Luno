package hunoia.luno.runtime.gesturepanel

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import hunoia.luno.runtime.pointer.PointerCursor
import kotlinx.coroutines.delay

/**
 * 手势会话的渲染面：动作面板、指针光标。
 *
 * 会话状态机已经搬到 [GestureSessionPipeline]（输入源无关），这里只负责把状态画出来。
 * 输入不从这里进：无障碍窗口与内核触摸流分别经 `MotionEventInputSource` /
 * `ShizukuTouchInputSource` 投给同一份管线。
 */
@Composable
fun SideGestureContainer(
    pipeline: GestureSessionPipeline,
    modifier: Modifier = Modifier,
) {
    val actionPanelState = pipeline.actionPanelState
    val pointer = pipeline.pointer

    // 收起方向只在这里处理：缩窗要等淡出结束（缩窗本身由 Coordinator 收 onActionPanelVisibilityChanged）。
    // 面板在这段等待里重新打开会自动取消这次收缩。
    LaunchedEffect(actionPanelState.visible) {
        if (!actionPanelState.visible) {
            delay(ACTION_PANEL_FADE_OUT_MS)
            pipeline.notifyActionPanelHidden()
        }
    }

    if (pointer.isActive) {
        PointerCursor(
            position = pointer.position,
            modifier = modifier,
            settings = pointer.settings,
            clickPulseKey = pointer.clickPulseKey,
        )
    }

    Box(modifier = modifier) {
        ActionPanelMenu(
            actionPanelStyle = pipeline.currentSettings.actionPanelStyle,
            actionPanelState = actionPanelState,
            modifier = Modifier.matchParentSize(),
            gestureSettings = pipeline.currentSettings.gestureSettings,
        )
    }
}
