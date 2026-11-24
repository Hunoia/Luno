package hunoia.luno.runtime.floatball

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import hunoia.luno.core.LunoLog as Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import hunoia.luno.bridge.ui.SystemBarsSafeArea
import hunoia.luno.bridge.ui.systemBarsSafeAreaOrNull
import hunoia.luno.runtime.floatball.inbox.BadgeState
import hunoia.luno.runtime.floatball.inbox.BallSource
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.theme.MotionPress
import hunoia.luno.ui.theme.MotionSettle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.CapsuleGestureBehavior
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallPosition
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.config.model.CapsuleShape as AlertCapsuleShape
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.core.LOG_TAG
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.alert.AlertEnvelopeShare
import hunoia.luno.runtime.floatball.alert.FixedMotionDurationScale
import hunoia.luno.runtime.floatball.alert.AlertLayerPlan
import hunoia.luno.runtime.floatball.alert.AlertPaletteColors
import hunoia.luno.runtime.floatball.alert.CapsuleSpec
import hunoia.luno.runtime.floatball.alert.PulseTuning
import hunoia.luno.runtime.floatball.alert.ShimmerTuning
import hunoia.luno.runtime.floatball.alert.easedOut
import hunoia.luno.runtime.floatball.alert.envelopeAt
import hunoia.luno.runtime.floatball.alert.lerpInRange
import hunoia.luno.runtime.floatball.alert.toColors
import hunoia.luno.runtime.floatball.inbox.BallItem
import hunoia.luno.runtime.floatball.inbox.NotificationItem
import hunoia.luno.runtime.floatball.inbox.ResultItem
import hunoia.luno.runtime.floatball.inbox.ResultPrimaryAction
import hunoia.luno.runtime.recognition.RecognitionAction
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.ui.theme.CapsuleShape
import hunoia.luno.ui.theme.toMarkColor
import hunoia.luno.ui.icon.SearchIconCache
import hunoia.luno.ui.theme.MotionFamily
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.theme.motionMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/** 胶囊纵向落点（屏高归一）：胶囊自己的默认位置。 */
internal const val CapsuleAnchorYRatio = 0.5f

/**
 * 胶囊窗口：一个「能长开的按钮」——收起 = 直径 = 行高（胶囊设置 `rowHeightDp`，默认
 * [FloatBallDefaults.CapsuleRowHeightDp]）的正圆，展开 = 内容栈。
 *
 * 它是独立对象，**不读悬浮球的任何状态**（尺寸 / 位置 / 静置 / 键盘让位 / 拖动 / 判定区）：
 * 行高、侧别、纵向落点都是胶囊自己的（`rowHeightDp` / [CapsuleAnchorYRatio]）。
 *
 * 同一个 overlay 窗口承担开合：窗口 = 画面 + 余量，尺寸与 x/y 一次写进
 * [WindowManager.LayoutParams]，Compose 只按已提交的画面矩形画——不会出现「形变面按
 * 目标几何算、却画在旧窗里」的第一帧偏移。
 *
 * 内容：
 * - 通知胶囊展示未命中的普通通知；结果胶囊展示分发规则的胶囊档结果。
 * - 每条一颗独立胶囊，同屏行数按各自设置，超出竖向滚动；行宽贴内容、靠屏缘一侧齐平。
 * - 横滑两方向与行长按执行可配的内置行为（[CapsuleRowGestures]，默认 = 出厂行为：
 *   朝屏缘收起 / 反向忽略该行 / 结果行长按弹操作菜单），点按 = 执行主动作。
 * - 收起态：点按 = 长开（[BallCapsuleWindow.onExpandRequested]），长按 = 胶囊自己的长按
 *   行为（[BallCapsuleWindow.onCollapsedLongPress]）。
 */
