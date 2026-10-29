package hunoia.luno.gesture.touchsource

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.gesture.TouchWindowHint
import hunoia.luno.gesture.touch.GetEventDevice
import hunoia.luno.gesture.touch.GetEventParser
import hunoia.luno.gesture.touch.TouchAction
import hunoia.luno.gesture.touch.TouchFrame
import hunoia.luno.gesture.touch.TouchPoint
import hunoia.luno.gesture.touch.TouchTracker
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.shizuku.ShizukuShellStream
import hunoia.luno.shizuku.ShizukuShellSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shizuku 触摸源的对外状态：设置页/首页用它解释“现在到底是哪条链路在跑”。 */
data class ShizukuTouchStatus(
    val state: State = State.STOPPED,
    val detail: String = "",
    val deviceName: String = "",
    /** 拦截窗是否生效；false 时下层应用会同时收到同一段手势。 */
    val intercepting: Boolean = false,
) {
    enum class State {
        STOPPED,
        WAITING_SHIZUKU,
        SCANNING,
        STREAMING,
        ERROR,
    }
}

/**
 * 内核触摸流输入源（Shizuku shell 身份读 `/dev/input/eventX`）。
 *
 * 职责边界：
 * - 只产生**触摸帧**并投给 [TouchInputRouter]，不做手势识别、不执行动作；
 * - 拦截窗是它自己的副作用（没有窗就拦不住系统手势），几何由 [TouchInputGeometry] 统一给；
 * - 失败一律降级并暴露在 [status] 上，不静默。
 *
 * 降级口径：Shizuku 未就绪 → 等；拿不到悬浮窗权限 → 只监听不拦截（此时不宣称独占
 * 拦截区，避免与无障碍窗口重复触发）；读到事件流结束 → 退避重连。
 */
