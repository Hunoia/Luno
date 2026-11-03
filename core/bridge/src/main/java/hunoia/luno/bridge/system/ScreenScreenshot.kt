package hunoia.luno.bridge.system

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 无障碍截屏的公用入口（`ScreenTextPreCapture` / 标注回退共用一份）。
 *
 * 不隐藏任何浮层：调用方负责在截屏前把自家窗口软隐藏，否则会把自己截进去。
 * 失败保留系统错误码：`ERROR_INTERVAL_TIME_SHORT`（限流）由上层决定是否退避重试。
 */
object ScreenScreenshot {

    /** `AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`。 */
    const val ERROR_INTERVAL_TIME_SHORT = 3

    sealed interface Result {
        data class Success(val bitmap: Bitmap) : Result
        data class Failure(val errorCode: Int) : Result

        /** 系统直接拒绝（服务未连接 / 权限失效），没有回调。 */
        data object Rejected : Result
    }

    suspend fun capture(
        service: AccessibilityService,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ): Result = suspendCancellableCoroutine { continuation ->
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val bitmap = runCatching {
                    Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                }.getOrNull()
                runCatching { result.hardwareBuffer.close() }
                if (continuation.isActive) {
                    continuation.resume(
                        if (bitmap != null) Result.Success(bitmap) else Result.Rejected,
                    )
                }
            }

            override fun onFailure(errorCode: Int) {
                if (continuation.isActive) continuation.resume(Result.Failure(errorCode))
            }
        }
        val accepted = runCatching {
            service.takeScreenshot(displayId, ContextCompat.getMainExecutor(service), callback)
        }.isSuccess
        if (!accepted && continuation.isActive) {
            continuation.resume(Result.Rejected)
        }
    }
}
