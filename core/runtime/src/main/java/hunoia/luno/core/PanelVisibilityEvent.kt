package hunoia.luno.core

/**
 * 面板类浮层（网格面板 / 动作面板 / 快速搜索 / 快速启动 / 音量刮擦 / 子手势）的开关广播。
 * 同在 `:service` 进程，`Events` 直达，无需 IPC。悬浮球按计数自动隐藏：Shown 在浮层
 * 立起来后发（attach 成功 / onCreate），Dismissed 在退场动画结束后发，归零后才恢复。
 * 「隐身但未关闭」的路径（编辑态让位等）不发 Dismissed。
 */
class PanelShownEvent

/** 与 [PanelShownEvent] 配对的关闭广播，退场动画播完才发。 */
class PanelDismissedEvent
