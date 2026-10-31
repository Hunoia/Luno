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
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import hunoia.luno.gesture.R
import hunoia.luno.gesture.BuildConfig
import hunoia.luno.bridge.system.copyImage
import hunoia.luno.bridge.system.copyText
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.capability.ocr.OcrEngine
import hunoia.luno.capability.ocr.OcrText
import hunoia.luno.config.model.OcrScript
import hunoia.luno.core.Events
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.overlay.text.A11yTextSnapshotReader
import hunoia.luno.runtime.overlay.text.ScreenPreCapture
import hunoia.luno.runtime.overlay.text.TextSnapshot
import hunoia.luno.runtime.overlay.text.TextSnapshotOps
import hunoia.luno.runtime.environment.BroadcastObserver
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

/** 框选区域小于该边长（px）视为误操作，按轻点收口。 */
private const val SCREEN_PICK_MIN_REGION_PX = 12f

/** 可选内容提示框（图片/拖动选框）的主题蓝。 */
private val PickBlue = Color(0xFF1A73E8)

/** 命中容差（dp）：文字节点 bounds 常比触点小一圈，贴边按会漏。 */
private const val HitToleranceDp = 6f

/** 面板退场后的额外停一拍：截图前再确认一次画面已稳定。 */
private const val PanelPostSettleMs = 80L

/** 框选框的拖角命中半径 / 最小尺寸（dp）。 */
private const val HandleHitRadiusDp = 22f
private const val AdjustMinSizeDp = 16f

/**
 * 幽灵卡的一次快照：`anchor`/`finger` 是屏幕坐标；[ScreenPickScreen] 渲染前会减窗口原点。
 * 卡片几何（含命中判定）由 [PortalLayout.cardRect] 统一计算。
 */
private data class PortalState(
    val anchor: Offset?,
    val finger: Offset?,
    val content: PortalContent,
    val resolving: Boolean,
)

/** 框选动作条的动作（关闭走 onDismiss，不在此枚举）。 */
private enum class PickSelectionAction { Ocr, Copy, Search, Share, Save }

/** 缓存目录保留的识屏分享图数量。 */
private const val CACHE_KEEP_FILES = 5

/**
 * 屏幕内容识别拾取层（动作 accessibility.screenPick）：全屏透明 a11y 窗。
 *
 * 打开时不扫全屏、不画提示框：轻点无操作，拖动 = 框选区域，长按 = 在落点懒查询节点，
 * 命中后出幽灵卡。截图只在需要裁剪图片时使用，图片从预截屏裁剪。
 */
