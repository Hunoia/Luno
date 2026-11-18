package hunoia.luno.bridge.system

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import androidx.core.content.ContextCompat
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 无障碍截屏的公用入口（`ScreenTextPreCapture` / 标注回退共用一份）。
 *
 * 不隐藏任何浮层：调用方负责在截屏前把自家窗口软隐藏，否则会把自己截进去。
 * 失败保留系统错误码：`ERROR_INTERVAL_TIME_SHORT`（限流）由上层决定是否退避重试。
 *
 * **必须带回调超时**：系统接受请求后可能永远不回调（实测：浮层窗口刚置 INVISIBLE、
 * 窗口事务还没提交完时，SurfaceFlinger 已经合成过一帧，但结果没回到 App）。
 * 没有超时就是一个永久挂起的协程 —— `finally` 不跑、调用方的 busy 永真、
 * 浮层再也亮不回来，只能靠重绑无障碍服务或息屏收场。
 */
object ScreenScreenshot {

    /** `AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`。 */
    const val ERROR_INTERVAL_TIME_SHORT = 3

    /** `AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW`：目标含 FLAG_SECURE 窗口，系统强制拒绝。 */
    const val ERROR_TAKE_SCREENSHOT_SECURE_WINDOW = 5

    /**
     * 单次截屏的回调超时。正常端到端在 200ms 内（含 SurfaceFlinger 合成），
     * 给到 1.2s 足够覆盖慢帧，又不至于让用户等太久。
     */
    const val CALLBACK_TIMEOUT_MS = 1_200L

    sealed interface Result {
        data class Success(val bitmap: Bitmap) : Result
        data class Failure(val errorCode: Int) : Result

        /** 系统直接拒绝（服务未连接 / 权限失效），没有回调。 */
        data object Rejected : Result

        /** 请求被系统接受，但 [CALLBACK_TIMEOUT_MS] 内没有任何回调（系统丢回调）。 */
        data object Timeout : Result
    }

    suspend fun capture(
        service: AccessibilityService,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ): Result = awaitCallback("display#$displayId") { callback ->
        service.takeScreenshot(displayId, ContextCompat.getMainExecutor(service), callback)
    }

    /** 只截某个窗口（API 34+，识屏按下点定位用）。超时语义与 [capture] 相同。 */
    suspend fun captureWindow(service: AccessibilityService, windowId: Int): Result =
        awaitCallback("window#$windowId") { callback ->
            service.takeScreenshotOfWindow(
                windowId,
                ContextCompat.getMainExecutor(service),
                callback,
            )
        }

    /**
     * 统一收口：发请求 → 等回调 → 超时。
     *
     * [request] 只负责把平台调用发出去；返回 false 表示系统当场拒绝（无回调）。
     * 超时后迟到的回调必须自己回收位图：协程已经取消，没人会再消费它。
     */
    private suspend fun awaitCallback(
        label: String,
        request: (AccessibilityService.TakeScreenshotCallback) -> Unit,
    ): Result {
        val result = withTimeoutOrNull(CALLBACK_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(shot: AccessibilityService.ScreenshotResult) {
                        val bitmap = copyOf(shot)
                        if (continuation.isActive) {
                            continuation.resume(
                                if (bitmap != null) Result.Success(bitmap) else Result.Rejected,
                            )
                        } else if (bitmap != null && !bitmap.isRecycled) {
                            // 超时/取消之后才到的结果：直接回收，避免泄漏一张全屏位图。
                            bitmap.recycle()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (continuation.isActive) continuation.resume(Result.Failure(errorCode))
                    }
                }
                val accepted = runCatching { request(callback) }.isSuccess
                if (!accepted && continuation.isActive) continuation.resume(Result.Rejected)
            }
        }
        if (result == null) {
            Log.w(
                LOG_TAG,
                "screenshot $label: no callback in ${CALLBACK_TIMEOUT_MS}ms (system dropped it)",
            )
            return Result.Timeout
        }
        return result
    }

    /** 把硬件缓冲拷成普通位图；无论成败都关掉缓冲（不关会让 SurfaceFlinger 卡住一个 slot）。 */
    private fun copyOf(shot: AccessibilityService.ScreenshotResult): Bitmap? {
        val bitmap = runCatching {
            Bitmap.wrapHardwareBuffer(shot.hardwareBuffer, shot.colorSpace)
                ?.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
        runCatching { shot.hardwareBuffer.close() }
        return bitmap
    }
}
