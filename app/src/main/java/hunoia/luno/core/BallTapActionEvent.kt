package hunoia.luno.core

import hunoia.luno.action.model.StoredAction

/**
 * 悬浮球点按 → 容器的事件。锚点 = 球心。
 *
 * 容器收到后交给 `runGestureActions` 统一分类执行（与触钮同链路）：
 * 面板动作开 ArcPanel 待选；`internal.subGesture` 进子手势态等下一根手指；
 * 音量刮擦走专用浮层；其余动作按直发处理。
 */
data class BallTapActionEvent(
    val action: StoredAction,
    val anchorX: Int,
    val anchorY: Int,
    val color: Int,
)
