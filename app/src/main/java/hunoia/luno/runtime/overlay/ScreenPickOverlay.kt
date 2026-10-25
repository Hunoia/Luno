package hunoia.luno.runtime.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect as AndroidRect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import hunoia.luno.R
import hunoia.luno.bridge.system.copyImage
import hunoia.luno.bridge.system.copyText
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.Paths
import hunoia.luno.runtime.GestureHost
import java.io.File
import java.util.concurrent.Executor
import kotlin.math.roundToInt

/** 框选区域小于该边长（px）视为误操作，按轻点收口。 */
private const val SCREEN_PICK_MIN_REGION_PX = 12f

/**
 * 屏幕内容识别拾取层（动作 accessibility.screenPick）：全屏透明 a11y 窗，照 PointPickOverlay
 * 的窗口套路。轻点 = 节点树取字（含文本直接复制；无文本取图片类节点 bounds，再退到包含
 * 触点的最小节点 bounds 兜底，如视频画面）；按住拖动 = 框选区域。
 * 文字进剪贴板；图片从开层时的预截屏裁剪（先截屏再挂任何 UI，截图里永远没有本层像素），
 * PNG 写缓存 → FileProvider URI → 写剪贴板并弹系统分享。
 */
internal class ScreenPickOverlay(private val host: GestureHost) {

    private companion object {
        /** 无障碍 overlay 不带这个标志时触摸会被系统拦掉（Android 12 起），只能照发。 */
        const val TRUSTED_OVERLAY_FLAG = 0x300000

        const val SCREEN_PICK_LABEL = "screen_pick"
        const val SCREEN_PICK_DIR = "screen_pick"

        /** 节点树 DFS 深度上限：常规应用一屏远达不到，防恶意/病态树卡主线程。 */
        const val MAX_NODE_DEPTH = 30

        /** 图片候选的最小边长（px）：小于它的 Image 类节点多半是装饰图标，裁出来没意义。 */
        const val MIN_IMAGE_PX = 24

        /** 拾取产物缓存保留时长：分享/粘贴方一般在短时间内读取，过期即清。 */
        const val STALE_MS = 60L * 60 * 1000
    }

    private val mainExecutor = Executor { r -> Handler(Looper.getMainLooper()).post(r) }

    private var overlayView: View? = null
    private var onDismiss: (() -> Unit)? = null

    // 截屏与命中结果只在主线程读写；captured 走 Compose 态驱动提示条渲染。
    private var capture: Bitmap? = null
    private var captured by mutableStateOf(false)
    private var viewSize = IntSize.Zero

