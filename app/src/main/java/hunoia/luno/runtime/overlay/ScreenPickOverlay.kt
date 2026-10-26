package hunoia.luno.runtime.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect as AndroidRect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.bridge.system.copyImage
import hunoia.luno.bridge.system.copyText
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.core.Events
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.environment.BroadcastObserver
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 框选区域小于该边长（px）视为误操作，按轻点收口。 */
private const val SCREEN_PICK_MIN_REGION_PX = 12f

/** 可选内容提示框（图片/拖动选框）的主题蓝。 */
private val PickBlue = Color(0xFF1A73E8)

/** 文本提示框的最小尺寸（dp）：过滤碎字/小字，密集列表不糊屏（命中逻辑不受影响）。 */
private const val PickTextMinWidthDp = 12f
private const val PickTextMinHeightDp = 8f

/** 提示框总量/遍历节点预算：防病态节点树拖延提示框上屏。 */
private const val MaxPickBoxes = 120
private const val MaxPickNodes = 400

/** 提示框扫描的最大深度（B 超广度优先，深层容器大多不在首屏）。 */
private const val MaxScanDepth = 24

/** 流式发布：每扫到多少个节点/多久就把已有提示框先交给 UI。 */
private const val PublishEveryNodes = 30
private const val PublishEveryMs = 80L

/**
 * 面板（搜索 Activity / 网格浮窗等）退场等待：截图与节点扫描都要等它退场，
 * 否则截屏会把面板拍进去、提示框也会按面板内容画（表现为错位）。
 * 事件先到就继续，超时兜底不卡死拾取层。
 */
private const val PanelSettleMaxMs = 800L
private const val PanelSettlePollMs = 50L

/** 提示框扫描重试：首轮没找到时再试一次（面板关后底层窗口可能刚恢复可见）。 */
private const val PickBoxAttempts = 2
private const val PickBoxRetryMs = 150L

/** 面板退场后的额外停一拍：截图前再确认一次画面已稳定。 */
private const val PanelPostSettleMs = 80L

/** 框选框的拖角命中半径 / 最小尺寸（dp）。 */
private const val HandleHitRadiusDp = 22f
private const val AdjustMinSizeDp = 16f

/** 提示框：文本节点白框，图片节点蓝框。 */
private data class PickBox(val rect: AndroidRect, val isImage: Boolean)

/**
 * 屏幕内容识别拾取层（动作 accessibility.screenPick）：全屏透明 a11y 窗，照 PointPickOverlay
 * 的窗口套路。轻点 = 节点树取字（含文本直接复制；无文本取图片类节点 bounds，再退到包含
 * 触点的最小节点 bounds 兜底，如视频画面）；按住拖动 = 框选区域。
 * 文字进剪贴板；图片从开层时的预截屏裁剪（挂窗时先不渲染任何像素，截图里永远没有本层 UI），
 * 写入 MediaStore（Pictures/Luno）→ 用带 MIME + clipData 授权的 content URI 写剪贴板并弹分享，
 * 对端按普通图库图片处理（不会变成一个「文件」）。
 */
internal class ScreenPickOverlay(private val host: GestureHost) {

    private companion object {
        /** 无障碍 overlay 不带这个标志时触摸会被系统拦掉（Android 12 起），只能照发。 */
        const val TRUSTED_OVERLAY_FLAG = 0x300000

        const val SCREEN_PICK_LABEL = "screen_pick"

        /** 节点树 DFS 深度上限：常规应用一屏远达不到，防恶意/病态树卡主线程。 */
        const val MAX_NODE_DEPTH = 30

        /** 图片候选的最小边长（px）：小于它的 Image 类节点多半是装饰图标，裁出来没意义。 */
        const val MIN_IMAGE_PX = 24
    }

    private val mainExecutor = Executor { r -> Handler(Looper.getMainLooper()).post(r) }

    private var overlayView: View? = null

    /** 图片落盘进行中：期间关闭拾取层不回收截图位图（后台仍在用它裁剪）。 */
    private var saving = false

