package hunoia.luno.runtime.recentspicker

import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import hunoia.luno.action.model.RecentsPickerLayout
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.core.Events
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.core.domain.AppLaunchAction
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.RecentApp
import hunoia.luno.runtime.overlay.OverlayPanelHost
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.surface.SurfaceDispatch
import hunoia.luno.ui.theme.DialogScrimAlpha
import hunoia.luno.ui.theme.SideGestureTheme
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 滚轮窗口与屏幕边缘的最小间距：触点贴边时仍留出呼吸空间，不顶到物理边缘。 */
private val RecentsPickerScreenMargin = 12.dp

/**
 * 最近应用滚动选择器悬浮窗（TYPE_APPLICATION_OVERLAY 可获焦宿主）：
 * 全屏透明窗 + 触点定位滚轮，点空白 / 返回键取消，点图标启动。
 *
 * 宿主骨架（BACK、组合常驻、Shown/Dismissed 事件）见 [OverlayPanelHost]；
 * 这里只留滚轮编排、点选启动与取消收口。滚动状态 [RecentsPickerState] 由
 * 手势管线持有并传入：按住阶段管线喂 [RecentsPickerState.onDrag]，抬手后
 * 窗口内滚轮直接操同一份 [RecentsPickerState.listState]。
 */
object RecentsPickerOverlay : OverlayPanelHost(
    logTag = LOG_TAG,
    logPrefix = "recents picker",
) {

    override val surfaceId: String = "recents-picker"

    private var state by mutableStateOf<RecentsPickerState?>(null)

    /** 窗口关闭后的收口回调（协调器刷新触钮等）；由 [show] 传入。 */
    private var onClosed: (() -> Unit)? = null

    /** @param state 管线持有的会话状态（已 activate，含列表/布局/锚点）。 */
    fun show(state: RecentsPickerState, onClosed: () -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(state, onClosed) }
            return
        }
        this.state = state
        this.onClosed = onClosed
        if (shownEventPosted) {
            // 已显示：新一次触发只换数据（列表/布局/锚点），复用窗口与组合。
            lifecycleOwner?.resume()
            return
        }
        val current = view
        val windowReady = when {
            current == null -> attach()
            !attached -> {
                wm.safeAddView(current, layoutParamsFor()).also { ok ->
                    if (ok) correctWindowOrigin(current)
                }
            }
            else -> {
                restore()
                true
            }
        }
        if (!windowReady || view == null) {
            this.state = null
            this.onClosed = null
            return
        }
        lifecycleOwner?.resume()
        if (!shownEventPosted) {
            shownEventPosted = true
            Events.post(PanelShownEvent())
        }
    }

    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { close() }
            return
        }
        if (!shownEventPosted && view == null) return
        Log.i(LOG_TAG, "recents picker close: view=${view != null} attached=${view?.isAttachedToWindow} shown=$shownEventPosted")
        closeImmediate()
    }

    override fun onBackKeyUp() {
        close()
    }

    override fun closeImmediate() {
        postDismissed()
        val s = state
        state = null
        s?.clear()
        removeAndTeardown()
        onClosed?.invoke()
        onClosed = null
    }

    @Composable
    override fun panelContent() {
        SideGestureTheme {
            val s = state ?: return@SideGestureTheme
            RecentsPickerContent(
                state = s,
                onLaunch = { app -> launchApp(app, miniWindow = false) },
                onLongPress = { app -> launchApp(app, miniWindow = true) },
                onDismiss = { closeAfterGesture() },
            )
        }
    }

    /**
     * 手势回调路径的关闭：延迟一帧执行。`detectTapGestures` 的 onTap/onLongPress 仍
     * 在 pointer 协程里，此时同步 `removeViewImmediate` 会抛
     * `Already resumed, but proposed with update`（Compose pointer 协程未结束）→ 移除失败
     * → 全屏窗口残留挡触摸。post 到下一帧让 pointer 协程先结束再移窗。
     */
    private fun closeAfterGesture() {
        mainHandler.post { close() }
    }

    /** 点选/长按：启动该应用（带 userId 直达分身实例），点击全屏、长按小窗；发起即收窗。 */
    private fun launchApp(app: RecentApp, miniWindow: Boolean) {
        // 小窗防抖：与按住停留（hold 检测）共享同一份状态，同一会话只放行一次小窗。
        if (miniWindow) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - (state?.lastMiniWindowLaunchAtMs ?: 0L) < RECENTS_MINI_DEBOUNCE_MS) return
            state?.lastMiniWindowLaunchAtMs = now
        }
        // 小窗跟随滚轮打开时的触点（state.anchor），不是点选时手指所在位置。
        val anchor = state?.anchor
            ?.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?.let { it.x.roundToInt() to it.y.roundToInt() }
        SurfaceDispatch.dispatch(
            action = AppLaunchAction.stored(
                packageName = app.packageName,
                userId = app.userId,
                miniWindow = miniWindow,
            ),
            origin = DispatchOrigin.PANEL,
            launchAnchor = anchor,
        ) { _ -> closeAfterGesture() }
    }
}