    fun show() {
        if (overlayView != null) return
        val wm = host.context.windowManager()
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                TRUSTED_OVERLAY_FLAG
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
        }
        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)
            setContent {
                ScreenPickScreen(
                    captured = captured,
                    onSizeChanged = { viewSize = it },
                    onTap = ::onTap,
                    onSelectRect = ::onSelectRect,
                    onDismiss = ::close,
                )
            }
        }
        // 先截屏再挂窗：任何本层 UI 都不会出现在截图里；回调到了才亮提示条。
        if (!takeScreenshot()) {
            showToast(R.string.screen_pick_failed)
            return
        }
        if (!wm.safeAddView(composeView, lp)) {
            Log.e(LOG_TAG, "screen pick: addView failed type=touch_trusted")
            showToast(R.string.screen_pick_failed)
            return
        }
        overlayView = composeView
    }

    fun close() {
        val view = overlayView ?: return
        val dismiss = onDismiss
        onDismiss = null
        overlayView = null
        capture = null
        captured = false
        viewSize = IntSize.Zero
        val wm = host.context.windowManager()
        runCatching { wm.removeView(view) }
        dismiss?.invoke()
    }

    // ---- 结算 ----

    private fun onTap(px: Offset) {
        val hit = findNodeHitAt(px.x.roundToInt(), px.y.roundToInt())
        val text = hit?.text
        if (!text.isNullOrBlank()) {
            copyText(host.context, SCREEN_PICK_LABEL, text)
            showToast(R.string.quick_search_copied)
            close()
            return
        }
        val bounds = hit?.imageBounds ?: hit?.fallbackBounds
        when {
            bounds == null -> showToast(R.string.screen_pick_no_content)
            !copyImageRegion(AndroidRect(bounds)) -> showToast(R.string.screen_pick_failed)
        }
        close()
    }

    private fun onSelectRect(rect: Rect) {
        if (rect.width < SCREEN_PICK_MIN_REGION_PX || rect.height < SCREEN_PICK_MIN_REGION_PX) return
        if (!copyImageRegion(AndroidRect(rect.left.roundToInt(), rect.top.roundToInt(), rect.right.roundToInt(), rect.bottom.roundToInt()))) {
            showToast(R.string.screen_pick_failed)
        }
        close()
    }

    private fun copyImageRegion(rect: AndroidRect): Boolean {
        val cap = capture ?: return false
        if (viewSize.width <= 0 || viewSize.height <= 0) return false
        // 窗口铺满整屏，视图坐标 ≈ 显示坐标；位图分辨率与逻辑分辨率可能不同，按比例换算。
        val sx = cap.width.toFloat() / viewSize.width
        val sy = cap.height.toFloat() / viewSize.height
        val crop = AndroidRect(
            (rect.left * sx).roundToInt().coerceIn(0, cap.width),
            (rect.top * sy).roundToInt().coerceIn(0, cap.height),
            (rect.right * sx).roundToInt().coerceIn(0, cap.width),
            (rect.bottom * sy).roundToInt().coerceIn(0, cap.height),
        )
        if (crop.isEmpty || crop.width() <= 0 || crop.height() <= 0) return false
        return runCatching {
            val context = host.context
            val cropped = Bitmap.createBitmap(cap, crop.left, crop.top, crop.width(), crop.height())
            val dir = File(Paths.AppCache, SCREEN_PICK_DIR).apply { mkdirs() }
            sweepStale(dir)
            val file = File(dir, "capture_${System.currentTimeMillis()}.png")
            file.outputStream().use { out -> cropped.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            if (!copyImage(context, SCREEN_PICK_LABEL, uri)) return@runCatching false
            showToast(R.string.screen_pick_image_copied)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(share, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.onFailure {
            Log.e(LOG_TAG, "screen pick: copy region failed: ${it.message}")
        }.getOrDefault(false)
    }

    /** 缓存目录只留近 [STALE_MS] 的产物：分享/粘贴方一般很快就读，过期即清。 */
    private fun sweepStale(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_MS
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    // ---- 截屏 ----

    private fun takeScreenshot(): Boolean = runCatching {
        host.accessibilityService.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val copied = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        if (copied == null) {
                            pickFailed()
                        } else {
                            capture = copied
                            captured = true
                        }
                    } finally {
                        result.hardwareBuffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    pickFailed()
                }

                private fun pickFailed() {
                    showToast(R.string.screen_pick_failed)
                    close()
                }
            },
        )
    }.onFailure {
        Log.e(LOG_TAG, "screen pick: takeScreenshot rejected: ${it.message}")
    }.isSuccess

    // ---- 节点查找 ----

    private data class NodeHit(
        val text: String?,
        val imageBounds: AndroidRect?,
        val fallbackBounds: AndroidRect?,
    )

    /** 从最上层包含触点的应用窗口开始找（跳过自家 overlay 与输入法窗）。 */
    private fun findNodeHitAt(x: Int, y: Int): NodeHit? {
        val windows = host.accessibilityService.windows ?: return null
        for (index in windows.indices.reversed()) {
            val window = windows.getOrNull(index) ?: continue
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            val windowBounds = AndroidRect()
            window.getBoundsInScreen(windowBounds)
            if (!windowBounds.contains(x, y)) continue
            return searchNode(root, x, y)
        }
        return null
    }

    private fun searchNode(root: AccessibilityNodeInfo, x: Int, y: Int): NodeHit {
        var bestText: String? = null
        var bestTextDepth = -1
        var bestImage: AndroidRect? = null
        var bestImageDepth = -1
        var bestFallback: AndroidRect? = null
        var bestFallbackDepth = -1

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_NODE_DEPTH) return
            val bounds = AndroidRect()
            node.getBoundsInScreen(bounds)
            // 父不含触点即剪枝：常规布局子节点都在父 bounds 内；框选是另一条兜底路。
            if (!bounds.contains(x, y)) return
            if (node.isVisibleToUser) {
                val text = node.text
                if (!text.isNullOrBlank() && depth > bestTextDepth) {
                    bestTextDepth = depth
                    bestText = text.toString()
                }
                val className = node.className?.toString().orEmpty()
                if (className.contains("Image") &&
                    bounds.width() >= MIN_IMAGE_PX &&
                    bounds.height() >= MIN_IMAGE_PX &&
                    depth > bestImageDepth
                ) {
                    bestImageDepth = depth
                    bestImage = bounds
                }
                if (depth > bestFallbackDepth) {
                    bestFallbackDepth = depth
                    bestFallback = bounds
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { walk(it, depth + 1) }
            }
        }

        walk(root, 0)
        return NodeHit(bestText, bestImage, bestFallback)
    }
}

@Composable
private fun ScreenPickScreen(
    captured: Boolean,
    onSizeChanged: (IntSize) -> Unit,
    onTap: (Offset) -> Unit,
    onSelectRect: (Rect) -> Unit,
    onDismiss: () -> Unit,
) {
    var dragRect by remember { mutableStateOf<Rect?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged(onSizeChanged)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.isConsumed) return@awaitEachGesture
                    var pastSlop = false
                    val start = down.position
                    var upPosition = down.position
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        upPosition = change.position
                        // 提示条上的点按被子级消费：让给子级，本层不动作。
                        if (change.isConsumed) break
                        if (!change.pressed) break
                        change.consume()
                        if (!pastSlop) {
                            val delta = change.position - start
                            if (delta.getDistance() > slop) pastSlop = true
                        }
                        if (pastSlop) {
                            dragRect = Rect(
                                left = minOf(start.x, change.position.x),
                                top = minOf(start.y, change.position.y),
                                right = maxOf(start.x, change.position.x),
                                bottom = maxOf(start.y, change.position.y),
                            )
                        }
                    }
                    val rect = dragRect
                    dragRect = null
                    if (rect != null && rect.width >= SCREEN_PICK_MIN_REGION_PX && rect.height >= SCREEN_PICK_MIN_REGION_PX) {
                        onSelectRect(rect)
                    } else {
                        onTap(upPosition)
                    }
                }
            },
    ) {
        // 拾取层 UI 在截屏完成后才渲染（截图里没有本层像素）。
        if (captured) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f)),
            )
            dragRect?.let { rect ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = Color(0xFF1A73E8).copy(alpha = 0.25f),
                        topLeft = rect.topLeft,
                        size = rect.size,
                    )
                    drawRect(
                        color = Color(0xFF1A73E8),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            Text(
                text = stringResource(R.string.screen_pick_hint),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { onDismiss() }
                    },
            )
        }
    }
}
