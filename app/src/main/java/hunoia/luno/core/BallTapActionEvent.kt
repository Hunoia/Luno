package hunoia.luno.core

import hunoia.luno.action.model.StoredAction

/**
 * 悬浮球点按 → 容器的事件，锚点 = 球心。容器交给 `runGestureActions` 统一分类执行
 *（与触钮同链路）：面板动作开 ArcPanel、`internal.subGesture` 进子手势态、
 * 音量刮擦走专用浮层，其余直发。
 */
data class BallTapActionEvent(
    val action: StoredAction,
    val anchorX: Int,
    val anchorY: Int,
    val color: Int,
)