class ScreenPickOverlay(
    private val host: GestureHost,
    /** OCR 能力（`:app` 注入）：默认 ML Kit；本模块只依赖契约，不直接引第三方 SDK。 */
    private val ocrEngine: OcrEngine,
    /** 幽灵卡「搜索」的落点（`:app` 注入快速搜索，本模块不依赖 `:feature:quicklaunch`）。 */
    private val onSearch: (String) -> Boolean = { false },
    /** 框选 OCR 完成后的结果页落点（`:app` 注入）；返回 true = 已接管，浮层关闭。 */
    private val onOcrResult: (String) -> Boolean = { false },
    /** 幽灵卡「打开链接」的落点（`:app` 注入链接分发，本模块不依赖链接分发实现）。 */
    private val onOpenLink: suspend (String) -> Boolean = { false },
    /** 长按/拖动参数：`:app` 注入运行时配置，保证与全屏长按使用同一套阈值。 */
    private val screenPickGesture: () -> hunoia.luno.config.model.ScreenPickGesture = {
        hunoia.luno.config.model.ScreenPickGesture()
    },
    /** 叠层关闭回调：用于恢复拦截条可触。 */
    private val onClosed: () -> Unit = {},
) : hunoia.luno.runtime.surface.SurfaceHandle {
    override val surfaceId: String = "screen-pick"
    override val surfaceKind: hunoia.luno.runtime.surface.SurfaceKind =
        hunoia.luno.runtime.surface.SurfaceKind.TRANSIENT

    override fun softHide() = close()
    override fun restore() = Unit
    override fun detach() = close()


    private companion object {
        /** 无障碍 overlay 不带这个标志时触摸会被系统拦掉（Android 12 起），只能照发。 */
        const val TRUSTED_OVERLAY_FLAG = 0x300000

        const val SCREEN_PICK_LABEL = "screen_pick"
    }

    private val mainExecutor = Executor { r -> Handler(Looper.getMainLooper()).post(r) }

    private var overlayView: View? = null

    /** 图片落盘进行中：期间关闭拾取层不回收截图位图（后台仍在用它裁剪）。 */
    private var saving = false

    /** 框选动作执行中：防动作条被连点造成重复落盘。 */
    private var resolvingAction = false

    /** 截图就绪信号：长按比截图快时，OCR 等它（最多 1.2s）。 */
    private var captureSignal = CompletableDeferred<Unit>()

    /** 幽灵卡会话号：每次长按 +1；OCR 回来时对不上就丢弃。 */
    private var portalSession = 0

    /** 进行中的 OCR 任务数：期间 close() 不回收截图位图（识别仍在读它）。 */
    private var ocrInFlight = 0

    // 截屏与命中结果只在主线程读写；captured 走 Compose 态驱动视觉层渲染。
    private var capture: Bitmap? = null
    private var captured by mutableStateOf(false)

    /** 长按按下时抓的 a11y 快照；框选按它裁字，避免应用菜单/后续变化污染。 */
    private var textSnapshot: TextSnapshot? = null

    /** 框选 OCR 结果面板；非 null 表示面板在屏。 */
    private var ocrPanelText by mutableStateOf<String?>(null)
    private var ocrPanelLoading by mutableStateOf(false)
    private var ocrPanelRect: AndroidRect? = null
    private var ocrPanelSession = 0
    /** 本次截图对应的屏幕区域：窗口截图时不是整屏（0,0 起），裁剪要先减原点再按区域缩放。 */
    private var captureOriginX = 0
    private var captureOriginY = 0
    private var captureScreenW = 0
    private var captureScreenH = 0

    /** 拾取窗在屏幕上的实际原点：a11y 节点 bounds 与截图都是屏幕坐标，绘制/命中用视图坐标。 */
    private var originX by mutableStateOf(0)
    private var originY by mutableStateOf(0)

    /** 在屏面板计数（自家搜索/网格/动作面板）：等它们全部退场再截图与扫节点。 */
    private val openPanels = hunoia.luno.runtime.overlay.OpenPanelCounter()

    /** 开层会话号：异步截屏回调回来时若已换会话/已关层，丢弃旧位图。 */
    @Volatile
    private var session = 0

    /** ROM 原点修正只做一次（LAYOUT_IN_SCREEN 不生效时把窗顶推回 0）。 */
    private var originCorrected = false

    /** 锁屏/息屏监听：拾取层不跨锁屏保留（拆窗时即清掉截图与提示框）。 */
    private var lockObserver: BroadcastObserver? = null

    // ---- 幽灵卡（长按出卡；拖卡移动；动作槽点击执行） ----

    /** 长按落点 / 当前手指位置：**屏幕坐标**，渲染时再减窗口原点。 */
    private var portalAnchor by mutableStateOf<Offset?>(null)
    private var portalFinger by mutableStateOf<Offset?>(null)

    /** 落点内容：文字节点或图片节点；识别期间为空。 */
    private var portalContent by mutableStateOf(PortalContent())
    private var portalResolving by mutableStateOf(false)

    /** 图片节点 bounds（屏幕坐标）：保存 / 分享图片时按它裁剪预截图。 */
    private var portalImageBounds: AndroidRect? = null

    private var portalJob: Job? = null
    private var portalPreviewJob: Job? = null

    /** 幽灵卡会话是否在进行中（已有落点、未松手）。 */
    val isPortalActive: Boolean get() = portalAnchor != null

    /** 拾取层窗口是否在屏（含无幽灵卡的普通框选态）。 */
    val isShown: Boolean get() = overlayView != null

    /** 服务销毁：退订全局面板事件并摘窗。 */
    fun release() {
        openPanels.close()
        close()
    }

    /** 返回是否成功挂窗（锁屏/挂窗被拒返回 false，动作层据此判失败）。 */
    fun show(preCapture: PickCapture? = null): Boolean {
        if (overlayView != null) {
            preCapture?.let { adoptPreCapture(it) }
            return true
        }
        // 锁屏状态不开层：拾取窗画在锁屏上既无意义又可能泄露上次内容。
        val keyguard = host.context.getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return false
        session++
        textSnapshot = null
        captureSignal = CompletableDeferred()
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
                    originX = originX,
                    originY = originY,
                    portal = PortalState(
                        anchor = portalAnchor,
                        finger = portalFinger,
                        content = portalContent,
                        resolving = portalResolving,
                    ),
                    onSelectionAction = ::onSelectionAction,
                    onPortalStart = ::beginPortalAtView,
                    onPortalMove = ::movePortalAtView,
                    onPortalAction = ::onPortalAction,
                    onFrameSelectStart = ::dismissPortal,
                    ocrPanel = ocrPanelText?.let {
                        OcrPanelState(text = it, loading = ocrPanelLoading)
                    },
                    onOcrTextChange = ::onOcrTextChange,
                    onOcrAction = ::onOcrAction,
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
        hunoia.luno.runtime.surface.SurfaceController.register(this)
        // 识屏分享图缓存只留最近几张；顺手清掉历史遗留的大缓存。
        host.coroutineScope.launch(Dispatchers.IO) { cleanScreenPickCache() }
        startLockWatch()
        composeView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> updateOrigin(v) }
        composeView.post { correctWindowOriginOnce(composeView) }
        if (preCapture != null) {
            adoptPreCapture(preCapture)
        } else {
            captureWhenSettled(session)
        }
        return true
    }

    /** 长按预捕获的截图已经在菜单前拍好：直接接管，不再重新截屏。 */
    private fun adoptPreCapture(preCapture: PickCapture) {
        if (preCapture.bitmap.isRecycled) {
            Log.w(LOG_TAG, "screen pick pre-capture dropped: bitmap recycled")
            return
        }
        capture?.let { old -> if (old !== preCapture.bitmap && !old.isRecycled) old.recycle() }
        capture = preCapture.bitmap
        captureOriginX = preCapture.originX
        captureOriginY = preCapture.originY
        captureScreenW = preCapture.screenW
        captureScreenH = preCapture.screenH
        captured = true
        if (!captureSignal.isCompleted) captureSignal.complete(Unit)
        refreshPortalPreview()
    }

    /**
     * 全屏长按入口：在落点直接进入幽灵卡态，不经过“轻点取词 / 拖动框选”。
     *
     * 手势由调用方（内核触摸流的 [hunoia.luno.gesture.touchsource.GlobalLongPressTrigger]）
     * 继续喂 [movePortal] / [releasePortal]：按下那一刻已经发生在别人的窗口上，
     * 本层拿不到后续 MOVE/UP，只能借同一股原始触摸流。
     */
    fun showPortalAt(
        x: Int,
        y: Int,
        preCapture: ScreenPreCapture? = null,
    ): Boolean {
        if (!show(preCapture?.capture)) return false
        beginPortal(x, y, preCapture?.text)
        return true
    }

    /** 幽灵卡跟随手指（屏幕坐标）。 */
    fun movePortal(x: Int, y: Int) {
        if (portalAnchor == null) return
        portalFinger = Offset(x.toFloat(), y.toFloat())
    }

    /**
     * 全局长按松手：只保留幽灵卡，不执行动作。
     *
     * “从卡片上拖动 = 移动卡片”，动作由底部按钮点击触发；
     * 松手直接退出会让卡片无法被拖动/继续框选。
     */
    fun releasePortal(x: Int, y: Int) {
        // 保留卡片；动作由卡片底部按钮点击执行。
    }

    /** 点击底部动作槽：立即执行，执行后关层（无例外）。 */
    private fun onPortalAction(action: PortalAction) {
        if (portalResolving) return
        val content = portalContent
        val imageBounds = portalImageBounds
        // 动作以参数捕获内容/截图，不依赖卡片状态；关层后后台仍能继续落盘/分享。
        val captureForAction = if (content.imageSize != null) currentCapture() else null
        // 关层会回收截图；图片动作异步在跑，先把 saving 置位让它拿走这张位图
        //（withCropped 收尾时会补回收）。
        if (captureForAction != null) saving = true
        executePortalAction(action, content, imageBounds, captureForAction)
        close()
    }

    /** 内嵌手势（识屏层自己收到的长按）入口：视图坐标换算成屏幕坐标后同一套流程。 */
    private fun beginPortalAtView(viewPoint: Offset) {
        beginPortal(
            (viewPoint.x + originX).roundToInt(),
            (viewPoint.y + originY).roundToInt(),
        )
    }

    private fun movePortalAtView(viewPoint: Offset) {
        movePortal((viewPoint.x + originX).roundToInt(), (viewPoint.y + originY).roundToInt())
    }

    private fun beginPortal(
        screenX: Int,
        screenY: Int,
        snapshot: TextSnapshot? = null,
    ) {
        textSnapshot = snapshot
        portalAnchor = Offset(screenX.toFloat(), screenY.toFloat())
        portalFinger = portalAnchor
        portalContent = PortalContent()
        portalImageBounds = null
        portalResolving = true
        val token = session
        val pickToken = ++portalSession
        portalJob?.cancel()
        // 节点树遍历是成百上千次 Binder 调用，绝不上主线程。
        portalJob = host.coroutineScope.launch {
            val liveSnapshot = if (snapshot == null) {
                withContext(Dispatchers.Default) {
                    runCatching {
                        A11yTextSnapshotReader(host.accessibilityService, host.context.packageName)
                            .capture(screenX, screenY)
                    }.getOrNull()
                }
            } else {
                null
            }
            val effective = snapshot ?: liveSnapshot ?: TextSnapshot.Empty
            if (snapshot == null && liveSnapshot != null) textSnapshot = liveSnapshot
            val hit = withContext(Dispatchers.Default) {
                snapshotHit(effective, screenX, screenY)
            }
            if (token != session || pickToken != portalSession || overlayView == null) return@launch
            if (BuildConfig.DEBUG) {
                Log.i(
                    LOG_TAG,
                    "screen pick hit text=${hit?.text?.length ?: 0} image=${hit?.imageBounds != null} " +
                        "snapshot=${snapshot != null}",
                )
            }
            val imageBounds = hit?.imageBounds
            portalImageBounds = imageBounds
            refreshPortalPreview()
            applyPortalText(hit?.text, imageBounds)
            // 图片/布局命中只出纯图片卡（W×H + 复制/分享/保存），不再回退 OCR：
            // 整屏图跑 OCR 耗时且独占引擎，还会把无关 UI 文字混进卡片。
        }
    }

    /**
     * 快照命中：落点直接压在图片节点上时优先出图片卡（Copy/Share/Save）；
     * 仅当同一落点同时压着更小的文字叶子（如图标内的字、背景图上的文字）才回退文字，
     * 避免整屏背景大图把上面的文字吞掉。图片节点不再被 contentDescription 当成文字
     * （见 [A11yTextSnapshotReader]）。
     */
    private fun snapshotHit(snapshot: TextSnapshot, x: Int, y: Int): NodeHit? {
        val tolerance = DensityProvider.dp2px(HitToleranceDp)
        val image = snapshot.imageAt(x, y, tolerance)
        val leaf = TextSnapshotOps.hitText(snapshot, x, y, tolerance)
        val text = leaf?.let { TextSnapshotOps.selectAll(it, x, y) }?.takeIf { it.isNotBlank() }
        val imageBounds = image?.bounds
        // 图片比压着的文字更小/更具体（或无文字）→ 图片优先；否则（小字压在大图上）回退文字。
        // text 非空必然来自 leaf 非空；用 leafArea 兜底，免去冗余的 null 判定。
        val leafArea = leaf?.bounds?.area ?: Int.MAX_VALUE
        if (imageBounds != null && (text == null || imageBounds.area <= leafArea)) {
            return NodeHit(
                null,
                AndroidRect(imageBounds.left, imageBounds.top, imageBounds.right, imageBounds.bottom),
                AndroidRect(imageBounds.left, imageBounds.top, imageBounds.right, imageBounds.bottom),
            )
        }
        if (text != null) return NodeHit(text, null, null)
        if (image != null) {
            val b = image.bounds
            return NodeHit(
                null,
                AndroidRect(b.left, b.top, b.right, b.bottom),
                AndroidRect(b.left, b.top, b.right, b.bottom),
            )
        }
        // 布局兜底：空白处（无文字叶子、无图片节点）取按下点所在的最小可视节点 bounds，
        // 走图片卡路径（截图裁剪出预览 + 复制/分享/保存）。
        snapshot.layoutAt(x, y, tolerance)?.let { rect ->
            return NodeHit(
                null,
                AndroidRect(rect.left, rect.top, rect.right, rect.bottom),
                AndroidRect(rect.left, rect.top, rect.right, rect.bottom),
            )
        }
        return null
    }

    /** 统一回填幽灵卡内容：文本与图片可并存（图片节点 OCR 出文字时两者都有）。 */
    private fun applyPortalText(text: String?, imageBounds: AndroidRect?) {
        portalContent = PortalContent(
            text = text,
            imageSize = imageBounds?.let { it.width() to it.height() },
            url = text?.let {
                runCatching { hunoia.luno.link.LinkUrls.extractUrls(it, allowBareDomains = true) }
                    .getOrNull()
                    ?.firstOrNull()
            },
        )
        portalResolving = false
        refreshPortalPreview()
    }

    /** 等预截图就绪（长按可能比截图快）；超时或截图失败返回 null。 */
    private suspend fun awaitCapture(timeoutMs: Long = 1_200): PickCapture? {
        if (currentCapture() == null) {
            withTimeoutOrNull(timeoutMs) { captureSignal.await() }
        }
        return currentCapture()
    }

    /**
     * 对屏幕区域做 OCR，返回屏幕坐标下的文本。
     *
     * 给了 [hitX]/[hitY] 时取落点命中的那一块（长按），否则把区域内所有行拼成文本（图片 / 框选）。
     * 只识别裁剪后的小图：更快，也避免整屏无关文字干扰落点命中。
     */
    private suspend fun ocrTextIn(
        token: Int,
        pickToken: Int,
        region: AndroidRect,
        hitX: Int? = null,
        hitY: Int? = null,
    ): String? {
        val snapshot = awaitCapture()
        if (snapshot == null) {
            Log.w(LOG_TAG, "screen pick ocr abort: no capture")
            return null
        }
        if (token != session || pickToken != portalSession) {
            if (BuildConfig.DEBUG) {
                Log.i(LOG_TAG, "screen pick ocr skip: session/token mismatch")
            }
            return null
        }
        val crop = cropRectFor(snapshot, region)
        if (crop == null) {
            Log.w(
                LOG_TAG,
                "screen pick ocr abort: crop null region=$region " +
                    "capture=${captureOriginX},${captureOriginY} ${captureScreenW}x${captureScreenH}",
            )
            return null
        }
        val scripts = screenPickGesture().ocrScripts.ifEmpty { listOf(OcrScript.Chinese) }
        ocrInFlight++
        return try {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    cropBitmap(
                        snapshot.bitmap,
                        crop,
                    )
                }.getOrNull()
            } ?: run {
                Log.w(LOG_TAG, "screen pick ocr abort: crop bitmap failed crop=$crop")
                return null
            }
            try {
                // ML Kit 的 Task 不可取消：识别段不可中断，避免回收正在被读取的位图。
                val result = withContext(NonCancellable) { ocrEngine.recognize(bitmap, scripts) }
                if (token != session || pickToken != portalSession) {
                    if (BuildConfig.DEBUG) {
                        Log.i(LOG_TAG, "screen pick ocr skip after recognize: session/token mismatch")
                    }
                    return null
                }
                val blocks = result.blocks.map { it.toScreenBlock(snapshot, crop) }
                val text = if (hitX != null && hitY != null) {
                    // 只认落点所在的那一块：按在空白处就该是空，不吸附到隔壁行。
                    OcrText.blockAt(
                        blocks = blocks,
                        x = hitX,
                        y = hitY,
                        tolerancePx = DensityProvider.dp2px(HitToleranceDp),
                        maxDistancePx = 0,
                    )?.text
                } else {
                    OcrText.join(blocks).ifBlank { null }
                }
                if (BuildConfig.DEBUG) {
                    Log.i(LOG_TAG, "screen pick ocr blocks=${blocks.size} text=${text?.length ?: 0}")
                }
                text
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        } finally {
            ocrInFlight--
            // 层已关/换会话时截图已从 overlay 摘下，这里补回收。
            if (capture !== snapshot.bitmap && !snapshot.bitmap.isRecycled) {
                snapshot.bitmap.recycle()
            }
        }
    }

    /** OCR 包围盒（裁剪图内坐标）→ 屏幕坐标，与 [cropRectFor] 互为逆运算。 */
    private fun hunoia.luno.capability.ocr.OcrBlock.toScreenBlock(
        capture: PickCapture,
        crop: AndroidRect,
    ): hunoia.luno.capability.ocr.OcrBlock {
        val sx = capture.bitmap.width.toFloat() / capture.screenW
        val sy = capture.bitmap.height.toFloat() / capture.screenH
        return copy(
            left = (capture.originX + (crop.left + left) / sx).roundToInt(),
            top = (capture.originY + (crop.top + top) / sy).roundToInt(),
            right = (capture.originX + (crop.left + right) / sx).roundToInt(),
            bottom = (capture.originY + (crop.top + bottom) / sy).roundToInt(),
        )
    }

    /** 图片节点的缩略图：等预截图就绪后从原图裁一小块给幽灵卡。 */
    private fun refreshPortalPreview() {
        val bounds = portalImageBounds ?: return
        if (portalContent.imageSize == null || portalContent.preview != null) return
        val snapshot = currentCapture() ?: return
        val token = session
        portalPreviewJob?.cancel()
        portalPreviewJob = host.coroutineScope.launch {
            val preview = withContext(Dispatchers.Default) {
                runCatching {
                    if (snapshot.bitmap.isRecycled) return@runCatching null
                    val crop = cropRectFor(snapshot, bounds) ?: return@runCatching null
                    val source = cropBitmap(
                        snapshot.bitmap,
                        crop,
                    )
                    val target = DensityProvider.dp2px(160)
                    val scale = (target.toFloat() / max(source.width, source.height)).coerceAtMost(1f)
                    val scaled = if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            source,
                            (source.width * scale).roundToInt().coerceAtLeast(1),
                            (source.height * scale).roundToInt().coerceAtLeast(1),
                            true,
                        ).also { if (it !== source) source.recycle() }
                    } else {
                        source
                    }
                    scaled.asImageBitmap()
                }.getOrNull()
            }
            if (token != session || preview == null) return@launch
            portalContent = portalContent.copy(preview = preview)
        }
    }

    private fun cancelPortal() {
        portalJob?.cancel()
        portalJob = null
        portalPreviewJob?.cancel()
        portalPreviewJob = null
        portalAnchor = null
        portalFinger = null
        portalContent = PortalContent()
        portalImageBounds = null
        portalResolving = false
    }

    /**
     * 撤掉幽灵卡但保留拾取层（进入框选时调用）：卡片只在长按取词阶段存在，
     * 框选开始后不再回来；在途的取词 OCR 一并作废，防止迟到结果把卡片填回来。
     */
    private fun dismissPortal() {
        if (portalAnchor == null) return
        portalJob?.cancel()
        portalJob = null
        portalPreviewJob?.cancel()
        portalPreviewJob = null
        portalSession++
        portalAnchor = null
        portalFinger = null
        portalContent = PortalContent()
        portalImageBounds = null
        portalResolving = false
    }

    private fun executePortalAction(
        action: PortalAction,
        content: PortalContent,
        imageBounds: AndroidRect?,
        capture: PickCapture? = null,
    ) {
        host.coroutineScope.launch {
            when (action) {
                    PortalAction.Copy -> {
                        val text = content.text
                        if (!text.isNullOrBlank()) {
                            copyText(host.context, SCREEN_PICK_LABEL, text)
                            showToast(R.string.quick_search_copied)
                        } else if (imageBounds != null) {
                            if (!copyImageRegion(AndroidRect(imageBounds), capture)) {
                                showToast(R.string.screen_pick_failed)
                            }
                        } else {
                            showToast(R.string.screen_pick_no_content)
                        }
                    }
                    PortalAction.Search -> {
                        val text = content.text
                        when {
                            text.isNullOrBlank() -> showToast(R.string.screen_pick_no_content)
                            !onSearch(text) -> showToast(R.string.screen_pick_failed)
                        }
                    }
                    PortalAction.Share -> {
                        val text = content.text
                        if (!text.isNullOrBlank()) {
                            shareText(text)
                        } else if (imageBounds != null) {
                            if (!shareImageRegion(AndroidRect(imageBounds), capture)) {
                                showToast(R.string.screen_pick_failed)
                            }
                        } else {
                            showToast(R.string.screen_pick_no_content)
                        }
                    }
                    PortalAction.Save -> {
                        if (imageBounds == null || !saveImageRegion(AndroidRect(imageBounds), capture)) {
                            showToast(R.string.screen_pick_failed)
                        }
                    }
                    PortalAction.OpenLink -> {
                        val url = content.url
                        when {
                            url.isNullOrBlank() -> showToast(R.string.screen_pick_no_content)
                            !onOpenLink(url) -> showToast(R.string.screen_pick_failed)
                        }
                    }
                }
        }
    }

    /** 「发送到应用」：纯文本走系统分享面板（与图片分享同一出口口径）。 */
    private fun shareText(text: String) {
        val context = host.context
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(share, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
            .onFailure { showToast(R.string.screen_pick_failed) }
    }

    fun close() {
        stopLockWatch()
        session++
        portalSession++
        resolvingAction = false
        closeOcrPanel()
        cancelPortal()
        textSnapshot = null
        if (!captureSignal.isCompleted) captureSignal.complete(Unit)
        if (BuildConfig.DEBUG) {
            Log.i(LOG_TAG, "screen pick close: hasCapture=${capture != null} saving=$saving ocrInFlight=$ocrInFlight")
        }
        val view = overlayView ?: return
        overlayView = null
        hunoia.luno.runtime.surface.SurfaceController.unregister(surfaceId)
        // 落盘 / OCR 在跑时不回收：后台仍在用这张位图；收尾处补回收。
        if (!saving && ocrInFlight == 0) {
            capture?.let { if (!it.isRecycled) it.recycle() }
            capture = null
        } else {
            // 交给仍持有它的后台任务回收；避免下一次会话误用旧截图。
            capture = null
        }
        captured = false
        captureOriginX = 0
        captureOriginY = 0
        captureScreenW = 0
        captureScreenH = 0
        originX = 0
        originY = 0
        originCorrected = false
        val wm = host.context.windowManager()
        runCatching { wm.removeView(view) }
        onClosed()
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

    /** 本次截图覆盖的屏幕区域（窗口截图时原点不是 0）。 */
    private fun captureRegion(capture: PickCapture): AndroidRect = AndroidRect(
        capture.originX,
        capture.originY,
        capture.originX + capture.screenW,
        capture.originY + capture.screenH,
    )

    /** 当前预截图快照；overlay 未就绪 / 截图已回收时返回 null。 */
    private fun currentCapture(): PickCapture? {
        val bitmap = capture ?: return null
        if (bitmap.isRecycled || captureScreenW <= 0 || captureScreenH <= 0) return null
        return PickCapture(bitmap, captureOriginX, captureOriginY, captureScreenW, captureScreenH)
    }

    /**
     * 屏幕矩形 → 截图位图内的裁剪矩形。先与截图区域求交再换算：越界部分取交集
     * （裁剪内容仍正确），完全落在区域外返回 null。不能用 coerceIn 把越界边钳到
     * 边缘 —— 那会把内容整体平移，复制到一张错位图。
     */
    private fun cropRectFor(capture: PickCapture, rect: AndroidRect): AndroidRect? {
        val region = captureRegion(capture)
        val inter = AndroidRect(rect)
        if (!inter.intersect(region) || inter.isEmpty) return null
        val sx = capture.bitmap.width.toFloat() / capture.screenW
        val sy = capture.bitmap.height.toFloat() / capture.screenH
        val crop = AndroidRect(
            ((inter.left - capture.originX) * sx).roundToInt().coerceIn(0, capture.bitmap.width),
            ((inter.top - capture.originY) * sy).roundToInt().coerceIn(0, capture.bitmap.height),
            ((inter.right - capture.originX) * sx).roundToInt().coerceIn(0, capture.bitmap.width),
            ((inter.bottom - capture.originY) * sy).roundToInt().coerceIn(0, capture.bitmap.height),
        )
        if (crop.width() <= 0 || crop.height() <= 0) return null
        return crop
    }

    /**
     * 裁剪预截图。`Bitmap.createBitmap` 在裁剪区域等于整图时会**原样返回源位图**，
     * 而调用方对裁剪结果都是“用完即回收”，那会把预截图一起收掉（相册里整屏兜底/大图
     * 命中后 OCR/保存全变成 no capture 的根因）。这里遇同实例强制拷贝。
     */
    private fun cropBitmap(src: Bitmap, crop: AndroidRect): Bitmap {
        val direct = Bitmap.createBitmap(src, crop.left, crop.top, crop.width(), crop.height())
        return if (direct === src) {
            direct.copy(Bitmap.Config.ARGB_8888, false) ?: direct
        } else {
            direct
        }
    }

    /** 框选动作（图标动作条）：文本复制/搜索/分享，图片复制/分享/保存，OCR 单独进可编辑面板。 */
    private fun onSelectionAction(rect: Rect, action: PickSelectionAction) {
        if (resolvingAction) return
        if (rect.width < SCREEN_PICK_MIN_REGION_PX || rect.height < SCREEN_PICK_MIN_REGION_PX) return
        resolvingAction = true
        val screenRect = AndroidRect(
            (rect.left + originX).roundToInt(),
            (rect.top + originY).roundToInt(),
            (rect.right + originX).roundToInt(),
            (rect.bottom + originY).roundToInt(),
        )
        if (action == PickSelectionAction.Ocr) {
            if (!screenPickGesture().ocrEnabled) {
                showToast(R.string.screen_pick_no_content)
                resolvingAction = false
                return
            }
            openOcrPanel(screenRect)
            resolvingAction = false
            return
        }
        host.coroutineScope.launch {
            val ok = when (action) {
                PickSelectionAction.Copy -> {
                    val text = selectionText(screenRect)
                    if (!text.isNullOrBlank()) {
                        copyText(host.context, SCREEN_PICK_LABEL, text)
                        showToast(R.string.quick_search_copied)
                        true
                    } else {
                        copyImageRegion(screenRect)
                    }
                }
                PickSelectionAction.Search -> {
                    val text = selectionText(screenRect)
                    when {
                        text.isNullOrBlank() -> {
                            showToast(R.string.screen_pick_no_content)
                            false
                        }
                        !onSearch(text) -> {
                            showToast(R.string.screen_pick_failed)
                            false
                        }
                        else -> true
                    }
                }
                PickSelectionAction.Share -> {
                    val text = selectionText(screenRect)
                    if (!text.isNullOrBlank()) {
                        shareText(text)
                        true
                    } else {
                        shareImageRegion(screenRect)
                    }
                }
                PickSelectionAction.Save -> saveImageRegion(screenRect)
                PickSelectionAction.Ocr -> false
            }
            resolvingAction = false
            // 动作执行即关层（无例外）；OCR 已改为打开面板，由面板按钮关层。
            if (action != PickSelectionAction.Ocr) close()
            if (!ok) {
                // 图片动作失败时给出与旧逻辑一致的提示；文本路径已各自提示。
                if (action != PickSelectionAction.Search) showToast(R.string.screen_pick_failed)
            }
        }
    }

    /** 选区文本：优先用长按快照；快照没有覆盖时补抓实时 a11y（过滤目标窗口）。 */
    private suspend fun selectionText(screenRect: AndroidRect): String? {
        val snapshot = textSnapshot
        if (snapshot != null && snapshot.leaves.isNotEmpty()) {
            val text = TextSnapshotOps.regionText(
                snapshot,
                screenRect.left,
                screenRect.top,
                screenRect.right,
                screenRect.bottom,
            )
            if (text.isNotBlank()) return text
        }
        return withContext(Dispatchers.Default) {
            val live = runCatching {
                A11yTextSnapshotReader(host.accessibilityService, host.context.packageName)
                    .capture(screenRect.left, screenRect.top)
            }.getOrNull() ?: return@withContext null
            TextSnapshotOps.regionText(
                live,
                screenRect.left,
                screenRect.top,
                screenRect.right,
                screenRect.bottom,
            ).takeIf { it.isNotBlank() }
        }
    }

    /** 框选菜单点 OCR：优先交给结果页 Activity，失败才回退可编辑浮层。 */
    private fun openOcrPanel(rect: AndroidRect) {
        ocrPanelRect = rect
        ocrPanelText = ""
        ocrPanelLoading = true
        // 先不抢焦点：结果页 Activity 自己处理 IME；只有回退到浮层时才需要可获焦。
        val token = session
        val panelToken = ++ocrPanelSession
        host.coroutineScope.launch {
            val text = ocrTextIn(session, portalSession, rect)
            if (token != session || panelToken != ocrPanelSession) return@launch
            ocrPanelLoading = false
            when {
                text.isNullOrBlank() -> {
                    ocrPanelText = ""
                    setWindowFocusable(true)
                    showToast(R.string.screen_pick_no_content)
                }
                runCatching { onOcrResult(text) }.getOrDefault(false) -> close()
                else -> {
                    ocrPanelText = text
                    setWindowFocusable(true)
                }
            }
        }
    }

    private fun closeOcrPanel() {
        if (ocrPanelText == null && !ocrPanelLoading) return
        ocrPanelSession++
        ocrPanelText = null
        ocrPanelLoading = false
        ocrPanelRect = null
        setWindowFocusable(false)
    }

    private fun onOcrTextChange(text: String) {
        ocrPanelText = text
    }

    /** OCR 面板按钮：除重试外，执行后一律关层。 */
    private fun onOcrAction(action: OcrPanelAction) {
        when (action) {
            OcrPanelAction.Retry -> ocrPanelRect?.let { openOcrPanel(it) }
            else -> {
                when (action) {
                    OcrPanelAction.Close -> Unit
                    OcrPanelAction.Copy -> {
                        val text = ocrPanelText.orEmpty()
                        if (text.isBlank()) {
                            showToast(R.string.screen_pick_no_content)
                        } else {
                            copyText(host.context, SCREEN_PICK_LABEL, text)
                            showToast(R.string.quick_search_copied)
                        }
                    }
                    OcrPanelAction.Search -> {
                        val text = ocrPanelText.orEmpty()
                        when {
                            text.isBlank() -> showToast(R.string.screen_pick_no_content)
                            !onSearch(text) -> showToast(R.string.screen_pick_failed)
                        }
                    }
                    OcrPanelAction.Share -> {
                        val text = ocrPanelText.orEmpty()
                        if (text.isBlank()) showToast(R.string.screen_pick_no_content)
                        else shareText(text)
                    }
                    else -> Unit
                }
                // 关闭/复制/搜索/分享都关层；toast 在拆窗前发出仍可见。
                close()
            }
        }
    }

    /**
     * OCR 面板要能编辑，识屏窗必须先去掉 `FLAG_NOT_FOCUSABLE` 并允许 IME；
     * 关面板时恢复成不可获焦，避免影响底层 App 的输入。
     */
    private fun setWindowFocusable(focusable: Boolean) {
        val view = overlayView ?: return
        val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
        lp.flags = if (focusable) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        lp.softInputMode = if (focusable) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        host.context.windowManager().safeUpdateViewLayout(view, lp)
    }

    /**
     * 复制图片：写系统图库后进剪贴板。粘贴链路仍需公开的 MediaStore URI，
     * 因此即使关了「保存到相册」，复制动作也会保留一份（分享才走缓存）。
     */
    private suspend fun copyImageRegion(
        rect: AndroidRect,
        capture: PickCapture? = null,
    ): Boolean = withCropped(rect, capture) { bitmap ->
        val uri = withContext(Dispatchers.IO) { saveImageToGallery(bitmap) } ?: return@withCropped false
        if (!copyImage(host.context, SCREEN_PICK_LABEL, uri)) return@withCropped false
        showToast(R.string.screen_pick_image_copied)
        true
    }

    /** 分享图片：按设置选相册或缓存（缓存路径不污染图库）。 */
    private suspend fun shareImageRegion(
        rect: AndroidRect,
        capture: PickCapture? = null,
    ): Boolean = withCropped(rect, capture) { bitmap ->
        val toGallery = host.advancedSettings?.screenPickSaveToGallery == true
        val uri = withContext(Dispatchers.IO) {
            if (toGallery) saveImageToGallery(bitmap) else saveImageToCache(bitmap)
        } ?: return@withCropped false
        shareImage(uri)
        true
    }

    /** 保存图片：无条件写相册，并顺手放进剪贴板；不弹分享。 */
    private suspend fun saveImageRegion(
        rect: AndroidRect,
        capture: PickCapture? = null,
    ): Boolean = withCropped(rect, capture) { bitmap ->
        val uri = withContext(Dispatchers.IO) { saveImageToGallery(bitmap) } ?: return@withCropped false
        copyImage(host.context, SCREEN_PICK_LABEL, uri)
        showToast(R.string.screen_pick_image_saved)
        true
    }

    private fun shareImage(uri: Uri) {
        val context = host.context
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // 必须同时带 clipData：只给 EXTRA_STREAM 时部分应用拿不到读取授权，
            // 会把图片当普通文件/附件显示（系统截屏分享走的是带授权的媒体 URI）。
            clipData = android.content.ClipData.newUri(context.contentResolver, SCREEN_PICK_LABEL, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(share, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(chooser) }
            .onFailure { Log.w(LOG_TAG, "screen pick: share chooser failed: ${it.message}") }
    }

    /**
     * 裁剪公共段：[block] 在图片落盘期间执行，裁剪图用完即回收；`saving` 期间
     * 关闭拾取层不回收截图位图（后台还在用它裁剪），收尾处补回收。
     */
    private suspend fun withCropped(
        rect: AndroidRect,
        capture: PickCapture? = null,
        block: suspend (Bitmap) -> Boolean,
    ): Boolean {
        val snapshot = capture ?: currentCapture()
        if (snapshot == null) {
            Log.w(LOG_TAG, "screen pick: crop abort: no capture rect=$rect")
            return false
        }
        val crop = cropRectFor(snapshot, rect)
        if (crop == null) {
            Log.w(
                LOG_TAG,
                "screen pick: crop abort: rect out of capture rect=$rect " +
                    "capture=${captureOriginX},${captureOriginY} ${captureScreenW}x${captureScreenH}",
            )
            return false
        }
        saving = true
        try {
            val cropped = withContext(Dispatchers.IO) {
                runCatching {
                    cropBitmap(
                        snapshot.bitmap,
                        crop,
                    )
                }.onFailure {
                    Log.e(LOG_TAG, "screen pick: crop failed: ${it.message}")
                }.getOrNull()
            } ?: return false
            return try {
                block(cropped)
            } finally {
                cropped.recycle()
            }
        } finally {
            saving = false
            // 关闭发生在落盘期间：图片已复制/UI 已拆，这里补回收截图位图。
            if (overlayView == null && this.capture === snapshot.bitmap) {
                if (!snapshot.bitmap.isRecycled) snapshot.bitmap.recycle()
                this.capture = null
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
        if (uri == null) {
            Log.w(LOG_TAG, "screen pick: save image: MediaStore insert failed")
            return@runCatching null
        }
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
        }.getOrDefault(false)
        if (!written) {
            Log.w(LOG_TAG, "screen pick: save image: compress/write failed")
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

    /** 裁剪图写入缓存目录，经 FileProvider 供分享；顺手清理旧文件。 */
    private fun saveImageToCache(bitmap: Bitmap): Uri? = runCatching {
        val context = host.context
        val dir = File(context.cacheDir, "screen_pick").apply { mkdirs() }
        cleanScreenPickCache(dir)
        val file = File(dir, "screen_pick_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.onFailure {
        Log.e(LOG_TAG, "screen pick: save cache image failed: ${it.message}")
    }.getOrNull()

    /** 清理识屏分享图缓存，只保留最近 [CACHE_KEEP_FILES] 张。 */
    private fun cleanScreenPickCache(
        dir: File = File(host.context.cacheDir, "screen_pick"),
    ) {
        runCatching {
            if (!dir.isDirectory) return
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(CACHE_KEEP_FILES)
                ?.forEach { it.delete() }
        }
    }

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
                if (BuildConfig.DEBUG) {
                    Log.i(LOG_TAG, "screen pick shot: window id=${window.id}")
                }
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

    private fun takeScreenshotOfDisplay(token: Int): Boolean {
        if (BuildConfig.DEBUG) {
            Log.i(LOG_TAG, "screen pick shot: display fallback")
        }
        return runCatching {
            host.accessibilityService.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                screenshotCallback(token, windowBounds = null, allowDisplayFallback = false),
            )
        }.onFailure {
            Log.e(LOG_TAG, "screen pick: takeScreenshot rejected: ${it.message}")
        }.isSuccess
    }

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
                    if (token != session) {
                        Log.w(LOG_TAG, "screen pick shot dropped: token mismatch ($token != $session)")
                        return
                    }
                    val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    val copied = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                    if (copied == null) {
                        pickFailed()
                    } else {
                        capture = copied
                        recordCaptureRegion(windowBounds)
                        if (!captureSignal.isCompleted) captureSignal.complete(Unit)
                        // 蒙层与提示框解耦：截图一到就亮层（可立即点/拖），提示框后台扫完再补。
                        captured = true
                        // 幽灵卡可能比截图先出现：截图就绪后补图片缩略图。
                        refreshPortalPreview()
                    }
                } finally {
                    result.hardwareBuffer.close()
                }
            }

            override fun onFailure(errorCode: Int) {
                if (token != session) return
                Log.w(LOG_TAG, "screen pick shot failed: errorCode=$errorCode")
                // 窗口截图失败（刚切走/系统拒绝）：回退整屏一次，不直接判失败。
                if (allowDisplayFallback && windowBounds != null && takeScreenshotOfDisplay(token)) return
                pickFailed()
            }

            private fun pickFailed() {
                Log.w(LOG_TAG, "screen pick shot: pick failed (no capture)")
                // 幽灵卡不靠截图也能用（复制/搜索/发送文字）：截图失败只降级图片动作，
                // 不把已经浮起的卡片直接收掉。
                if (!captureSignal.isCompleted) captureSignal.complete(Unit)
                val keepPortal = portalAnchor != null
                showToast(R.string.screen_pick_failed)
                if (!keepPortal) close()
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
            val hadPanels = openPanels.hasOpenPanels
            if (BuildConfig.DEBUG) {
                Log.i(LOG_TAG, "screen pick captureWhenSettled: panels=$hadPanels")
            }
            openPanels.awaitDismissed()
            if (token != session || overlayView == null || capture != null) {
                if (BuildConfig.DEBUG) {
                    Log.i(
                        LOG_TAG,
                        "screen pick captureWhenSettled skip: session=${token == session} " +
                            "shown=${overlayView != null} hasCapture=${capture != null}",
                    )
                }
                return@launch
            }
            // 面板退场多留一拍：动作面板的淡出在 Dismissed 之后还可能再收尾。
            if (hadPanels) delay(PanelPostSettleMs)
            // 扫描与截屏并行：提示框不用等 takeScreenshot 回调（它往往是慢的那一步）。
            if (!takeScreenshot()) {
                Log.w(LOG_TAG, "screen pick shot: takeScreenshot rejected outright")
                showToast(R.string.screen_pick_failed)
                // 幽灵卡仍可用文字/搜索动作：截图被拒也只降级图片动作，不拆窗。
                if (portalAnchor == null) close()
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

    private data class NodeHit(
        val text: String?,
        val imageBounds: AndroidRect?,
        val fallbackBounds: AndroidRect?,
    )


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
    originX: Int,
    originY: Int,
    portal: PortalState,
    onSelectionAction: (Rect, PickSelectionAction) -> Unit,
    onPortalStart: (Offset) -> Unit,
    onPortalMove: (Offset) -> Unit,
    onPortalAction: (PortalAction) -> Unit,
    onFrameSelectStart: () -> Unit,
    ocrPanel: OcrPanelState?,
    onOcrTextChange: (String) -> Unit,
    onOcrAction: (OcrPanelAction) -> Unit,
    onDismiss: () -> Unit,
) {
    // dragRect = 正在画的新框；adjustRect = 已落定、可拖动/缩放、待确认的框。
    var dragRect by remember { mutableStateOf<Rect?>(null) }
    var adjustRect by remember { mutableStateOf<Rect?>(null) }
    // 框选期间隐藏幽灵卡（需求：框选时不显示卡片）。
    var selecting by remember { mutableStateOf(false) }
    val handleHitPx = with(LocalDensity.current) { HandleHitRadiusDp.dp.toPx() }
    val minSizePx = with(LocalDensity.current) { AdjustMinSizeDp.dp.toPx() }
    val handleDrawPx = with(LocalDensity.current) { 6.dp.toPx() }
    val scope = rememberCoroutineScope()
    val portalActive = rememberUpdatedState(portal.anchor != null)
    val currentPortal = rememberUpdatedState(portal)
    val density = LocalDensity.current.density
    var cardHeightPx by remember { mutableStateOf(0f) }
    var cardWidthPx by remember { mutableStateOf(0f) }
    val currentCardHeight = rememberUpdatedState(cardHeightPx)
    val currentCardWidth = rememberUpdatedState(cardWidthPx)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(ocrPanel != null) {
                if (ocrPanel != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.isConsumed) return@awaitEachGesture
                    val start = down.position
                    // 已落定的选框（adjustRect）优先命中：拖角=缩放、框内=平移。
                    // 必须在卡片/框选分支之前判定：幽灵卡一旦长按出过就一直 active
                    // （releasePortal 保留卡片），否则选框永远走不到调整分支，
                    // 表现为“拖不动、一点就新建框”。
                    if (adjustRect != null) {
                        val handle = adjustRect!!.handleAt(start, handleHitPx)
                        if (handle != null || adjustRect!!.contains(start)) {
                            var last = start
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                change.consume()
                                val delta = change.position - last
                                last = change.position
                                val r = adjustRect ?: break
                                adjustRect = if (handle != null) {
                                    r.resized(handle, change.position, minSizePx, size.toSizeF())
                                } else {
                                    r.moved(delta.x, delta.y, size.toSizeF())
                                }
                            }
                            return@awaitEachGesture
                        }
                    }
                    val portalFinger = currentPortal.value.finger ?: currentPortal.value.anchor
                    val cardRect = if (portalFinger != null) {
                        PortalLayout.cardRect(
                            finger = Offset(portalFinger.x - originX, portalFinger.y - originY),
                            screenWidth = DensityProvider.screenWidthPx.toFloat(),
                            screenHeight = DensityProvider.screenHeightPx.toFloat(),
                            density = density,
                            cardWidthPx = currentCardWidth.value,
                            cardHeightPx = currentCardHeight.value,
                        )
                    } else {
                        null
                    }
                    val downOnCard = cardRect?.contains(start) == true

                    // 按住卡片拖动 = 移动卡片；松手不执行动作。
                    if (portalActive.value && downOnCard) {
                        var moved = false
                        var upPosition = start
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                upPosition = change.position
                                if (change.isConsumed) break
                                if (!change.pressed) break
                                change.consume()
                                if (!moved &&
                                    (change.position - start).getDistance() >
                                    viewConfiguration.touchSlop
                                ) {
                                    moved = true
                                }
                                if (moved) {
                                    onPortalMove(change.position)
                                }
                            }
                        } finally {
                            // 卡片拖动后保持在最后位置；未移动的轻点视为取消并退出。
                        }
                        if (!moved) onDismiss()
                        return@awaitEachGesture
                    }

                    // 幽灵卡在屏、按在空白：拖动=框选；长按=重新取词；轻点=清空但不退出。
                    if (portalActive.value) {
                        var moved = false
                        var longPressed = false
                        var upPosition = start
                        var childConsumed = false
                        val longPress = scope.launch {
                            delay(viewConfiguration.longPressTimeoutMillis)
                            if (!moved && !childConsumed) {
                                longPressed = true
                                selecting = false
                                dragRect = null
                                adjustRect = null
                                onPortalStart(start)
                            }
                        }
                        try {
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
                                if ((change.position - start).getDistance() >
                                    viewConfiguration.touchSlop
                                ) {
                                    moved = true
                                    longPress.cancel()
                                    if (!longPressed) {
                                        selecting = true
                                        // 框选一开始就撤卡：卡只在取词阶段存在，落框/动作阶段不再回来。
                                        onFrameSelectStart()
                                        dragRect = Rect(
                                            left = minOf(start.x, change.position.x),
                                            top = minOf(start.y, change.position.y),
                                            right = maxOf(start.x, change.position.x),
                                            bottom = maxOf(start.y, change.position.y),
                                        )
                                    }
                                }
                            }
                        } finally {
                            longPress.cancel()
                        }
                        if (childConsumed) return@awaitEachGesture
                        if (longPressed) return@awaitEachGesture
                        if (moved) {
                            val rect = dragRect
                            dragRect = null
                            selecting = false
                            if (rect != null &&
                                rect.width >= minSizePx && rect.height >= minSizePx
                            ) {
                                adjustRect = rect
                            } else {
                                onDismiss()
                            }
                        } else {
                            // 轻点空白：取消本次取词并退出识屏层；层内长按仍走重新取词。
                            selecting = false
                            dragRect = null
                            adjustRect = null
                            onDismiss()
                        }
                        return@awaitEachGesture
                    }

                    // 无幽灵卡：拖动=框选；长按=出幽灵卡；轻点=退出。
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
                    var portalStarted = false
                    fun startPortal(at: Offset) {
                        portalStarted = true
                        selecting = false
                        adjustRect = null
                        onPortalStart(start)
                        onPortalMove(at)
                    }
                    // 识屏层内长按“按住即触发”，不设拖动取消窗口；
                    // dragStartTimeoutMs 只作用于全局长按路径（GlobalLongPressTrigger）。
                    val portalTimer = scope.launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        if (!moved && !childConsumed && !portalStarted &&
                            initial == null && handle == null && !moving
                        ) {
                            startPortal(start)
                        }
                    }
                    try {
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
                            if (portalStarted) {
                                onPortalMove(change.position)
                                continue
                            }
                            if (!moved && (change.position - start).getDistance() >
                                viewConfiguration.touchSlop
                            ) {
                                moved = true
                            }
                            when {
                                resizing != null -> {
                                    val r = adjustRect ?: break
                                    adjustRect = r.resized(
                                        resizing, change.position, minSizePx, size.toSizeF(),
                                    )
                                }
                                moving -> {
                                    val r = adjustRect ?: break
                                    adjustRect = r.moved(delta.x, delta.y, size.toSizeF())
                                }
                                moved -> {
                                    selecting = true
                                    dragRect = Rect(
                                        left = minOf(start.x, change.position.x),
                                        top = minOf(start.y, change.position.y),
                                        right = maxOf(start.x, change.position.x),
                                        bottom = maxOf(start.y, change.position.y),
                                    )
                                }
                            }
                        }
                    } finally {
                        portalTimer.cancel()
                    }
                    if (childConsumed) return@awaitEachGesture
                    if (portalStarted) {
                        selecting = false
                        dragRect = null
                        return@awaitEachGesture
                    }
                    if (resizing == null && !moving) {
                        val rect = dragRect
                        dragRect = null
                        selecting = false
                        if (rect != null &&
                            rect.width >= minSizePx && rect.height >= minSizePx
                        ) {
                            adjustRect = rect
                        } else if (!moved) {
                            // 轻点：取消并退出识屏层。
                            onDismiss()
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
            // 新拖动优先显示：已有旧框时也要能看见正在画的框。
            val activeRect = dragRect ?: adjustRect
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
            // 选区待确认时显示动作条；无选区不画底部关闭按钮，统一由「点击空白」关闭。
            val sel = adjustRect
            if (sel != null) {
                SelectionActionBar(
                    rect = sel,
                    originX = originX,
                    originY = originY,
                    onAction = { action ->
                        adjustRect = null
                        onSelectionAction(sel, action)
                    },
                )
            }
        }
        // 框选期间/选区待确认/OCR 面板打开时隐藏幽灵卡；否则显示并跟手。
        if (portal.anchor != null && !selecting && adjustRect == null && ocrPanel == null) {
            PortalLayer(
                finger = (portal.finger ?: portal.anchor).let {
                    Offset(it.x - originX, it.y - originY)
                },
                content = portal.content,
                resolving = portal.resolving,
                actions = portalActionsFor(portal.content),
                cardHeightPx = cardHeightPx,
                cardWidthPx = cardWidthPx,
                onCardSize = {
                    cardWidthPx = it.width.toFloat()
                    cardHeightPx = it.height.toFloat()
                },
                onAction = onPortalAction,
            )
        }
        ocrPanel?.let { panel ->
            ScreenOcrPanel(
                state = panel,
                onTextChange = onOcrTextChange,
                onAction = onOcrAction,
            )
        }
    }
}

