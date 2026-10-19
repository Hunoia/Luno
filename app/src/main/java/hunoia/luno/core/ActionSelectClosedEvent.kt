package hunoia.luno.core

/**
 * 动作选择宿主（ActionSelectHostActivity）结束 → 网格面板浮窗回显。
 *
 * 双方都常驻 :service 进程，走进程内 Events 总线；浮窗收到后恢复显示，
 * 编辑态（editMode/本地项列表）原样继续 —— 组合从未销毁。
 *
 * 由宿主的 `onDestroy` 发（不是 `finish`）：任务被移除等没走 finish 的销毁路径
 * 也要唤醒面板，否则窗口会停在 alpha 0。宿主 manifest 上有 configChanges，
 * 转屏重建不会走到这里。
 */
class ActionSelectClosedEvent
