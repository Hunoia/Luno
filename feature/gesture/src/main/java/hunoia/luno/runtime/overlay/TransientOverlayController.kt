package hunoia.luno.runtime.overlay

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.config.model.SubGesture
import hunoia.luno.config.model.sectorCenterDegrees
import hunoia.luno.config.model.sectorWidthDegrees
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.gesture.MotionEventDispatcher
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.gesturepanel.ActionPanelIcon
import hunoia.luno.ui.theme.toMarkColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class TransientOverlayController(
    private val host: GestureHost,
) {
    private var actionPanelOverlayView: View? = null
    private var subGestureOverlayView: ComposeView? = null

    /** 子手势角度示意的动态状态：复用窗口时只改这两个，Compose 自动重组重绘。 */
    private val guideCenter = mutableStateOf(Offset.Unspecified)
    private val guideSubGesture = mutableStateOf<SubGesture?>(null)

    fun attachActionPanelOverlay() {
        if (actionPanelOverlayView != null) return
        val wm = host.context.windowManager()
        val lp = WindowLayoutFactory.actionPanelOverlayLayoutParams()
        val view = View(host.context).apply {
            setOnTouchListener { _, event ->
                MotionEventDispatcher.dispatch(event)
                true
            }
        }
        if (!wm.safeAddView(view, lp)) return
        actionPanelOverlayView = view
    }

    /** @return 是否真的拆掉了一个窗（供销毁路径补发配对的 PanelDismissed 用）。 */
    fun detachActionPanelOverlay(): Boolean {
        val view = actionPanelOverlayView ?: return false
        host.context.removeWindow(view)
        actionPanelOverlayView = null
        return true
    }

    /**
     * 挂上子手势会话窗（触摸源 + 角度示意）。窗口以 [center]（识别初始点）为中心的
     * `2×captureRadius` 正方形；[subGesture] 非空且 [SubGesture.showAngleGuide] 开启时，
     * 以初始点为圆心画扇区边界线 + 短滑/长滑范围圆（用子手势主题色）；否则纯透明。
     */
    fun attachSubGestureOverlay(center: Offset, subGesture: SubGesture?) {
        val wm = host.context.windowManager()
        val radiusPx = subGesture?.captureRadius ?: DensityProvider.dp2px(48f)
        val lp = WindowLayoutFactory.subGestureLayoutParams(center, radiusPx)
        // 窗口被屏幕边缘 clamp 后中心不一定在窗口中心，绘制圆心用「初始点 − 窗口左上角」。
        val localCenter = if (center.x.isFinite() && center.y.isFinite()) {
            Offset(center.x - lp.x, center.y - lp.y)
        } else {
            Offset(lp.width / 2f, lp.height / 2f)
        }
        guideCenter.value = localCenter
        guideSubGesture.value = subGesture
        subGestureOverlayView?.let { view ->
            wm.safeUpdateViewLayout(view, lp)
            return
        }
        val view = ComposeView(host.context).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setOnTouchListener { _, event ->
                MotionEventDispatcher.dispatch(event)
                true
            }
            setContent {
                val center by guideCenter
                val subGesture by guideSubGesture
                SubGestureGuideContent(center, subGesture)
            }
        }
        if (!wm.safeAddView(view, lp)) return
        subGestureOverlayView = view
    }

    /** @return 是否真的拆掉了一个窗（供销毁路径补发配对的 PanelDismissed 用）。 */
    fun detachSubGestureOverlay(): Boolean {
        val view = subGestureOverlayView ?: return false
        host.context.removeWindow(view)
        subGestureOverlayView = null
        return true
    }

    /** 拆掉所有瞬态浮层，返回拆掉的数量。 */
    fun detachAll(): Int {
        var count = 0
        if (detachActionPanelOverlay()) count++
        if (detachSubGestureOverlay()) count++
        return count
    }
}

/**
 * 子手势角度示意：以识别初始点（圆心）画扇区边界线 + 短滑/长滑范围圆，
 * 并在每个非零宽扇区的长滑圆弧中心叠两排动作图标：
 * 弧外是长滑 / 长滑停留，弧内是短滑 / 短按停留；滑动无底，停留实心底。
 *
 * 坐标约定与设置页的 `SubGestureAngleDial` 一致：边界值归一化 0..1（0° = 右，
 * 逆时针，屏幕 y 向下 → 端点 `y = cy − r·sin(θ)`）。颜色用子手势主题色
 * （未设置时跟随主题主色），保证在浅色背景上也能看清。
 */