    /** 轻点命中查询进行中：防重复触发（查询已挪到后台线程）。 */
    private var resolvingTap = false

    // 截屏与命中结果只在主线程读写；captured 走 Compose 态驱动视觉层渲染。
    private var capture: Bitmap? = null
    private var captured by mutableStateOf(false)
    private var pickBoxes by mutableStateOf<List<PickBox>>(emptyList())

    /** 本次截图对应的屏幕区域：窗口截图时不是整屏（0,0 起），裁剪要先减原点再按区域缩放。 */
    private var captureOriginX = 0
    private var captureOriginY = 0
    private var captureScreenW = 0
    private var captureScreenH = 0

    /** 拾取窗在屏幕上的实际原点：a11y 节点 bounds 与截图都是屏幕坐标，绘制/命中用视图坐标。 */
    private var originX by mutableStateOf(0)
    private var originY by mutableStateOf(0)

    /** 在屏面板计数（自家搜索/网格/动作面板）：等它们全部退场再截图与扫节点。 */
    private var openPanelCount = 0

    /** 开层会话号：异步截屏回调回来时若已换会话/已关层，丢弃旧位图。 */
    @Volatile
    private var session = 0

    /** ROM 原点修正只做一次（LAYOUT_IN_SCREEN 不生效时把窗顶推回 0）。 */
    private var originCorrected = false

    private var boxesJob: Job? = null

    /** 锁屏/息屏监听：拾取层不跨锁屏保留（拆窗时即清掉截图与提示框）。 */
    private var lockObserver: BroadcastObserver? = null

    private val panelShownSubscriber: (PanelShownEvent) -> Unit = { openPanelCount++ }
    private val panelDismissedSubscriber: (PanelDismissedEvent) -> Unit = {
        openPanelCount = (openPanelCount - 1).coerceAtLeast(0)
    }

    init {
        Events.subscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.subscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
    }

    /** 服务销毁：退订全局面板事件并摘窗。 */
    fun release() {
        Events.unsubscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.unsubscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
        close()
    }

