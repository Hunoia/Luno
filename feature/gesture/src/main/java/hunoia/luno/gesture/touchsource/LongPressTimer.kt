package hunoia.luno.gesture.touchsource

import android.os.Handler
import android.os.Looper

/** 可替换的定时器：生产实现挂在主线程，JVM 测试用假时钟手动推进。 */
fun interface LongPressTimer {
    fun schedule(delayMs: Long, task: () -> Unit): Cancelable
}

fun interface Cancelable {
    fun cancel()
}

/** 主线程 Handler 定时器：手势会话的 job/震动/面板都按主线程写，这里不另起线程。 */
class MainThreadTimer : LongPressTimer {
    private val handler = Handler(Looper.getMainLooper())

    override fun schedule(delayMs: Long, task: () -> Unit): Cancelable {
        val runnable = Runnable { task() }
        handler.postDelayed(runnable, delayMs)
        return Cancelable { handler.removeCallbacks(runnable) }
    }
}
