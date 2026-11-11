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
                onLaunch = { app -> launchApp(app) },
                onDismiss = { close() },
            )
        }
    }

    /** 点选：启动该应用（带 userId 直达分身实例），成功即关窗。 */
    private fun launchApp(app: RecentApp) {
        SurfaceDispatch.dispatch(
            action = AppLaunchAction.stored(
                packageName = app.packageName,
                userId = app.userId,
            ),
            origin = DispatchOrigin.PANEL,
        ) { launched -> if (launched) close() }
    }
}

/** 全屏滚轮内容：底色遮罩 + 触点定位滚轮；空白点击取消。 */
@Composable
private fun RecentsPickerContent(
    state: RecentsPickerState,
    onLaunch: (RecentApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val rowSizePx = with(density) { RecentsWheelRowSize.toPx() }
    val crossPx = with(density) { RecentsWheelCrossSize.toPx() }
    val vertical = state.layout == RecentsPickerLayout.VERTICAL
    val wheelWidthPx = if (vertical) crossPx else rowSizePx * RecentsWheelVisibleRows
    val wheelHeightPx = if (vertical) rowSizePx * RecentsWheelVisibleRows else crossPx
    val anchor: Offset = state.anchor
    val screenWidth = DensityProvider.screenWidthPx
    val screenHeight = DensityProvider.screenHeightPx
    // 滚轮窗口与屏幕边缘的最小间距：触点贴边时滚轮不会顶到物理边缘，视觉不拥挤。
    val marginPx = with(density) { RecentsPickerScreenMargin.toPx() }.roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = DialogScrimAlpha))
            // 点击空白 = 取消。拖动由下面的 detectDragGestures 消费，tap 检测因 move 被消费自动取消。
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            // 空白处拖动也滚动滚轮（图标随手指）：把增量喂给共享 state，松手吸附。
            // 滚轮自身的滚动在子节点被消费，不会落到这里的 drag 检测（slop 判定取消）。
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.onDrag(dragAmount)
                    },
                    onDragEnd = { state.onDragEnd() },
                    onDragCancel = { state.onDragEnd() },
                )
            },
    ) {
        RecentsWheel(
            apps = state.apps,
            listState = state.listState,
            layout = state.layout,
            onAppTap = onLaunch,
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
