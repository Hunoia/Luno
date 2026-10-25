package hunoia.luno.core

/**
 * 真手势（触钮/侧滑/子手势滑动，decision 非空）按物理触点归侧
 * （触点 x < 屏宽/2 = 左）：悬浮球据此即时跟到触发侧。面板内点击/球点按的
 * decision 为 null，不会发本事件 —— 点面板部件不拽球，球的位置也不反哺判定。
 */
data class TouchButtonSideEvent(val leftSide: Boolean)
