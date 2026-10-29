package hunoia.luno.shizuku

import android.os.SystemClock

/**
 * 以 shell 身份注入输入：
 * - [tap]/[swipe]/[longPress] 走 `input`，用于「被拦截但没有落到动作」之外的场景
 *   （按压取词等后续功能需要的按下/抬起/长按原语也在这里）；
 * - [keyEvent] 走 `input keyevent`，是无障碍全局动作的 shell 等价物；
 * - [expandNotifications]/[expandQuickSettings] 走 `cmd statusbar`。
 *
 * 这里只提供**平台原语**：谁该注入、注入了算什么结果，由 Executor 决定。
 * Trigger 侧不得直接调用本对象（架构 §2.3）。
 */
object ShizukuInputInjection {

    /** 注入点击；坐标是屏幕像素。 */
    suspend fun tap(x: Int, y: Int): Boolean =
        ShizukuManager.executeShell("input tap $x $y").isSuccess

    /** 注入滑动；坐标为屏幕像素，时长钳到 50..1500ms（与 `input` 的实现限制一致）。 */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        val duration = durationMs.coerceIn(50L, 1_500L)
        return ShizukuManager.executeShell("input swipe $x1 $y1 $x2 $y2 $duration").isSuccess
    }

    /**
     * 长按注入。`input` 没有原生 longpress-with-coords，用「原地点内滑动 + 足够时长」
     * 触发 View 的长按回调，这是 shell 侧唯一稳定的写法。
     */
    suspend fun longPress(x: Int, y: Int, durationMs: Long = 700L): Boolean =
        swipe(x, y, x, y, durationMs)

    /** 原始 motionevent 三步；给需要精确控制按下/抬起间隔的场景用。 */
    suspend fun motionEvent(action: String, x: Int, y: Int): Boolean =
        ShizukuManager.executeShell("input motionevent ${action.uppercase()} $x $y").isSuccess

    suspend fun keyEvent(keyCode: Int): Boolean =
        ShizukuManager.executeShell("input keyevent $keyCode").isSuccess

    suspend fun expandNotifications(): Boolean =
        ShizukuManager.executeShell("cmd statusbar expand-notifications").isSuccess

    suspend fun expandQuickSettings(): Boolean =
        ShizukuManager.executeShell("cmd statusbar expand-settings").isSuccess

    /** 注入窗口期：调用方用它屏蔽 getevent 里由自己注入的事件。 */
    fun suppressUntil(durationMs: Long): Long =
        SystemClock.uptimeMillis() + durationMs
}