internal class BallCapsuleWindow(
    private val context: android.content.Context,
    /** 槽来源：通知 / 结果。决定内容描述文案与默认球面图标。 */
    private val source: BallSource,
    /** 球面固定图标（通知球 / 结果球）；null = 按单击动作的图标。 */
    private val fixedIcon: Any?,
    /** 无徽标时的内容描述；null = 智能球文案。 */
    private val idleDescription: String?,
    /** 有未读时的内容描述生成器；null = 智能球未读文案。 */
    private val badgeDescription: ((Int) -> String)?,
) {

    /**
     * 胶囊自己的几何与姿态：**不读悬浮球的任何状态**（尺寸 / 位置 / 静置 / 键盘让位 / 拖动）。
     *
     * 收起态 = 直径 [rowHeightPx] 的正圆，贴 [sideRight] 那一侧、纵向落在 [posYPx]；
     * 展开 = 同一条相位把宽高长到内容尺寸。它就是一个「能长开的按钮」，与悬浮球无关。
     */

    /** 落点兜底（屏高归一）：[posYPx] 还没建立时用它（首帧位置）。拖动后不再读它。 */
    private var anchorYRatio = CapsuleAnchorYRatio

    /** 贴哪条屏缘：胶囊自己的侧别（默认右缘，可拖动换边 / 跟手换边）。
     *  必须是快照状态：Compose 按它决定画面贴窗口哪一侧，普通字段改了不重组。 */
    private var sideRight by mutableStateOf(true)

    /** 胶囊自己的落点：收起圆顶边的**屏幕绝对** y（px）。拖动只改它，< 0 = 尚未建立。 */
    private var posYPx = -1f

    /** 贴边缓动中的横向外移（px，带符号）：0 = 已贴紧 [sideRight] 那一侧。 */
    private var edgeOffsetPx = 0f

    /** 系统栏安全区（屏幕绝对 px）：胶囊不该停到状态栏 / 导航栏底下。 */
    private var barSafeArea = SystemBarsSafeArea()

    /** 键盘底部高度（display 绝对 px，0 = 无键盘）与它推出的抬升量（只进显示值）。 */
    private var imeBottomPx = 0
    private var imeLiftPx = 0f

    /** 收起圆正在被拖动：跟手位移直接写 LP（圆心由 [dragCenterX]/[dragCenterY] 接管）。 */
    private var circleDragging = false

    /** 拖动中的圆心（屏幕绝对 px）；NaN = 没在拖。 */
    private var dragCenterX = Float.NaN
    private var dragCenterY = Float.NaN

    /** 上一帧已消费的累计位移：把 arbiter 的累计 dx/dy 换成单帧增量。 */
    private var draggedDx = 0f
    private var draggedDy = 0f

    /** 静置变暗 / 半出屏：胶囊自己的姿态（胶囊设置里的 `idleDimDelayMs`），展开态永不静置。 */
    private var idled = false
    private var idleRunnable: Runnable? = null

    /** 松手贴边的缓动 / 换边淡出淡回的动画句柄。 */
    private var snapAnimator: ValueAnimator? = null
    private var sideFadeAnimator: ValueAnimator? = null
    private var visualAnimator: ValueAnimator? = null
    private var imeAnimator: ValueAnimator? = null

    /**
     * 按压缩放 / 行回弹：同样走 [ValueAnimator]，不用 `view.animate()`。
     * 收起圆的按压 pivot 必须落圆心（见 [applyCapsulePivot]），否则缩放会带着圆一起位移。
     */
    private var pressAnimator: ValueAnimator? = null
    private var rowReboundAnimator: ValueAnimator? = null

    /**
     * 横滑锁轴时解析出的行为 + 方向：视觉**只听行为**，不听手指方向。
     * 方向允许中途翻转（手指来回），翻转时重解析一次。
     */
    private var swipeBehavior = CapsuleGestureBehavior.NONE
    private var swipeTowardEdge = true

    /** 横滑松手速度（px/s，绝对值）：收起接续的节奏来源，[hideCapsule] 取一次即清。 */
    private var collapseEntrySpeedPx = 0f

    /** 胶囊侧设置（行色 / 行数 / 手势）：服务注入，与球侧互不影响。 */
    private var capsuleSettings by mutableStateOf(FloatBallSettings())

    /** 首次设置到达后已按当前朝向恢复过落点（只做一次）。 */
    private var positionRestored = false

    /** 指定朝向已存档的落点；null = 从未落过盘（保持内存现位）。 */
    private fun savedPosition(orientation: Boolean): FloatBallPosition? =
        when (source) {
            BallSource.NOTIFICATION -> capsuleSettings.notificationBallPosition.position(orientation)
            BallSource.RESULT -> capsuleSettings.resultBallPosition.position(orientation)
        }

    /** 收起圆上的未读数：服务下发，画在圆面右下角。 */
    private var collapsedBadgeState by mutableStateOf<BadgeState?>(null)

    /** 圆面固定图标（通知 / 结果），运行期可替换。 */
    private var fixedIconState by mutableStateOf(fixedIcon)

    /** 锁屏 / 息屏 / 面板软隐藏：整个窗口不可见也不可点（窗口保留）。 */
    private var visuallyHidden = false
    private var lockHidden = false
    private var panelHidden = false

    /**
     * LP 坐标系原点在屏幕上的 y。带 LAYOUT_IN_SCREEN 后应为 0；个别 ROM 仍以
     * 状态栏下沿为原点，保留实测兜底。
     */
    private var lpOriginY = 0

    /** 当前朝向：决定读横屏还是竖屏的落点。快照状态：转屏要重推落点与行宽。 */
    private var landscape by mutableStateOf(false)

    /** 上一次应用过几何的屏宽 / 屏高（px）：同朝向的折叠 / 分屏 / 改显示大小也要重排。 */
    private var displayWidthPx = 0
    private var displayHeightPx = 0

    /**
     * 几何 / 屏尺寸版本：`setOrientation` 自增，驱动 Compose 侧重算行宽（LaunchedEffect
     * 与行宽 remember 的键）。同朝向改了屏宽时 `landscape` 不变，光靠它作键会漏。
     */
    private var layoutEpoch by mutableIntStateOf(0)

    private val screenWidthPx: Int get() = DensityProvider.screenWidthPx
    private val screenHeightPx: Int get() = DensityProvider.screenHeightPx

    /** 胶囊面的存在性：窗口在 = 胶囊在（没有球态了）。 */
    private val isCapsule: Boolean get() = view != null

    /**
     * 画面是否处于「展开 / 正在展开」：服务侧记账与画面不同步时用它自检。
     * 收起圆（相位 0）不算展开。
     */
    val isExpandingOrOpen: Boolean
        get() = view != null &&
            (transition == Transition.EXPANDING || morphPhase > 0.001f)

    /** 收起圆的屏幕矩形（到达动效锚点用）：窗口未挂时返回 null。 */
    internal fun visibleRectOnScreen(): BubbleAnchor? =
        if (view == null) null else frozenAnchor ?: capsuleAnchor()

    // ---- 胶囊自己的锚点 ----

    /**
     * 收起态正圆的屏幕矩形：贴 [sideRight] 侧 + 自己的落点 [posYPx] + 键盘让位 + 贴边缓动。
     *
     * 拖动中由 [dragCenterX]/[dragCenterY] 接管（侧别跟着圆心实时翻转），松手才回到
     * 「贴边 + 落点」的常态。这是胶囊几何的唯一来源：窗口 x/y、形变基准、到达动效锚点
     * 都由它推，不存在第二份坐标。
     */
    private fun capsuleAnchor(): BubbleAnchor {
        val size = rowHeightPx.coerceAtLeast(1)
        if (!dragCenterX.isNaN()) {
            val bounds = circleBounds(size)
            val left = (dragCenterX - size / 2f).roundToInt()
                .coerceIn(bounds.minX.roundToInt(), bounds.maxX.roundToInt())
            // 纵向也按安全区夹：拖动中就不该钻到状态栏底下，否则松手会“跳”回安全区。
            val top = (dragCenterY - size / 2f).roundToInt()
                .coerceIn(bounds.minY.roundToInt(), bounds.maxY.roundToInt())
            // 拖过中线就换边：内容区贴哪一侧当场跟着变，松手不用再“传送”。
            sideRight = dragCenterX >= screenWidthPx / 2f
            return BubbleAnchor(left, top, left + size, top + size)
        }
        val top = displayTopPx(size)
        val travel = edgeOffsetPx.roundToInt()
        val left = if (sideRight) screenWidthPx - size - travel else travel
        return BubbleAnchor(left, top, left + size, top + size)
    }

    /** 收起圆显示的顶边（屏幕绝对 px）：落点 − 键盘抬升，再夹进安全区。 */
    private fun displayTopPx(size: Int): Int {
        val bounds = circleBounds(size)
        val base = baseTopPx()
        return (base - imeLiftPx).roundToInt()
            .coerceIn(bounds.minY.roundToInt(), bounds.maxY.roundToInt())
    }

    /** 落点基准（屏幕绝对 px，未减键盘抬升）：首次用时由 [anchorYRatio] 建立。 */
    private fun baseTopPx(): Float {
        if (posYPx < 0f) posYPx = anchorYRatio * screenHeightPx
        return posYPx
    }

    /** 收起圆可落范围：上下避让系统栏，水平贴物理屏边（与悬浮球同口径的纯几何）。 */
    private fun circleBounds(size: Int): BallBounds =
        BallPlacement.bounds(screenWidthPx, screenHeightPx, size, barSafeArea, lpOriginY)

    // ---- 键盘让位 ----

    /**
     * 键盘起落：只改显示位置（抬升量），**不动基准 [posYPx]**。
     *
     * 胶囊自己让位，不依赖悬浮球：球关掉、只有胶囊时键盘也得把圆抬起来，
     * 否则底部输入栏上的圆会被键盘盖住一半。
     */
    fun updateIme(imeBottomPx: Int) {
        if (imeBottomPx == this.imeBottomPx) return
        this.imeBottomPx = imeBottomPx
        if (circleDragging) return
        animateImeLift(resolveImeLift())
    }

    /** 键盘要把圆抬多高：圆底 + 边距越过键盘顶的部分，封顶在圆顶正好到安全区上界。 */
    private fun resolveImeLift(): Float {
        val size = rowHeightPx.coerceAtLeast(1)
        val marginPx = DensityProvider.dp2px(
            FloatBallDefaults.ImeMarginDp + FloatBallDefaults.ImeExtraLiftDp,
        )
        val base = baseTopPx()
        return BallPlacement.imeLiftAmount(
            base = base.roundToInt(),
            size = size,
            marginPx = marginPx,
            imeBottomPx = imeBottomPx,
            minY = circleBounds(size).minY.roundToInt(),
            screenHeightPx = screenHeightPx,
        ).toFloat()
    }

    /** 抬升缓动：逐帧落窗口；目标差值极小直接落定。 */
    private fun animateImeLift(target: Float) {
        imeAnimator?.cancel()
        imeAnimator = null
        val v = view
        if (v == null || abs(imeLiftPx - target) < 0.5f) {
            imeLiftPx = target
            v?.let { applyCapsuleGeometry(it) }
            return
        }
        val from = imeLiftPx
        imeAnimator = ValueAnimator.ofFloat(from, target).apply {
            duration = IME_LIFT_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (imeAnimator !== anim) return@addUpdateListener
                imeLiftPx = anim.animatedValue as Float
                view?.let { applyCapsuleGeometry(it) }
            }
        }.also { it.start() }
    }

    private fun cancelImeLift() {
        imeAnimator?.cancel()
        imeAnimator = null
    }

    // ---- 收起圆：拖动 / 贴边 / 静置 ----

    /**
     * 收起圆的跟手拖动：逐帧写 LP（与球的拖动同一套坐标）。
     *
     * 不用 View translation：松手要把圆写成新的落点 / 侧别，translation 与 LP 不可能在
     * 同一帧同时生效，必有「先跳回贴边位再回来」的一帧。改这里之前先看球的拖动实现。
     */
    private fun onCircleDragged(v: View) {
        if (!circleDragging) {
            if (abs(dragDx) <= touchSlop && abs(dragDy) <= touchSlop) return
            circleDragging = true
            draggedDx = 0f
            draggedDy = 0f
            cancelIdle()
            visualAnimator?.cancel()
            visualAnimator = null
            cancelSnap()
            cancelSideFade()
            applyPressScale(false)
            val start = capsuleAnchor()
            dragCenterX = start.centerX + dragDx
            dragCenterY = start.centerY + dragDy
        } else {
            dragCenterX += dragDx - draggedDx
            dragCenterY += dragDy - draggedDy
        }
        draggedDx = dragDx
        draggedDy = dragDy
        applyCapsuleGeometry(v)
    }

    /** 松手结算：写回落点（侧别已在拖动中跟手翻转），然后贴边缓动。 */
    private fun commitCircleDrag(v: View?) {
        circleDragging = false
        if (v == null) {
            dragCenterX = Float.NaN
            dragCenterY = Float.NaN
            return
        }
        val size = rowHeightPx.coerceAtLeast(1)
        val released = capsuleAnchor()
        dragCenterX = Float.NaN
        dragCenterY = Float.NaN
        draggedDx = 0f
        draggedDy = 0f
        val bounds = circleBounds(size)
        // 拖动全程走显示坐标（[dragCenterX]/[dragCenterY]），收尾把**显示落点**当基准；
        // 不再把键盘抬升量叠回基准（`+ imeLiftPx` 会让关键盘后圆沉到抬升量之下）。
        // 用户拖动即显式定位，覆盖自动让位：松手不重抬（`resolveImeLift`），否则会弹回。
        posYPx = released.top.toFloat().coerceIn(bounds.minY, bounds.maxY)
        imeLiftPx = 0f
        // 拖动收尾落点（归一）：服务分向落盘（横竖屏各一份，和悬浮球一致）。
        val normX = if (sideRight) {
            ((screenWidthPx - size) / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val normY = if (screenHeightPx > 0) (posYPx / screenHeightPx).coerceIn(0f, 1f) else 0.5f
        onDragFinished?.invoke(normX, normY)
        // 离目标屏缘还差多少（带符号）：贴边缓动从这里回 0。
        edgeOffsetPx = if (sideRight) {
            ((screenWidthPx - size) - released.left).toFloat()
        } else {
            released.left.toFloat()
        }
        applyCapsuleGeometry(v)
        snapToEdge()
        scheduleIdle()
    }

    /** 松手贴边缓动：[edgeOffsetPx] → 0，逐帧落窗口（与球松手贴边同一档时长 / 曲线）。 */
    private fun snapToEdge() {
        val v = view ?: return
        val from = edgeOffsetPx
        if (abs(from) < 0.5f) {
            edgeOffsetPx = 0f
            applyCapsuleGeometry(v)
            return
        }
        cancelSnap()
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionSettle.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (snapAnimator !== anim) return@addUpdateListener
                edgeOffsetPx = from * (1f - (anim.animatedValue as Float))
                view?.let { applyCapsuleGeometry(it) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (snapAnimator !== animation) return
                    snapAnimator = null
                    edgeOffsetPx = 0f
                    view?.let { applyCapsuleGeometry(it) }
                }
            })
        }.also { it.start() }
    }

    private fun cancelSnap() {
        snapAnimator?.cancel()
        snapAnimator = null
    }

    private fun cancelSideFade() {
        sideFadeAnimator?.cancel()
        sideFadeAnimator = null
    }

    /**
     * 换边：淡出 → 落到 [left] 那一侧 → 淡回。
     *
     * alpha 用 [ValueAnimator] 而不是 `view.animate()`：后者是每个 View 唯一的实例，
     * 和按压 / 静置共用时互相 cancel，`withEndAction` 就丢了（换边静默不生效）。
     */
    private fun fadeSwapSide(left: Boolean) {
        val v = view ?: return
        cancelSideFade()
        cancelSnap()
        visualAnimator?.cancel()
        visualAnimator = null
        val fadeMs = motionMillis(MotionFamily.Floating).toLong()
        val from = v.alpha
        sideFadeAnimator = ValueAnimator.ofFloat(from, 0f).apply {
            duration = fadeMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (sideFadeAnimator === anim) v.alpha = anim.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (sideFadeAnimator !== animation) return
                    sideFadeAnimator = null
                    if (view !== v) return
                    // [left] = 贴左缘，与 sideRight 相反。
                    sideRight = !left
                    edgeOffsetPx = 0f
                    applyCapsuleGeometry(v)
                    applyVisual()
                    scheduleIdle()
                }
            })
        }.also { it.start() }
    }

    /**
     * 跟手换边（胶囊自己的侧别）：手在哪侧，圆就搬到哪侧。
     *
     * [left] = 「贴到左缘」——与 `sideRight` **相反**（这里错过一次：触钮在左、圆跑到了右）。
     * 窗口不在屏上时只改侧别（无对象可淡入淡出），下次上屏直接落在新侧；
     * 正在拖动 / 按压 / 形变时返回 false，由服务短重试。
     */
    fun swapSide(left: Boolean): Boolean {
        val targetRight = !left
        val v = view
        if (v == null) {
            sideRight = targetRight
            edgeOffsetPx = 0f
            return true
        }
        if (targetRight == sideRight && abs(edgeOffsetPx) < 0.5f) return true
        if (circleDragging || pressed || transition != Transition.NONE) return false
        fadeSwapSide(left)
        return true
    }

    /** 胶囊自己的静置时长（胶囊设置，与悬浮球无关）：0 = 不变暗。 */
    private fun idleDelayMs(): Long = when (source) {
        BallSource.NOTIFICATION -> capsuleSettings.notificationCapsule.idleDimDelayMs
        BallSource.RESULT -> capsuleSettings.resultCapsule.idleDimDelayMs
    }

    /** 静置计时：到点后变暗 + 半出屏。只在**收起态**生效，展开 / 形变中直接跳过。 */
    private fun scheduleIdle() {
        idleRunnable?.let(mainHandler::removeCallbacks)
        idleRunnable = null
        val delay = idleDelayMs()
        if (delay <= 0L || circleDragging) {
            if (idled) {
                idled = false
                applyVisual()
            }
            return
        }
        val runnable = Runnable {
            idleRunnable = null
            if (!collapsedVisual || visuallyHidden) return@Runnable
            idled = true
            applyVisual()
        }
        idleRunnable = runnable
        mainHandler.postDelayed(runnable, delay)
    }

    private fun cancelIdle() {
        idleRunnable?.let(mainHandler::removeCallbacks)
        idleRunnable = null
        if (idled) {
            idled = false
            applyVisual()
        }
    }

    /**
     * 胶囊的视觉姿态（alpha / 静置外移）：静置只作用于收起圆，展开态始终全亮且不偏移。
     *
     * 与按压一样走 View 层而不走 Compose：服务进程托管的 ComposeView 重组不上屏。
     */
    private fun applyVisual() {
        val v = view ?: return
        if (!v.isAttachedToWindow) return
        val resting = idled && collapsedVisual
        val alpha = when {
            visuallyHidden -> 0f
            resting -> FloatBallDefaults.IdleDimAlpha
            else -> 1f
        }
        val shift = if (resting) {
            BallPlacement.idleShiftPx(!sideRight, rowHeightPx, FloatBallDefaults.IdleOutRatio)
        } else {
            0f
        }
        visualAnimator?.cancel()
        val fromAlpha = v.alpha
        val fromShift = v.translationX
        // 已经到位（例如收起动画刚把残余收回）：不再起一个空跑的 MotionSettle。
        if (abs(fromAlpha - alpha) < 0.001f && abs(fromShift - shift) < 0.5f) {
            visualAnimator = null
            v.alpha = alpha
            v.translationX = shift
            return
        }
        visualAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionSettle.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (visualAnimator !== anim) return@addUpdateListener
                val t = anim.animatedValue as Float
                v.alpha = fromAlpha + (alpha - fromAlpha) * t
                v.translationX = fromShift + (shift - fromShift) * t
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (visualAnimator !== animation) return
                    visualAnimator = null
                    v.alpha = alpha
                    v.translationX = shift
                }
            })
        }.also { it.start() }
    }

    // ---- 窗口生命周期 ----

    /** 运行期替换圆面图标（通知 / 结果 / 最新通知应用图标）。 */
    fun setFixedIcon(icon: Any?) {
        if (fixedIconState === icon || fixedIconState == icon) return
        fixedIconState = icon
    }

    /**
     * 朝向或屏尺寸变了：重推落点与行宽。返回是否真的需要重排。
     *
     * **屏尺寸也要判**：折叠 / 展开、分屏 / 自由窗口改尺寸、改显示大小都不翻朝向，
     * 只按 [landscape] 早退会把旧屏宽算出的行宽与窗口原样留在新屏上——窗口比屏还宽，
     * 贴边侧内容直接被裁掉。
     */
    fun setOrientation(landscape: Boolean): Boolean {
        val width = screenWidthPx
        val height = screenHeightPx
        val orientationChanged = this.landscape != landscape
        val heightChanged = displayHeightPx > 0 && displayHeightPx != height
        if (!orientationChanged && displayHeightPx == height && displayWidthPx == width) return false
        // 落点存的是屏幕绝对 y：先按**上一块屏**的高度折回比例，再按新屏高重建。
        if (posYPx >= 0f && displayHeightPx > 0) {
            anchorYRatio = (posYPx / displayHeightPx).coerceIn(0f, 1f)
        }
        this.landscape = landscape
        displayWidthPx = width
        displayHeightPx = height
        // 同朝向只改了屏宽时保留当前落点（用户没动过它，不该跳回存档位）。
        if (orientationChanged) {
            // 目标朝向已有存档 → 按它落（横竖屏分开记录）；无存档保留上面折现的比例。
            val saved = savedPosition(landscape)
            if (saved != null) {
                sideRight = !BallPlacement.isLeftEdge(saved.x, screenWidthPx, rowHeightPx.coerceAtLeast(1))
                anchorYRatio = saved.y.coerceIn(0f, 1f)
            }
        }
        if (orientationChanged || heightChanged) posYPx = -1f
        refreshBarSafeArea()
        cancelSnap()
        cancelImeLift()
        imeLiftPx = resolveImeLift()
        // 屏宽变了，行宽与贴边位置都要按新屏宽重推；epoch 驱动 Compose 侧重算行宽。
        frozenAnchor = null
        edgeOffsetPx = 0f
        capsuleWidthPx = 0
        layoutEpoch++
        view?.let { applyCapsuleGeometry(it) }
        return true
    }

    /** 锁屏 / 息屏：整个窗口不可见也不可点，解锁恢复。 */
    fun setHidden(hidden: Boolean) {
        lockHidden = hidden
        applyHiddenFlags()
    }

    /** 面板 / 临时隐藏：软隐藏——窗口保留，只置不可见不可点。 */
    fun setPanelHidden(hidden: Boolean) {
        panelHidden = hidden
        applyHiddenFlags()
    }

    /** 收起圆上的未读数：null / 不可见 = 不画。 */
    fun setCollapsedBadge(badge: BadgeState?) {
        collapsedBadgeState = badge
        updateContentDescription()
    }

    private fun updateContentDescription() {
        val badge = collapsedBadgeState
        view?.contentDescription = when {
            badge != null && badge.visible && badgeDescription != null ->
                badgeDescription.invoke(badge.unpinnedCount + badge.pinnedCount)
            else -> idleDescription ?: context.getString(R.string.smart_ball_content_desc)
        }
    }

    private fun applyHiddenFlags() {
        val current = view ?: return
        val hidden = lockHidden || panelHidden
        if (hidden == visuallyHidden) return
        visuallyHidden = hidden
        if (hidden) {
            bodyArbiter.cancel()
            // 在途的拖动 / 贴边 / 静置计时全部收掉：软隐藏期间的迟到任务会把窗口重新按亮。
            circleDragging = false
            dragCenterX = Float.NaN
            dragCenterY = Float.NaN
            cancelIdle()
            cancelSnap()
            cancelSideFade()
            // 待跑的贴边缓动作废：直接把圆归位到屏缘（窗口也一起写，否则 LP 还停在半路）。
            if (abs(edgeOffsetPx) > 0.5f) {
                edgeOffsetPx = 0f
                applyCapsuleGeometry(current)
            }
            cancelImeLift()
            visualAnimator?.cancel()
            visualAnimator = null
            current.animate().cancel()
            current.alpha = 0f
            applyBallTouchFlags(current, wm, hidden = true)
            settleBallWindowAlpha(current, wm)
        } else {
            applyBallTouchFlags(current, wm, hidden = false)
            // 恢复原地亮回；静置姿态由计时重新建立（隐藏前变暗的那份不继承）。
            applyVisual()
            scheduleIdle()
        }
    }

    private fun dp(value: Int): Int = DensityProvider.dp2px(value)

    /** 视觉尺寸夹到 [FloatBallDefaults.MinSizeDp]..[FloatBallDefaults.MaxSizeDp]。 */
    private fun sizePx(settings: FloatBallSettings): Int = DensityProvider.dp2px(
        settings.sizeDp.coerceIn(FloatBallDefaults.MinSizeDp, FloatBallDefaults.MaxSizeDp),
    )

    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var composeView: ComposeView? = null

    /** hide() 的兜底任务：show() 复用时必须撤销，否则会把刚亮起的胶囊重新按下去。 */
    private var hideFallback: Runnable? = null

    /**
     * 展开 / 收起的唯一进行态：同一时刻只允许一条转换在跑。
     *
     * 以前 `entering` / `enterPending` / `collapsing` 三个布尔各自为政，置位散在
     * show / hide / 布局回调 / 兜底任务里，漏一个就停在「半个球态」（不贴边、不静置、
     * 徽标不回来）。现在收敛成这一份状态 + [enterPending]（展开已请求、形变未起手）。
     */
    private enum class Transition { NONE, EXPANDING, COLLAPSING }

    private var transition = Transition.NONE

    /** 展开已请求、形变还没起手：等窗口落到胶囊矩形 + 内容宽收敛（[startMorphIfPending]）。 */
    private var enterPending = false

    /**
     * 转换起手的球锚点（屏幕坐标）：形变期间冻结。
     *
     * 在途的贴边缓动 / 换边淡出会逐帧改写 `posX`，形变基准若实时读它，展开时胶囊会停在
     * 离屏缘十几像素处（那条缝），收起时球会从半路的位置往边缘滑。两端都以「球的最终
     * 位置」为准，只在启动那一刻算一次。
     */
    private var frozenAnchor: BubbleAnchor? = null

    /**
     * 收起形变播完后的服务回调（prune / refresh / 换边记账 / 摘窗）。
     *
     * **保证恰好交付一次**：[deliverCollapse] 是唯一出口——正常收尾、中途被展开打断、
     * hide 幂等重入都要交付。丢了它服务侧会永远以为胶囊还没收完，徽标 / 静置 / 贴边全卡住。
     */
    private var collapseCallback: (() -> Unit)? = null

    /** Compose 是否真正跑过一帧；用于兜底重挂内容（窗口 View 偶发不组合）。 */
    @Volatile
    private var contentReady = false

    private var items by mutableStateOf<List<BallItem>>(emptyList())
    private var currentIndex by mutableIntStateOf(0)
    private var offsetPx by mutableFloatStateOf(0f)

    /** 每行实测宽度（行宽贴内容后行不再铺满窗宽）：命中与逐行描边都要按行宽收口。 */
    private val rowWidthsPx = mutableStateMapOf<String, Int>()

    /** 行手势行为集：由 slot 随设置推送（[CapsuleSlot.updateSettings]）。 */
    private var rowGesturesState by mutableStateOf(CapsuleRowGestures())

    /**
     * 到达提示动效（胶囊本体脉冲 / 边框流光）：画在本窗口自己的 Compose 层，
     * 不新增窗口、不动几何。播放中再次到达合并（不重启）；收起 / 清空即停。
     */
    private var arrivalAlert by mutableStateOf<AlertLayerPlan?>(null)

    /** 到达提示的动效颜色来源（调色板回退的单色；随 [arrivalAlert] 一起下发）。 */
    private var arrivalColorSpec by mutableStateOf(ColorSpec.Unset)

    /**
     * 单行高 = 收起态圆直径 = 形变基准（胶囊自己的 `rowHeightDp` 设置）。
     *
     * 以前这是「锚点球高度」，从一个不存在的球态读，首展开永远是 0：内容判定成空、
     * CapsuleStack 不组合、行宽不收敛，结果就是「一出现只是个圆」。现在行高只由胶囊
     * 自己的设置派生，不依赖任何外部状态。
     */
    private var rowHeightPx by mutableIntStateOf(0)
    private var capsuleWidthPx by mutableIntStateOf(0)

    /** 最近一次按下所在行（点按 / 横滑都路由到具体那一行）。 */
    private var tappedItemId: String? = null

    /** 多胶囊：按下那颗的缩放反馈；手势升级为拖动时立即清掉，滚动不再保持缩小态。 */
    private var pressedRowId by mutableStateOf<String?>(null)

    /** 多胶囊横滑：反向滑动只位移按下的那颗；朝球侧收起时才动整窗。 */
    private var rowDragId by mutableStateOf<String?>(null)
    private var rowDragDx by mutableFloatStateOf(0f)

    /** 滚动条只在拖动 / 惯性期间出现，停下后延时隐藏。 */
    private var scrollThumbVisible by mutableStateOf(false)
    private var scrollThumbHide: Runnable? = null

    /** 同屏可见行数来源：slot 按胶囊各自设置注入（通知 / 结果各读各的）。 */
    var visibleRowsSource: (() -> Int)? = null

    /** 最近一次真正落进窗口高的同屏行数：行数设置变更的判据（来源是服务侧实时值）。 */
    private var appliedVisibleRows = 0

    /** 推送行手势行为集（slot 在 [CapsuleSlot.updateSettings] 里随设置更新）。 */
    fun setRowGestures(next: CapsuleRowGestures) {
        rowGesturesState = next
    }

    private fun effectiveVisibleRows(): Int =
        (visibleRowsSource?.invoke() ?: FloatBallDefaults.CapsuleVisibleRows)
            .coerceIn(FloatBallDefaults.CapsuleVisibleRowsMin, FloatBallDefaults.CapsuleVisibleRowsMax)

    /** 滚动栈几何：窗口高、可滚动距离、按下点 → 行号共用同一份口径。 */
    private val stackMetrics: StackMetrics
        get() = StackMetrics(
            rowHeightPx = rowHeightPx,
            itemCount = items.size,
            visibleRows = effectiveVisibleRows(),
            gapPx = DensityProvider.dp2px(RowGapDp),
        )

    /** 窗口实际高度 = 可见胶囊 + 彼此间距。 */
    private val windowHeightPx: Int
        get() = stackMetrics.viewportHeightPx

    /**
     * 辉光余量（dp）：脉冲 / 流光的描边辉光越出胶囊轮廓的空间，上下各留一份；
     * 贴屏侧不留——「滑入屏缘」的裁剪是天然的正确观感。
     */
    private val glowMarginPx: Int
        get() = DensityProvider.dp2px(GlowMarginDp)

    /**
     * 中侧余量（px）：只有辉光出血（16dp），**不**按横滑橡皮筋留白——行位移在内容区内
     * 就被 `clipToBounds` 裁掉，窗口边缘用不到那 72dp；留它只会让窗口比内容宽一大截、
     * 那片空的透明区还吃触摸（点空白也会当成点胶囊）。**按相位缩放**：[innerMarginPx]
     * 在收起态是 0，否则窗口比圆宽出这一小条。
     *
     * 系统没有可用的「窗口内局部可触摸区」（`TOUCHABLE_INSETS_REGION` 已从公开 API 移除），
     * 所以做法是让窗口本身在收起态等于圆：多出来的那些像素根本不存在。
     */
    private val centerMarginPx: Int
        get() = DensityProvider.dp2px(CenterMarginDp)

    /**
     * 当前相位下真正的中侧余量：0（收起圆窗口 == 圆）→ [centerMarginPx]（展开，只留辉光出血）。
     *
     * 窗口宽度、画面左边、命中口径三处必须读同一份；相位的进度在 [applyCapsuleGeometry]
     * 里冻结成 [drawnInnerMarginPx] 给 Compose 用。
     */
    private fun innerMarginPx(): Int = (centerMarginPx * morphPhase).roundToInt()

    /** 当前相位下的上下辉光余量：同样从 0 长出来（收起态窗口 == 正圆）。 */
    private fun outerGlowPx(): Int = (glowMarginPx * morphPhase).roundToInt()

    /** 长按后当前页展开的动作行；null = 普通内容。 */
    private var menuItemId by mutableStateOf<String?>(null)

    /** 复制成功后短暂显示「已复制」的条目。 */
    private var copiedItemId by mutableStateOf<String?>(null)

    private var originReady = false

    /**
     * 球 ↔ 胶囊统一形变的相位：0 = 球矩形，1 = 内容矩形。
     *
     * 由 View 侧 [morphAnimator] 写，Compose 侧**只在 draw / layer 阶段读**（Canvas 的
     * draw lambda / `graphicsLayer` block）：每帧只失效绘制，180ms 里一次树都不重组。
     * 窗口几何在整个形变期间钉在胶囊矩形，形变全在窗口内画出来——不再用 View
     * 的 scaleX / pivot / translationY 去凑，那会把球面一起横向压扁，看起来是
     * 「球消失、胶囊出现」两个东西。
     */
    private var morphPhase by mutableFloatStateOf(1f)
    private var morphAnimator: ValueAnimator? = null

    /**
     * 最近一次真正写进 LP 的画面尺寸（px）：Compose 只按它画。
     *
     * 相位是「想要多大」，它才是「窗口现在多大」。两者差一帧时以窗口为准——画的内容
     * 因此永远不会比窗口大，也就不会被窗口的直边切出一条硬边。
     */
    private var drawnSurfaceW by mutableFloatStateOf(0f)
    private var drawnSurfaceH by mutableFloatStateOf(0f)

    /** 最近一次写进 LP 的中侧余量（px）：与 [drawnSurfaceW] 同一次提交，画面对齐用。 */
    private var drawnInnerMarginPx by mutableFloatStateOf(0f)

    /** 最近一次写进 LP 的上下辉光余量（px）：同上（收起态是 0，窗口 == 正圆）。 */
    private var drawnOuterGlowPx by mutableFloatStateOf(0f)

    /** 内容宽是否已由 [CapsuleStack] 量完（窗口宽收敛）：没收敛就起形变 = 形变面横向跳一段。 */
    private var widthSettled = false
    private var pressed = false

    private var hitTarget: HitTarget = HitTarget.Body

    private var dragDx = 0f
    private var dragDy = 0f
    private var dragAxis = DragAxis.NONE

    /**
     * 松手结算重入闩：[hideCapsule] 会 `bodyArbiter.cancel()`，而 cancel 在拖拽中会再回调
     * `onDragEnded` —— 收起一开始就被「再结算一次」打断，拖拽残余会被当成 axis NONE 抹掉。
     */
    private var finishingDrag = false

    private var velocityTracker: VelocityTracker? = null
    private var settleAnimator: ValueAnimator? = null


    private val bodyArbiter = FloatBallTouchArbiter(
        onSlideDragged = { dx, dy -> onBodyDragged(dx, dy) },
        onTapped = { handleTap() },
        onPressChanged = { nowPressed ->
            this.pressed = nowPressed
            onPressStateChanged?.invoke(nowPressed)
            // 在 [cancelEnterFeedback] 之前求值：它会把展开态直接抬到相位 1。
            val onCircle = collapsedVisual
            cancelEnterFeedback()
            // 上手先把静置姿态唤醒（变暗 / 半出屏 → 全亮贴边）。
            if (nowPressed) cancelIdle()
            // 只缩按下的那一颗；整窗缩放会让整串一起抖。收起圆是按钮，只有它整窗缩。
            pressedRowId = if (nowPressed && !onCircle) tappedItemId else null
            applyPressScale(nowPressed && onCircle)
            if (nowPressed) {
                dragDx = 0f
                dragDy = 0f
                dragAxis = DragAxis.NONE
                swipeBehavior = CapsuleGestureBehavior.NONE
                swipeTowardEdge = true
                collapseEntrySpeedPx = 0f
                resetRowDrag()
            }
        },
        onDragEnded = { finishBodyDrag() },
        onLongPress = { handleLongPress() },
    )

    /** 正文点按（通知直达 / 结果项主动作）。[String] 是点中的条目 id（路由到具体行）。 */
    var onBodyTapped: ((String) -> Unit)? = null

    /** 收起圆被点：胶囊自己要长开（不再经过悬浮球）。 */
    var onExpandRequested: (() -> Unit)? = null

    /** 收起圆被长按：按胶囊设置的长按行为处理（清空队列 / 无）。 */
    var onCollapsedLongPress: (() -> Unit)? = null

    /** 结果项动作行 / 主动作回调。 */
    var onResultAction: ((String, ResultAction) -> Unit)? = null

    /** 朝屏缘侧横滑：只收起，保留队列项、不标记 dismissed。 */
    var onCollapsed: (() -> Unit)? = null

    /** 反向横滑：移除按下那一行并翻下一条（多条）/ 收起（末条/单条）。参数是条目 id。 */
    var onSwipedAway: ((String) -> Unit)? = null

    /** 按住状态：暂停 / 恢复自动收起。 */
    var onPressStateChanged: ((Boolean) -> Unit)? = null

    /** 拖动收尾落点（归一 x/y，已含贴边侧别）：服务据此分向落盘（横竖屏各一份）。 */
    var onDragFinished: ((Float, Float) -> Unit)? = null

    /** 胶囊首帧真正画出来（行宽已收敛）：服务这时才结算徽标。 */
    var onReady: (() -> Unit)? = null

    /** 组合失败 / 窗口不可用：服务收起胶囊，避免「空白窗 + 徽标挂不上」。 */
    var onUnavailable: (() -> Unit)? = null

    fun updateCapsuleSettings(next: FloatBallSettings) {
        capsuleSettings = next
        if (!positionRestored) {
            positionRestored = true
            // 启动恢复：按当前朝向加载已存落点（横竖屏各一份，和悬浮球一致）。
            savedPosition(landscape)?.let { saved ->
                sideRight = !BallPlacement.isLeftEdge(saved.x, screenWidthPx, rowHeightPx.coerceAtLeast(1))
                anchorYRatio = saved.y.coerceIn(0f, 1f)
                posYPx = -1f
            }
        }
        // 静置时长可能刚被改掉：收起态要按新值重新起算（展开态本来就不静置）。
        if (collapsedVisual) scheduleIdle()
        // 行高是胶囊自己的几何来源，与行数变更是两件事：变高 / 变矮要重推行高与落点夹取。
        val rowHeight = capsuleRowHeightPx()
        val heightChanged = rowHeight != rowHeightPx
        if (heightChanged) {
            rowHeightPx = rowHeight
            // 圆变大后原来的落点可能越界：按新安全区夹一次（贴边 / 圆心仅受屏幕边界约束）。
            clampAnchorY(rowHeight)
        }
        // 同屏行数变了必须立即改窗口高：否则新视口会被旧窗口裁掉 / 留白。
        // 旧守卫比较两次 effectiveVisibleRows()（都读服务侧已更新的值）永远相等，
        // 这里改成记「上次落窗的行数」。
        val rows = effectiveVisibleRows()
        if (rows == appliedVisibleRows) return
        appliedVisibleRows = rows
        offsetPx = stackMetrics.clamp(offsetPx)
        if (isCapsule) view?.let { applyCapsuleGeometry(it) }
    }

    /** 收起态行高（px）：胶囊自己的大小设置（48–72dp，通知 / 结果各存一份）。 */
    private fun capsuleRowHeightPx(): Int =
        DensityProvider.dp2px(
            when (source) {
                BallSource.NOTIFICATION -> capsuleSettings.notificationCapsule.capsuleRowHeightDp
                BallSource.RESULT -> capsuleSettings.resultCapsule.capsuleRowHeightDp
            }.toFloat(),
        ).coerceAtLeast(1)

    /** 行高变化 / 转屏后把纵向落点夹回安全区：圆变大后原落点可能越界。 */
    private fun clampAnchorY(size: Int) {
        if (posYPx <= 0f) return
        val bounds = circleBounds(size)
        posYPx = posYPx.coerceIn(bounds.minY, bounds.maxY)
    }
    /** 复制成功的短暂反馈：显示「已复制」。 */
    fun showCopied(itemId: String) {
        menuItemId = null
        copiedItemId = itemId
    }

    /**
     * 播放一次到达提示动效（仅胶囊本体脉冲 / 边框流光走这条；其他效果由独立 overlay 承载）。
     * 播放中的请求合并丢弃；播完由效果自己清空 [arrivalAlert]，收起 / 摘窗时也会被清。
     */
    fun playArrivalAlert(plan: AlertLayerPlan, colorSpec: ColorSpec) {
        if (arrivalAlert != null) return
        arrivalAlert = plan
        arrivalColorSpec = colorSpec
    }

    /** 胶囊层动效是否在播：服务侧「在播不播」判定的一部分。 */
    fun isAlertPlaying(): Boolean = arrivalAlert != null

    /** 立即停掉在播的到达提示动效（息屏 / 服务销毁）。 */
    fun cancelArrivalAlert() {
        arrivalAlert = null
        arrivalColorSpec = ColorSpec.Unset
    }

    /** 清除「已复制」反馈（链接复制等不自动翻页的场景）。 */
    fun clearCopied() {
        copiedItemId = null
    }

    /** 只刷新列表（不抢当前页、不重置拖动偏移）：新内容在胶囊已展开时入队用。 */
    fun updateItems(items: List<BallItem>, index: Int) {
        if (items.isEmpty()) return
        // 收件箱每次变更都传 `state.copy(items = …)` 的新列表：内容没变就别再通知快照，
        // 否则 Compose 会为一次没变化的更新整树重组（所有行都读同一批状态）。
        if (sameItems(items, this.items)) return
        this.items = items
        pruneRowTransient(items)
        if (menuItemId != null && items.none { it.id == menuItemId }) menuItemId = null
        if (copiedItemId != null && items.none { it.id == copiedItemId }) copiedItemId = null
        if (currentIndex > items.lastIndex) {
            currentIndex = index.coerceIn(0, items.lastIndex)
            offsetPx = 0f
        }
        // 条数变化会改变窗口高，立即重排；滚动位置按新范围收拢。
        offsetPx = stackMetrics.clamp(offsetPx)
        view?.let { applyCapsuleGeometry(it) }
    }

    /**
     * 展开胶囊（必要时建窗）。已展开时只换内容与几何，不重播展开形变。
     *
     * 锚点、行高、侧别全部取自胶囊**自己**（屏缘 + 纵向落点 + 行高常量），不读悬浮球的
     * 位置 / 尺寸 / 静置 / 键盘让位——两边任何一边动，另一边都不该跟着抖。
     */
    fun showCapsule(items: List<BallItem>, index: Int): Boolean {
        if (items.isEmpty()) return false
        val resuming = transition == Transition.COLLAPSING
        val fresh = view == null
        // 「已展开」按**画面**判（相位 1），不按窗口在不在：窗口在、相位 0 的是收起圆，
        // 它必须重新长开（否则就是「点了不展开」）。新建窗不算已展开（相位初值就是 1）。
        val wasOpen = !fresh && !resuming && transition == Transition.NONE && morphPhase >= 1f
        // 收起态直径 = 行高：胶囊自己的设置（不读悬浮球）。
        val rowHeight = capsuleRowHeightPx()
        if (rowHeightPx != rowHeight) rowHeightPx = rowHeight
        // 与 [updateItems] 同一条纪律：内容没变就不通知快照，避免无谓的整树重组。
        if (!sameItems(items, this.items)) this.items = items
        pruneRowTransient(this.items)
        if (!pressed) {
            val settling = settleAnimator
            settleAnimator = null
            settling?.cancel()
            currentIndex = index.coerceIn(0, items.lastIndex)
            offsetPx = 0f
            menuItemId = null
        } else {
            currentIndex = currentIndex.coerceIn(0, items.lastIndex)
        }
        if (menuItemId != null && items.getOrNull(currentIndex)?.id != menuItemId) menuItemId = null
        if (copiedItemId != null && items.none { it.id == copiedItemId }) copiedItemId = null
        // 目标宽：通知行按内容（Compose 侧量出的终值），结果胶囊内容驱动（实测行宽收敛，
        // 未量出前按公式兜底，见 [fixedCapsuleWidth]）。
        val newWidth = if (items.any { it is NotificationItem } && capsuleWidthPx > 0) {
            capsuleWidthPx
        } else {
            fixedCapsuleWidth()
        }
        if (newWidth != capsuleWidthPx) capsuleWidthPx = newWidth

        if (!ensureWindowRoot()) {
            Log.e(TAG, "capsule window unavailable")
            return false
        }
        val existing = view ?: return false
        // 展开 = 贴紧屏缘的全亮姿态：静置外移 / 换边淡出必须先收掉，否则形变基准会落在半路。
        cancelIdle()
        cancelSnap()
        cancelSideFade()
        edgeOffsetPx = 0f
        visualAnimator?.cancel()
        frozenAnchor = capsuleAnchor()
        probe("showCapsule resuming=$resuming wasOpen=$wasOpen")
        if (!contentReady) composeView?.setContent { WindowContent() }
        applyBallTouchFlags(existing, wm, hidden = false)
        hideFallback?.let(mainHandler::removeCallbacks)
        hideFallback = null
        existing.animate().cancel()
        // 从收起中途折返：接续收起把横移 / alpha 交给了 View，展开要从常态起手
        // （不清就会把「正在收」的位移留在展开态上，成了永久偏移）。
        if (resuming) {
            existing.translationX = 0f
            existing.alpha = 1f
        }
        existing.visibility = View.VISIBLE
        existing.alpha = if (visuallyHidden) 0f else 1f
        updateContentDescription()
        when {
            resuming -> {
                // 收起播到一半又被叫回：从当前相位接着展开，不重播（硬归位会跳一帧）。
                transition = Transition.EXPANDING
                enterPending = false
                animateMorphTo(1f) {
                    if (view === existing) {
                        transition = Transition.NONE
                        frozenAnchor = null
                    }
                }
                existing.post { deliverCollapse() }
            }

            wasOpen -> {
                // 已展开的刷新：相位归 1，不重播。
                transition = Transition.NONE
                enterPending = false
                morphPhase = 1f
                frozenAnchor = null
                applyCapsuleGeometry(existing)
            }

            // 展开已经起手（内容追加 / 重入）：只刷内容与几何，不把相位拉回 0 重播。
            !fresh && transition == Transition.EXPANDING -> {
                applyCapsuleGeometry(existing)
            }

            else -> startEntering(existing)
        }
        existing.postDelayed({
            if (view === existing) {
                if (contentReady) onReady?.invoke() else onUnavailable?.invoke()
            }
        }, 600L)
        return true
    }

    /**
     * 起展开：相位先钉在 0（收起圆），窗口落到「圆 + 余量」，等布局与内容宽都就绪再长开。
     *
     * 新建窗时原点未实测，y 还写不进 LP：保持隐形，等布局回调测完原点、形变真正起手
     * （[startMorphIn]）再亮，否则第一帧会画在屏幕顶部闪一下。
     */
    private fun startEntering(v: View) {
        transition = Transition.EXPANDING
        enterPending = true
        morphPhase = 0f
        // 内容已经量过一轮（行宽缓存非空）就不必再等一次收敛门。
        widthSettled = rowWidthsPx.isNotEmpty() && capsuleWidthPx > 0
        applyCapsuleGeometry(v)
        v.alpha = if (originReady && !visuallyHidden) 1f else 0f
        v.post { if (view === v) startMorphIfPending(v) }
        v.postDelayed({ if (view === v) startMorphIfPending(v, force = true) }, EnterFallbackMs)
    }

    /** 建窗（幂等）：一次展开一个窗口实例，队列清空 / 功能关闭时摘掉。 */
    private fun ensureWindowRoot(): Boolean {
        if (view != null) return true
        contentReady = false
        originReady = false
        // 几何要落在一个真实的屏幕范围里：建窗前先量一次安全区（胶囊自己的那份）。
        refreshBarSafeArea()
        val (compose, created) = FloatBallLifecycleOwner.createWindowRoot(context) { WindowContent() }
        created.alpha = 0f
        created.setOnTouchListener { v, event -> handleTouch(v, event) }
        // 布局回调是唯一「几何真的落地」的信号：测原点、补写几何、按需起形变。
        created.addOnLayoutChangeListener { v, _, _, _, _, oldLeft, oldTop, oldRight, oldBottom ->
            if (v.width <= 0 || v.height <= 0) return@addOnLayoutChangeListener
            val oldW = oldRight - oldLeft
            val oldH = oldBottom - oldTop
            if (v.width != oldW || v.height != oldH) {
                Log.d(LOG_TAG, "PROBE layout ${oldW}x${oldH} -> ${v.width}x${v.height} | ${snapshot()}")
            }
            if (!originReady) {
                lpOriginY = screenOriginY(v)
                originReady = true
                applyCapsuleGeometry(v)
            }
            startMorphIfPending(v)
        }
        if (!wm.safeAddView(created, currentLayoutParams())) {
            Log.e(TAG, "capsule addView failed (overlay permission or window rejected)")
            return false
        }
        view = created
        composeView = compose
        transition = Transition.EXPANDING
        enterPending = true
        created.post {
            val v = view
            if (v === created) {
                applyCapsuleGeometry(v)
                startMorphIfPending(v)
            }
        }
        // 安全网：极端时序下（组合失败 / 拿不到布局回调）也保证几何与可见性收敛。
        mainHandler.postDelayed({
            val v = view
            if (v === created) {
                applyCapsuleGeometry(v)
                if (!contentReady) composeView?.setContent { WindowContent() }
                if (transition != Transition.EXPANDING && v.alpha < 0.99f) v.alpha = 1f
                if (isCapsule) {
                    if (contentReady) {
                        onReady?.invoke()
                    } else {
                        mainHandler.postDelayed({
                            if (view === created) {
                                if (contentReady) onReady?.invoke() else onUnavailable?.invoke()
                            }
                        }, 500L)
                    }
                }
                startMorphIfPending(v, force = true)
            }
        }, 420L)
        return true
    }

    /** 窗口 LP：按当前相位与胶囊自己的锚点算（尺寸含余量）。 */
    private fun currentLayoutParams(): WindowManager.LayoutParams {
        val rowHeight = rowHeightPx.coerceAtLeast(capsuleRowHeightPx())
        // 建窗时照当前相位算：相位 0 时窗口就是圆本身，没有任何透明余量。
        return capsuleLayoutParams(
            rowHeight + innerMarginPx(),
            rowHeight + outerGlowPx() * 2,
        )
    }

    /**
     * 收起胶囊：相位 1 → 0，收尾停在收起圆上（不换窗、不写球方窗）。
     *
     * [onHidden] 是「胶囊已经不在屏上」的记账回调，**保证恰好交付一次**（[deliverCollapse]）：
     * 正常收尾、中途被展开打断、hide 幂等重入都算。
     */
    fun hideCapsule(onHidden: (() -> Unit)? = null) {
        if (onHidden != null) collapseCallback = onHidden
        // 横滑收起的接续速度/位移只服务「这一次」收起：取一次即清（自动收起不继承）。
        val entrySpeed = collapseEntrySpeedPx
        collapseEntrySpeedPx = 0f
        bodyArbiter.cancel()
        hitTarget = HitTarget.Body
        val v = view
        if (v == null) {
            transition = Transition.NONE
            enterPending = false
            deliverCollapse()
            return
        }
        // 收起中再次收起：只登记回调，不叠加第二条动画 / 兜底。
        if (transition == Transition.COLLAPSING) return
        // 已经是收起圆（相位 0）：没有可播的形变，直接结账。这条路径是账号同步
        // （service 侧 showing 与画面不一致）的兜底，不能把圆停在「自以为在收」。
        if (transition == Transition.NONE && morphPhase <= 0.001f) {
            // 相位 0 不会有拖拽残余（收起圆的拖动走 LP，不动 View）：保留静置位移，
            // 交给 finishCollapse → applyVisual 按静置姿态收拾。
            finishCollapse(v)
            return
        }
        cancelIdle()
        cancelSnap()
        cancelSideFade()
        // cancelIdle 会顺手起一条 applyVisual（把静置位移收回 0）：收起要自己接这一棒，
        // 否则两条动画抢同一个 translationX（一条收静置、一条收横滑残余）。
        visualAnimator?.let { visualAnimator = null; it.cancel() }
        // 收起从贴边的圆起手：松手贴边还没跑完就先归位，否则形变基准会落在屏中间。
        edgeOffsetPx = 0f
        frozenAnchor = frozenAnchor ?: capsuleAnchor()
        transition = Transition.COLLAPSING
        enterPending = false
        v.animate().cancel()
        // 接住横滑残余：位移 / alpha / 松手速度一起交给**同一条**动画。
        // 以前残余没人接手，收完又被 applyVisual 抹一遍，看着就是「整块淡出后又缩一次」。
        val carryX = v.translationX
        val carryAlpha = v.alpha
        val duration = collapseDurationMs(entrySpeed)
        probe("hideCapsule carryX=$carryX alpha=$carryAlpha speed=$entrySpeed ms=$duration")
        animateMorphTo(
            target = 0f,
            durationMs = duration,
            carryX = carryX,
            carryAlphaFrom = carryAlpha,
        ) { finishCollapse(v) }
        val fallback = Runnable { finishCollapse(v) }
        hideFallback = fallback
        mainHandler.postDelayed(fallback, duration + 120L)
    }

    /**
     * 收起时长：按松手速度换算（快甩快收 / 慢拖慢收），夹在家族基长的 0.6–1.4×。
     * 无速度（自动收起 / 拖得极慢）就用 [morphAnimMs] 基准。
     */
    private fun collapseDurationMs(speedPxPerSec: Float): Long {
        val base = morphAnimMs
        if (speedPxPerSec < 1f) return base
        val raw = base * (FlingVelocityPxPerSec / speedPxPerSec)
        return raw.toLong().coerceIn(base * 3 / 5, base * 7 / 5)
    }

    /** 收起收尾：停在收起圆上（相位 0 的几何就是圆 + 余量），交付回调。 */
    private fun finishCollapse(v: View) {
        if (transition != Transition.COLLAPSING) {
            deliverCollapse()
            return
        }
        transition = Transition.NONE
        hideFallback?.let(mainHandler::removeCallbacks)
        hideFallback = null
        frozenAnchor = null
        if (view === v) {
            morphPhase = 0f
            applyCapsuleGeometry(v)
            menuItemId = null
            offsetPx = 0f
            resetRowDrag()
            if (lockHidden || panelHidden) {
                v.alpha = 0f
                applyBallTouchFlags(v, wm, hidden = true)
            } else {
                applyBallTouchFlags(v, wm, hidden = false)
                // 收起圆的常态：贴紧屏缘的全亮圆，静置计时重新起算。
                applyVisual()
                scheduleIdle()
            }
            updateContentDescription()
        }
        deliverCollapse()
    }

    /** 交付「胶囊已不在屏上」的记账回调（全局只交付一次）。 */
    private fun deliverCollapse() {
        val callback = collapseCallback ?: return
        collapseCallback = null
        callback.invoke()
    }

    fun detachNow() {
        Log.d(LOG_TAG, "PROBE detachNow | ${snapshot()}")
        bodyArbiter.cancel()
        transition = Transition.NONE
        hideFallback?.let(mainHandler::removeCallbacks)
        hideFallback = null
        // 摘窗不再回放服务侧的收起记账（prune / refresh / 换边）：窗口都拆了，那些都由服务侧
        // 的显隐流程接手，迟到的回调只会在拆窗后反手再拉一次窗口。
        collapseCallback = null
        val v = view
        view = null
        resetTransientState()
        v?.let {
            it.animate().cancel()
            context.removeWindow(it)
        }
        // 摘窗后不再保留 ComposeView / lifecycle owner：常驻复用只在“收起”期间。
        composeView = null
        contentReady = false
        // 显隐位与原点属于「窗口实例」：重建后由服务重新下发 setHidden/setPanelHidden，
        // 原点等首次布局回调重测。留着旧值会让重建窗停在“自以为已隐藏”的状态。
        lockHidden = false
        panelHidden = false
        visuallyHidden = false
        originReady = false
    }

    /**
     * 收起 / 摘窗前把瞬态状态一次性归零。
     *
     * [hideCapsule] 与 [detachNow] 共用这一份：两条路径以前各写一遍，增删一条就会各走各的。
     */
    private fun resetTransientState() {
        morphAnimator?.let { morphAnimator = null; it.cancel() }
        morphPhase = 1f
        widthSettled = false
        transition = Transition.NONE
        enterPending = false
        frozenAnchor = null
        pressed = false
        arrivalAlert = null
        items = emptyList()
        tappedItemId = null
        pressedRowId = null
        resetRowDrag()
        rowWidthsPx.clear()
        scrollThumbVisible = false
        scrollThumbHide = null
        menuItemId = null
        copiedItemId = null
        offsetPx = 0f
        dragDx = 0f
        dragDy = 0f
        dragAxis = DragAxis.NONE
        circleDragging = false
        dragCenterX = Float.NaN
        dragCenterY = Float.NaN
        draggedDx = 0f
        draggedDy = 0f
        swipeBehavior = CapsuleGestureBehavior.NONE
        swipeTowardEdge = true
        collapseEntrySpeedPx = 0f
        // 拖动 / 贴边 / 静置 / 键盘的动画与计时全部收掉；**落点与侧别不归零**（位置要跨摘窗保留）。
        cancelSnap()
        cancelSideFade()
        cancelImeLift()
        cancelIdle()
        visualAnimator?.cancel()
        visualAnimator = null
        pressAnimator?.let { pressAnimator = null; it.cancel() }
        settleAnimator?.cancel()
        settleAnimator = null
        velocityTracker?.recycle()
        velocityTracker = null
        mainHandler.removeCallbacksAndMessages(null)
        hideFallback?.let(mainHandler::removeCallbacks)
        hideFallback = null
    }

    /**
     * 转屏 / 屏宽变化 / 系统栏变化后重推几何：由胶囊自己的锚点算（不读悬浮球的位置）。
     *
     * 侧别（[sideRight]）不在这里推导 —— 它是用户拖出来的状态，从锚点反推会在
     * 贴边缓动途中把自己翻回去。
     */
    fun updateCapsuleAnchor() {
        if (!isCapsule) return
        // 现推锚点接管：冻结值只服务一次形变。
        frozenAnchor = null
        refreshBarSafeArea()
        val anchor = capsuleAnchor()
        val newRowHeight = capsuleRowHeightPx()
        if (newRowHeight != rowHeightPx) rowHeightPx = newRowHeight
        // 有通知行时保留内容驱动宽（LaunchedEffect 收敛），键盘让位/转屏不撑宽；
        // 结果胶囊（无通知行）按实测行宽收敛，未量出前按公式兜底。
        if (items.none { it is NotificationItem } || capsuleWidthPx <= 0) {
            capsuleWidthPx = fixedCapsuleWidth()
        }
        view?.let { applyCapsuleGeometry(it) }
    }

    /** 安全区复测：display 绝对坐标（与键盘让位同源）；拿不到就沿用上一次的值。 */
    private fun refreshBarSafeArea() {
        barSafeArea = wm.systemBarsSafeAreaOrNull() ?: return
    }

    // ---- 多胶囊行状态 ----

    private fun resetRowDrag() {
        rowDragId = null
        rowDragDx = 0f
    }

    /** 行回弹动画还在跑就取消（列表重置 / 新手势接管前）。 */
    private fun cancelRowRebound() {
        rowReboundAnimator?.let { rowReboundAnimator = null; it.cancel() }
    }

    /** 列表变化后清掉已不存在的按压 / 横滑行与行宽记录，避免状态指向幽灵条目。 */
    private fun pruneRowTransient(items: List<BallItem>) {
        if (pressedRowId != null && items.none { it.id == pressedRowId }) pressedRowId = null
        if (rowDragId != null && items.none { it.id == rowDragId }) resetRowDrag()
        val ids = items.mapTo(mutableSetOf()) { it.id }
        rowWidthsPx.keys.retainAll(ids)
    }

    /** 滚动条：拖动 / 惯性期间显示，最后一次滚动后 [ScrollThumbHideMs] 隐藏。 */
    private fun showScrollThumb() {
        if (!scrollThumbVisible) scrollThumbVisible = true
        scrollThumbHide?.let(mainHandler::removeCallbacks)
        val hide = Runnable {
            scrollThumbVisible = false
            scrollThumbHide = null
        }
        scrollThumbHide = hide
        mainHandler.postDelayed(hide, ScrollThumbHideMs)
    }

    /** 行实测宽度记录（Compose onSizeChanged → 命中 / 描边按行宽收口）。 */
    private fun onRowMeasured(id: String, widthPx: Int) {
        if (widthPx > 0) rowWidthsPx[id] = widthPx
    }

    // ---- 手势 ----

    /**
     * 当前画的是收起圆（没有展开完的内容）。
     *
     * 收起圆的命中区只有圆本身，此时点按 / 长按属于「按钮」语义（长开 / 清空），
     * **不能**落到不可见的行上——否则点一下收起圆就直接打开了那条通知。
     */
    private val collapsedVisual: Boolean
        get() = transition == Transition.NONE && morphPhase <= 0.001f

    private fun handleTouch(v: View, event: MotionEvent): Boolean {
        // 展开/收起由窗口自己播形变：收起 = 可点的圆，展开 = 行命中/滚动/横滑。
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            settleAnimator?.cancel()
            settleAnimator = null
            hitTarget = resolveHitTarget(v, event.x, event.y)
            // 点按 / 横滑都路由到按下所在的那一行（收起态没有可见行）。
            tappedItemId = if (collapsedVisual) null else rowItem(event.y)?.id
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
        } else {
            velocityTracker?.addMovement(event)
        }
        // 所有触摸都交给同一个 arbiter：从动作 chip / 动作行开始竖向拖也能滚动。
        val handled = bodyArbiter.onTouch(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            recycleVelocity()
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) hitTarget = HitTarget.Body
        }
        return handled
    }

    private fun currentTapId(): String? = tappedItemId ?: items.getOrNull(currentIndex)?.id

    private fun recycleVelocity() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun handleTap() {
        // 收起圆 = 自己的按钮：点一下长开成胶囊。
        if (collapsedVisual) {
            hitTarget = HitTarget.Body
            onExpandRequested?.invoke()
            return
        }
        when (val target = hitTarget) {
            is HitTarget.Menu -> {
                hitTarget = HitTarget.Body
                val item = items.firstOrNull { it.id == menuItemId }
                if (item is ResultItem) {
                    val actions = menuActions(item)
                    val action = actions.getOrNull(target.index) ?: return
                    if (action == ResultAction.CLOSE) {
                        menuItemId = null
                    } else {
                        menuItemId = null
                        onResultAction?.invoke(item.id, action)
                    }
                }
            }

            HitTarget.Action -> {
                hitTarget = HitTarget.Body
                val item = items.firstOrNull { it.id == tappedItemId }
                if (item is ResultItem) {
                    onResultAction?.invoke(item.id, item.primaryAction())
                } else {
                    val id = currentTapId() ?: return
                    onBodyTapped?.invoke(id)
                }
            }

            HitTarget.Body -> {
                if (menuItemId != null) {
                    menuItemId = null
                    return
                }
                hitTarget = HitTarget.Body
                val id = currentTapId() ?: return
                onBodyTapped?.invoke(id)
            }

            HitTarget.None -> {
                hitTarget = HitTarget.Body
            }
        }
    }

    /**
     * 行长按行为（[CapsuleGestureBehavior]）：结果项展开操作菜单 / 忽略该行 / 无。
     * 通知行默认无行为；菜单只在结果项上有意义。
     */
    private fun handleLongPress() {
        // 与主球同档的触觉反馈：长按是动作，不只是一种手势。
        view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        // 收起圆的长按属于胶囊设置（清空队列 / 无），不是行手势。
        if (collapsedVisual) {
            onCollapsedLongPress?.invoke()
            return
        }
        val id = tappedItemId ?: items.getOrNull(currentIndex)?.id ?: return
        val item = items.firstOrNull { it.id == id } ?: return
        when (rowGesturesState.longPress) {
            CapsuleGestureBehavior.MENU -> if (item is ResultItem) {
                menuItemId = item.id
                copiedItemId = null
            }

            CapsuleGestureBehavior.IGNORE_ROW -> {
                menuItemId = null
                onSwipedAway?.invoke(item.id)
            }

            else -> Unit
        }
    }

    /** 按下点所在行（滚动偏移换算到行号）。 */
    private fun rowItem(y: Float): BallItem? =
        items.getOrNull(stackMetrics.rowAt(y, offsetPx))

    /** 按下点相对行的横向范围：行宽贴内容后行不再铺满窗宽，靠球一侧齐平。 */
    private fun rowXRange(v: View, rowWidth: Int): IntRange {
        val left = if (sideRight) (v.width - rowWidth).coerceAtLeast(0) else 0
        return left..(left + rowWidth)
    }

    /**
     * 按下点分类：菜单行 > 动作区 > 行内正文 > 行外空白（不命中）。
     * 命中按每行实测宽度（[rowWidthsPx]）收口：行外空白点按不触发动作，
     * 拖动（滚动 / 横滑）仍整窗有效。
     */
    private fun resolveHitTarget(v: View, x: Float, y: Float): HitTarget {
        // 收起态：行都不可见，整圆都是「长开」按钮。
        if (collapsedVisual) return HitTarget.Body
        val row = rowItem(y) ?: return HitTarget.None
        // 行宽用实测值；未收敛（首帧）时回退到「真正画出的画面宽」，而不是 capsuleWidthPx——
        // 后者在结果胶囊是固定公式、可能比实际行宽，会把内容框里的空区算成命中区。
        val rowWidth = (rowWidthsPx[row.id] ?: drawnSurfaceW.roundToInt()).coerceAtLeast(1)
        val range = rowXRange(v, rowWidth)
        if (x < range.first || x > range.last) return HitTarget.None
        if (row is ResultItem && menuItemId == row.id) {
            val count = menuActions(row).size.coerceAtLeast(1)
            val index = (((x - range.first) / rowWidth) * count).toInt()
            return HitTarget.Menu(index.coerceIn(0, count - 1))
        }
        if (row is ResultItem && x >= range.last - DensityProvider.dp2px(ActionHitDp)) {
            return HitTarget.Action
        }
        return HitTarget.Body
    }

    private fun onBodyDragged(dx: Float, dy: Float) {
        dragDx += dx
        dragDy += dy
        val v = view ?: return
        // 收起圆 = 可拖的按钮：跟手挪位置，松手才贴边（不滚动、不横滑收起）。
        if (collapsedVisual) {
            onCircleDragged(v)
            return
        }
        if (dragAxis == DragAxis.NONE &&
            (abs(dragDx) > touchSlop || abs(dragDy) > touchSlop)
        ) {
            // 竖向 = 滚动，横向 = 收起 / 滑掉；先跨 slop 的方向定轴。
            dragAxis = if (abs(dragDx) > abs(dragDy)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
            // 一旦进入拖动就撤掉按压形变：滚动 / 横滑不该保持缩小的样子。
            pressedRowId = null
            applyPressScale(false)
            if (dragAxis == DragAxis.HORIZONTAL) {
                resetRowDrag()
                // 锁轴即定行为：视觉只听行为，不听方向（方向翻转时在 onStackRowDragged 里重解析）。
                swipeTowardEdge = towardEdge(dragDx)
                swipeBehavior = resolveSwipeBehavior(swipeTowardEdge)
            }
            if (dragAxis == DragAxis.VERTICAL) showScrollThumb()
        }
        when (dragAxis) {
            DragAxis.VERTICAL -> {
                // 滚动栈：内容跟随手指，两端直接停住，不露出空白槽位。
                offsetPx = stackMetrics.clamp(offsetPx + dy)
                showScrollThumb()
            }

            DragAxis.HORIZONTAL -> onStackRowDragged(v)

            DragAxis.NONE -> Unit
        }
    }

    /** 横滑方向：手指是否朝屏缘那一侧。 */
    private fun towardEdge(dx: Float): Boolean = if (sideRight) dx >= 0f else dx <= 0f

    /** 方向 → 「向缘 / 背离」两个槽的行为（设置在槽内互选，已归一化）。 */
    private fun resolveSwipeBehavior(toward: Boolean): CapsuleGestureBehavior =
        if (toward) rowGesturesState.swipeToward else rowGesturesState.swipeAway

    /**
     * 多胶囊横滑：窗口是否跟手**按行为 + 方向**定，行的跟手不区分。
     *
     * - `COLLAPSE` 且朝屏缘：整窗位移 + 淡出（收起 = 缩回屏缘的圆，与手指从哪边来无关）。
     * - `COLLAPSE` 但背离屏缘 / `IGNORE_ROW` / `NONE`：窗口原地不动，只动按下的那一颗
     *   （1:1 跟手 + 橡皮筋）；背离方向的 `COLLAPSE` 松手过阈值仍播同一套向缘收起。
     *
     * 以前这里只看 [towardEdge]：把朝缘改成「忽略该行」后执行对了、动画还是整块淡出。
     */
    private fun onStackRowDragged(v: View) {
        val toward = towardEdge(dragDx)
        if (toward != swipeTowardEdge) {
            swipeTowardEdge = toward
            swipeBehavior = resolveSwipeBehavior(toward)
        }
        val followsWindow = swipeBehavior == CapsuleGestureBehavior.COLLAPSE && toward
        // 窗口只在「收起 + 朝缘」时跟手；其余组合先拉回常态（方向翻转时不能停在半路）。
        if (!followsWindow) resetDragVisual(v)
        if (followsWindow) {
            // 窗口跟手时行不位移：收起不带行。
            if (rowDragId != null) resetRowDrag()
            v.translationX = dragDx * CollapseFollowRatio
            val fade = (abs(dragDx) / collapseThresholdPx).coerceIn(0f, 1f)
            v.alpha = 1f - fade * 0.25f
        } else {
            // 窗口不跟手：按下的那一颗跟手（忽略该行 / 无行为 / 背离方向的收起）。
            rowDragId = tappedItemId
            rowDragDx = dragDx
        }
    }

    private fun finishBodyDrag() {
        // hideCapsule → bodyArbiter.cancel() → onDragEnded 的重入：本次结算已在进行，忽略。
        if (finishingDrag) return
        finishingDrag = true
        try {
            settleBodyDrag()
        } finally {
            finishingDrag = false
        }
    }

    /** 松手结算主体（[finishBodyDrag] 的重入闩包一层）。 */
    private fun settleBodyDrag() {
        val v = view
        // 收起圆拖动松手：落点 / 侧别结算 + 贴边缓动（不走行的滚动 / 横滑语义）。
        if (circleDragging) {
            dragDx = 0f
            dragDy = 0f
            dragAxis = DragAxis.NONE
            resetRowDrag()
            commitCircleDrag(v)
            return
        }
        val dx = dragDx
        val axis = dragAxis
        val vy = flingVelocityY()
        val vx = if (axis == DragAxis.HORIZONTAL) flingVelocityX() else 0f
        // 松手前记住被横滑的那颗：resetRowDrag 之后还要用它决定删哪一条。
        val swipedId = rowDragId ?: currentTapId()
        dragDx = 0f
        dragDy = 0f
        dragAxis = DragAxis.NONE
        when (axis) {
            DragAxis.VERTICAL -> {
                resetRowDrag()
                resetDragVisual(v)
                menuItemId = null
                settleScroll(vy)
            }

            DragAxis.HORIZONTAL -> {
                menuItemId = null
                val towardEdge = if (sideRight) dx >= collapseThresholdPx else dx <= -collapseThresholdPx
                val awayFromEdge = if (sideRight) dx <= -collapseThresholdPx else dx >= collapseThresholdPx
                val behavior = when {
                    towardEdge -> rowGesturesState.swipeToward
                    awayFromEdge -> rowGesturesState.swipeAway
                    else -> CapsuleGestureBehavior.NONE
                }
                when (behavior) {
                    // 收起从当前拖拽姿态接着播：位移 / alpha / 松手速度一起交给同一条动画。
                    // 先写回 alpha=1 / translationX=0 会「亮一下、跳回原位」再收。
                    CapsuleGestureBehavior.COLLAPSE -> {
                        collapseEntrySpeedPx = abs(vx)
                        // 行若跟过手（背离方向的收起）：与收起同时缓回；窗口跟手时这里没有行位移。
                        reboundRowDrag()
                        onCollapsed?.invoke()
                        // 服务侧记账已认为收起时窗口不会接这一棒：残余就地收回常态。
                        if (transition != Transition.COLLAPSING) {
                            collapseEntrySpeedPx = 0f
                            resetDragVisual(v)
                        }
                    }

                    CapsuleGestureBehavior.IGNORE_ROW -> {
                        resetRowDrag()
                        resetDragVisual(v)
                        swipedId?.let { onSwipedAway?.invoke(it) }
                    }

                    // 无行为 / 未过阈值：窗口与行都缓回原位（不瞬移）。
                    else -> {
                        reboundWindowDrag()
                        reboundRowDrag()
                    }
                }
            }

            DragAxis.NONE -> {
                resetRowDrag()
                resetDragVisual(v)
            }
        }
    }

    /** 横滑跟手的整窗位移 / 淡出硬回弹（收起 / 不该跟手的分支用）。 */
    private fun resetDragVisual(v: View?) {
        if (v == null) return
        if (v.alpha != 1f) v.alpha = 1f
        if (v.translationX != 0f) v.translationX = 0f
    }

    /**
     * 窗口横滑残余的缓回（未过阈值）：[MotionSettle] 把位移 / alpha 收回常态。
     *
     * 与行回弹分开：行位移画在 Compose 的 `graphicsLayer` 上（读 [rowDragId] /
     * [rowDragDx]），窗口位移在 View 层。
     */
    private fun reboundWindowDrag() {
        val v = view ?: return
        val fromX = v.translationX
        val fromAlpha = v.alpha
        if (abs(fromX) < 0.5f && abs(fromAlpha - 1f) < 0.001f) return
        visualAnimator?.cancel()
        visualAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionSettle.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (visualAnimator !== anim) return@addUpdateListener
                val t = anim.animatedValue as Float
                v.translationX = fromX * (1f - t)
                v.alpha = fromAlpha + (1f - fromAlpha) * t
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (visualAnimator !== animation) return
                    visualAnimator = null
                    v.translationX = 0f
                    v.alpha = 1f
                }
            })
        }.also { it.start() }
    }

    /**
     * 行回弹：位移从当前值缓回 0（[MotionSettle]），不瞬移。
     *
     * 与窗口的 [resetDragVisual] 分开：行的位移画在 Compose 的 `graphicsLayer` 上
     * （读 [rowDragId] / [rowDragDx]），窗口位移在 View 层。
     */
    private fun reboundRowDrag() {
        val id = rowDragId ?: return
        val from = rowDragDx
        if (abs(from) < 1f) {
            resetRowDrag()
            return
        }
        rowReboundAnimator?.cancel()
        rowReboundAnimator = ValueAnimator.ofFloat(from, 0f).apply {
            duration = MotionSettle.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (rowReboundAnimator !== anim) return@addUpdateListener
                if (rowDragId != id) return@addUpdateListener
                rowDragDx = anim.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (rowReboundAnimator !== animation) return
                    rowReboundAnimator = null
                    if (rowDragId == id) {
                        rowDragId = null
                        rowDragDx = 0f
                    }
                }
            })
        }.also { it.start() }
    }

    /** 纵向惯性速度（px/s）：滚动松手时取一次，喂给 [settleScroll]。 */
    private fun flingVelocityY(): Float {
        val tracker = velocityTracker ?: return 0f
        tracker.computeCurrentVelocity(1000)
        return tracker.yVelocity
    }

    /** 横向惯性速度（px/s）：行横滑松手接续用。 */
    private fun flingVelocityX(): Float {
        val tracker = velocityTracker ?: return 0f
        tracker.computeCurrentVelocity(1000)
        return tracker.xVelocity
    }

    private fun settleScroll(vy: Float) {
        val metrics = stackMetrics
        if (metrics.maxScrollPx <= 0) {
            offsetPx = 0f
            return
        }
        val projected = if (abs(vy) > 1200f) offsetPx + 0.12f * vy else offsetPx
        val target = metrics.clamp(projected)
        if (target == offsetPx) return
        animateScrollTo(target)
    }

    private fun animateScrollTo(target: Float) {
        settleAnimator?.cancel()
        showScrollThumb()
        val from = offsetPx
        val animator = ValueAnimator.ofFloat(from, target).apply {
            duration = motionMillis(MotionFamily.FloatingSnap).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (settleAnimator !== anim) return@addUpdateListener
                offsetPx = anim.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (settleAnimator !== animation) return
                    settleAnimator = null
                    offsetPx = target
                }
            })
        }
        settleAnimator = animator
        animator.start()
    }

    /** 滚动条拇指的纵向位置：在 offset lambda 里调用，读偏移只触发 layout 不触发重组。 */
    private fun scrollThumbTopPx(metrics: StackMetrics, thumbHeightPx: Int): Float {
        val track = (metrics.viewportHeightPx - thumbHeightPx).coerceAtLeast(0)
        if (track <= 0 || metrics.maxScrollPx <= 0) return 0f
        val progress = (-offsetPx / metrics.maxScrollPx).coerceIn(0f, 1f)
        return progress * track
    }

    private val touchSlop: Float
        get() = DensityProvider.dp2px(12f).toFloat()

    private val collapseThresholdPx: Float
        get() = DensityProvider.dp2px(CollapseThresholdDp).toFloat()

    /**
     * 展开 / 收起形变时长：同一家族、同长同曲线（ui-guidelines §5.1）。
     * 气泡 morph 走 [MotionFamily.FloatingMorph]（标准档 180ms）。
     */
    private val morphAnimMs: Long
        get() = motionMillis(MotionFamily.FloatingMorph).toLong()

    // ---- 定位 ----

    /** 画面（内容）宽的硬上限：窗口再叠加中侧余量也不越屏（见 [CapsuleWidths]）。 */
    private val maxSurfaceWidthPx: Int
        get() = CapsuleWidths.maxSurfaceWidthPx(
            screenWidthPx = screenWidthPx,
            centerMarginPx = centerMarginPx,
            edgeInsetPx = CapsuleWidths.edgeInsetPx(DensityProvider.density),
        )

    private fun fixedCapsuleWidth(): Int {
        // 结果胶囊按实测行宽收敛：行已量出时窗口宽 = 最长行宽（贴内容），不再用固定公式
        // 把窗口撑到 48% 屏宽、留出一片空的、可触摸的窗口区（点空白也触发胶囊）。
        // 兜底下限还是球宽，上限是「窗口不越屏」的余量口径（[CapsuleWidths]）。
        val contentDriven = rowWidthsPx.values.maxOrNull()
        if (contentDriven != null) {
            return contentDriven.coerceIn(rowHeightPx, maxSurfaceWidthPx)
        }
        val cap = CapsuleWidths.surfaceCapPx(
            screenWidthPx = screenWidthPx,
            centerMarginPx = centerMarginPx,
            edgeInsetPx = CapsuleWidths.edgeInsetPx(DensityProvider.density),
            absoluteCapPx = DensityProvider.dp2px(MaxWidthDp),
            widthRatio = WidthRatio,
        )
        return max(rowHeightPx, cap).coerceAtMost(maxSurfaceWidthPx)
    }

    /**
     * 当前相位下的画面宽度（px）：0 = 收起圆，1 = 内容。
     *
     * 行宽还没重算（转屏 / 改尺寸刚把 [capsuleWidthPx] 清零）时按收起圆算，窗口立刻
     * 缩回球大小，而不是把旧屏的宽值原样留在新屏上。
     */
    private fun phaseSurfaceW(): Float {
        val content = if (capsuleWidthPx > 0) capsuleWidthPx else rowHeightPx
        return (rowHeightPx + (content - rowHeightPx) * morphPhase).coerceAtLeast(1f)
    }

    /** 当前相位下的画面高度（px）：收起圆 = 行高，多行随行数长高。 */
    private fun phaseSurfaceH(): Float =
        (rowHeightPx + (windowHeightPx - rowHeightPx) * morphPhase).coerceAtLeast(1f)

    /**
     * 胶囊窗几何：**画面尺寸由相位推，窗口 = 画面 + 余量**，宽高与 xy 一次写完。
     *
     * 展开 / 收起由窗口自己承担（不再是「窗口钉在胶囊矩形、形变在窗内画」）：画面矩形与
     * 窗口内容区是同一个矩形，所以几何与绘制不可能错位。Compose 只按 [drawnSurfaceW] /
     * [drawnSurfaceH]（最近一次真正写进 LP 的值）画，永远不超过窗口——反过来窗口提交慢
     * 一帧也只是整条动画慢一帧，不会出现「形变面按目标几何算、却画在旧窗里」的第一帧偏移。
     */
    private fun applyCapsuleGeometry(v: View) {
        // 窗口未挂：几何无从落窗。
        if (!isCapsule) return
        val targetH = windowHeightPx
        // 首帧前 rowHeight 还没测出来：别把窗口写成 0 高。
        if (targetH <= 0) return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val screenW = screenWidthPx.coerceAtLeast(1)
        val desiredSurfaceW = phaseSurfaceW()
        val surfaceH = phaseSurfaceH()
        val desiredWindowW = desiredSurfaceW.roundToInt() + innerMarginPx()
        // 末端硬夹：任何来源（旧屏宽遗留 / 相位中间值 / ROM 上报偏差）都不让窗口越出屏幕。
        // 被夹时同步收窄画面宽，保证 Compose 画的永远不超过窗口（[drawnSurfaceW] 口径）。
        val windowWidth = CapsuleWidths.windowWidthPx(
            surfaceWidthPx = desiredSurfaceW.roundToInt(),
            innerMarginPx = innerMarginPx(),
            screenWidthPx = screenW,
        )
        val surfaceW = if (windowWidth < desiredWindowW) {
            val narrowed = (windowWidth - innerMarginPx()).coerceAtLeast(1)
            Log.w(
                LOG_TAG,
                "capsule width clamped ${desiredWindowW}px -> ${windowWidth}px" +
                    " (screen=$screenW surface=${desiredSurfaceW.roundToInt()} phase=$morphPhase" +
                    " sideRight=$sideRight) | ${snapshot()}",
            )
            narrowed.toFloat()
        } else {
            desiredSurfaceW
        }
        val windowHeight = surfaceH.roundToInt() + outerGlowPx() * 2
        // 形变期间用冻结锚点：在途位移不能把胶囊基准挪走。
        val live = currentAnchor()
        // 侧别是胶囊自己的状态（拖动 / 换边 / 跟手），**不从锚点反推**：贴边缓动途中
        // 锚点还在屏中间，反推会把侧别翻掉、圆心当场跳一下。
        // 贴屏侧那条边钉在锚点上：x 由窗口宽反推，宽和 x 必须是同一次写。
        val nextX = if (sideRight) {
            (live.right - windowWidth).coerceIn(0, (screenW - windowWidth).coerceAtLeast(0))
        } else {
            live.left.coerceIn(0, (screenW - windowWidth).coerceAtLeast(0))
        }
        // 顶边跟着球的最终位置走；长高时按**当前**高度夹回屏内（球贴近底边时胶囊往上长）。
        val top = min(live.top.toFloat(), (screenHeightPx - surfaceH).coerceAtLeast(0f)).roundToInt()
        val nextY = ((top - outerGlowPx()) - lpOriginY).coerceAtLeast(0)
        // 原点未实测前先落尺寸与 x；y 依赖原点，等布局回调测完再写。
        val writeY = originReady
        if (lp.width == windowWidth && lp.height == windowHeight && lp.x == nextX &&
            (!writeY || lp.y == nextY)
        ) {
            // 尺寸没变也可能换了目标（转屏 / 行宽收敛）：已提交画面跟着刷一次。
            drawnSurfaceW = surfaceW
            drawnSurfaceH = surfaceH
            drawnInnerMarginPx = innerMarginPx().toFloat()
            drawnOuterGlowPx = outerGlowPx().toFloat()
            return
        }
        lp.width = windowWidth
        lp.height = windowHeight
        lp.x = nextX
        if (writeY) lp.y = nextY
        drawnSurfaceW = surfaceW
        drawnSurfaceH = surfaceH
        drawnInnerMarginPx = innerMarginPx().toFloat()
        drawnOuterGlowPx = outerGlowPx().toFloat()
        Log.d(
            LOG_TAG,
            "PROBE writeSurface ${lp.width}x${lp.height}@(${lp.x},${lp.y})" +
                " surface=${surfaceW.roundToInt()}x${surfaceH.roundToInt()}" +
                " phase=$morphPhase writeY=$writeY | ${snapshot()}",
        )
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun screenOriginY(v: View): Int {
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        return (location[1] - ((v.layoutParams as? WindowManager.LayoutParams)?.y ?: 0))
            .coerceIn(0, DensityProvider.dp2px(MaxOriginDp))
    }

    /**
     * 形变基准锚点：转换期间用冻结值，稳态用现推的球位。
     *
     * 冻结是「同一颗球」的硬条件：形变期间 `posX` 被在途动画逐帧改写，实时读它会让
     * 形变面与真实球位错开（展开时胶囊离屏缘一条缝，收起时球往边缘滑）。
     */
    private fun currentAnchor(): BubbleAnchor = frozenAnchor ?: capsuleAnchor()

    /**
     * 画面矩形（**目标窗口坐标**）：贴屏侧齐平，上下各留辉光余量。
     *
     * 尺寸取 [drawnSurfaceW] / [drawnSurfaceH]（最近一次真正写进 LP 的值）：Compose 只画
     * 窗口里真实存在的那些像素。窗口变窄时它也跟着变窄，于是“揭示 / 收回”就是窗口自己
     * 的裁剪，不再需要一套独立的形变面几何。
     */
    private fun surfaceRectInWindow(): Rect {
        val w = drawnSurfaceW.coerceAtLeast(1f)
        val h = drawnSurfaceH.coerceAtLeast(1f)
        val left = if (sideRight) drawnInnerMarginPx else 0f
        val top = drawnOuterGlowPx
        return Rect(left, top, left + w, top + h)
    }

    /** 收起圆矩形（目标窗口坐标）：与画面矩形共用贴屏侧与顶边，相位 0 时两者完全重合。 */
    private fun ballRectInWindow(): Rect {
        val rect = surfaceRectInWindow()
        val size = rowHeightPx.coerceAtLeast(1).toFloat()
        val left = if (sideRight) rect.right - size else rect.left
        return Rect(left, rect.top, left + size, rect.top + size)
    }

    /**
     * 胶囊轮廓路径（窗口坐标）：形变面的底色 / 描边与内容裁剪共用一份口径。
     *
     * 半径随尺寸自适应（[CapsuleShape] 是百分比连续曲率胶囊）：画面等于球矩形时就是一个
     * 连续曲率的圆面，等于内容矩形时就是胶囊。**不要**在这里换成 `CircleShape` /
     * `addOval`：收起态与展开态必须是同一条曲线，混用会在 1:1 时露出双重边缘。
     */
    private fun capsulePath(rect: Rect, density: Density): Path? {
        if (rect.width <= 1f || rect.height <= 1f) return null
        val path = when (val outline = CapsuleShape.createOutline(rect.size, LayoutDirection.Ltr, density)) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        // 轮廓以左上角为原点生成：平移到窗口坐标（Path.translate 是原位变换）。
        return path.apply { translate(Offset(rect.left, rect.top)) }
    }

    /**
     * 按压缩放支点：**收起圆矩形的中心**。
     *
     * 根视图的按压缩放只发生在收起圆（行的按压画在行自己的 `graphicsLayer` 上）。
     * pivot 必须是圆自己的圆心：以前用展开态胶囊宽 / 中心当 pivot，收起态 pivot 落在
     * 窗口外约一条胶囊长的地方，`scale 0.9` 会把整颗圆平移二十多 dp
     * （真机就是「按下时圆跳一下」）。圆心当 pivot 时缩放不挪位置。
     */
    private fun applyCapsulePivot(v: View) {
        val ball = ballRectInWindow()
        v.pivotX = ball.center.x
        v.pivotY = ball.center.y
    }

    /**
     * 展开形变的一次性触发：`enterPending` 守卫保证只启动一次。
     *
     * 默认等两个条件：窗口真的按目标宽高布局完（[capsuleGeometryApplied]）——否则形变面
     * 按目标几何算、却画在旧球窗里，第一帧就偏；内容宽已收敛（[widthSettled]）——否则形变
     * 途中窗口变宽，形变面横向跳一段（就是原来的「侧滑」）。
     * [force] 只在兜底超时时用——宁可起始帧略偏，也不能让胶囊停在隐形。
     */
    private fun startMorphIfPending(v: View, force: Boolean = false) {
        if (!isCapsule) return
        if (transition != Transition.EXPANDING) return
        if (!enterPending) return
        if (!force && !(widthSettled && capsuleGeometryApplied(v))) return
        startMorphIn(v)
    }

    /**
     * 窗口是否已经按**当前相位的**画面尺寸完成布局（允许 1px 取整误差）。
     *
     * 必须按相位算：窗口现在跟随画面，展开前窗口是「球 + 余量」而不是胶囊矩形，
     * 拿目标胶囊尺寸去比会永远不等——形变只能靠兜底超时起手，看起来就是「延迟 200ms 才动」。
     */
    private fun capsuleGeometryApplied(v: View): Boolean {
        val targetW = phaseSurfaceW().roundToInt() + innerMarginPx()
        val targetH = phaseSurfaceH().roundToInt() + outerGlowPx() * 2
        if (targetW <= 0 || targetH <= 0) return false
        return abs(v.width - targetW) <= 1 && abs(v.height - targetH) <= 1
    }

    /** 展开形变：相位当前值 → 1（球矩形 → 内容矩形），时长 / 曲线与其他浮窗家族一致。 */
    private fun startMorphIn(v: View) {
        enterPending = false
        // 展开是「收起圆按钮 → 内容」：收起圆的按压缩放（scale 0.9）不得带进展开态，
        // 否则整颗胶囊小一圈。回弹被中断（快速重按 / 到达动效自动展开）时 scale 可能
        // 停在 0.9，这里起手硬复位。
        pressAnimator?.let { pressAnimator = null; it.cancel() }
        v.scaleX = 1f
        v.scaleY = 1f
        // 相位不硬归 0：收起被打断时就是从半路接着长（硬归位会跳一帧）。
        // 位置已由 [startMorphIfPending] 的门控确保落好（新建窗之前是隐形的），可以亮出来。
        if (!visuallyHidden) v.alpha = 1f
        cancelIdle()
        Log.d(LOG_TAG, "PROBE startMorphIn pose | ${snapshot()}")
        animateMorphTo(1f) {
            if (view === v) {
                transition = Transition.NONE
                frozenAnchor = null
            }
        }
        mainHandler.postDelayed(
            {
                if (view === v && transition == Transition.EXPANDING) {
                    transition = Transition.NONE
                    frozenAnchor = null
                }
            },
            morphAnimMs + 80L,
        )
    }

    /**
     * 相位动画：只改 [morphPhase]（Compose 侧 draw / layer 阶段读），不动 View 变换。
     * [durationMs] = 0 表示瞬时归位（起手姿态 / 收尾），不播缓动。
     *
     * [carryX] / [carryAlphaFrom] 只给「横滑接续收起」用：同一条曲线把拖拽残余的窗口位移
     * 与透明度一起收回常态（相位 → 0 时位移 → 0、alpha → 1），不让它先跳回常态再播一遍。
     */
    private fun animateMorphTo(
        target: Float,
        durationMs: Long = morphAnimMs,
        carryX: Float = 0f,
        carryAlphaFrom: Float = 1f,
        onEnd: () -> Unit = {},
    ) {
        // 先摘引用再 cancel：cancel 会同步回调 onAnimationEnd，摘掉旧动画后它的收尾会被忽略。
        val previous = morphAnimator
        morphAnimator = null
        previous?.cancel()
        val from = morphPhase
        val carries = carryX != 0f || carryAlphaFrom != 1f
        if (durationMs <= 0L || abs(target - from) < 0.001f) {
            morphPhase = target
            if (carries) {
                view?.let {
                    it.translationX = 0f
                    it.alpha = 1f
                }
            }
            view?.let(::applyCapsuleGeometry)
            onEnd()
            return
        }
        val span = target - from
        val animator = ValueAnimator.ofFloat(from, target)
        animator.duration = durationMs
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            val phase = it.animatedValue as Float
            morphPhase = phase
            // 窗口跟随画面：每帧一次 updateViewLayout，宽 + 高 + x + y 同一次写。
            view?.let(::applyCapsuleGeometry)
            if (carries) {
                val progress = ((phase - from) / span).coerceIn(0f, 1f)
                view?.let { v ->
                    v.translationX = carryX * (1f - progress)
                    // 锁定 / 息屏中途把胶囊藏起来时，接续动画不得把 alpha 写回去。
                    if (!visuallyHidden && !lockHidden && !panelHidden) {
                        v.alpha = carryAlphaFrom + (1f - carryAlphaFrom) * progress
                    }
                }
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (morphAnimator !== animation) return
                morphAnimator = null
                onEnd()
            }
        })
        morphAnimator = animator
        animator.start()
    }

    private fun cancelEnterFeedback() {
        // 展开形变在按下时直接落到终点：相位停在中途会让行内容半隐、点按落穿。
        if (transition != Transition.EXPANDING) return
        enterPending = false
        transition = Transition.NONE
        frozenAnchor = null
        animateMorphTo(1f, durationMs = 0L)
        val v = view ?: return
        pressAnimator?.let { pressAnimator = null; it.cancel() }
        v.scaleX = 1f
        v.scaleY = 1f
        v.translationY = 0f
        v.alpha = 1f
    }

    /**
     * 按压缩放反馈（§5.3，与主球同一档位）。
     *
     * 住手 120ms（`MotionPress`）、回弹 240ms（`MotionSettle`），用 [ValueAnimator] 逐帧写
     * scale——不用 `view.animate()`（与换边淡出 / 静置共用同一个 ViewPropertyAnimator 会互相 cancel）。
     * 行的按压缩放画在行自己的 `graphicsLayer` 上；这里只负责根视图（收起圆 / 整窗）。
     */
    private fun applyPressScale(pressed: Boolean) {
        val v = view ?: return
        if (!v.isAttachedToWindow) return
        applyCapsulePivot(v)
        val target = if (pressed) FloatBallDefaults.PressScale else 1f
        val from = v.scaleX
        if (abs(target - from) < 0.001f) return
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(from, target).apply {
            duration = (if (pressed) MotionPress else MotionSettle).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (pressAnimator !== anim) return@addUpdateListener
                val scale = anim.animatedValue as Float
                v.scaleX = scale
                v.scaleY = scale
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (pressAnimator !== animation) return
                    pressAnimator = null
                    v.scaleX = target
                    v.scaleY = target
                }
            })
        }.also { it.start() }
    }

    // ---- Compose ----

    /** 单窗内容根：一条相位画完「收起圆 ↔ 胶囊内容」。 */
    @Composable
    private fun WindowContent() {
        CapsuleMorph()
    }

    /**
     * 收起态圆面：胶囊自己的「按钮」形态——底色圆 + 图标 + 未读数。
     *
     * 不读悬浮球的设置（自定义图标 / 判定区 / 静置），那是另一个对象。
     */
    @Composable
    private fun CollapsedCircle(sizeDp: Dp, modifier: Modifier = Modifier) {
        val accent = capsuleSettings.colorSpec.toMarkColor()
        val badge = collapsedBadgeState
        Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 圆面的形与**内容**（图标 / 描边）都用胶囊角色：1:1 时它是连续曲率的圆面，
                    // 与形变面的 [CapsuleShape] 同一条曲线。混用 `CircleShape` 会让里面的
                    // 正圆比外面方一点，收起态看着就是“壳是 squircle、心是圆”。
                    .background(accent.copy(alpha = 0.92f), CapsuleShape)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        CapsuleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when (val icon = fixedIconState) {
                    is ImageVector -> Image(
                        imageVector = icon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.size(sizeDp / 2),
                    )

                    is ImageBitmap -> Image(
                        bitmap = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CapsuleShape),
                    )

                    is TextIcon -> TextIconGlyph(
                        text = icon.text,
                        size = sizeDp / 2,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )

                    else -> Unit
                }
            }
            if (badge != null && badge.visible) {
                // 圆形角标在**远离屏缘**那一侧的上角：右贴边 → 圆的左上，左贴边 → 右上。
                // 横向不越出窗口（收起态窗口 == 圆），所以贴在内侧弧上而不是往外挂。
                CountBadge(
                    label = badgeLabel(badge),
                    modifier = Modifier
                        .align(if (sideRight) Alignment.TopStart else Alignment.TopEnd)
                        .offset(x = if (sideRight) 2.dp else (-2).dp, y = 2.dp),
                )
            }
        }
    }

    /** 收起圆角标的文案：1–9 显数字，>9 只显一个圆点；固定项用图钉。 */
    private fun badgeLabel(badge: BadgeState): String {
        if (badge.showPinOnly) return "📌"
        val count = badge.unpinnedCount + badge.pinnedCount
        return if (count in 1..9) count.toString() else ""
    }

    /** 未读数角标：正圆（直径固定，数字只占一位），无数字时就是一个红点。 */
    @Composable
    private fun CountBadge(label: String, modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .size(BadgeDiameter)
                .clip(CircleShape)
                .background(Color(0xFFE53935)),
            contentAlignment = Alignment.Center,
        ) {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 9.sp,
                    maxLines = 1,
                    style = TextStyle(lineHeight = 9.sp),
                )
            }
        }
    }

    /**
     * 胶囊态内容：一个对象从球矩形长成内容矩形（[morphPhase] 0 → 1）。
     *
     * 三层共用窗口坐标里的同一份几何：
     * - 形变面：轮廓矩形按相位从球矩形插值到内容矩形（[CapsuleShape] 是百分比圆角，
     *   球矩形时就画成圆），尾段淡出、把画面交给实胶囊的行；
     * - 行内容：保持最终尺寸，只被形变面裁住逐帧「露出」并在尾段淡入（不缩放文本）；
     * - 球面：头段淡出（图标没有同步形变对象，先隐；形状由形变面接住）。
     *
     * 相位只在 draw / layer 阶段读：180ms 里一次重组也不触发，只失效绘制。
     */
    @Composable
    private fun CapsuleMorph() {
        SideGestureTheme {
            val list = items
            val density = LocalDensity.current
            // 列表空了（收起途中被清队列）也不要整窗变空：至少把球面继续画在球矩形上。
            val hasContent = list.isNotEmpty() && rowHeightPx > 0
            val rowHeight = with(density) { rowHeightPx.toDp() }
            val accent = capsuleSettings.colorSpec.toMarkColor()
            val contentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
            val surfaceColor = accent.copy(alpha = accent.alpha * BubbleBackgroundAlpha)
            val borderColor = contentColor.copy(alpha = RowBorderAlpha)
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (hasContent) {
                    // 形变面：球面与胶囊行的底色 / 描边同源，淡出后由行自己接管（单行时两者重合）。
                    // 矩形取**已提交**的画面矩形：它永远落在窗口里。
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val phase = morphPhase
                        val fade = 1f - ((phase - SurfaceFadeStart) / (1f - SurfaceFadeStart))
                            .coerceIn(0f, 1f)
                        if (fade <= 0f) return@Canvas
                        val path = capsulePath(surfaceRectInWindow(), this) ?: return@Canvas
                        drawPath(path, surfaceColor.copy(alpha = surfaceColor.alpha * fade))
                        drawPath(
                            path = path,
                            color = borderColor.copy(alpha = borderColor.alpha * fade),
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                    Box(
                        modifier = Modifier
                            // 内容按**最终**窗口尺寸布局（requiredSize 不被窗口约束压缩）：动画期间
                            // 不重排、不重组，窗口变窄后只是把它裁出来。
                            .requiredSize(
                                width = with(density) { (capsuleWidthPx + centerMarginPx).toDp() },
                                height = with(density) { (windowHeightPx + glowMarginPx * 2).toDp() },
                            )
                            .align(if (sideRight) Alignment.TopEnd else Alignment.TopStart)
                            .graphicsLayer {
                                alpha = ((morphPhase - ContentFadeStart) / (1f - ContentFadeStart))
                                    .coerceIn(0f, 1f)
                            }
                            .drawWithContent {
                                // 相位到 1（稳态）后不再裁剪：到达提示的辉光要能越入窗口余量。
                                val phase = morphPhase
                                if (phase >= 1f) {
                                    this@drawWithContent.drawContent()
                                    return@drawWithContent
                                }
                                val path = capsulePath(surfaceRectInWindow(), this)
                                if (path == null) return@drawWithContent
                                clipPath(path) { this@drawWithContent.drawContent() }
                            },
                    ) {
                        CapsuleStack(list = list, rowHeight = rowHeight)
                    }
                }
                // 收起圆层：贴屏侧齐平、与胶囊锚点同一份坐标（窗口变窄时它不会飘）。
                CollapsedCircle(
                    sizeDp = with(density) { rowHeightPx.toDp() },
                    modifier = Modifier
                        .offset {
                            val rect = ballRectInWindow()
                            IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                        }
                        .graphicsLayer {
                            alpha = (1f - morphPhase / BallFadeEnd).coerceIn(0f, 1f)
                        },
                )
            }
        }
    }

    /**
     * 滚动栈：每条一颗独立胶囊，最多设置里的同屏条数可见，超出竖向滚动。
     *
     * 窗口只做透明承载；每颗胶囊各自裁形 / 底色 / 描边，之间留 [RowGapDp] 的间距。
     * 行宽贴内容（夹到窗宽上限），靠球一侧齐平、向屏幕内伸缩—— Column 整体 wrap 内容，
     * [sideRight] 决定对齐侧；实测行宽记进 [rowWidthsPx] 供命中与逐行描边收口。
     * 滚动位移直接作用在 Column 的 layout offset 上（[offsetPx] 在 lambda 里读），
     * 跟手与横滑都不触发重组。
     *
     * 窗口按非对称余量扩边（[GlowMarginDp] / [centerMarginPx]）：内容区 padding 让位并
     * [clipToBounds] 裁住滚动溢出，到达动效覆盖层铺满全窗、辉光可越入余量不被裁。
     */
    @Composable
    private fun CapsuleStack(list: List<BallItem>, rowHeight: androidx.compose.ui.unit.Dp) {
        val accent = capsuleSettings.colorSpec.toMarkColor()
        val contentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
        val density = LocalDensity.current
        val iconSize = with(density) {
            min(rowHeight.value - 16f, 24f).coerceAtLeast(16f).dp
        }
        val maxRowWidth = with(density) { capsuleWidthPx.toDp() }
        // 胶囊角色（§2.1.1）：连续曲率胶囊，半径随高度自适应。每颗各自裁形，不共享容器。
        val shape = CapsuleShape
        // 通知行宽度联动窗口宽：最长通知行 = 内容自然宽×1/2（夹最小可读宽与半屏上限），
        // 窗口加宽到能容纳；结果行保持「贴内容」不受影响。测量用 TextMeasurer（无约束）。
        val textMeasurer = rememberTextMeasurer()
        val notifFallbackTitle = stringResource(R.string.smart_ball_notification)
        val titleStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
        val bodyStyle = MaterialTheme.typography.bodySmall
        val iconPx = with(density) { iconSize.toPx().roundToInt() }
        val rowGapPx = with(density) { 8.dp.toPx().roundToInt() }
        val rowPadPx = with(density) { RowHorizontalPadding.toPx().roundToInt() }
        fun notificationContentWidthPx(item: NotificationItem): Int {
            val titleW = textMeasurer.measure(
                item.title.ifBlank { notifFallbackTitle }, titleStyle, constraints = Constraints(),
            ).size.width
            val bodyW = if (item.text.isNotBlank()) {
                textMeasurer.measure(item.text, bodyStyle, constraints = Constraints()).size.width
            } else 0
            val contentW = max(titleW, bodyW)
            // 行宽上限按**整行**（文本列+图标+间距+内距）计：文本列上限再扣掉装饰宽，
            // 避免「文本列 + 图标/内距」实际超过行宽上限（dp 封顶，窄屏按比例兜底）。
            // 上限还要扣掉中侧余量与屏缘留白：行宽 + 余量不得越屏（[CapsuleWidths]）。
            val rowDecoration = iconPx + rowGapPx + rowPadPx * 2
            val maxRowPx = CapsuleWidths.surfaceCapPx(
                screenWidthPx = DensityProvider.screenWidthPx,
                centerMarginPx = centerMarginPx,
                edgeInsetPx = CapsuleWidths.edgeInsetPx(DensityProvider.density),
                absoluteCapPx = DensityProvider.dp2px(MaxWidthCapDp),
                widthRatio = NarrowWidthRatio,
            )
            val maxContentPx = maxRowPx - rowDecoration
            // 按实际内容宽：内容宽 ≤ 上限 → 全显贴内容（正文不滚）；
            // 内容宽 > 上限 → 文本列封顶上限，正文由 MarqueeText 自动滚动看全文。
            return contentW.coerceAtMost(maxContentPx)
        }
        // 首帧窗口宽按公式兜底，Compose 测量出通知行目标宽后再收窄/加宽（一次收敛）。
        // 有通知行时只按内容驱动（不叠加 fixedCapsuleWidth 兜底下限，否则短内容被撑到 ~48% 屏宽）；
        // 无通知行（结果胶囊）保持固定公式。key 带朝向与屏尺寸版本：转屏 / 折叠 / 改显示大小后
        // 立即按新屏宽重推行宽，不等下一条通知。
        LaunchedEffect(list, layoutEpoch, rowHeightPx) {
            val notifMax = list.filterIsInstance<NotificationItem>()
                .maxOfOrNull { notificationContentWidthPx(it) + iconPx + rowGapPx + rowPadPx * 2 }
                ?: 0
            // 内容驱动宽不能比球窄：短文案的胶囊比球还小会像“缩了一下”，
            // 也让展开形变的起点（球宽）失去参照。
            val desired = if (list.any { it is NotificationItem }) {
                notifMax.coerceAtLeast(rowHeightPx)
            } else {
                fixedCapsuleWidth()
            }
            val capped = desired.coerceIn(1, maxSurfaceWidthPx)
            if (capped != capsuleWidthPx) {
                capsuleWidthPx = capped
                // 宽变了必须连 x 一起重写（贴屏侧那条边由宽反推）。
                view?.let { applyCapsuleGeometry(it) }
            }
            // 内容宽收敛：展开形变可以从球矩形起了（窗口是否已落几何由 startMorphIfPending 判）。
            widthSettled = true
            view?.let { startMorphIfPending(it) }
        }
        // 结果胶囊按实测行宽收口：行量出后把窗口收到最长行宽，紧贴内容（余量只剩 16dp）。
        // 只在「比当前窗口窄」时收，不回加宽——行宽由内容决定、窗口宽只是容器，收敛一次即稳；
        // 通知行不走这里（上面的 effect 已按内容驱动，重复收会跟它抢宽）。
        LaunchedEffect(rowWidthsPx.values.maxOrNull(), rowWidthsPx.size) {
            if (list.any { it is NotificationItem }) return@LaunchedEffect
            val contentDriven = rowWidthsPx.values.maxOrNull() ?: return@LaunchedEffect
            val narrowed = contentDriven.coerceIn(rowHeightPx, maxSurfaceWidthPx)
            if (narrowed < capsuleWidthPx) {
                capsuleWidthPx = narrowed
                // 宽变了必须连 x 一起重写（贴屏侧那条边由宽反推）。
                view?.let { applyCapsuleGeometry(it) }
            }
        }
        val metrics = stackMetrics
        val thumbHeightPx = if (metrics.maxScrollPx > 0) {
            (metrics.viewportHeightPx.toFloat() * metrics.viewportHeightPx / metrics.contentHeightPx)
                .roundToInt()
                .coerceIn(DensityProvider.dp2px(ScrollThumbMinDp), metrics.viewportHeightPx)
        } else {
            0
        }
        val centerMargin = with(density) { centerMarginPx.toDp() }
        val glowMargin = with(density) { glowMarginPx.toDp() }
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // 内容区：贴屏侧齐平，中侧 + 上下让出余量；clip 住滚动溢出（行不越入余量）。
            Box(
                modifier = Modifier
                    .padding(
                        start = if (sideRight) centerMargin else 0.dp,
                        end = if (sideRight) 0.dp else centerMargin,
                        top = glowMargin,
                        bottom = glowMargin,
                    )
                    .clipToBounds()
                    .onSizeChanged { if (it.width > 0 && it.height > 0) contentReady = true },
            ) {
                Column(
                    modifier = Modifier
                        .offset { IntOffset(0, offsetPx.roundToInt()) },
                    horizontalAlignment = if (sideRight) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(RowGapDp.dp),
                ) {
                list.forEach { item ->
                    // 通知行：文本列宽 = 内容自然宽×1/2（夹最小可读宽与半屏），行宽 = 列宽+图标+间距+内距。
                    // 结果行：保持「贴内容」夹窗口宽。测量结果按内容缓存（条目少，重组不重复测）；
                    // key 带 [layoutEpoch]：同朝向改了屏宽后行宽要按新屏宽重算，不能留着旧屏的值。
                    val notifContentWidth = if (item is NotificationItem) {
                        remember(item.id, item.title, item.text, layoutEpoch) {
                            with(density) { notificationContentWidthPx(item).toDp() }
                        }
                    } else {
                        null
                    }
                    Row(
                        modifier = Modifier
                            .then(
                                if (notifContentWidth != null) {
                                    Modifier.width(
                                        notifContentWidth + iconSize + 8.dp + RowHorizontalPadding * 2,
                                    )
                                } else {
                                    Modifier.widthIn(max = maxRowWidth)
                                },
                            )
                            .height(rowHeight)
                            .onSizeChanged { onRowMeasured(item.id, it.width) }
                            .graphicsLayer {
                                // 横滑只让按下的那颗跟手；其余胶囊原地不动。
                                // 视觉位移过阈值后橡皮筋递减并钳在余量内（判定用原始位移）。
                                val dragging = item.id == rowDragId
                                translationX = if (dragging) rubberBandedRowDx(rowDragDx) else 0f
                                alpha = if (dragging) {
                                    1f - (abs(rowDragDx) / collapseThresholdPx).coerceIn(0f, 1f) * 0.5f
                                } else {
                                    1f
                                }
                                // 按压反馈也只缩按下的那颗；拖动开始即被清空。
                                val scale = if (item.id == pressedRowId) {
                                    FloatBallDefaults.PressScale
                                } else {
                                    1f
                                }
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(shape)
                            // 自定义背景色按取色 alpha 等比缩放：不透明默认色 = 历史的 0.88。
                            .background(accent.copy(alpha = accent.alpha * BubbleBackgroundAlpha))
                            .border(1.dp, contentColor.copy(alpha = RowBorderAlpha), shape)
                            .padding(horizontal = RowHorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (item is ResultItem && menuItemId == item.id) {
                            // 长按行为 = 操作菜单：行内换成动作行（菜单行撑到行宽上限）。
                            ResultMenuRow(
                                item = item,
                                contentColor = contentColor,
                                iconSize = iconSize,
                            )
                        } else {
                            when (item) {
                                is NotificationItem -> NotificationContent(
                                    item = item,
                                    contentColor = contentColor,
                                    iconSize = iconSize,
                                    contentWidthDp = notifContentWidth ?: maxRowWidth,
                                )

                                is ResultItem -> ResultContent(
                                    item = item,
                                    contentColor = contentColor,
                                    iconSize = iconSize,
                                    copied = copiedItemId == item.id,
                                )
                            }
                        }
                    }
                }
            }
            if (scrollThumbVisible && thumbHeightPx > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(ScrollThumbWidth)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .offset {
                            IntOffset(
                                x = -DensityProvider.dp2px(ScrollThumbInsetDp),
                                y = scrollThumbTopPx(metrics, thumbHeightPx).roundToInt(),
                            )
                        }
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = ScrollThumbAlpha)),
                )
            }
            }
            // 到达提示动效（本体脉冲 / 边框流光）：铺满全窗、可越入余量，不挡触摸与内容。
            arrivalAlert?.let { layer ->
                // 与舞台窗同口径：系统移除动画也照播（动画协程上下文固定 1f）。
                CapsuleArrivalOverlay(
                    layer = layer,
                    colorSpec = arrivalColorSpec,
                    rowHeightPx = rowHeightPx,
                    metrics = metrics,
                    contentColor = contentColor,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }

    /**
     * 胶囊本体的到达提示：逐行沿 [CapsuleShape] 描边。
     *
     * - 脉冲：3 层嵌套描边辉光（核心 + 两层加宽减淡），亮度按 sin 包络呼吸。
     * - 流光：主亮带沿边框横扫（缓出），后随一道更宽更暗的拖尾。
     *
     * 观感参数按效果独立调校（[PulseTuning] / [ShimmerTuning]，不与其他效果共用）。
     * 行路径按行实测宽度逐行缓存，绘制阶段读 [Animatable] 只触发重绘不重组；
     * 时间线播完等末帧再清状态（ui-guidelines §5.1），窗口复用不受影响。
     */
    @Composable
    private fun CapsuleArrivalOverlay(
        layer: AlertLayerPlan,
        colorSpec: ColorSpec,
        rowHeightPx: Int,
        metrics: StackMetrics,
        contentColor: Color,
        modifier: Modifier = Modifier,
    ) {
        val density = LocalDensity.current
        // 动效颜色：选调色板用 [layer.palette] 解析，否则单色回退 [colorSpec]（未设跟随主题主色）。
        val palette = layer.palette?.toColors(colorSpec.toMarkColor())
            ?: AlertPaletteColors(listOf(colorSpec.toMarkColor()), rainbow = false)
        // 行宽贴内容后各行实测宽度不同：逐行按自己的宽度生成描边路径。
        val rowWidths = items.map { rowWidthsPx[it.id] ?: capsuleWidthPx }
        val rowPaths = remember(rowWidths, rowHeightPx) {
            rowWidths.map { widthPx ->
                if (widthPx <= 0 || rowHeightPx <= 0) {
                    null
                } else {
                    val outline = CapsuleShape.createOutline(
                        size = Size(widthPx.toFloat(), rowHeightPx.toFloat()),
                        layoutDirection = LayoutDirection.Ltr,
                        density = density,
                    )
                    when (outline) {
                        is Outline.Generic -> outline.path
                        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                        is Outline.Rectangle -> null
                    }
                }
            }
        }
        val elapsed = remember { Animatable(0f) }
        // 沿轮廓扫光：每行缓存 PathMeasure（轮廓不变），绘制期只取子段。
        val rowMeasures = remember(rowPaths) {
            rowPaths.map { path ->
                path?.let { PathMeasure().apply { setPath(it, false) } }
            }
        }
        val scratchPath = remember { Path() }
        LaunchedEffect(layer) {
            withContext(FixedMotionDurationScale) {
                // 层延迟：窗口可以先挂好（透明），到点再起手。
                if (layer.startAt > 0) delay(layer.startAt.toLong())
                elapsed.snapTo(0f)
                elapsed.animateTo(
                    layer.totalMillis.toFloat(),
                    tween(durationMillis = layer.totalMillis, easing = LinearEasing),
                )
            }
            withFrameNanos { }
            arrivalAlert = null
        }
        Canvas(modifier = modifier) {
            if (elapsed.value >= layer.totalMillis) return@Canvas
            val envelope = envelopeAt(elapsed.value, layer.totalMillis, AlertEnvelopeShare)
            val t = (elapsed.value / layer.cycleMillis) % 1f
            val gapPx = metrics.gapPx
            val rows = items.size
            // 全窗坐标系：内容区在贴屏侧齐平、中侧与上下让出余量；行靠球侧对齐，
            // 逐行按实测宽算 x（窄行不再画到窗缘）。
            val rowLeft = { index: Int ->
                if (sideRight) (capsuleWidthPx + centerMarginPx - (rowWidths[index])).toFloat() else 0f
            }
            val topOrigin = glowMarginPx.toFloat()
            val spec = layer.spec as CapsuleSpec
            val sweep = spec.sweep
            val pulse = spec.pulse
            val flip = spec.flip
            when (spec.shape) {
                // 描边呼吸：辉光层数 + 呼吸缩放是旋钮。
                AlertCapsuleShape.Pulse -> {
                    val boost = layer.brightness.lerpInRange(
                        PulseTuning.BoostMin,
                        PulseTuning.BoostMax,
                    )
                    val breathe = sin(PI.toFloat() * t)
                    val peak = (RowBorderAlpha + boost * breathe).coerceAtMost(1f) * envelope
                    val corePx = pulse.strokeWidthDp.dp.toPx()
                    val glow = listOf(
                        PulseTuning.GlowWidthScale2 to PulseTuning.GlowAlphaFraction2,
                        PulseTuning.GlowWidthScale1 to PulseTuning.GlowAlphaFraction1,
                    ).take(pulse.glowLayers.coerceIn(0, 2))
                    val scaleFactor = 1f + pulse.breatheScale.coerceIn(0f, 1f) * breathe
                    for (index in 0 until rows) {
                        val path = rowPaths.getOrNull(index) ?: continue
                        translate(
                            left = rowLeft(index),
                            top = topOrigin + index * (rowHeightPx + gapPx).toFloat() + offsetPx,
                        ) {
                            val rowColor = palette.at(index)
                            for ((widthScale, alphaFraction) in glow) {
                                val width = corePx * widthScale * pulse.glowScale
                                val alpha = peak * alphaFraction * pulse.glowScale
                                if (width > 0f && alpha > 0f) {
                                    drawPath(
                                        path = path,
                                        color = rowColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                                        style = Stroke(width = width),
                                    )
                                }
                            }
                            scale(
                                scale = scaleFactor,
                                pivot = Offset(rowWidths[index].toFloat() / 2f, rowHeightPx / 2f),
                            ) {
                                drawPath(
                                    path = path,
                                    color = rowColor.copy(alpha = peak.coerceIn(0f, 1f)),
                                    style = Stroke(width = corePx),
                                )
                            }
                        }
                    }
                }

                // 扫光：fill = 行内横向扫，否则沿轮廓扫；heads 决定单头 / 双头。
                AlertCapsuleShape.Sweep -> {
                    // 扫光不套整体淡入淡出，避免被读成「呼吸」。
                    val peak = layer.brightness.lerpInRange(
                        ShimmerTuning.PeakAlphaMin,
                        ShimmerTuning.PeakAlphaMax,
                    )
                    val eased = ((t * sweep.travel.coerceIn(0f, 1f)) % 1f)
                        .easedOut(ShimmerTuning.SweepEaseExp)
                    if (sweep.fill) {
                        val pos = if (sweep.reverse) 1f - eased else eased
                        val band = sweep.bandPercent.coerceIn(5, 90) / 100f
                        val start = (pos - band / 2f).coerceIn(0f, 1f)
                        val end = (pos + band / 2f).coerceIn(0f, 1f)
                        val span = (end - start).coerceAtLeast(0.001f)
                        for (index in 0 until rows) {
                            val path = rowPaths.getOrNull(index) ?: continue
                            translate(
                                left = rowLeft(index),
                                top = topOrigin + index * (rowHeightPx + gapPx).toFloat() + offsetPx,
                            ) {
                                val rowW = rowWidths[index].toFloat()
                                val brush = Brush.horizontalGradient(
                                    start * rowW to Color.Transparent,
                                    (start + span / 2f) * rowW to
                                        palette.at(index).copy(alpha = peak),
                                    end * rowW to Color.Transparent,
                                )
                                drawPath(path = path, brush = brush)
                            }
                        }
                    } else {
                        val heads = sweep.heads.coerceIn(1, 2)
                        val stroke = Stroke(
                            width = sweep.strokeWidthDp.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        val direction = if (sweep.reverse) -1f else 1f
                        for (index in 0 until rows) {
                            val measure = rowMeasures.getOrNull(index) ?: continue
                            val length = measure.length
                            if (length <= 1f) continue
                            translate(
                                left = rowLeft(index),
                                top = topOrigin + index * (rowHeightPx + gapPx).toFloat() + offsetPx,
                            ) {
                                val bandPx = (sweep.bandPercent.coerceIn(5, 90) / 100f) *
                                    rowWidths[index].toFloat()
                                val halfSpan = bandPx / 2f
                                for (head in 0 until heads) {
                                    val centerDist = (if (head == 0) eased else 1f - eased) * length
                                    if (sweep.trailScale > 0f && sweep.trailAlpha > 0f) {
                                        val trailSpan = bandPx * sweep.trailScale
                                        drawPerimeterBand(
                                            measure = measure,
                                            length = length,
                                            centerDistance = centerDist -
                                                direction * (halfSpan + trailSpan / 2f),
                                            halfSpan = trailSpan / 2f,
                                            palette = palette,
                                            peakAlpha = peak * sweep.trailAlpha,
                                            stroke = stroke,
                                            scratch = scratchPath,
                                            slices = ShimmerTuning.TrailSlices,
                                        )
                                    }
                                    drawPerimeterBand(
                                        measure = measure,
                                        length = length,
                                        centerDistance = centerDist,
                                        halfSpan = halfSpan,
                                        palette = palette,
                                        peakAlpha = peak,
                                        stroke = stroke,
                                        scratch = scratchPath,
                                        slices = ShimmerTuning.BandSlices,
                                    )
                                }
                            }
                        }
                    }
                }

                // 3D 翻转：描边绕行中心横向压扁 / 展开，配合辉光呼吸。
                AlertCapsuleShape.Flip -> {
                    val boost = layer.brightness.lerpInRange(
                        PulseTuning.BoostMin,
                        PulseTuning.BoostMax,
                    )
                    val angle = PI.toFloat() * t * (flip.degrees / 180f)
                    val scaleX = abs(cos(angle)).coerceAtLeast(0.05f)
                    val alpha = (RowBorderAlpha + boost * sin(PI.toFloat() * t))
                        .coerceAtMost(1f) * envelope
                    val strokeW = flip.strokeWidthDp.dp.toPx()
                    for (index in 0 until rows) {
                        val path = rowPaths.getOrNull(index) ?: continue
                        translate(
                            left = rowLeft(index),
                            top = topOrigin + index * (rowHeightPx + gapPx).toFloat() + offsetPx,
                        ) {
                            val color = palette.at(index)
                            scale(
                                scaleX = scaleX,
                                scaleY = 1f,
                                pivot = Offset(rowWidths[index].toFloat() / 2f, rowHeightPx / 2f),
                            ) {
                                drawPath(
                                    path = path,
                                    color = color.copy(alpha = alpha * 0.35f),
                                    style = Stroke(width = strokeW * 3.2f),
                                )
                                drawPath(
                                    path = path,
                                    color = color.copy(alpha = alpha),
                                    style = Stroke(width = strokeW),
                                )
                            }
                        }
                    }
                }

            }
        }
    }

    /**
     * 沿路径绘制一段「带峰值」的扫光：按 [slices] 取子路径，颜色按中点周长位置采样，
     * alpha 用三角包络（中心最亮、两端为 0）。
     *
     * 用 PathMeasure 子段而不是整行水平渐变：胶囊左右圆弧也吃光，与「边框流光」文案一致；
     * 多色调色板沿轮廓变化，而不是整行一个颜色。
     */
    private fun DrawScope.drawPerimeterBand(
        measure: PathMeasure,
        length: Float,
        centerDistance: Float,
        halfSpan: Float,
        palette: AlertPaletteColors,
        peakAlpha: Float,
        stroke: Stroke,
        scratch: Path,
        slices: Int,
    ) {
        if (halfSpan <= 0f || peakAlpha <= 0f || slices <= 0) return
        val start = (centerDistance - halfSpan).coerceAtLeast(0f)
        val end = (centerDistance + halfSpan).coerceAtMost(length)
        if (end - start < 1f) return
        val step = (end - start) / slices
        for (i in 0 until slices) {
            val a = start + step * i
            val b = (a + step).coerceAtMost(end)
            if (b - a < 0.5f) continue
            val mid = (a + b) / 2f
            val weight = 1f - (abs(mid - centerDistance) / halfSpan).coerceIn(0f, 1f)
            if (weight <= 0.05f) continue
            scratch.rewind()
            if (!measure.getSegment(a, b, scratch, true)) continue
            drawPath(
                path = scratch,
                color = palette.sample(mid / length).copy(alpha = peakAlpha * weight),
                style = stroke,
            )
        }
    }

    @Composable
    private fun RowScope.NotificationContent(
        item: NotificationItem,
        contentColor: Color,
        iconSize: androidx.compose.ui.unit.Dp,
        contentWidthDp: androidx.compose.ui.unit.Dp,
    ) {
        AppIcon(packageName = item.packageName, tint = contentColor, size = iconSize)
        Column(
            // 文本列宽固定 = 内容自然宽×1/2（父级按内容算出）；正文超宽时自动滚动（marquee）。
            modifier = Modifier.width(contentWidthDp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = item.title.ifBlank { stringResource(R.string.smart_ball_notification) },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.text.isNotBlank()) {
                MarqueeText(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    /**
     * 自动横向滚动文本：正文完整宽 > 视口宽时单向滚动，尾部贴左缘后直接跳回开头（marquee），
     * 与手势无关；视口内能放下就不滚（短内容全显，不裁）。
     */
    @Composable
    private fun MarqueeText(
        text: String,
        style: TextStyle,
        color: Color,
        modifier: Modifier = Modifier,
    ) {
        val textMeasurer = rememberTextMeasurer()
        val layout = remember(text, style) { textMeasurer.measure(text, style, constraints = Constraints()) }
        BoxWithConstraints(modifier = modifier.clipToBounds()) {
            val viewport = constraints.maxWidth
            val textWidth = layout.size.width
            if (textWidth > viewport) {
                // 单副本：正文完整宽 > 视口时单向滚动，尾部贴左缘后直接跳回开头（不续接、
                // 无尾部提前出现）。单 Text 渲染宽 == 测量宽，无布局歧义。
                val distance = (textWidth - viewport).coerceAtLeast(1)
                val transition = rememberInfiniteTransition()
                val dx by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = -distance.toFloat(),
                    animationSpec = infiniteRepeatable(
                        tween(
                            durationMillis = (distance / MarqueePxPerSec * 1000)
                                .roundToInt().coerceAtLeast(600),
                            easing = LinearEasing,
                        ),
                        // 单副本：滚到文本尾部贴左缘后直接跳回开头（不续接、无尾部提前出现）。
                        repeatMode = RepeatMode.Restart,
                    ),
                )
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .requiredWidth(with(LocalDensity.current) { textWidth.toDp() })
                        .offset { IntOffset(dx.roundToInt(), 0) },
                )
            } else {
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    private fun RowScope.ResultContent(
        item: ResultItem,
        contentColor: Color,
        iconSize: androidx.compose.ui.unit.Dp,
        copied: Boolean,
    ) {
        ResultIcon(item = item, contentColor = contentColor, size = iconSize)
        Column(
            // fill=false：文字短时行贴内容收缩，文字长时吃满剩余空间并省略。
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = resultTitle(item),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (copied) {
                    stringResource(R.string.result_copied)
                } else {
                    resultBody(item)
                },
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = if (copied) 1f else 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (copied) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(
                text = resultActionLabel(item),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.95f),
                maxLines = 1,
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(contentColor.copy(alpha = 0.16f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }

    @Composable
    private fun ResultMenuRow(
        item: ResultItem,
        contentColor: Color,
        iconSize: androidx.compose.ui.unit.Dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (action in menuActions(item)) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = actionIcon(action),
                        contentDescription = actionDescription(action),
                        tint = if (action == ResultAction.IGNORE) {
                            contentColor.copy(alpha = 0.7f)
                        } else {
                            contentColor
                        },
                        modifier = Modifier.size((iconSize.value * 0.8f).dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun resultTitle(item: ResultItem): String = item.title
        .ifBlank { stringResource(R.string.result_generic) }
        .let { title ->
            // 链接类规则：标题用目标应用名（「发往 小米浏览器」的信息密度比「链接」高）。
            if (item.urls.isNotEmpty() && item.targetLabel != null) item.targetLabel else title
        }

    @Composable
    private fun resultBody(item: ResultItem): String {
        if (item.urls.size > 1) {
            return stringResource(R.string.result_link_count, item.urls.size)
        }
        return item.text
    }

    @Composable
    private fun resultActionLabel(item: ResultItem): String = when (item.action) {
        ResultPrimaryAction.COPY -> stringResource(R.string.result_copy)
        ResultPrimaryAction.SHARE -> stringResource(R.string.result_share)
        ResultPrimaryAction.OPEN -> item.targetLabel?.let {
            stringResource(R.string.result_open_target, it)
        } ?: stringResource(R.string.result_open)
        // 动作输出：按规则名当按钮标题（用户配的动作自己起过名字）。
        ResultPrimaryAction.RUN_ACTION -> item.title
            .ifBlank { stringResource(R.string.result_generic) }
    }

    @Composable
    private fun actionDescription(action: ResultAction): String = when (action) {
        ResultAction.OPEN -> stringResource(R.string.result_open)
        ResultAction.COPY -> stringResource(R.string.result_copy)
        ResultAction.SHARE -> stringResource(R.string.result_share)
        ResultAction.IGNORE -> stringResource(R.string.result_ignore)
        ResultAction.CLOSE -> stringResource(R.string.result_close)
    }

    private fun actionIcon(action: ResultAction) = when (action) {
        ResultAction.OPEN -> Icons.AutoMirrored.Filled.OpenInNew
        ResultAction.COPY -> Icons.Default.ContentCopy
        ResultAction.SHARE -> Icons.Default.Share
        ResultAction.IGNORE -> Icons.Default.Block
        ResultAction.CLOSE -> Icons.Default.Close
    }

    /**
     * 行菜单：只翻译识别器给出的 [RecognitionAction]，不再自己判输出类型。
     *
     * 顺序固定（主张作在前 → 复制 → 分享 → 打开 → 忽略 → 关闭），与识屏工具条的建议槽同源。
     */
    private fun menuActions(item: ResultItem): List<ResultAction> {
        val derived = item.recognized?.actions.orEmpty()
        val actions = mutableListOf<ResultAction>()
        // 主张作（点卡片正面执行的那个）永远排第一，用户已经习惯了它的位置。
        val primary = item.primaryAction().takeIf { it != ResultAction.IGNORE }
        when (primary) {
            ResultAction.OPEN -> if (item.urls.isNotEmpty()) actions += ResultAction.OPEN
            ResultAction.SHARE -> actions += ResultAction.SHARE
            else -> Unit
        }
        if (RecognitionAction.Copy in derived && ResultAction.COPY !in actions) {
            actions += ResultAction.COPY
        }
        if (RecognitionAction.Share in derived && ResultAction.SHARE !in actions) {
            actions += ResultAction.SHARE
        }
        if (RecognitionAction.Open in derived && ResultAction.OPEN !in actions) {
            actions += ResultAction.OPEN
        }
        if (ResultAction.COPY !in actions) actions += ResultAction.COPY
        actions += ResultAction.IGNORE
        actions += ResultAction.CLOSE
        return actions
    }

    @Composable
    private fun ResultIcon(
        item: ResultItem,
        contentColor: Color,
        size: androidx.compose.ui.unit.Dp,
    ) {
        val target = item.targetPackage
        if (target != null) {
            AppIcon(packageName = target, tint = contentColor, size = size)
        } else {
            Icon(
                imageVector = when {
                    item.fromCapture -> Icons.Default.Lock
                    item.urls.isNotEmpty() -> Icons.Default.Link
                    else -> Icons.Default.ContentCopy
                },
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.9f),
                modifier = Modifier.size(size),
            )
        }
    }

    @Composable
    private fun AppIcon(
        packageName: String,
        tint: Color,
        size: androidx.compose.ui.unit.Dp,
    ) {
        val sizePx = DensityProvider.dp2px(22)
        val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
            val cached = SearchIconCache.get(packageName, sizePx)
            if (cached != null) {
                value = cached
            } else {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        SearchIconCache.ensureAll(
                            context,
                            listOf(SearchIconCache.Key(packageName)),
                            sizePx,
                        )
                        SearchIconCache.get(packageName, sizePx)
                    }.getOrNull()
                }
            }
        }
        val loaded = icon
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = tint.copy(alpha = 0.85f),
                modifier = Modifier.size(size),
            )
        }
    }

    private sealed interface HitTarget {
        /** 行外空白：点按不触发动作（拖动仍整窗有效）。 */
        data object None : HitTarget
        data object Body : HitTarget
        data object Action : HitTarget
        data class Menu(val index: Int) : HitTarget
    }

    private enum class DragAxis { NONE, VERTICAL, HORIZONTAL }

    // ---- TEMP probe：展开 / 收起位移排查（定位后整块删除）----

    private var probeSerial = 0

    /** 逐帧采样视图变换 + 窗口 rect，覆盖一次 morph（默认 60 帧 ≈ 1s）。 */
    /**
     * 逐帧探针：**有意抽稀**。日志落盘是单线程有界队列（2048 条，满了丢最旧），
     * 每帧一行会把同期的 `PROBE slot=` 业务日志挤掉 —— 之前排查时那句“谁把胶囊收起了”
     * 就是这样消失的。现在只在前几帧与关键节点打点。
     */
    private fun probe(label: String, frames: Int = 60) {
        val start = view ?: return
        val id = ++probeSerial
        val startedAt = SystemClock.uptimeMillis()
        Log.d(LOG_TAG, "PROBE#$id $label start | ${snapshot()}")
        var index = 0
        val step = object : Runnable {
            override fun run() {
                val live = view
                if (live == null || index > frames) {
                    Log.d(LOG_TAG, "PROBE#$id $label stop | ${snapshot()}")
                    return
                }
                val interesting = index <= 2 || index % 10 == 0
                if (interesting) {
                    Log.d(
                        LOG_TAG,
                        "PROBE#$id $label f=$index t=${SystemClock.uptimeMillis() - startedAt}" +
                            " | ${snapshot()}",
                    )
                }
                index++
                live.postOnAnimation(this)
            }
        }
        start.postOnAnimation(step)
    }

    private fun snapshot(): String {
        val v = view ?: return "view=null"
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val lp = v.layoutParams as? WindowManager.LayoutParams
        return "vis=${v.visibility}" +
            " v=${v.width}x${v.height}@(${loc[0]},${loc[1]})" +
            " lp=${lp?.width}x${lp?.height}@(${lp?.x},${lp?.y})" +
            " alpha=${v.alpha}" +
            " row=$rowHeightPx capW=$capsuleWidthPx capH=$windowHeightPx" +
            " glow=$glowMarginPx center=$centerMarginPx sideRight=$sideRight" +
            " origin=$lpOriginY phase=$morphPhase settled=$widthSettled" +
            " swipe=$swipeBehavior/$swipeTowardEdge press=${v.scaleX}" +
            " transition=$transition enterPending=$enterPending frozen=${frozenAnchor != null}" +
            " items=${items.size}"
    }

    private companion object {
        const val TAG = LOG_TAG


        /** 收起圆角标：正圆直径（固定，不随数字变宽——数字只显示 1–9）。 */
        val BadgeDiameter = 16.dp

        /** 键盘让位缓动时长：足够平滑，又不拖泥带水。 */
        const val IME_LIFT_ANIM_MS = 220L

        const val EnterFallbackMs = 260L
        const val MaxOriginDp = 200f

        /**
         * 形变面的淡出区间起点（相位）：之后由行自身的底色 / 描边接管。
         * 单行时两者矩形重合、看不出交接；多行时残影在本区间内澄出。
         */
        const val SurfaceFadeStart = 0.55f

        /** 行内容的淡入区间起点（相位）：前面的相位里只有形变面在长。 */
        const val ContentFadeStart = 0.3f

        /** 球面淡出结束相位：图标没有同步形变对象，头段先隐，形状交给形变面。 */
        const val BallFadeEnd = 0.35f

        /** 堆叠模式单行高上限的历史常量已废除：行高就是收起圆直径，不再夹取。 */

        /** 滚动松手后的惯性投影时长（秒）：速度 × 时长 ≈ 还会滑多远。 */
        const val FlingProjectionSec = 0.12f

        /** 滚动栈：行内横向留白、胶囊间距、右缘滚动条与隐藏延时。 */
        val RowHorizontalPadding = 10.dp
        const val RowGapDp = 6f
        val ScrollThumbWidth = 2.dp
        const val ScrollThumbInsetDp = 4f
        const val ScrollThumbMinDp = 16f
        const val ScrollThumbAlpha = 0.3f
        const val ScrollThumbHideMs = 600L

        const val MaxWidthDp = 180f
        const val WidthRatio = 0.48f

        /**
         * 通知行宽上限（整行含图标/内距；内容宽超上限时文本列封顶、正文自动滚动）。
         * dp 主导：横竖屏 / 平板都封在同一条绝对宽度上，比例只作窄屏（< 屏宽/比例处）兜底防溢出。
         */
        const val MaxWidthCapDp = 250f

        /** 窄屏兜底比例：屏宽 × 该值小于 [MaxWidthCapDp] 时（小屏竖屏）按它封顶。 */
        const val NarrowWidthRatio = 0.6f

        /** 正文 marquee 滚动速度（px/s）：位移 = 距离/速度 得动画时长。 */
        const val MarqueePxPerSec = 40f

        const val ActionHitDp = 72f
        const val CollapseThresholdDp = 48f
        const val CollapseFollowRatio = 0.22f
        const val FlingVelocityPxPerSec = 900f
        const val BubbleBackgroundAlpha = 0.88f

        /** 常驻胶囊描边的基线透明度：本体脉冲在其上叠加呼吸增量。 */
        const val RowBorderAlpha = 0.22f

        /** 辉光余量（dp）：脉冲 / 流光描边越出轮廓的空间，上下各留；贴屏侧不留。 */
        const val GlowMarginDp = 12f

        /**
         * 中侧余量（dp）：只留辉光出血。窗口宽 = 内容宽 + 这个值，紧贴内容——
         * 行位移在内容区内就被裁掉，窗口边缘用不到 [DragRubberCapDp]，留了只会多出
         * 一片空的、可触摸的窗口区（点空白也触发胶囊）。
         */
        const val CenterMarginDp = 16f

        /** 反向横滑的视觉上限（dp）：行位移橡皮筋钳到这个值（行在内容区内被裁，不撑窗口）。 */
        const val DragRubberCapDp = 72f

        /** 橡皮筋阻尼：超过收起阈值后的位移按此比例递减跟进。 */
        const val DragRubberDamping = 0.35f
    }

    /**
     * 反向横滑的视觉位移：阈值内 1:1 跟手，阈值后按 [DragRubberDamping] 递减，
     * 钳在 [DragRubberCapDp] 内——只钳行位移，不撑窗口（行在内容区内被裁）。
     * 手势判定（收起 / 滑掉的阈值派发）仍用原始位移，不受钳制影响。
     */
    private fun rubberBandedRowDx(raw: Float): Float {
        val distance = abs(raw)
        val threshold = collapseThresholdPx
        if (distance <= threshold) return raw
        val cap = DensityProvider.dp2px(DragRubberCapDp).toFloat()
        val excess = (distance - threshold) * DragRubberDamping
        return Math.signum(raw) * min(threshold + excess, cap)
    }
}

/** 收件箱条目逐条比较（都是 data class）：内容相等就不必通知快照。 */
private fun sameItems(a: List<BallItem>, b: List<BallItem>): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (a[i] != b[i]) return false
    }
    return true
}

/**
 * 行手势行为集（窗口侧的消费视图）：slot 从两颗胶囊各自的设置归一化后推送。
 * 默认值 = 出厂行为（长按无 / 背球滑忽略该行 / 向球滑收起）。
 */
internal data class CapsuleRowGestures(
    val longPress: CapsuleGestureBehavior = CapsuleGestureBehavior.NONE,
    val swipeAway: CapsuleGestureBehavior = CapsuleGestureBehavior.IGNORE_ROW,
    val swipeToward: CapsuleGestureBehavior = CapsuleGestureBehavior.COLLAPSE,
)

/** 结果胶囊的动作。 */
internal enum class ResultAction {
    OPEN,
    COPY,
    SHARE,
    IGNORE,
    CLOSE,
}

/** 结果项的主动作：由规则动作决定；创建阶段已把无链接的 OPEN 降级为 COPY。 */
internal fun ResultItem.primaryAction(): ResultAction = when (action) {
    ResultPrimaryAction.COPY -> ResultAction.COPY
    ResultPrimaryAction.OPEN -> ResultAction.OPEN
    ResultPrimaryAction.SHARE -> ResultAction.SHARE
    // 动作输出复用 OPEN 的点击通路：具体跑哪个动作由执行口按 ruleId 取规则决定。
    ResultPrimaryAction.RUN_ACTION -> ResultAction.OPEN
}