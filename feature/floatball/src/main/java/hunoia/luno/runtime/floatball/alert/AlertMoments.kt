package hunoia.luno.runtime.floatball.alert

import android.content.Context
import hunoia.luno.runtime.floatball.FloatBallCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * “时刻动效”对外入口（锁屏 / 触钮）。
 *
 * 悬浮球服务在跑时走服务的统一播放器（与到达提示 / 动作动效共享并发与舞台）；
 * 没跑时走同进程的一次性舞台兜底（`MomentLocalHost`），保证只开触钮也能播。
 * 两种后端不互相排队，这一点是已知口径。
 */
object AlertMoments {

    /**
     * 锁屏等待上限：关闭动效压黑后回调会立刻返回；超过这个上限就直接锁屏，
     * 不让异常动画卡住系统。上限 = 压黑上限 2.0s + 0.3s 余量。
     */
    const val LockScreenMaxWaitMs = 2_300L

    /**
     * 锁屏前播一次 `lockAlert`，等它压到全黑（最长 2.0s）/ 被跳过 / 超时 2.3s。
     * 压黑后窗口继续保持在屏，直到真正息屏；未配置 / 没有悬浮窗权限时立即返回。
     */
    suspend fun playScreenOff(context: Context) {
        awaitMomentFinished(
            timeoutMs = LockScreenMaxWaitMs,
            start = { finishOnce ->
                val command = FloatBallCommand.playLockMoment
                if (command != null) {
                    command(finishOnce)
                } else {
                    MomentLocalHost.playScreenOff(context.applicationContext, finishOnce)
                }
            },
        )
    }

    /**
     * 等一次时刻动效收尾：回调先到就立即返回，超时返回 false（锁屏照常继续）。
     * 纯协程实现，单测覆盖「回调不来也绝不卡住锁屏」。
     */
    internal suspend fun awaitMomentFinished(
        timeoutMs: Long,
        start: (onDone: () -> Unit) -> Unit,
    ): Boolean {
        val done = CompletableDeferred<Unit>()
        var completed = false
        start {
            if (!completed) {
                completed = true
                done.complete(Unit)
            }
        }
        return withTimeoutOrNull(timeoutMs) { done.await() } != null
    }

    /** 触钮按下：按该钮绑定的预设播一次，锚点跟手指。 */
    fun playTouch(context: Context, buttonId: String, x: Float, y: Float) {
        val command = FloatBallCommand.playTouchMoment
        if (command != null) {
            command(buttonId, x, y)
        } else {
            MomentLocalHost.playTouch(context.applicationContext, buttonId, x, y)
        }
    }

    /** 按住拖动：更新当前触钮动效的锚点（没有在播时就静默）。 */
    fun moveTouch(x: Float, y: Float) {
        val command = FloatBallCommand.moveTouchMoment
        if (command != null) command(x, y) else MomentLocalHost.moveTouch(x, y)
    }
}