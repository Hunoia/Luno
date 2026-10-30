package hunoia.luno.runtime.overlay.text

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import hunoia.luno.core.LOG_TAG
import hunoia.luno.runtime.overlay.PickCapture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一次按下时预捕获的内容：a11y 文本快照 + 菜单前截图。 */
data class ScreenPreCapture(
    val text: TextSnapshot?,
    val capture: PickCapture?,
)

/**
 * 全局长按的预捕获：DOWN 后 100ms 仍按住时抓一次 a11y 快照与截图。
 *
 * 目的不是“提前弹出卡片”，而是在应用自己的长按菜单出现前把内容固定下来。
 * 轻点/提前滑动由 [cancel] 丢弃，不付遍历成本。
 */
class ScreenTextPreCapture(
    private val service: AccessibilityService,
    private val ownPackage: String,
    private val scope: CoroutineScope,
    private val delayMs: Long = 100L,
) {

    private val reader = A11yTextSnapshotReader(service, ownPackage)
    private val mainExecutor = Executor { runnable -> Handler(Looper.getMainLooper()).post(runnable) }

    private var job: Job? = null
    private var generation = 0
    private var text: TextSnapshot? = null
    private var capture: PickCapture? = null
    private var handedOff = false

    /** 按下时调用；重复按下会取消上一次未完成的捕获。 */
    fun start(x: Int, y: Int) {
        cancel()
        val token = ++generation
        val startedAt = SystemClock.uptimeMillis()
        handedOff = false
        job = scope.launch {
            delay(delayMs)
            launch {
                val captured = withContext(Dispatchers.Default) {
                    runCatching { reader.capture(x, y, startedAt) }
                        .onFailure { Log.w(LOG_TAG, "screen text pre-capture failed: ${it.message}") }
                        .getOrNull()
                }
                if (token == generation) text = captured
            }
            launch {
                val captured = captureScreenshot()
                if (token == generation) capture = captured
            }
        }
    }

    /** 长按触发时取走预捕获；没有就返回 null，由 overlay 走截图/节点回退。 */
    fun peek(): ScreenPreCapture? {
        val current = ScreenPreCapture(text = text, capture = capture)
        if (current.text == null && current.capture == null) return null
        handedOff = true
        return current
    }

    /** 松手/取消长按：丢弃未完成结果，避免下一次会话误用。 */
    fun cancel() {
        generation++
        job?.cancel()
        job = null
        text = null
        if (!handedOff) capture?.bitmap?.let { if (!it.isRecycled) it.recycle() }
        capture = null
        handedOff = false
    }

    /** 截当前目标应用窗口；API 34+ 优先窗口截图，失败回退整屏。 */
    private suspend fun captureScreenshot(): PickCapture? {
        val window = topApplicationWindow()
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val bounds = android.graphics.Rect()
            window.getBoundsInScreen(bounds)
            val shot = takeWindowScreenshot(window.id)
            if (shot != null) {
                return PickCapture(
                    bitmap = shot,
                    originX = bounds.left,
                    originY = bounds.top,
                    screenW = bounds.width(),
                    screenH = bounds.height(),
                )
            }
        }
        val shot = takeDisplayScreenshot() ?: return null
        return PickCapture(
            bitmap = shot,
            originX = 0,
            originY = 0,
            screenW = hunoia.luno.bridge.ui.DensityProvider.screenWidthPx,
            screenH = hunoia.luno.bridge.ui.DensityProvider.screenHeightPx,
        )
    }

    private suspend fun takeWindowScreenshot(windowId: Int): Bitmap? =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val bitmap = runCatching {
                        val hardware = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer,
                            result.colorSpace,
                        )
                        hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    }.getOrNull()
                    runCatching { result.hardwareBuffer.close() }
                    if (continuation.isActive) continuation.resumeWith(Result.success(bitmap))
                }

                override fun onFailure(errorCode: Int) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(null))
                }
            }
            val accepted = runCatching {
                service.takeScreenshotOfWindow(windowId, mainExecutor, callback)
            }.isSuccess
            if (!accepted && continuation.isActive) {
                continuation.resumeWith(Result.success(null))
            }
        }

    private suspend fun takeDisplayScreenshot(): Bitmap? =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val bitmap = runCatching {
                        val hardware = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer,
                            result.colorSpace,
                        )
                        hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    }.getOrNull()
                    runCatching { result.hardwareBuffer.close() }
                    if (continuation.isActive) continuation.resumeWith(Result.success(bitmap))
                }

                override fun onFailure(errorCode: Int) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(null))
                }
            }
            val accepted = runCatching {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
            }.isSuccess
            if (!accepted && continuation.isActive) {
                continuation.resumeWith(Result.success(null))
            }
        }

    private fun topApplicationWindow(): AccessibilityWindowInfo? {
        val windows = runCatching { service.windows }.getOrNull() ?: return null
        for (index in windows.indices.reversed()) {
            val window = windows.getOrNull(index) ?: continue
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull()
            if (pkg == null || pkg == ownPackage) continue
            return window
        }
        return null
    }
}