@Composable
private fun SubGestureGuideContent(center: Offset, subGesture: SubGesture?) {
    if (subGesture == null || !subGesture.showAngleGuide) return
    if (!center.x.isFinite() || !center.y.isFinite()) return
    val guideColor = subGesture.colorSpec.toMarkColor()
    val shortRadius = subGesture.triggerDistance.toFloat()
    val longRadius = subGesture.effectiveLongSlideTriggerDistance.toFloat()
    val boundaries = subGesture.angle.boundaries
    val activeSlots = boundaries.indices.filter { sectorWidthDegrees(boundaries, it) > 0.01f }
    val density = LocalDensity.current
    val gapPx = with(density) { 2.dp.toPx() }
    // 8 方向 + 最小长滑距离时按扇区弧长收小，避免相邻扇区的两排图标叠在一起。
    val arcSpacing = if (activeSlots.isEmpty()) 0f else {
        (2f * PI.toFloat() * (longRadius * 0.8f)) / activeSlots.size
    }
    val badgePx = (arcSpacing * 0.32f).coerceIn(
        with(density) { 12.dp.toPx() },
        with(density) { 18.dp.toPx() },
    )

    Box(Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 短滑 / 长滑触发范围圆：半径 = 各自触发距离，颜色随主题但透明度区分主次。
            drawCircle(
                color = guideColor.copy(alpha = 0.30f),
                radius = shortRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = guideColor.copy(alpha = 0.50f),
                radius = longRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx()),
            )
            // 扇区边界线：圆心 → 长滑圆端点，实色主题色。
            for (bound in boundaries) {
                val angleRad = bound * 2f * PI.toFloat()
                drawLine(
                    color = guideColor,
                    start = center,
                    end = Offset(
                        x = center.x + longRadius * cos(angleRad),
                        y = center.y - longRadius * sin(angleRad),
                    ),
                    strokeWidth = 3.dp.toPx(),
                )
            }
        }

        if (activeSlots.isNotEmpty()) {
            val outerRadius = longRadius + gapPx + badgePx / 2f
            val innerRadius = (longRadius - gapPx - badgePx / 2f).coerceAtLeast(badgePx / 2f)
            activeSlots.forEach { slot ->
                val centerDegrees = sectorCenterDegrees(boundaries, slot)
                val centerRad = centerDegrees / 360.0 * 2.0 * PI
                val outerDelta = (badgePx + gapPx) / (2f * outerRadius)
                val innerDelta = (badgePx + gapPx) / (2f * innerRadius)
                // 每组靠扇区中心角摆放：滑动在左、停留在右；只有一颗时居中。
                val outerActions = listOfNotNull(
                    subGesture.longSlideActionFor(slot)?.let { GuideAction(it, hold = false) },
                    subGesture.longSlideHoldActionFor(slot)?.let { GuideAction(it, hold = true) },
                )
                val innerActions = listOfNotNull(
                    subGesture.slideActionFor(slot)?.let { GuideAction(it, hold = false) },
                    subGesture.slideHoldActionFor(slot)?.let { GuideAction(it, hold = true) },
                )
                GuideActionCluster(outerActions, center, centerRad, outerRadius, outerDelta, guideColor, badgePx)
                GuideActionCluster(innerActions, center, centerRad, innerRadius, innerDelta, guideColor, badgePx)
            }
        }
    }
}

private data class GuideAction(val action: StoredAction, val hold: Boolean)

@Composable
private fun GuideActionCluster(
    actions: List<GuideAction>,
    center: Offset,
    centerRad: Double,
    radius: Float,
    deltaRad: Float,
    color: Color,
    badgePx: Float,
) {
    if (actions.isEmpty()) return
    val offsets = if (actions.size == 1) listOf(0f) else listOf(-deltaRad, deltaRad)
    actions.forEachIndexed { index, item ->
        val angle = centerRad + offsets[index]
        val position = Offset(
            x = center.x + radius * cos(angle).toFloat(),
            y = center.y - radius * sin(angle).toFloat(),
        )
        GuideActionBadge(item, color, badgePx, position)
    }
}

@Composable
private fun GuideActionBadge(
    item: GuideAction,
    color: Color,
    badgePx: Float,
    center: Offset,
) {
    val density = LocalDensity.current
    val badge = with(density) { badgePx.toDp() }
    val iconSize = badge * 0.58f
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (center.x - badgePx / 2f).roundToInt(),
                    (center.y - badgePx / 2f).roundToInt(),
                )
            }
            .size(badge)
            .then(
                if (item.hold) {
                    Modifier.clip(CircleShape).background(color)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        ActionPanelIcon(
            action = item.action,
            iconSize = iconSize,
            bitmapIconSize = iconSize,
            tint = if (item.hold) holdContentColor(color) else color,
        )
    }
}

private fun holdContentColor(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White