    /** 返回是否成功挂窗（锁屏/挂窗被拒返回 false，动作层据此判失败）。 */
    fun show(): Boolean {
        if (overlayView != null) return true
        // 锁屏状态不开层：拾取窗画在锁屏上既无意义又可能泄露上次内容。
        val keyguard = host.context.getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return false
        session++
        originCorrected = false
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
                    boxes = pickBoxes,
                    originX = originX,
                    originY = originY,
                    onTap = ::onTap,
                    onSelectRect = ::onSelectRect,
                    onDismiss = ::close,
                )
            }
        }
        // 先挂窗再截屏：截屏在 captured 置位前才发，此时本层渲染为空（全透明），
        // 截图里不会有本层像素；反过来面板还没退场时截图会把面板拍进去。
        if (!wm.safeAddView(composeView, lp)) {
            Log.e(LOG_TAG, "screen pick: addView failed type=touch_trusted")
            showToast(R.string.screen_pick_failed)
            return false
        }
        overlayView = composeView
        startLockWatch()
        composeView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> updateOrigin(v) }
        composeView.post { correctWindowOriginOnce(composeView) }
        captureWhenSettled(session)
        return true
    }

    fun close() {
        stopLockWatch()
        session++
        resolvingTap = false
        val view = overlayView ?: return
        overlayView = null
        boxesJob?.cancel()
        boxesJob = null
        // 落盘在跑时不回收：后台仍在用这张位图裁剪，收尾处（copyImageRegion 的 finally）补回收。
        if (!saving) {
            capture?.let { if (!it.isRecycled) it.recycle() }
            capture = null
        }
        captured = false
        captureOriginX = 0
        captureOriginY = 0
        captureScreenW = 0
        captureScreenH = 0
        pickBoxes = emptyList()
        originX = 0
        originY = 0
        originCorrected = false
        val wm = host.context.windowManager()
        runCatching { wm.removeView(view) }
    }

    /** 锁屏/息屏即时关层并清空（截屏、提示框、拖框都不跨锁屏保留）。 */
    private fun startLockWatch() {
        stopLockWatch()
        lockObserver = BroadcastObserver(host.context, onScreenOff = { close() }).also { it.register() }
    }

    private fun stopLockWatch() {
        lockObserver?.unregister()
        lockObserver = null
    }

    // ---- 结算 ----

    private fun onTap(px: Offset) {
        // 视图坐标 → 屏幕坐标：节点 bounds 与截图都在屏幕空间。
        val screenX = px.x.roundToInt() + originX
        val screenY = px.y.roundToInt() + originY
        if (resolvingTap) return
        resolvingTap = true
        val token = session
        // 节点树遍历是成百上千次 Binder 调用，绝不上主线程（性能审查 §2.2）。
        host.coroutineScope.launch {
            val hit = withContext(Dispatchers.Default) {
                runCatching { findNodeHitAt(screenX, screenY) }.getOrNull()
            }
            if (token != session || overlayView == null) return@launch
            resolvingTap = false
            val text = hit?.text
            if (!text.isNullOrBlank()) {
                copyText(host.context, SCREEN_PICK_LABEL, text)
                showToast(R.string.quick_search_copied)
                close()
                return@launch
            }
            val bounds = hit?.imageBounds ?: hit?.fallbackBounds
            when {
                bounds == null -> showToast(R.string.screen_pick_no_content)
                !copyImageRegion(AndroidRect(bounds)) -> showToast(R.string.screen_pick_failed)
            }
            close()
        }
    }

    private fun onSelectRect(rect: Rect) {
        if (rect.width < SCREEN_PICK_MIN_REGION_PX || rect.height < SCREEN_PICK_MIN_REGION_PX) return
        val screenRect = AndroidRect(
            (rect.left + originX).roundToInt(),
            (rect.top + originY).roundToInt(),
            (rect.right + originX).roundToInt(),
            (rect.bottom + originY).roundToInt(),
        )
        // 裁剪 + 落盘 + 分享都在后台（大图 PNG 压缩放主线程会卡顿/ANR）。
        host.coroutineScope.launch {
            if (!copyImageRegion(screenRect)) {
                showToast(R.string.screen_pick_failed)
            }
            close()
        }
    }

    /** 本次截图覆盖的屏幕区域（窗口截图时原点不是 0）。 */
    private fun captureRegion(): AndroidRect? {
        if (captureScreenW <= 0 || captureScreenH <= 0) return null
        return AndroidRect(
            captureOriginX,
            captureOriginY,
            captureOriginX + captureScreenW,
            captureOriginY + captureScreenH,
        )
    }

    /**
     * 屏幕矩形 → 截图位图内的裁剪矩形。先与截图区域求交再换算：越界部分取交集
     * （裁剪内容仍正确），完全落在区域外返回 null。不能用 coerceIn 把越界边钳到
     * 边缘 —— 那会把内容整体平移，复制到一张错位图。
     */
    private fun cropRectFor(cap: Bitmap, rect: AndroidRect): AndroidRect? {
        val region = captureRegion() ?: return null
        val inter = AndroidRect(rect)
        if (!inter.intersect(region) || inter.isEmpty) return null
        val sx = cap.width.toFloat() / captureScreenW
        val sy = cap.height.toFloat() / captureScreenH
        val crop = AndroidRect(
            ((inter.left - captureOriginX) * sx).roundToInt().coerceIn(0, cap.width),
            ((inter.top - captureOriginY) * sy).roundToInt().coerceIn(0, cap.height),
            ((inter.right - captureOriginX) * sx).roundToInt().coerceIn(0, cap.width),
            ((inter.bottom - captureOriginY) * sy).roundToInt().coerceIn(0, cap.height),
        )
        if (crop.width() <= 0 || crop.height() <= 0) return null
        return crop
    }

    /**
     * [rect] 为**屏幕坐标**：截图可能是窗口级（原点非 0），先求交再按位图与区域比例换算。
     * 裁剪/落盘在 IO 线程，UI 与分享回主线程。
     */
    private suspend fun copyImageRegion(rect: AndroidRect): Boolean {
        val cap = capture ?: return false
        val crop = cropRectFor(cap, rect) ?: return false
        saving = true
        try {
            val cropped = withContext(Dispatchers.IO) {
                runCatching {
                    Bitmap.createBitmap(cap, crop.left, crop.top, crop.width(), crop.height())
                }.onFailure {
                    Log.e(LOG_TAG, "screen pick: crop failed: ${it.message}")
                }.getOrNull()
            } ?: return false
            val uri = withContext(Dispatchers.IO) { saveImageToGallery(cropped) }
            cropped.recycle()
            if (uri == null) return false
            val context = host.context
            if (!copyImage(context, SCREEN_PICK_LABEL, uri)) return false
            showToast(R.string.screen_pick_image_copied)
            val resolver = context.contentResolver
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                // 必须同时带 clipData：只给 EXTRA_STREAM 时部分应用拿不到读取授权，
                // 会把图片当普通文件/附件显示（系统截屏分享走的是带授权的媒体 URI）。
                clipData = android.content.ClipData.newUri(resolver, SCREEN_PICK_LABEL, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(share, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(chooser) }
                .onFailure { Log.w(LOG_TAG, "screen pick: share chooser failed: ${it.message}") }
            return true
        } finally {
            saving = false
            // 关闭发生在落盘期间：图片已复制/UI 已拆，这里补回收截图位图。
            if (overlayView == null && capture === cap) {
                if (!cap.isRecycled) cap.recycle()
                capture = null
            }
        }
    }

    /**
     * 裁剪图写入系统图库（Pictures/Luno），返回 content URI。
     * 用 MediaStore 而非 FileProvider：接收方/粘贴方按普通图片（带 MIME、可被系统预览）处理，
     * 不会识别成一个无缩略图的「文件」。
     */
    private fun saveImageToGallery(bitmap: Bitmap): Uri? = runCatching {
        val resolver = host.context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "screen_pick_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Luno",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return@runCatching null
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    }.onFailure {
        Log.e(LOG_TAG, "screen pick: save image failed: ${it.message}")
    }.getOrNull()

    // ---- 截屏 ----

    /** 最上层的应用窗口（跳过自家窗口）：提示框与窗口截图都以它为准。 */
    private fun topApplicationWindow(): AccessibilityWindowInfo? {
        val windows = runCatching { host.accessibilityService.windows }.getOrNull() ?: return null
        val ownPackage = host.context.packageName
        for (index in windows.indices.reversed()) {
            val window = windows.getOrNull(index) ?: continue
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = runCatching { window.root }.getOrNull() ?: continue
            if (root.packageName?.toString() == ownPackage) continue
            return window
        }
        return null
    }

    /**
     * 优先 `takeScreenshotOfWindow`（API 34+）：只抓目标应用窗，分辨率不被其他悬浮层稀释，
     * 也不依赖「本层还没渲染」的时间窗；失败/低版本回退整屏截屏。
     */
    private fun takeScreenshot(): Boolean {
        val token = session
        val window = topApplicationWindow()
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val accepted = runCatching {
                host.accessibilityService.takeScreenshotOfWindow(
                    window.id,
                    mainExecutor,
                    screenshotCallback(token, windowBounds = window.boundsOnScreen(),
                        allowDisplayFallback = true),
                )
            }.onFailure {
                Log.w(LOG_TAG, "screen pick: window screenshot rejected: ${it.message}")
            }.isSuccess
            if (accepted) return true
        }
        return takeScreenshotOfDisplay(token)
    }

    private fun takeScreenshotOfDisplay(token: Int): Boolean = runCatching {
        host.accessibilityService.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            screenshotCallback(token, windowBounds = null, allowDisplayFallback = false),
        )
    }.onFailure {
        Log.e(LOG_TAG, "screen pick: takeScreenshot rejected: ${it.message}")
    }.isSuccess

    private fun AccessibilityWindowInfo.boundsOnScreen(): AndroidRect =
        AndroidRect().also { getBoundsInScreen(it) }

    private fun screenshotCallback(
        token: Int,
        windowBounds: AndroidRect?,
        allowDisplayFallback: Boolean,
    ): AccessibilityService.TakeScreenshotCallback =
        object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                try {
                    if (token != session) return
                    val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    val copied = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    if (copied == null) {
                        pickFailed()
                    } else {
                        capture = copied
                        recordCaptureRegion(windowBounds)
                        // 蒙层与提示框解耦：截图一到就亮层（可立即点/拖），提示框后台扫完再补。
                        captured = true
                    }
                } finally {
                    result.hardwareBuffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                if (token != session) return
                // 窗口截图失败（刚切走/系统拒绝）：回退整屏一次，不直接判失败。
                if (allowDisplayFallback && windowBounds != null && takeScreenshotOfDisplay(token)) return
                pickFailed()
            }

            private fun pickFailed() {
                showToast(R.string.screen_pick_failed)
                close()
            }
        }

    /** 记录截图 bitmap 覆盖的屏幕区域（窗口截图时原点不是 0）；裁剪按它换算。 */
    private fun recordCaptureRegion(windowBounds: AndroidRect?) {
        if (windowBounds != null && windowBounds.width() > 0 && windowBounds.height() > 0) {
            captureOriginX = windowBounds.left
            captureOriginY = windowBounds.top
            captureScreenW = windowBounds.width()
            captureScreenH = windowBounds.height()
        } else {
            captureOriginX = 0
            captureOriginY = 0
            captureScreenW = DensityProvider.screenWidthPx
            captureScreenH = DensityProvider.screenHeightPx
        }
    }

    // ---- 节点查找 ----

    /**
     * 面板退场后再截屏：面板是自家窗口，在屏时截屏会把面板拍进去，与退场后的
     * 实际画面不一致（裁剪图片会拿到错内容）。事件先到就继续，超时兜底。
     */
    private fun captureWhenSettled(token: Int) {
        host.coroutineScope.launch {
            val hadPanels = openPanelCount > 0
            var waited = 0L
            while (openPanelCount > 0 && waited < PanelSettleMaxMs) {
                delay(PanelSettlePollMs)
                waited += PanelSettlePollMs
            }
            if (token != session || overlayView == null || capture != null) return@launch
            // 面板退场多留一拍：动作面板的淡出在 Dismissed 之后还可能再收尾。
            if (hadPanels) delay(PanelPostSettleMs)
            // 扫描与截屏并行：提示框不用等 takeScreenshot 回调（它往往是慢的那一步）。
            schedulePickBoxes(token)
            if (!takeScreenshot()) {
                showToast(R.string.screen_pick_failed)
                close()
            }
        }
    }

    /**
     * 拾取窗的原点（状态栏/分屏偏移）：绘制与命中的屏幕/视图坐标换算都按它来。
     * 布局变化时持续更新（IME/旋转），但窗口修正只做一次。
     */
    private fun updateOrigin(v: View) {
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        originX = location[0]
        originY = location[1]
    }

    /** ROM 让 2032 窗也带状态栏偏移时，把窗口顶边推回屏幕原点（同 OverlayPanelHost）。 */
    private fun correctWindowOriginOnce(v: View) {
        if (originCorrected) return
        originCorrected = true
        updateOrigin(v)
        if (originX == 0 && originY == 0) return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.x -= originX
        lp.y -= originY
        v.context.windowManager().safeUpdateViewLayout(v, lp)
    }

    /**
     * 提示框后台扫描：面板退场后底层窗口可能还没恢复可见，多试几轮。
     * 节点树遍历是成百上千次 Binder 调用，绝不上主线程（性能审查 §2.2）。
     */
    private fun schedulePickBoxes(token: Int) {
        boxesJob?.cancel()
        boxesJob = host.coroutineScope.launch {
            repeat(PickBoxAttempts) { attempt ->
                if (token != session || overlayView == null) return@launch
                val found = withContext(Dispatchers.Default) {
                    runCatching { scanPickBoxes(token) }.getOrDefault(false)
                }
                if (found) return@launch
                if (attempt < PickBoxAttempts - 1) delay(PickBoxRetryMs)
            }
        }
    }

    /**
     * 广度优先扫**最上层应用窗口**：先拿到浅层可见内容，边扫边发布提示框。
     * 不扫底层/自家窗口；屏幕外的非空节点直接剪掉子树。返回是否发布过非空提示框。
     */
    private fun scanPickBoxes(token: Int): Boolean {
        val root = topApplicationWindow()?.root ?: return false
        val minTextW = DensityProvider.dp2px(PickTextMinWidthDp)
        val minTextH = DensityProvider.dp2px(PickTextMinHeightDp)
        val screen = AndroidRect(0, 0, DensityProvider.screenWidthPx, DensityProvider.screenHeightPx)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        val boxes = ArrayList<PickBox>()
        var visited = 0
        var published = 0
        var lastPublishAt = 0L
        while (queue.isNotEmpty() && visited < MaxPickNodes && boxes.size < MaxPickBoxes) {
            if (token != session) return published > 0
            val (node, depth) = queue.removeFirst()
            visited++
            val bounds = AndroidRect()
            node.getBoundsInScreen(bounds)
            val onScreen = !bounds.isEmpty && AndroidRect.intersects(bounds, screen)
            if (onScreen && node.isVisibleToUser) {
                // 密码框文本不取（无障碍可能暴露明文）。
                val text = if (node.isPassword) null else node.text
                if (!text.isNullOrBlank() &&
                    bounds.width() >= minTextW &&
                    bounds.height() >= minTextH
                ) {
                    boxes += PickBox(bounds, isImage = false)
                } else {
                    val className = node.className?.toString().orEmpty()
                    if (className.contains("Image") &&
                        bounds.width() >= MIN_IMAGE_PX &&
                        bounds.height() >= MIN_IMAGE_PX
                    ) {
                        boxes += PickBox(bounds, isImage = true)
                    }
                }
            }
            // 屏幕外（且能报出 bounds）的子树不扫；空 bounds 的容器照常下钻。
            if (depth < MaxScanDepth && (bounds.isEmpty || onScreen)) {
                for (i in 0 until node.childCount) {
                    if (visited + queue.size >= MaxPickNodes) break
                    node.getChild(i)?.let { queue.addLast(it to depth + 1) }
                }
            }
            val now = android.os.SystemClock.uptimeMillis()
            if (boxes.size > published &&
                (boxes.size - published >= PublishEveryNodes || now - lastPublishAt >= PublishEveryMs)
            ) {
                if (token != session) return published > 0
                // 扫描在 Default 线程：发布 Compose 态走显式快照，避免跨线程裸写。
                val snapshotBoxes = ArrayList(boxes)
                Snapshot.withMutableSnapshot { pickBoxes = snapshotBoxes }
                published = boxes.size
                lastPublishAt = now
            }
        }
        if (boxes.size > published) {
            if (token != session) return published > 0
            val snapshotBoxes = ArrayList(boxes)
            Snapshot.withMutableSnapshot { pickBoxes = snapshotBoxes }
            published = boxes.size
        }
        return published > 0
    }

    private data class NodeHit(
        val text: String?,
        val imageBounds: AndroidRect?,
        val fallbackBounds: AndroidRect?,
    )

    /** 从最上层包含触点的应用窗口开始找（跳过自家 overlay 与输入法窗）。 */
    private fun findNodeHitAt(x: Int, y: Int): NodeHit? {
        val windows = host.accessibilityService.windows ?: return null
        val ownPackage = host.context.packageName
        for (index in windows.indices.reversed()) {
            val window = windows.getOrNull(index) ?: continue
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            // 同一套口径：自家面板（搜索页等）不能当拾取目标。
            if (root.packageName?.toString() == ownPackage) continue
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
        var visited = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_NODE_DEPTH || visited >= MaxPickNodes) return
            visited++
            val bounds = AndroidRect()
            node.getBoundsInScreen(bounds)
            // 父不含触点即剪枝：常规布局子节点都在父 bounds 内；框选是另一条兜底路。
            if (!bounds.contains(x, y)) return
            if (node.isVisibleToUser) {
                // 密码框文本不取（无障碍可能暴露明文）；仍允许框选兜底成图片。
                val text = if (node.isPassword) null else node.text
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

/** 框选调整期的命中部位。 */
private enum class SelHandle { TopLeft, TopRight, BottomLeft, BottomRight }

/** 按下点是否落在某个拖角上。 */
private fun Rect.handleAt(p: Offset, radius: Float): SelHandle? = listOf(
    SelHandle.TopLeft to topLeft,
    SelHandle.TopRight to topRight,
    SelHandle.BottomLeft to bottomLeft,
    SelHandle.BottomRight to bottomRight,
).firstOrNull { (_, corner) -> (corner - p).getDistance() <= radius }?.first

/** 拖角缩放：只移动被拖的那条边，保持对角固定，并保证最小尺寸。 */
private fun Rect.resized(handle: SelHandle, p: Offset, minSize: Float, bounds: Size): Rect {
    val x = p.x.coerceIn(0f, bounds.width)
    val y = p.y.coerceIn(0f, bounds.height)
    var left = left
    var top = top
    var right = right
    var bottom = bottom
    when (handle) {
        SelHandle.TopLeft -> { left = x; top = y }
        SelHandle.TopRight -> { right = x; top = y }
        SelHandle.BottomLeft -> { left = x; bottom = y }
        SelHandle.BottomRight -> { right = x; bottom = y }
    }
    if (right - left < minSize) {
        if (handle == SelHandle.TopLeft || handle == SelHandle.BottomLeft) {
            left = right - minSize
        } else {
            right = left + minSize
        }
    }
    if (bottom - top < minSize) {
        if (handle == SelHandle.TopLeft || handle == SelHandle.TopRight) {
            top = bottom - minSize
        } else {
            bottom = top + minSize
        }
    }
    return Rect(
        left.coerceIn(0f, bounds.width),
        top.coerceIn(0f, bounds.height),
        right.coerceIn(0f, bounds.width),
        bottom.coerceIn(0f, bounds.height),
    )
}

/** 平移选框并夹在屏内。 */
private fun Rect.moved(dx: Float, dy: Float, bounds: Size): Rect {
    val maxLeft = (bounds.width - width).coerceAtLeast(0f)
    val maxTop = (bounds.height - height).coerceAtLeast(0f)
    val left = (left + dx).coerceIn(0f, maxLeft)
    val top = (top + dy).coerceIn(0f, maxTop)
    return Rect(left, top, left + width, top + height)
}

@Composable
private fun ScreenPickScreen(
    captured: Boolean,
    boxes: List<PickBox>,
    originX: Int,
    originY: Int,
    onTap: (Offset) -> Unit,
    onSelectRect: (Rect) -> Unit,
    onDismiss: () -> Unit,
) {
    // dragRect = 正在画的新框；adjustRect = 已落定、可拖动/缩放、待确认的框。
    var dragRect by remember { mutableStateOf<Rect?>(null) }
    var adjustRect by remember { mutableStateOf<Rect?>(null) }
    val handleHitPx = with(LocalDensity.current) { HandleHitRadiusDp.dp.toPx() }
    val minSizePx = with(LocalDensity.current) { AdjustMinSizeDp.dp.toPx() }
    val handleDrawPx = with(LocalDensity.current) { 6.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 子级（取消/确认按钮）消费掉的手势不参与拾取。
                    if (down.isConsumed) return@awaitEachGesture
                    val start = down.position
                    val initial = adjustRect
                    val handle = initial?.handleAt(start, handleHitPx)
                    var resizing: SelHandle? = handle
                    var moving = false
                    if (initial != null && handle == null) {
                        moving = initial.contains(start)
                    }
                    var moved = false
                    var last = start
                    var upPosition = start
                    var childConsumed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        upPosition = change.position
                        if (change.isConsumed) {
                            childConsumed = true
                            break
                        }
                        if (!change.pressed) break
                        change.consume()
                        val delta = change.position - last
                        last = change.position
                        if (!moved && (change.position - start).getDistance() > viewConfiguration.touchSlop) {
                            moved = true
                        }
                        when {
                            resizing != null -> {
                                val r = adjustRect ?: break
                                adjustRect = r.resized(resizing!!, change.position, minSizePx, size.toSizeF())
                            }
                            moving -> {
                                val r = adjustRect ?: break
                                adjustRect = r.moved(delta.x, delta.y, size.toSizeF())
                            }
                            moved -> {
                                dragRect = Rect(
                                    left = minOf(start.x, change.position.x),
                                    top = minOf(start.y, change.position.y),
                                    right = maxOf(start.x, change.position.x),
                                    bottom = maxOf(start.y, change.position.y),
                                )
                            }
                        }
                    }
                    if (childConsumed) return@awaitEachGesture
                    if (resizing == null && !moving) {
                        // 本次是在画新框；落定后进入「可拖动/缩放 + 确认」状态，不再自动复制。
                        val rect = dragRect
                        dragRect = null
                        if (rect != null && rect.width >= minSizePx && rect.height >= minSizePx) {
                            adjustRect = rect
                        } else if (!moved) {
                            if (adjustRect != null) {
                                // 点选框外空白：丢弃选框，仍留在拾取层。
                                adjustRect = null
                            } else {
                                onTap(upPosition)
                            }
                        }
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
            // 可选内容提示框：深色底描边 + 彩色内描边，深浅背景都可见。
            // 节点 bounds 是屏幕坐标，减去窗口原点才是视图坐标。
            Canvas(modifier = Modifier.fillMaxSize()) {
                boxes.forEach { box ->
                    val topLeft = Offset(
                        (box.rect.left - originX).toFloat(),
                        (box.rect.top - originY).toFloat(),
                    )
                    val size = Size(
                        box.rect.width().toFloat(),
                        box.rect.height().toFloat(),
                    )
                    val color = if (box.isImage) PickBlue else Color.White
                    if (box.isImage) {
                        drawRect(color.copy(alpha = 0.08f), topLeft = topLeft, size = size)
                    }
                    drawRect(
                        Color.Black.copy(alpha = 0.4f),
                        topLeft = topLeft,
                        size = size,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                    drawRect(
                        color,
                        topLeft = topLeft,
                        size = size,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            // 正在拖的新框 / 已落定待确认的框。
            val activeRect = adjustRect ?: dragRect
            activeRect?.let { rect ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        color = PickBlue.copy(alpha = 0.25f),
                        topLeft = rect.topLeft,
                        size = rect.size,
                    )
                    drawRect(
                        color = PickBlue,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    if (adjustRect != null) {
                        // 四角拖拽把手。
                        listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight)
                            .forEach { corner ->
                                drawCircle(Color.White, radius = handleDrawPx, center = corner)
                                drawCircle(
                                    Color.Black.copy(alpha = 0.55f),
                                    radius = handleDrawPx,
                                    center = corner,
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                    }
                }
            }
            // 取消/确认统一放底部（不再占右上角，顶部留给内容）。
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.68f),
                    contentColor = Color.White,
                ) {
                    Text(
                        text = stringResource(R.string.screen_pick_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                if (adjustRect != null) {
                    Surface(
                        onClick = {
                            val rect = adjustRect ?: return@Surface
                            adjustRect = null
                            onSelectRect(rect)
                        },
                        shape = RoundedCornerShape(50),
                        color = PickBlue,
                        contentColor = Color.White,
                    ) {
                        Text(
                            text = stringResource(R.string.screen_pick_confirm),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** IntSize → Float Size（PointerInputScope 的 size 是 IntSize）。 */
private fun IntSize.toSizeF() = Size(width.toFloat(), height.toFloat())
