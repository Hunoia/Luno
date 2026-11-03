package hunoia.luno.annotate.screenshot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.bridge.system.ScreenScreenshot
import hunoia.luno.core.LOG_TAG
import hunoia.luno.shizuku.ShizukuScreenshot
import kotlinx.coroutines.delay

/**
 * 标注专用截图链（计划 D5）：
 *
 * ```text
 * Shizuku screencap（无限流）
 *   → 失败 / 未就绪 → a11y takeScreenshot
 *       错误码 3（限流）→ 退避 ≥1s 重试一次
 *   → 仍失败 → null（宿主 toast，不进编辑器）
 * ```
 *
 * 调用方必须先软隐藏自家浮层（球 / 面板），否则会把自己截进底图；
 * 本函数只负责「拿图」，不碰 Surface，也不弹提示。
 */
object AnnotateScreenshot {

    private const val RATE_LIMIT_BACKOFF_MS = 1_100L

    suspend fun capture(service: AccessibilityService?): Bitmap? {
        ShizukuScreenshot.capture()?.let { return it }

        if (service == null) {
            Log.w(LOG_TAG, "annotate screenshot: no accessibility service for fallback")
            return null
        }

        var result = ScreenScreenshot.capture(service)
        if (result is ScreenScreenshot.Result.Failure &&
            result.errorCode == ScreenScreenshot.ERROR_INTERVAL_TIME_SHORT
        ) {
            delay(RATE_LIMIT_BACKOFF_MS)
            result = ScreenScreenshot.capture(service)
        }
        return when (result) {
            is ScreenScreenshot.Result.Success -> result.bitmap
            is ScreenScreenshot.Result.Failure -> {
                Log.w(LOG_TAG, "annotate screenshot failed: errorCode=${result.errorCode}")
                null
            }

            ScreenScreenshot.Result.Rejected -> {
                Log.w(LOG_TAG, "annotate screenshot rejected by system")
                null
            }
        }
    }
}
