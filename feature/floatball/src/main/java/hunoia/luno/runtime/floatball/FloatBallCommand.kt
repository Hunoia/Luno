package hunoia.luno.runtime.floatball

/**
 * 手势服务向悬浮球下发的进程内同步命令口（同为 `:service` 进程）。
 *
 * 跟手换位必须在动作执行**之前**发生：走 `Events.post` 的话事件会晚于动作到达，
 * 球这时可能已被动作打开的面板/按压态占用，`repositionTo()` 直接失败且不重试，
 * 表现就是「动作触发了但球没换边」。触钮路径改为直接调这里；其他手势仍走事件，
 * 但两边最终都落到同一套「记录目标侧 + 球空闲时重试」逻辑。
 *
 * 由 [FloatBallService] 在 `onCreate` 注入、`onDestroy` 清空；球未运行时为 null。
 */
object FloatBallCommand {
    /**
     * 触点落在 [leftSide] 侧：请求球换到该侧。
     *
     * 返回值只表示球是否接收了这次请求（已在同侧 / 已开始换位 / 已排队重试）；
     * 动作照常执行，不受返回值影响。`false` = 跟手关闭、球不可见或命令未注册。
     */
    var requestFollowHandSwap: ((leftSide: Boolean) -> Boolean)? = null
}
