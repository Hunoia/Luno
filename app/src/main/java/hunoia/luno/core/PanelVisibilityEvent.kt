package hunoia.luno.core

/**
 * 面板类浮层（网格面板 / 动作面板 / 快速搜索 / 快速启动 / 音量刮擦 / 子手势）
 * 的开关广播。全组件同在 `:service` 进程，`Events` 直达，无需 IPC。
 *
 * 悬浮球订阅这对事件做「面板打开时自动隐藏球」：show 计数 +1 摘掉球窗，
 * dismiss 计数 -1，归零后原地淡入恢复（spring 250ms）。多面板叠加时
 * 以最后一个关闭为准，计数器天然处理。
 *
 * 发贴位置约定：
 * - Shown 在浮层**真正立起来**之后（attach 成功 / Activity onCreate）；
 * - Dismissed 在**退场动画结束**（移窗 / finish）之后，编辑态让位这类
 *   「隐身但未关闭」的路径不发。
 */
class PanelShownEvent

/** 与 [PanelShownEvent] 配对的关闭广播，退场动画播完才发。 */
class PanelDismissedEvent
