package hunoia.luno.core

import hunoia.luno.config.model.ColorSpec
import hunoia.luno.core.domain.StoredAction

/**
 * 悬浮球点按 → 容器的事件，锚点 = 球心。容器交给 `runGestureActions` 统一分类执行
 *（与触钮同链路）：面板动作开 ArcPanel、`internal.subGesture` 进子手势态、
 * 音量刮擦走专用浮层，其余直发。
 *
 * [colorSpec] 传的是颜色来源快照而非解析后的 ARGB：面板要等渲染时才按主题实时取色。
 * [foregroundHint] 是网格面板打开时的前台应用快照（空串 = 桌面/无应用，null = 未采集）。
 */
data class BallTapActionEvent(
    val action: StoredAction,
    val anchorX: Int,
    val anchorY: Int,
    val colorSpec: ColorSpec,
    val foregroundHint: String? = null,
)
