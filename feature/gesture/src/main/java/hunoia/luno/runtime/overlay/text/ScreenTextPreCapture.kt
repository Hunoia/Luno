package hunoia.luno.runtime.overlay.text

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import hunoia.luno.core.LunoLog as Log
import android.view.accessibility.AccessibilityWindowInfo
import hunoia.luno.core.LOG_TAG
import hunoia.luno.runtime.overlay.PickCapture
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 一次按下时预捕获的句柄：a11y 文本快照 + 菜单前截图。
 *
 * 两个结果都可能是异步晚到的。消费方（[hunoia.luno.runtime.overlay.ScreenPickOverlay]）
 * 必须等待这里按下时启动的捕获，而不能在触发瞬间重新读节点 —— 全屏手势不拦截触摸，
 * 触发时应用自己的长按菜单已经弹出，现场读到的就是菜单内容。
 */
class ScreenPreCapture internal constructor() {

    private val textDeferred = CompletableDeferred<TextSnapshot?>()
    private val captureDeferred = CompletableDeferred<PickCapture?>()

    @Volatile
    private var textValue: TextSnapshot? = null

    @Volatile
    private var captureValue: PickCapture? = null

    @Volatile
    private var discarded = false

    val text: TextSnapshot? get() = textValue

    val capture: PickCapture? get() = captureValue

    /** 结果是否还在图中：消费方据此决定要不要等待，而不是直接走触发时回退。 */
    val awaitingText: Boolean get() = textValue == null && !textDeferred.isCompleted

    val awaitingCapture: Boolean get() = captureValue == null && !captureDeferred.isCompleted

    internal fun completeText(value: TextSnapshot?) {
        if (textDeferred.isCompleted) return
        textValue = value
        textDeferred.complete(value)
    }

    internal fun completeCapture(value: PickCapture?) {
        if (captureDeferred.isCompleted) return
        val accepted = if (discarded) null else value
        if (discarded) value?.recycleBitmap()
        captureValue = accepted
        captureDeferred.complete(accepted)
    }

    suspend fun awaitText(timeoutMs: Long): TextSnapshot? =
        textValue ?: withTimeoutOrNull(timeoutMs) { textDeferred.await() }

    suspend fun awaitCapture(timeoutMs: Long): PickCapture? =
        captureValue ?: withTimeoutOrNull(timeoutMs) { captureDeferred.await() }

    /** 注册表丢弃未消费的预捕获：位图可能还在异步路上，到了直接回收。 */
    fun discard() {
        discarded = true
        captureValue?.recycleBitmap()
    }

    private fun PickCapture.recycleBitmap() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

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
    private var handle: ScreenPreCapture? = null
    private var handedOff = false

    /** 按下时调用；重复按下会取消上一次未完成的捕获。 */
    fun start(x: Int, y: Int) {
        cancel()
        val startedAt = SystemClock.uptimeMillis()
        val next = ScreenPreCapture()
        handle = next
        handedOff = false
        job = scope.launch {
            delay(delayMs)
            launch {
                val captured = withContext(Dispatchers.Default) {
                    runCatching { reader.capture(x, y, startedAt) }
                        .onFailure { Log.w(LOG_TAG, "screen text pre-capture failed: ${it.message}") }
                        .getOrNull()
                }
                next.completeText(captured)
            }
            launch {
                val captured = captureScreenshot()
                next.completeCapture(captured)
            }
        }
    }

    /**
     * 长按触发时取走预捕获句柄。
     *
     * 即使文字/截图还在途中也返回句柄 —— 调用方必须等按下时启动的捕获，
     * 不能因为 `text == null` 就在触发瞬间重新读节点（那会读到应用的长按菜单）。
     */
    fun peek(): ScreenPreCapture? {
        val current = handle ?: return null
        handedOff = true
        return current
    }

    /** 松手/取消长按：丢弃未完成结果，避免下一次会话误用。 */
    fun cancel() {
        // 已经被触发侧取走的句柄不能在抬手时杀掉：消费方还要等它完成。
        if (handedOff) {
            handedOff = false
            return
        }
        job?.cancel()
        job = null
        handle?.discard()
        handle?.completeText(null)
        handle?.completeCapture(null)
        handle = null
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

    private suspend fun takeDisplayScreenshot(): Bitmap? {
        // 截屏入口统一在 bridge，避免与标注回归各写一份回调。
        val result = hunoia.luno.bridge.system.ScreenScreenshot.capture(service)
        return (result as? hunoia.luno.bridge.system.ScreenScreenshot.Result.Success)?.bitmap
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
