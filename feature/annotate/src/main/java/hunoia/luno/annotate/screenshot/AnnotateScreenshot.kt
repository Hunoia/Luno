package hunoia.luno.annotate.screenshot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.bridge.system.ScreenScreenshot
import hunoia.luno.core.LOG_TAG
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 标注专用截图链（计划 D5）——纯无障碍，不用 Shizuku：
 *
 * ```text
 * a11y takeScreenshot（平台原生、最快）
 *   → 成功即返回
 *   → 错误码 3（限流）→ 退避 ≥1s 后重试一次
 *   → 错误码 5（secure 窗口）→ Failed(SecureWindow)，不重试
 *   → 其它失败 / 被拒 / 回调超时 → Failed(Unavailable)
 * ```
 *
 * 调用方必须先软隐藏自家浮层（球 / 面板），否则会把自己截进底图；
 * 本函数只负责「拿图」，不碰 Surface，也不弹提示。
 *
 * **不用 Shizuku**：本机 Shizuku 的 `screencap -p` 通道被租约包装卡到 EOF 永不返回，
 * 只能靠超时强关；a11y 实测 24–31ms 且稳定。这里统一走 a11y（与识屏
 * `ScreenTextPreCapture` 同链路），Shizuku 截图代码已删除。
 *
 * **secure 窗口不可绕过**：目标含 `FLAG_SECURE` 时系统强制拒绝（错误码 5），
 * 重试只会再撞同码，所以直接失败并带 [CaptureResult.Reason.SecureWindow]，
 * 宿主据此提示「该应用禁止截屏」。
 *
 * 每段都有超时（见 [TOTAL_TIMEOUT_MS] 与 `ScreenScreenshot.CALLBACK_TIMEOUT_MS`）：
 * 这条链上任何一步挂住都会让宿主停在「两窗已隐藏、busy 永真」的死状态。
 */
object AnnotateScreenshot {

    /** 截图结果：宿主据此选提示文案（失败不再只是一句笼统 toast）。 */
    sealed interface CaptureResult {
        data class Success(val bitmap: Bitmap) : CaptureResult
        data class Failed(val reason: Reason) : CaptureResult

        enum class Reason {
            /** 整条链超预算 / 系统丢回调。 */
            Timeout,

            /** 目标含 `FLAG_SECURE` 窗口，系统拒绝截图（应用级不可绕过）。 */
            SecureWindow,

            /** 服务未连接 / 被拒 / 其它错误码。 */
            Unavailable,
        }
    }

    private const val RATE_LIMIT_BACKOFF_MS = 1_100L

    /**
     * 整条链的总预算。最坏 = a11y 超时(1.2s) + 退避(1.1s) + 重试(1.2s) ≈ 3.5s；
     * 超出即判定截图不可用，让宿主马上 toast 收场。
     */
    private const val TOTAL_TIMEOUT_MS = 4_000L

    suspend fun capture(service: AccessibilityService?): CaptureResult {
        val startedAt = SystemClock.elapsedRealtime()
        var finished = false
        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            captureChain(service, startedAt).also { finished = true }
        }
        if (!finished) {
            Log.w(LOG_TAG, "annotate screenshot: total timeout ${TOTAL_TIMEOUT_MS}ms")
            return CaptureResult.Failed(CaptureResult.Reason.Timeout)
        }
        // finished == true 时 lambda 一定返回非空；`?:` 仅为类型收窄。
        return result ?: CaptureResult.Failed(CaptureResult.Reason.Timeout)
    }

    private suspend fun captureChain(service: AccessibilityService?, startedAt: Long): CaptureResult {
        if (service == null) {
            Log.w(LOG_TAG, "annotate screenshot: no accessibility service")
            return CaptureResult.Failed(CaptureResult.Reason.Unavailable)
        }

        var result = ScreenScreenshot.capture(service)
        Log.i(LOG_TAG, "annotate screenshot: a11y ${describe(result)} (${elapsed(startedAt)}ms)")
        when (result) {
            is ScreenScreenshot.Result.Success ->
                return successOrSecure(result.bitmap)

            is ScreenScreenshot.Result.Failure -> when (result.errorCode) {
                ScreenScreenshot.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> {
                    // FLAG_SECURE 窗口：重试也只会再撞同一个错误码，直接失败，不浪费一次往返。
                    Log.w(LOG_TAG, "annotate screenshot: secure window blocks capture")
                    return CaptureResult.Failed(CaptureResult.Reason.SecureWindow)
                }

                ScreenScreenshot.ERROR_INTERVAL_TIME_SHORT -> {
                    // 限流（1s 内第二次）：退避到限流窗口过去，重试一次。
                    delay(RATE_LIMIT_BACKOFF_MS)
                    result = ScreenScreenshot.capture(service)
                    Log.i(
                        LOG_TAG,
                        "annotate screenshot: a11y retry ${describe(result)} (${elapsed(startedAt)}ms)",
                    )
                    return when (result) {
                        is ScreenScreenshot.Result.Success -> successOrSecure(result.bitmap)
                        else -> CaptureResult.Failed(CaptureResult.Reason.Unavailable)
                    }
                }

                else -> return CaptureResult.Failed(CaptureResult.Reason.Unavailable)
            }

            ScreenScreenshot.Result.Rejected,
            ScreenScreenshot.Result.Timeout,
            -> return CaptureResult.Failed(CaptureResult.Reason.Unavailable)
        }
    }

    /**
     * a11y「成功」的位图可能其实是 secure 合成：部分 ROM 不报错误码 5，而是返回一张
     * 纯黑位图（SurfaceFlinger 把 `FLAG_SECURE` 层合成成黑）。这里识别成全黑 → 按
     * secure 处理（不进入黑底标注态），并回收这张没用的位图。
     */
    private fun successOrSecure(bitmap: Bitmap): CaptureResult {
        if (isSecureBlack(bitmap)) {
            Log.w(LOG_TAG, "annotate screenshot: all-black frame (secure window composite)")
            if (!bitmap.isRecycled) bitmap.recycle()
            return CaptureResult.Failed(CaptureResult.Reason.SecureWindow)
        }
        return CaptureResult.Success(bitmap)
    }

    /** 稀疏采样判断是否近乎纯黑；secure 合成是全黑，正常屏幕极少全黑。 */
    private fun isSecureBlack(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return false
        val stepX = (w / 16).coerceAtLeast(1)
        val stepY = (h / 16).coerceAtLeast(1)
        var x = 0
        while (x < w) {
            var y = 0
            while (y < h) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) > 8 || Color.green(p) > 8 || Color.blue(p) > 8) return false
                y += stepY
            }
            x += stepX
        }
        return true
    }

    private fun describe(result: ScreenScreenshot.Result): String = when (result) {
        is ScreenScreenshot.Result.Success -> "ok ${result.bitmap.width}x${result.bitmap.height}"
        is ScreenScreenshot.Result.Failure -> "failure(errorCode=${result.errorCode})"
        ScreenScreenshot.Result.Rejected -> "rejected"
        ScreenScreenshot.Result.Timeout -> "timeout"
    }

    private fun elapsed(startedAt: Long): Long = SystemClock.elapsedRealtime() - startedAt
}