class ShizukuTouchInputSource(
    private val context: Context,
    private val scope: CoroutineScope,
    private val router: TouchInputRouter,
    private val layoutProvider: () -> TouchInputLayout,
) {

    private val _status = MutableStateFlow(ShizukuTouchStatus())
    val status: StateFlow<ShizukuTouchStatus> = _status.asStateFlow()

    private val windows = InterceptWindows(context)
    private val tracker = TouchTracker()
    private val mapper = TouchScreenMapper(context)

    private var monitorJob: Job? = null
    private var session: ShizukuShellSession? = null

    /** 上一次真正摆上去的拦截区；配置每次发射都重建窗会闪，相同就跳过。 */
    private var appliedRegions: List<InterceptRegion> = emptyList()

    /** 注入期间屏蔽自己注入的事件（按压取词等后续功能复用）。 */
    @Volatile
    var suppressUntilMs: Long = 0L

    /**
     * 全屏触摸帧监听（坐标已归屏幕像素）：每一帧（含非拦截区）都会回调，
     * 由 :app 用来做“全屏任意位置长按取词”等全局手势识别。
     * 与来源仲裁互不干扰——本监听不取会话归属、不消费触摸，底层 App 照常收到触摸。
     */
    var globalFrameListener: ((screen: Offset, action: TouchAction) -> Unit)? = null

    /** 拦截窗是否生效：生效时路由才认为拦截区归本来源（否则整块按钮区仍归无障碍窗口）。 */
    @Volatile
    var intercepting: Boolean = false
        private set

    val isRunning: Boolean get() = monitorJob?.isActive == true

    fun start() {
        if (isRunning) return
        // 逐帧解析与手势投递都在主线程：手势会话的 job/震动/面板都按主线程写，
        // 且文本解析每行只是短正则，主线程成本远低于一次跨线程切上下文。
        monitorJob = scope.launch(Dispatchers.Main.immediate) { monitorLoop() }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        closeSession()
        releaseWindows()
        router.reset()
        _status.value = ShizukuTouchStatus()
    }

    /** 按钮配置/拦截宽度变化：只重建窗，不重启事件流。 */
    fun refreshGeometry() {
        applyWindows()
    }

    private suspend fun monitorLoop() {
        var attempt = 0
        while (scope.isActive) {
            _status.value = ShizukuTouchStatus(ShizukuTouchStatus.State.WAITING_SHIZUKU, "等待 Shizuku 授权")
            if (!awaitShizuku()) {
                releaseWindows()
                val backoff = backoffMs(attempt++)
                _status.value = ShizukuTouchStatus(
                    ShizukuTouchStatus.State.WAITING_SHIZUKU,
                    "Shizuku 未运行或未授权，${backoff / 1000}s 后重试",
                )
                delay(backoff)
                continue
            }

            _status.value = ShizukuTouchStatus(ShizukuTouchStatus.State.SCANNING, "扫描触摸设备")
            val device = scanTouchDevice()
            if (device == null) {
                val backoff = backoffMs(attempt++)
                _status.value = ShizukuTouchStatus(
                    ShizukuTouchStatus.State.ERROR,
                    "未找到触摸屏设备（检查 getevent 权限），${backoff / 1000}s 后重试",
                )
                delay(backoff)
                continue
            }

            mapper.device = device
            applyWindows(force = true)
            _status.value = ShizukuTouchStatus(
                state = ShizukuTouchStatus.State.STREAMING,
                detail = if (intercepting) {
                    "监听中：拦截区归 Shizuku，其余触摸照常穿透"
                } else {
                    "监听中：悬浮窗未授权，未拦截（下层会同时收到同一手势）"
                },
                deviceName = device.name.ifBlank { device.path },
                intercepting = intercepting,
            )

            attempt = 0
            openAndCollect(device)
            closeSession()
            releaseWindows()
            router.reset()
            if (scope.isActive) delay(1_500)
        }
    }

    private suspend fun openAndCollect(device: GetEventDevice) {
        val opened = ShizukuShellStream.openArgv("/system/bin/getevent", "-lt", device.path)
        val stream = when (opened) {
            is ShizukuShellStream.OpenResult.Failure -> {
                _status.value = ShizukuTouchStatus(
                    ShizukuTouchStatus.State.ERROR,
                    "打开 ${device.path} 失败：${opened.reason}",
                )
                return
            }
            is ShizukuShellStream.OpenResult.Success -> opened.session
        }
        session = stream
        try {
            tracker.reset()
            stream.lines.collect { line ->
                if (SystemClock.uptimeMillis() < suppressUntilMs) return@collect
                val event = GetEventParser.parseEvent(line) ?: return@collect
                val frame = tracker.feed(event) ?: return@collect
                dispatch(frame)
            }
            _status.value = ShizukuTouchStatus(
                ShizukuTouchStatus.State.ERROR,
                "事件流已结束：${stream.stderrText.ifBlank { "无 stderr" }}",
            )
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            _status.value = ShizukuTouchStatus(
                ShizukuTouchStatus.State.ERROR,
                "读取事件失败：${t.message ?: t.javaClass.simpleName}",
            )
        }
    }

    private fun dispatch(frame: TouchFrame) {
        val point = primaryPoint(frame) ?: return
        val screen = mapper.toScreen(point) ?: return
        // 全屏帧先喂全局监听（含非拦截区）：长按取词等全局手势识别复用同一股原始输入，
        // 不取会话归属、不消费触摸（与 一墨 T3 同口径，底层 App 照常收到触摸）。
        globalFrameListener?.invoke(screen, frame.action)
        when (frame.action) {
            TouchAction.Down -> {
                val region = router.acceptsShizuku(screen) ?: return
                mapper.lastScreen = screen
                router.onDown(
                    TouchSourceKind.SHIZUKU,
                    screen,
                    TouchWindowHint(region.buttonId, region.mirrored),
                )
            }
            TouchAction.Move -> {
                if (router.currentSource != TouchSourceKind.SHIZUKU) return
                val last = mapper.lastScreen
                mapper.lastScreen = screen
                if (last == null) return
                router.onMove(screen - last)
            }
            TouchAction.Up -> {
                if (router.currentSource != TouchSourceKind.SHIZUKU) return
                mapper.lastScreen = null
                router.onUp()
            }
        }
    }

    /** 多指时锁定第一根落下的手指；其余手指不参与，也不打断会话。 */
    private fun primaryPoint(frame: TouchFrame): TouchPoint? =
        frame.points.minByOrNull { it.id }

    private suspend fun awaitShizuku(): Boolean {
        repeat(20) {
            ShizukuManager.updateStatus()
            val status = ShizukuManager.currentStatus()
            if (status.isReady) return true
            if (!status.binderAlive) delay(500)
        }
        return ShizukuManager.currentStatus().isReady
    }

    private suspend fun scanTouchDevice(): GetEventDevice? = withContext(Dispatchers.IO) {
        val result = ShizukuManager.executeShell("getevent -lp", maxChars = 256 * 1024)
        if (!result.isSuccess) return@withContext null
        val devices = GetEventParser.parseDevices(result.stdout)
        devices.filter { it.isTouchscreen }.maxByOrNull { it.score }
            ?: devices.maxByOrNull { it.score }
    }

    /**
     * 重建拦截窗。只吃「这一按钮配了 Shizuku 来源」的那块，宽度按该按钮自己的设置。
     * 配置每次发射都会走到这里，所以相同几何直接跳过，避免窗口抖动。
     */
    private fun applyWindows(force: Boolean = false) {
        if (!isRunning) return
        val next = layoutProvider().interceptRegions()
        if (!force && next == appliedRegions && windows.isShowing == next.isNotEmpty()) {
            syncIntercepting()
            return
        }
        appliedRegions = next
        windows.update(next)
        syncIntercepting()
    }

    private fun syncIntercepting() {
        intercepting = windows.isShowing
        val current = _status.value
        if (current.state == ShizukuTouchStatus.State.STREAMING) {
            _status.value = current.copy(
                intercepting = windows.isShowing,
                detail = if (windows.isShowing) {
                    "监听中：拦截区归 Shizuku，其余触摸照常穿透"
                } else {
                    "监听中：悬浮窗未授权，未拦截（下层会同时收到同一手势）"
                },
            )
        }
    }

    private fun releaseWindows() {
        windows.release()
        appliedRegions = emptyList()
        intercepting = false
    }

    private fun closeSession() {
        session?.let { runCatching { it.close() } }
        session = null
    }

    private fun backoffMs(attempt: Int): Long {
        val steps = longArrayOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
        return steps[attempt.coerceIn(0, steps.lastIndex)]
    }
}

