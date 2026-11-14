package hunoia.luno.runtime.floatball

/**
 * 手势服务向悬浮球下发的进程内同步命令口（同为 `:service` 进程）。
 *
 * 跟手换位必须在动作执行**之前**发生：走 `Events.post` 的话事件会晚于动作到达，
 * 球这时可能已被动作打开的面板/按压态占用，`repositionTo()` 直接失败且不重试，
 * 表现就是「动作触发了但球没换边」。触钮路径改为直接调这里；其他手势仍走事件，
 * 但两边最终都落到同一套「记录目标侧 + 球空闲时重试」逻辑。
 *
 * 一次触点侧别由 [FloatBallService] 扇出给主球与两个内容球，各自按自己的
 * 跟手开关独立处理 —— 只分发主球会让胶囊永远换不过去。
 *
 * 由 [FloatBallService] 在 `onCreate` 注入、`onDestroy` 清空；球未运行时为 null。
 */
object FloatBallCommand {
    /**
     * 触点落在 [leftSide] 侧：请求三球各自换到该侧。
     *
     * 返回值只表示是否有球接收了这次请求（任一球跟手开启且已排上/已开始换位）；
     * 动作照常执行，不受返回值影响。`false` = 三球都没接（跟手全关 / 全不可见）或命令未注册。
     */
    var requestFollowHandSwap: ((leftSide: Boolean) -> Boolean)? = null
}