/** 小窗启动防抖：长按图标与按住停留可能同时触发，间隔内只启动一次。 */
private const val RECENTS_MINI_DEBOUNCE_MS = 800L

/** 全屏滚轮内容：底色遮罩 + 触点定位滚轮；空白点击取消。 */
@Composable
private fun RecentsPickerContent(
    state: RecentsPickerState,
    onLaunch: (RecentApp) -> Unit,
    onLongPress: (RecentApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val iconSize = state.iconSizeDp.dp
    val rowSizePx = with(density) { recentsWheelRowSize(iconSize).toPx() }
    val crossPx = with(density) { recentsWheelCrossSize(iconSize).toPx() }
    val vertical = state.layout == RecentsPickerLayout.VERTICAL
    val wheelWidthPx = if (vertical) crossPx else rowSizePx * RecentsWheelVisibleRows
    val wheelHeightPx = if (vertical) rowSizePx * RecentsWheelVisibleRows else crossPx
    val anchor: Offset = state.anchor
    val screenWidth = DensityProvider.screenWidthPx
    val screenHeight = DensityProvider.screenHeightPx
    // 滚轮窗口与屏幕边缘的最小间距：触点贴边时滚轮不会顶到物理边缘，视觉不拥挤。
    val marginPx = with(density) { RecentsPickerScreenMargin.toPx() }.roundToInt()
    // 窗口复用：show() 再次触发时只替换 state，组合与 pointerInput 节点不会重建。
    // 手势闭包必须现读当前会话状态，否则新一次触发拖动的还是上一份已 clear() 的 state。
    val currentState = rememberUpdatedState(state)
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DialogScrimAlpha))
            // 点击空白 = 取消。拖动由下面的 detectDragGestures 消费，tap 检测因 move 被消费自动取消。
            .pointerInput(Unit) { detectTapGestures { currentOnDismiss.value() } }
            // 空白处拖动也滚动滚轮（图标随手指）：把增量喂给共享 state，松手吸附。
            // 滚轮自身的滚动在子节点被消费，不会落到这里的 drag 检测（slop 判定取消）。
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentState.value.onDrag(dragAmount)
                    },
                    onDragEnd = { currentState.value.onDragEnd() },
                    onDragCancel = { currentState.value.onDragEnd() },
                )
            },
    ) {
        RecentsWheel(
            apps = state.apps,
            listState = state.listState,
            layout = state.layout,
            iconSize = iconSize,
            onAppTap = onLaunch,
            onAppLongPress = onLongPress,
            modifier = Modifier.offset {
                val maxX = (screenWidth - wheelWidthPx).roundToInt() - marginPx
                val maxY = (screenHeight - wheelHeightPx).roundToInt() - marginPx
                val x = (anchor.x - wheelWidthPx / 2f).roundToInt()
                    .coerceIn(marginPx, maxX.coerceAtLeast(marginPx))
                val y = (anchor.y - wheelHeightPx / 2f).roundToInt()
                    .coerceIn(marginPx, maxY.coerceAtLeast(marginPx))
                IntOffset(x = x, y = y)
            },
        )
    }
}