/** 框选动作条：贴在选框底部；下方放不下就翻到上方，水平夹在屏内。不含关闭按钮（点击空白即关闭）。 */
@Composable
private fun SelectionActionBar(
    rect: Rect,
    originX: Int,
    originY: Int,
    onAction: (PickSelectionAction) -> Unit,
) {
    val density = LocalDensity.current
    val button = 40.dp
    val gap = 8.dp
    val pad = 6.dp
    val margin = 8.dp
    val barW = with(density) { (button * 5 + gap * 4 + pad * 2).toPx() }
    val barH = with(density) { (button + pad * 2).toPx() }
    val marginPx = with(density) { margin.toPx() }
    val screenW = DensityProvider.screenWidthPx.toFloat()
    val screenH = DensityProvider.screenHeightPx.toFloat()
    // 选框坐标 → 视图坐标。
    val left = rect.left - originX
    val top = rect.top - originY
    val right = rect.right - originX
    val bottom = rect.bottom - originY
    val x = ((left + right) / 2f - barW / 2f)
        .coerceIn(marginPx, (screenW - barW - marginPx).coerceAtLeast(marginPx))
    val below = bottom + marginPx
    val y = if (below + barH <= screenH - marginPx) {
        below
    } else {
        (top - marginPx - barH).coerceAtLeast(marginPx)
    }
    Row(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(pad),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PickActionIcon(
            icon = Icons.Default.TextFields,
            contentDescription = stringResource(R.string.screen_pick_extract_text),
        ) { onAction(PickSelectionAction.Ocr) }
        PickActionIcon(
            icon = Icons.Default.ContentCopy,
            contentDescription = stringResource(R.string.screen_pick_copy_image),
        ) { onAction(PickSelectionAction.Copy) }
        PickActionIcon(
            icon = Icons.Default.Search,
            contentDescription = stringResource(R.string.screen_pick_portal_search),
        ) { onAction(PickSelectionAction.Search) }
        PickActionIcon(
            icon = Icons.Default.Share,
            contentDescription = stringResource(R.string.screen_pick_share_image),
        ) { onAction(PickSelectionAction.Share) }
        PickActionIcon(
            icon = Icons.Default.SaveAlt,
            contentDescription = stringResource(R.string.screen_pick_save_image),
        ) { onAction(PickSelectionAction.Save) }
    }
}

@Composable
private fun PickActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White,
        modifier = Modifier.size(40.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** IntSize → Float Size（PointerInputScope 的 size 是 IntSize）。 */
private fun IntSize.toSizeF() = Size(width.toFloat(), height.toFloat())


