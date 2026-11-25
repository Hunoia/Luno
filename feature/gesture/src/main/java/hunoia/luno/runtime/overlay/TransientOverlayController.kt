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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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

    /** 子手势角度示意的动态状态：复用窗口时只改这几个，Compose 自动重组重绘。 */
    private val guideCenter = mutableStateOf(Offset.Unspecified)
    private val guideSubGesture = mutableStateOf<SubGesture?>(null)

    /** 释手进入、尚未重新按下时只画淡提示（圆心在屏幕中心），按下后转正色并搬到按点。 */
    private val guideHint = mutableStateOf(false)

    /** 子手势窗左上角（全局坐标）：把「按下点」换算成窗内坐标用。 */
    private var subGestureWindowOrigin = Offset.Zero

    /** 子手势窗形态与半径：重新按下时按同一形态把窗口搬到新按点。 */
    private var subGestureFullScreen = false
    private var subGestureRadiusPx = 0

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
     * 挂上子手势会话窗（触摸源 + 角度示意）。
     *
     * - [releaseEntry] = 手指已抬起才进入（抬手触发 / 面板点选 / 悬浮球点按）：全屏透明窗，
     *   点哪都算第二次按下；按下之前示意先摆在屏幕中心当淡提示。
     * - 按着进入：以 [center]（识别初始点）为中心的 `2×captureRadius` 方块，手指还在主
     *   overlay 上继续拖，窗口只做视觉。
     *
     * [subGesture] 非空且 [SubGesture.showAngleGuide] 开启时才画；否则纯透明。
     */
    fun attachSubGestureOverlay(center: Offset, subGesture: SubGesture?, releaseEntry: Boolean) {
        val wm = host.context.windowManager()
        val radiusPx = subGesture?.captureRadius ?: DensityProvider.dp2px(48f)
        val lp = WindowLayoutFactory.subGestureLayoutParams(center, radiusPx, fullScreen = releaseEntry)
        // 全屏窗原点恒为 (0,0)；小方块被屏幕边缘 clamp 后中心不一定在窗口中心，
        // 绘制圆心用「初始点 − 窗口左上角」。
        val localCenter = when {
            releaseEntry -> Offset(
                DensityProvider.screenWidthPx / 2f,
                DensityProvider.screenHeightPx / 2f,
            )
            center.x.isFinite() && center.y.isFinite() -> Offset(center.x - lp.x, center.y - lp.y)
            else -> Offset(lp.width / 2f, lp.height / 2f)
        }
        guideCenter.value = localCenter
        guideHint.value = releaseEntry
        guideSubGesture.value = subGesture
        subGestureWindowOrigin = Offset(lp.x.toFloat(), lp.y.toFloat())
        subGestureFullScreen = releaseEntry
        subGestureRadiusPx = radiusPx
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
                val hint by guideHint
                SubGestureGuideContent(center, subGesture, hint)
            }
        }
        if (!wm.safeAddView(view, lp)) return
        subGestureOverlayView = view
    }

    /** 会话内重新按下：判定圆心换到 [center]，示意搬过去并转正色。 */
    fun updateSubGestureCenter(center: Offset) {
        val view = subGestureOverlayView ?: return
        if (!center.x.isFinite() || !center.y.isFinite()) return
        guideHint.value = false
        if (subGestureFullScreen) {
            // 全屏窗原点恒为 (0,0)，全局坐标即窗内坐标。
            guideCenter.value = center
            return
        }
        // 小方块（按着进入）：窗口跟着新按点走，否则示意会被画到窗外裁掉。
        val lp = WindowLayoutFactory.subGestureLayoutParams(
            center,
            subGestureRadiusPx,
            fullScreen = false,
        )
        subGestureWindowOrigin = Offset(lp.x.toFloat(), lp.y.toFloat())
        guideCenter.value = center - subGestureWindowOrigin
        host.context.windowManager().safeUpdateViewLayout(view, lp)
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
 * 子手势角度示意：以判定圆心画扇区边界线 + 短滑/长滑范围圆，并在每个非零宽扇区的
 * 长滑圆弧中心叠两排动作图标：弧外是长滑 / 长滑停留，弧内是短滑 / 短按停留；
 * 滑动无底，停留实心底。
 *
 * 坐标约定与设置页的 `SubGestureAngleDial` 一致：边界值归一化 0..1（0° = 右，
 * 逆时针，屏幕 y 向下 → 端点 `y = cy − r·sin(θ)`）。颜色用子手势主题色
 * （未设置时跟随主题主色），保证在浅色背景上也能看清。
 *
 * [hint] = 释手进入后还没重新按下：整组图按半透明画在屏幕中心当提示。
 */
@Composable
private fun SubGestureGuideContent(center: Offset, subGesture: SubGesture?, hint: Boolean) {
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
    val strength = if (hint) 0.5f else 1f
    val strokeAlpha = 0.45f * strength
    val fillAlpha = 0.04f * strength

    Box(Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val shortWidth = 2.dp.toPx()
            val longWidth = 3.dp.toPx()
            val rayWidth = 2.5.dp.toPx()

            // 扇区淡填充：不开线也能看出方向分区。
            activeSlots.forEach { slot ->
                val startDeg = boundaries[slot] * 360f
                val sweepDeg = sectorWidthDegrees(boundaries, slot)
                // drawArc 0° = 右、顺时针；本仓角度逆时针为正，起点取 −(start + sweep)。
                drawArc(
                    color = guideColor.copy(alpha = fillAlpha),
                    startAngle = -(startDeg + sweepDeg),
                    sweepAngle = sweepDeg,
                    useCenter = true,
                    topLeft = Offset(center.x - longRadius, center.y - longRadius),
                    size = Size(longRadius * 2f, longRadius * 2f),
                )
            }

            // 两圈与射线在同一不透明度图层里用不透明色绘制：交叉处不会叠出更亮的断点。
            drawIntoCanvas { canvas ->
                val layerPaint = Paint().apply { alpha = strokeAlpha }
                canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                // 短滑 / 长滑范围圆：同色同透明度，只靠线宽区分主次。
                drawCircle(guideColor, shortRadius, center, style = Stroke(width = shortWidth))
                drawCircle(guideColor, longRadius, center, style = Stroke(width = longWidth))
                // 扇区边界线：圆心 → 长滑圆内沿，圆头收进圈里，不留方头豁口。
                val rayEnd = longRadius - longWidth / 2f
                for (bound in boundaries) {
                    val angleRad = bound * 2f * PI.toFloat()
                    drawLine(
                        color = guideColor,
                        start = center,
                        end = Offset(
                            x = center.x + rayEnd * cos(angleRad),
                            y = center.y - rayEnd * sin(angleRad),
                        ),
                        strokeWidth = rayWidth,
                        cap = StrokeCap.Round,
                    )
                }
                canvas.restore()
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
                GuideActionCluster(outerActions, center, centerRad, outerRadius, outerDelta, guideColor, badgePx, strength)
                GuideActionCluster(innerActions, center, centerRad, innerRadius, innerDelta, guideColor, badgePx, strength)
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
    alpha: Float,
) {
    if (actions.isEmpty()) return
    val offsets = if (actions.size == 1) listOf(0f) else listOf(-deltaRad, deltaRad)
    actions.forEachIndexed { index, item ->
        val angle = centerRad + offsets[index]
        val position = Offset(
            x = center.x + radius * cos(angle).toFloat(),
            y = center.y - radius * sin(angle).toFloat(),
        )
        GuideActionBadge(item, color, badgePx, position, alpha)
    }
}

@Composable
private fun GuideActionBadge(
    item: GuideAction,
    color: Color,
    badgePx: Float,
    center: Offset,
    alpha: Float = 1f,
) {
    val density = LocalDensity.current
    val badge = with(density) { badgePx.toDp() }
    val iconSize = badge * 0.58f
    Box(
        modifier = Modifier
            .alpha(alpha)
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
