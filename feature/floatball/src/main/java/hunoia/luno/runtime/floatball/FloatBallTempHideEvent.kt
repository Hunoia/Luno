package hunoia.luno.runtime.floatball

/**
 * 悬浮球临时隐藏广播（`internal.floatBallVisibility` 的 hideForMs 模式）。
 * 与面板事件同拓扑：执行器和 [FloatBallService] 同在 `:service` 进程，`Events` 直达。
 * 只作用运行时窗口、不碰 `floatBall.enabled` 存量 —— 恢复定时器在内存里，
 * 随进程被杀（应用更新/系统回收）一起消失是预期行为，球按落盘配置照常恢复。
 */
class FloatBallTempHideEvent(val durationMs: Long)