/**
 * 原始触摸设备坐标 → 当前屏幕像素。假设设备原生为竖屏，按当前旋转做行列变换。
 *
 * 旋转每 300ms 最多查一次：`getevent` 每帧都来，逐帧绑 binder 不值得。
 */
internal class TouchScreenMapper(private val context: Context) {

    var device: GetEventDevice? = null

    /** 上一帧的屏幕坐标，用于算 MOVE 增量。 */
    var lastScreen: Offset? = null

    private var rotation: Int = Surface.ROTATION_0
    private var rotationFetchedAt = 0L

    fun toScreen(point: TouchPoint): Offset? {
        val d = device ?: return null
        val nx = d.normalizedX(point.x) ?: return null
        val ny = d.normalizedY(point.y) ?: return null
        val width = DensityProvider.screenWidthPx.toFloat()
        val height = DensityProvider.screenHeightPx.toFloat()
        if (width <= 0f || height <= 0f) return null
        return when (currentRotation()) {
            Surface.ROTATION_90 -> Offset((1f - ny) * width, nx * height)
            Surface.ROTATION_270 -> Offset(ny * width, (1f - nx) * height)
            Surface.ROTATION_180 -> Offset((1f - nx) * width, (1f - ny) * height)
            else -> Offset(nx * width, ny * height)
        }
    }

    private fun currentRotation(): Int {
        val now = SystemClock.uptimeMillis()
        if (now - rotationFetchedAt < 300L) return rotation
        rotationFetchedAt = now
        rotation = runCatching {
            val manager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            manager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        }.getOrDefault(Surface.ROTATION_0)
        return rotation
    }
}
