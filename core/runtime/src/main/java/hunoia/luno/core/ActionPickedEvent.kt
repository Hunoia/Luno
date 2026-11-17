package hunoia.luno.core

import hunoia.luno.core.domain.StoredAction

/**
 * 「只挑一个动作」模式（`ActionSelect.copyDeepLink` 的同族用法）下选中动作的通知。
 *
 * 动作选择页是独立页面 / 独立 Activity，返回值不能靠 Activity Result 传（调用方可能
 * 在设置页深处、也可能在 `:service` 的宿主里）。这里走进程内 Events 总线：
 * 选择页发一次，订阅方取走即注销；[token] 让同一进程里的并发挑选互不串台。
 *
 * 与 [ActionSelectClosedEvent] 同一口径：双方同进程，不跨进程传输。
 */
class ActionPickedEvent(
    val token: String,
    val action: StoredAction,
)
