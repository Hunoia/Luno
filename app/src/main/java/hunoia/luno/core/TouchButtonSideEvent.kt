package hunoia.luno.core

/**
 * 触钮手势按物理触点归侧（触点 x < 屏宽/2 = 左）：悬浮球据此跟随持机手。
 * 悬浮球/分身/侧滑手势的 sourceButton 为 null，不会发本事件 —— 球的位置
 * 不反哺持机侧判定，避免自反馈回路。
 */
data class TouchButtonSideEvent(val leftSide: Boolean)
