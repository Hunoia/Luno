package hunoia.luno.runtime.floatball

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import hunoia.luno.core.LunoLog as Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewPropertyAnimator
import android.view.WindowInsets
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import java.io.File
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.CapsuleGestureBehavior
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.config.model.NotificationAlertEffect
import hunoia.luno.core.LOG_TAG
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.alert.AlertPlayPlan
import hunoia.luno.runtime.floatball.alert.PulseSpec
import hunoia.luno.runtime.floatball.alert.PulseTuning
import hunoia.luno.runtime.floatball.alert.ShimmerSpec
import hunoia.luno.runtime.floatball.alert.ShimmerTuning
import hunoia.luno.runtime.floatball.alert.easedOut
import hunoia.luno.runtime.floatball.alert.lerpInRange
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 球 + 胶囊合并窗口：常态 = 内容球球面，展开 = 胶囊内容栈，同一个 overlay 窗口、
 * 同一份坐标（posX/posY/imeLift/lpOriginY/sideRight/barSafeArea）、同一个状态机
 * （[VisualState.BALL] ↔ [VisualState.CAPSULE]）。
 *
 * 合并根治两处「两窗口分离」的同步缺口：
 * - 展开动画方向（系统窗口过渡 vs 自身形变）：窗口常驻，展开只切内容/尺寸，不再 addView。
 * - 键盘避让（跨窗锚点/抬升不同步）：胶囊几何直接由本窗口自己的 posY/imeLift 推导。
 *
 * 内容：
 * - 通知胶囊展示未命中的普通通知；结果胶囊展示分发规则的胶囊档结果。
 * - 每条一颗独立胶囊，同屏行数按各自设置，超出竖向滚动；行宽贴内容、靠球一侧齐平。
 * - 横滑两方向与行长按执行可配的内置行为（[CapsuleRowGestures]，默认 = 出厂行为：
 *   朝球收起 / 反向忽略该行 / 结果行长按弹操作菜单），点按 = 执行主动作。
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

    /** 视觉状态机：BALL = 内容球常态，CAPSULE = 胶囊展开。 */
    internal enum class VisualState { BALL, CAPSULE }

    /** 当前视觉状态。Compose 内容切换据此渲染；窗口尺寸/坐标随之切换。 */
    private var state by mutableStateOf(VisualState.BALL)

    /** 是否处于胶囊态（状态机镜像，供手势/定位分流）。 */
    private val isCapsule: Boolean get() = state == VisualState.CAPSULE

    // ---- 统一坐标（两态共用，消除跨窗口锚点/抬升同步缺口） ----

    /** 球顶屏幕坐标（px）。BALL 态窗口顶边；CAPSULE 态胶囊参考原点。 */
    private var posX = 0f
    private var posY = 0f

    /** 键盘抬升量（px）。显示位置 = 基准位 − 抬升量。两态共用。 */
    private var imeLiftPx = 0f

    /** 抬升缓动动画：键盘起落平滑跟随，避免瞬跳。 */
    private var imeAnimator: ValueAnimator? = null

    /** 最近一次上报的键盘底边（px）。拖动中如收起键盘，拖完结算按此值归零。 */
    private var imeBottomPx = 0

    /** 系统栏安全区（屏幕绝对坐标，只约束上下）。取不到时沿用上一次。 */
    private var barSafeArea = SystemBarsSafeArea()

    /**
     * LP 坐标系原点在屏幕上的 y。带 LAYOUT_IN_SCREEN 后应为 0；个别 ROM 仍以
     * 状态栏下沿为原点，保留实测兜底。两态共用同一份原点（单窗口，无跨窗偏差）。
     */
    private var lpOriginY = 0

    /** 当前朝向：决定读 portrait 还是 landscape 落点。快照状态：转屏要触发胶囊行宽按新屏宽重推。 */
    private var landscape by mutableStateOf(false)

    /** 球面尺寸（px）：BALL 态窗口边长，也是 [collapsedScale] 形变基准与胶囊行高的锚点。 */
    private var ballPx = 0

    // ---- 球态（从 FloatBallWindow 移植，与胶囊共用同一份窗口/坐标） ----

    /** 球面固定图标：ImageVector / TextIcon / ImageBitmap（运行期可替换，见 [setFixedIcon]）。 */
    private var fixedIconState by mutableStateOf(fixedIcon)

    /** 静置半出屏 / 变暗态。Compose 快照状态：球面里的静置暂停动图要跟着它重组。 */
    private var idled by mutableStateOf(false)

    /** 气泡（胶囊）在屏期间抑制静置变暗/半出屏，避免锚点与球之间留白。 */
    private var idleSuppressed = false

    /** 拖动中球心进入判定区（球面高亮提示），松手即清零。 */
    private var dropHint by mutableStateOf(false)

    private var dragging = false

    /** 自动挪球的「淡出→传送」是否在飞：期间拒绝新的传送，避免两段 endAction 抢写位置。 */
    private var transferring = false

    /** 在飞的换边传送目标与回调：淡出被隐藏/回场打断时改为立即结算，不再丢传送。 */
    private var pendingTransferX = 0f
    private var pendingTransferY = 0f
    private var pendingTransferLanded: (() -> Unit)? = null

    /** 隐藏路径置位：抑制 arbiter.cancel 经 onPressChanged 触发的 applyVisual。 */
    private var visualSuppressed = false

    /** 软隐藏 / 锁屏 / 胶囊态：整体不可见不可点。 */
    private var visuallyHidden = false
    private var lockHidden = false
    private var panelHidden = false

    /** 隐形结算后尚未落窗：回场动画置 alpha=0 后消费。 */
    private var transferPendingLayout = false

    private var idleRunnable: Runnable? = null
    private var moveAnimator: ValueAnimator? = null

    private val handler = Handler(Looper.getMainLooper())

    private val screenWidthPx: Int get() = DensityProvider.screenWidthPx
    private val screenHeightPx: Int get() = DensityProvider.screenHeightPx

    /** insets 变化后的合并复测：先测 LP 原点，再读安全区，最后只写一次窗口。 */
    private val remeasureSystemInsets = Runnable {
        postMeasureLpOriginY({ view }) { measured ->
            lpOriginY = measured
            applySystemInsets()
        }
    }

    // ---- 球态回调 ----

    /** 抬手点按（未超 touchSlop）。 */
    var onTap: (() -> Unit)? = null

    /** 长按（按住不动到 `FloatBallSettings.longPressMs` 且未移动；先动 = 拖）。 */
    var onLongPress: (() -> Unit)? = null

    /** 开始拖动：服务侧据此立即收起气泡，且本轮不再自动重开。 */
    var onDragStarted: (() -> Unit)? = null

    /** 拖动松手，带归一化 (-[(顶边 x)/W, (顶边 y)/H])。 */
    var onDragFinished: ((Float, Float) -> Unit)? = null

    /** 拖动松手时球心落在判定区（仅配置了 `dropAction` 才可能为真）。 */
    var onDropZoneReleased: ((centerX: Int, centerY: Int) -> Unit)? = null

    /** 球触摸仲裁：BALL 态拖动 / 点按 / 长按。 */
    private val ballArbiter = FloatBallTouchArbiter(
        onSlideDragged = { dx, dy -> onBallDragged(dx, dy) },
        onTapped = { onTap?.invoke() },
        onPressChanged = { pressed ->
            this.pressed = pressed
            if (!pressed) scheduleIdle()
            if (!visualSuppressed) applyVisual()
        },
        onDragEnded = { onBallDragEnded() },
        onLongPress = {
            view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        },
        longPressMs = {
            settings.longPressMs.coerceIn(
                hunoia.luno.config.model.FloatBallDefaults.LongPressMsMin,
                hunoia.luno.config.model.FloatBallDefaults.LongPressMsMax,
            )
        },
    )

    // ---- 球态行为（移植自 FloatBallWindow） ----

    /** 运行期替换球面图标；通知球用来显示最新通知的应用图标。 */
    fun setFixedIcon(icon: Any?) {
        if (fixedIconState === icon || fixedIconState == icon) return
        fixedIconState = icon
    }

    /** 球在屏左半（按窗口实位、球心判，与静置外移方向同口径）。 */
    fun isOnLeft(): Boolean = BallPlacement.isOnLeft(posX, screenWidthPx, ballPx)

    /** 该朝向落盘落点对应的贴边 x（侧别按目标朝向自己的归一值判）。 */
    private fun savedEdgeX(landscape: Boolean): Float =
        BallPlacement.edgeX(settings.position(landscape).x, screenWidthPx, ballPx)

    /** 球顶屏幕坐标（px）：跟手挪球 / 胶囊锚点由同一份坐标推导。 */
    fun currentPosition(): Pair<Float, Float> = posX to posY

    /**
     * 球当前可见矩形（屏幕坐标）。静置半出屏时用平移后的实际可见边，
     * 胶囊据此贴边，不会飘到球和屏幕之间。
     */
    internal fun visibleRectOnScreen(): BubbleAnchor? {
        if (view == null) return null
        val displayY = max(lpOriginY.toFloat(), posY - imeLiftPx)
        val shift = if (idled) idleShiftPx() else 0f
        val left = (posX + shift).roundToInt()
        val top = displayY.roundToInt()
        return BubbleAnchor(left, top, left + ballPx, top + ballPx)
    }

    /**
     * 胶囊锚点由本窗口自己的球基准坐标推导（不再跨窗口握手）：
     * 顶边 = posY − imeLift（含键盘让位），左右按贴边侧 + 静置外移。
     */
    private fun capsuleAnchor(): BubbleAnchor {
        val displayY = max(lpOriginY.toFloat(), posY - imeLiftPx)
        val shift = if (idled) idleShiftPx() else 0f
        val left = (posX + shift).roundToInt()
        val top = displayY.roundToInt()
        return BubbleAnchor(left, top, left + ballPx, top + ballPx)
    }

    // ---- 球态设置 / 朝向 ----

    /** 合并窗口设置：ball = 球侧（尺寸/静置/拖拽/球面），capsule = 胶囊侧（行色/行数/手势）。 */
    fun updateSettings(ball: FloatBallSettings, capsule: FloatBallSettings) {
        updateBallSettings(ball)
        updateCapsuleSettings(capsule)
    }

    private fun updateBallSettings(next: FloatBallSettings) {
        val sizeChanged = next.sizeDp != settings.sizeDp
        settings = next
        if (!sizeChanged) return
        val nextPx = sizePx(next)
        if (nextPx == ballPx) return
        if (view == null) {
            ballPx = nextPx
            return
        }
        // 胶囊展开中不拆窗（拆窗会把胶囊打断并静默切回球态）：只更新尺寸基准，
        // 让锚点/行高按新球径重算；收起后下次挂窗自然按新尺寸重建。
        if (isCapsule) {
            ballPx = nextPx
            updateCapsuleAnchor()
            return
        }
        // 面球尺寸由 Compose 组合驱动，而服务进程托管的 ComposeView 重组不上屏：
        // 改尺寸只能拆窗重挂（新窗首组合直接按新球径画）。落点由 attachBall 按
        // 持久化坐标重摆，和窗口尺寸同一轮写入，不再“先挂旧位置再搬回去”。
        detachNow()
        ballPx = nextPx
        attachAnimated()
    }

    /** 转屏：换用该朝向记忆的落点。返回是否真的换了朝向。 */
    fun setOrientation(landscape: Boolean): Boolean {
        if (this.landscape == landscape) return false
        this.landscape = landscape
        if (view == null) return true
        DensityProvider.invalidateMetrics()
        barSafeArea = readBarSafeArea() ?: barSafeArea
        cancelSettle()
        posX = savedEdgeX(landscape)
        posY = settings.position(landscape).y * screenHeightPx
        applyWindowBounds()
        realignVisualAfterReposition()
        view?.post { remeasureLpOriginY() }
        return true
    }

    // ---- 球态窗口生命周期 ----

    /** 球态挂窗（BALL 态）：建窗并按当前状态渲染。false = 没建起来（未授权 / 系统拒绝）。 */
    fun attachBall(): Boolean {
        if (view != null) {
            // 已建（胶囊复用）：切回球态并重摆姿态。
            enterBallState()
            return true
        }
        if (ballPx <= 0) ballPx = sizePx(settings)
        if (!ensureWindowRoot()) return false
        // 落点先于任何定位写入：posX/posY 是两态共用坐标，默认 0 会把球放到左上角。
        // 与主球窗（FloatBallWindow.attach）同口径，读当前朝向的持久化落点。
        posX = savedEdgeX(landscape)
        posY = settings.position(landscape).y * screenHeightPx
        state = VisualState.BALL
        visuallyHidden = false
        applyBallTouchFlags(view!!, wm, hidden = false)
        view!!.visibility = View.VISIBLE
        view!!.alpha = 1f
        view!!.scaleX = 1f
        view!!.scaleY = 1f
        applyWindowBounds()
        return true
    }

    /** 球态挂窗并原地淡入弹回（面板期间被移除后的补回场）。 */
    fun attachAnimated(): Boolean {
        if (!attachBall()) return false
        view?.let(::animateRestoreIn)
        return true
    }

    /** 锁屏 / 息屏：整个窗口不可见也不可点，解锁恢复。 */
    fun setHidden(hidden: Boolean) {
        lockHidden = hidden
        applyHiddenFlags()
    }

    /** 面板/临时隐藏：软隐藏 —— 窗口保留，只置不可见不可点。 */
    fun setPanelHidden(hidden: Boolean) {
        panelHidden = hidden
        applyHiddenFlags()
    }

    /** 徽标：只在球态（胶囊收起）时显示；[badge] 为 null / 不可见时清空。 */
    internal fun setBadge(badge: BadgeState?) {
        lastBadge = badge
        renderBadge()
    }

    private fun renderBadge() {
        val tv = badgeView ?: return
        // 胶囊态（含收起动画中）不画徽标：它是球面部件，露在胶囊上会压住行。
        val badge = lastBadge.takeUnless { isCapsule }
        if (badge == null || !badge.visible) {
            tv.visibility = View.GONE
            view?.contentDescription =
                idleDescription ?: context.getString(R.string.smart_ball_content_desc)
            return
        }
        val label = when {
            badge.showPinOnly -> "📌"
            else -> badge.countLabel?.let { if (it.length > 2) "99+" else it } ?: "📌"
        }
        tv.text = label
        tv.visibility = View.VISIBLE
        val total = badge.unpinnedCount + badge.pinnedCount
        val description = when {
            badgeDescription != null -> badgeDescription.invoke(total)
            badge.showPinOnly -> context.getString(R.string.smart_ball_pinned_only)
            else -> context.getString(R.string.smart_ball_unread, total)
        }
        view?.contentDescription = description
        updateBadgeGravity()
    }

    // ---- 球态键盘让位 ----

    /** 键盘弹出时上抬，保证「球底 + 边距 + 额外抬升」不压输入栏；拖动期间只记账。 */
    fun updateIme(newImeBottomPx: Int) {
        if (newImeBottomPx == imeBottomPx) return
        this.imeBottomPx = newImeBottomPx
        if (dragging) return
        animateImeLift(computeImeLift(newImeBottomPx).toFloat())
    }

    /** 抬升缓动：从当前值到目标值，逐帧落窗口（两态几何都跟随同一份 imeLift）。 */
    private fun animateImeLift(target: Float) {
        imeAnimator?.cancel()
        imeAnimator = null
        if (kotlin.math.abs(imeLiftPx - target) < 0.5f) {
            imeLiftPx = target
            applyWindowBounds()
            return
        }
        imeAnimator = ValueAnimator.ofFloat(imeLiftPx, target).apply {
            duration = IME_LIFT_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (imeAnimator !== anim) return@addUpdateListener
                imeLiftPx = anim.animatedValue as Float
                // 胶囊态的锚点由 imeLiftPx 现推（applyCapsuleGeometry），逐帧跟随键盘。
                applyWindowBounds()
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (imeAnimator === a) imeAnimator = null
                }
                override fun onAnimationStart(a: android.animation.Animator) {}
                override fun onAnimationCancel(a: android.animation.Animator) {
                    if (imeAnimator === a) imeAnimator = null
                }
                override fun onAnimationRepeat(a: android.animation.Animator) {}
            })
            start()
        }
    }

    private fun cancelImeAnim() {
        imeAnimator?.cancel()
        imeAnimator = null
    }

    private fun computeImeLift(imeBottomPx: Int): Int {
        if (imeBottomPx <= 0) return 0
        val marginPx = DensityProvider.dp2px(FloatBallDefaults.ImeMarginDp + FloatBallDefaults.ImeExtraLiftDp)
        // 胶囊态要让**整个胶囊**的底边都在键盘之上（多行时比球高得多）；
        // 球态按球底算。两态共用一个 imeLift，几何在同一份坐标里推。
        val contentBottom = posY + (if (isCapsule) windowHeightPx else ballPx)
        val overflow = contentBottom.roundToInt() + marginPx - (screenHeightPx - imeBottomPx)
        val minY = BallPlacement.bounds(
            screenWidthPx,
            screenHeightPx,
            ballPx,
            barSafeArea,
            lpOriginY,
        ).minY.roundToInt()
        return max(0, overflow).coerceAtMost(max(0, (posY).roundToInt() - minY))
    }

    // ---- 球态触摸 → 定位 ----

    private fun onBallDragged(dx: Float, dy: Float) {
        if (!dragging) {
            dragging = true
            onDragStarted?.invoke()
            cancelSettle()
            cancelImeAnim()
            cancelIdle()
        }
        posX += dx
        posY += dy
        applyWindowBounds()
        updateDropHint()
    }

    private fun onBallDragEnded() {
        if (!dragging) return
        dragging = false
        finishBallDrag()
    }

    private fun finishBallDrag() {
        imeLiftPx = computeImeLift(imeBottomPx).toFloat()
        applyWindowBounds()
        if (dropHint) {
            onDropZoneReleased?.invoke(
                (posX + ballPx / 2f).roundToInt(),
                (posY + ballPx / 2f).roundToInt(),
            )
        }
        dropHint = false
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            val targetX = BallPlacement.edgeX(isOnLeft(), screenWidthPx, ballPx)
            settleToEdge(targetX)
            onDragFinished?.invoke(targetX / screenWidthPx, posY / screenHeightPx)
        }
        scheduleIdle()
    }

    private fun updateDropHint() {
        val inZone = settings.dropAction != null && isBallInDropZone(
            centerX = (posX + ballPx / 2f).roundToInt(),
            centerY = (posY + ballPx / 2f).roundToInt(),
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            zone = settings.dropZone(landscape),
        )
        if (dropHint != inZone) dropHint = inZone
    }

    // ---- 球态松手贴边（缓动） ----

    private fun cancelSettle() {
        settleAnimator?.cancel()
        settleAnimator = null
    }

    private fun settleToEdge(targetX: Float) {
        val fromX = posX
        if (kotlin.math.abs(targetX - fromX) < 0.5f) return
        cancelSettle()
        settleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionSettle.toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                posX = fromX + (targetX - fromX) * t
                applyWindowBounds()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (settleAnimator === animation) settleAnimator = null
                    posX = targetX
                    applyWindowBounds()
                }
            })
            start()
        }
    }

    // ---- 球态自动挪球 ----

    fun repositionTo(leftEdge: Boolean, yRatio: Float, onLanded: (() -> Unit)? = null): Boolean {
        if (dragging || transferring || pressed) return false
        val target = view ?: return false
        val bounds = BallPlacement.bounds(screenWidthPx, screenHeightPx, ballPx, barSafeArea, lpOriginY)
        val targetX = BallPlacement.edgeX(leftEdge, screenWidthPx, ballPx)
        val targetY = (yRatio * screenHeightPx).coerceIn(bounds.minY, bounds.maxY)
        if (kotlin.math.abs(targetX - posX) < 0.5f && kotlin.math.abs(targetY - posY) < 0.5f) {
            scheduleIdle()
            onLanded?.invoke()
            return true
        }
        cancelSettle()
        cancelIdleTimer()
        moveAnimator?.cancel()
        transferring = true
        pendingTransferX = targetX
        pendingTransferY = targetY
        pendingTransferLanded = onLanded
        visualAnimator?.cancel()
        target.animate()
            .alpha(0f)
            .setDuration(motionMillis(MotionFamily.Floating).toLong())
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                transferring = false
                pendingTransferLanded = null
                if (dragging) {
                    applyVisual()
                    return@withEndAction
                }
                settleTransfer()
            }
            .start()
        return true
    }

    private fun settleTransfer() {
        posX = pendingTransferX
        posY = pendingTransferY
        view?.translationX = 0f
        applyWindowBounds()
        applyVisual()
        scheduleIdle()
        pendingTransferLanded?.invoke()
        pendingTransferLanded = null
    }

    private fun settlePendingTransferNow() {
        if (pendingTransferLanded == null) return
        visualAnimator?.cancel()
        transferring = false
        posX = pendingTransferX
        posY = pendingTransferY
        val landed = pendingTransferLanded
        pendingTransferLanded = null
        transferPendingLayout = true
        landed?.invoke()
    }

    // ---- 球态静置 ----

    private fun scheduleIdle() {
        idleRunnable?.let(handler::removeCallbacks)
        idleRunnable = null
        if (idleSuppressed || isCapsule) {
            // 胶囊态球面不画：只清标志，不动窗口姿态（否则会打断展开/收起形变）。
            if (idled) idled = false
            return
        }
        val delay = settings.idleDimDelayMs
        if (delay <= 0L) {
            if (idled) {
                idled = false
                applyVisual()
            }
            return
        }
        val runnable = Runnable {
            idled = true
            applyVisual()
        }
        idleRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    private fun cancelIdleTimer() {
        idleRunnable?.let(handler::removeCallbacks)
        idleRunnable = null
    }

    private fun cancelIdle() {
        cancelIdleTimer()
        if (!idled) return
        idled = false
        // 胶囊态的窗口姿态属于展开/收起动效，这里只清静置标志。
        if (!isCapsule) applyVisual()
    }

    /** 气泡在屏期间保持球的完整姿态（不采用半出屏）。 */
    fun setIdleSuppressed(suppressed: Boolean) {
        if (idleSuppressed == suppressed) return
        idleSuppressed = suppressed
        if (suppressed) cancelIdle() else scheduleIdle()
    }

    /** 胶囊展开/收起：只控球态静置计时（状态切换由 [showCapsule]/[hideCapsule] 驱动）。 */
    fun setCapsuleActive(active: Boolean) {
        if (active) cancelIdle() else scheduleIdle()
    }

    // ---- 球态外观（View 层） ----

    private fun idleShiftPx(): Float =
        BallPlacement.idleShiftPx(isOnLeft(), ballPx, FloatBallDefaults.IdleOutRatio)

    private fun visualAlpha(): Float = when {
        // 胶囊态不画球面（WindowContent 只画胶囊栈），窗口 alpha 由展开/收起动效控制，
        // 不能在这里归 0——否则一次球态刷新会把整颗胶囊一起淡掉。
        visuallyHidden -> 0f
        idled -> FloatBallDefaults.IdleDimAlpha
        else -> 1f
    }

    private var visualAnimator: ViewPropertyAnimator? = null

    private fun applyVisual() {
        val target = view ?: return
        transferring = false
        val scale = if (pressed) FloatBallDefaults.PressScale else 1f
        val alpha = visualAlpha()
        val shiftPx = if (idled) idleShiftPx() else 0f
        visualAnimator?.cancel()
        visualAnimator = target.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(alpha)
            .translationX(shiftPx)
            .setDuration(if (pressed) MotionPress.toLong() else MotionSettle.toLong())
            .setInterpolator(DecelerateInterpolator())
    }

    private fun realignVisualAfterReposition() {
        if (transferring || visualSuppressed) return
        val target = view ?: return
        visualAnimator?.cancel()
        visualAnimator = null
        val scale = if (pressed) FloatBallDefaults.PressScale else 1f
        target.scaleX = scale
        target.scaleY = scale
        target.alpha = visualAlpha()
        target.translationX = if (idled) idleShiftPx() else 0f
    }

    private fun applyHiddenFlags() {
        val current = view ?: return
        // 只认锁屏 / 面板这两个显式隐藏源。胶囊态本身不是“窗口隐藏”——球面在胶囊态
        // 不画即可；把它算进来会让 refreshWindows 的 setPanelHidden(false) 把展开的胶囊按掉。
        val hidden = lockHidden || panelHidden
        if (hidden == visuallyHidden) return
        visuallyHidden = hidden
        if (hidden) {
            visualSuppressed = true
            ballArbiter.cancel()
            cancelIdleTimer()
            visualSuppressed = false
            settlePendingTransferNow()
            applyBallTouchFlags(current, wm, hidden = true)
            current.animate()
                .alpha(0f)
                .setDuration(motionMillis(MotionFamily.Floating).toLong())
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { settleBallWindowAlpha(current, wm) }
                .start()
        } else {
            current.animate().cancel()
            applyBallTouchFlags(current, wm, hidden = false)
            animateRestoreIn(current)
            scheduleIdle()
        }
    }

    private fun animateRestoreIn(target: View) {
        visualAnimator?.cancel()
        settlePendingTransferNow()
        target.alpha = 0f
        target.scaleX = 0.85f
        target.scaleY = 0.85f
        if (transferPendingLayout) {
            transferPendingLayout = false
            target.translationX = if (idled) idleShiftPx() else 0f
            applyWindowBounds()
        }
        visualAnimator = target.animate()
            .alpha(if (idled) FloatBallDefaults.IdleDimAlpha else 1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(motionMillis(MotionFamily.Floating).toLong())
            .setInterpolator(OvershootInterpolator(1.2f))
    }

    // ---- 球态窗口定位 ----

    /** 实测 LP 原点（布局原点随状态栏显隐 / 转屏变化），测完回写并重夹一次。 */
    private fun remeasureLpOriginY() {
        postMeasureLpOriginY({ view }) { measured ->
            lpOriginY = measured
            applyWindowBounds()
        }
    }

    /** 安全区复测：`currentWindowMetrics` 的 display 绝对坐标。 */
    private fun applySystemInsets() {
        updateBarSafeArea(readBarSafeArea() ?: return)
    }

    private fun readBarSafeArea(): SystemBarsSafeArea? {
        val next = wm.systemBarsSafeAreaOrNull()
        if (next == null) Log.w(TAG, "system bar insets unavailable")
        return next
    }

    private fun updateBarSafeArea(next: SystemBarsSafeArea) {
        if (next == barSafeArea) return
        barSafeArea = next
        applyWindowBounds()
    }

    /** 统一夹取 + 写 LP：按状态路由（BALL = 球方窗，CAPSULE = 胶囊窗）。 */
    private fun applyWindowBounds() {
        val current = view ?: return
        if (isCapsule) {
            applyCapsuleGeometry(current)
        } else {
            applyBallBounds(current)
        }
    }

    /** BALL 态：球方窗，屏幕位置 = posX / (posY − imeLift)，LP y = 屏幕值 − lpOriginY。 */
    private fun applyBallBounds(current: View) {
        if (ballPx <= 0) return
        val bounds = BallPlacement.bounds(screenWidthPx, screenHeightPx, ballPx, barSafeArea, lpOriginY)
        posX = posX.coerceIn(bounds.minX, bounds.maxX)
        posY = posY.coerceIn(bounds.minY, bounds.maxY)
        val displayY = max(lpOriginY.toFloat(), posY - imeLiftPx)
        writeBallPosition(current, wm, posX, displayY, lpOriginY)
        updateBadgeGravity()
    }

    private fun updateBadgeGravity() {
        val tv = badgeView ?: return
        val lp = tv.layoutParams as? FrameLayout.LayoutParams ?: return
        val gravity = Gravity.TOP or if (isOnLeft()) Gravity.END else Gravity.START
        if (lp.gravity != gravity) {
            lp.gravity = gravity
            tv.layoutParams = lp
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

    /** 徽标（球态未读角标）：并入单窗根，BALL 态才显示。 */
    private var badgeView: TextView? = null

    /** 服务最近一次下发的徽标：收起动画期间先记账，切回球态再画（[renderBadge]）。 */
    private var lastBadge: BadgeState? = null

    /** hide() 的兜底任务：show() 复用时必须撤销，否则会把刚亮起的胶囊重新按下去。 */
    private var hideFallback: Runnable? = null

    /** 收起形变进行中：内容保留，播完才切回球态（重展开会打断）。 */
    private var collapsing = false

    /** 收起形变播完后的服务回调（prune / refresh / 换边记账）。重展开直接丢弃。 */
    private var pendingHideCallback: (() -> Unit)? = null

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
    private var arrivalAlert by mutableStateOf<AlertPlayPlan?>(null)

    /** 锚点球高度：普通模式的行高，也是 [collapsedScale] 的形变基准。 */
    private var pageHeightPx by mutableIntStateOf(0)

    /** 堆叠模式单行高：跟着球高走但夹在 [RowHeightMinDp]–[RowHeightMaxDp]，不随大球膨胀。 */
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
     * 中侧余量（px）：横滑视觉上限 + 辉光余量。反向滑的行位移钳在 [DragRubberCapDp] 内，
     * 行不再被窗口边缘硬裁。窗口的触摸区是矩形：这条余量会吞掉落在其上的触摸
     * （系统的窗口触摸区无法挖洞），命中判定仍按行宽收口，行外点按不触发动作。
     */
    private val centerMarginPx: Int
        get() = DensityProvider.dp2px(GlowMarginDp + DragRubberCapDp)

    /** 长按后当前页展开的动作行；null = 普通内容。 */
    private var menuItemId by mutableStateOf<String?>(null)

    /** 复制成功后短暂显示「已复制」的条目。 */
    private var copiedItemId by mutableStateOf<String?>(null)

    private var settings by mutableStateOf(FloatBallSettings())

    /** 胶囊侧设置（行色 / 行数 / 手势），与球侧 [settings] 分离：两态各读各的。 */
    private var capsuleSettings by mutableStateOf(FloatBallSettings())

    /** 球在哪一侧：true = 右侧（胶囊贴右屏缘、向左伸展）。Compose 读取以决定余量方向。 */
    private var sideRight by mutableStateOf(false)
    private var originReady = false
    private var entering = false
    private var enterPending = false
    private var pressed = false

    private var hitTarget: HitTarget = HitTarget.Body

    private var dragDx = 0f
    private var dragDy = 0f
    private var dragAxis = DragAxis.NONE
    private var velocityTracker: VelocityTracker? = null
    private var settleAnimator: ValueAnimator? = null

    private val bodyArbiter = FloatBallTouchArbiter(
        onSlideDragged = { dx, dy -> onBodyDragged(dx, dy) },
        onTapped = { handleTap() },
        onPressChanged = { pressed ->
            this.pressed = pressed
            onPressStateChanged?.invoke(pressed)
            cancelEnterFeedback()
            // 只缩按下的那一颗；整窗缩放会让整串一起抖。
            pressedRowId = if (pressed) tappedItemId else null
            applyPressScale(false)
            if (pressed) {
                dragDx = 0f
                dragDy = 0f
                dragAxis = DragAxis.NONE
                resetRowDrag()
            }
        },
        onDragEnded = { finishBodyDrag() },
        onLongPress = { handleLongPress() },
    )

    /** 正文点按（通知直达 / 结果项主动作）。[String] 是点中的条目 id（路由到具体行）。 */
    var onBodyTapped: ((String) -> Unit)? = null

    /** 结果项动作行 / 主动作回调。 */
    var onResultAction: ((String, ResultAction) -> Unit)? = null

    /** 朝球所在侧横滑：只收起，保留队列项、不标记 dismissed。 */
    var onCollapsed: (() -> Unit)? = null

    /** 反向横滑：移除按下那一行并翻下一条（多条）/ 收起（末条/单条）。参数是条目 id。 */
    var onSwipedAway: ((String) -> Unit)? = null

    /** 按住状态：暂停 / 恢复自动收起。 */
    var onPressStateChanged: ((Boolean) -> Unit)? = null

    /** 胶囊首帧真正画出来：服务这时才隐藏球窗。 */
    var onReady: (() -> Unit)? = null

    /** 组合失败/窗口不可用：服务收起胶囊、恢复球，避免“球隐藏 + 空白窗”。 */
    var onUnavailable: (() -> Unit)? = null

    fun updateCapsuleSettings(next: FloatBallSettings) {
        capsuleSettings = next
        // 同屏行数变了必须立即改窗口高：否则新视口会被旧窗口裁掉 / 留白。
        // 旧守卫比较两次 effectiveVisibleRows()（都读服务侧已更新的值）永远相等，
        // 这里改成记「上次落窗的行数」。
        val rows = effectiveVisibleRows()
        if (rows == appliedVisibleRows) return
        appliedVisibleRows = rows
        offsetPx = stackMetrics.clamp(offsetPx)
        if (isCapsule) view?.let { applyCapsuleGeometry(it) }
    }

    fun isPinned(): Boolean = items.getOrNull(currentIndex)?.pinned == true

    /** 复制成功的短暂反馈：显示「已复制」。 */
    fun showCopied(itemId: String) {
        menuItemId = null
        copiedItemId = itemId
    }

    /**
     * 播放一次到达提示动效（仅胶囊本体脉冲 / 边框流光走这条；其他效果由独立 overlay 承载）。
     * 播放中的请求合并丢弃；播完由效果自己清空 [arrivalAlert]，收起 / 摘窗时也会被清。
     */
    fun playArrivalAlert(plan: AlertPlayPlan) {
        if (arrivalAlert != null) return
        arrivalAlert = plan
    }

    /** 立即停掉在播的到达提示动效（息屏 / 服务销毁）。 */
    fun cancelArrivalAlert() {
        arrivalAlert = null
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
     * 展开胶囊（切到 [VisualState.CAPSULE]）。已展开时更新列表与当前项，不重播展开动画。
     * 锚点由本窗口自己的球基准坐标推导（[capsuleAnchor]），不再跨窗握手。
     */
    fun showCapsule(
        items: List<BallItem>,
        index: Int,
    ): Boolean {
        if (items.isEmpty()) return false
        // BALL → CAPSULE 才播展开形变；已展开的刷新（新通知 / 换当前项）只换内容与几何，
        // 不能重播——否则每来一条通知胶囊都会缩一下再弹开。
        val wasCapsule = isCapsule
        state = VisualState.CAPSULE
        // 徽标是球面部件：切胶囊先收掉，收起动画期间也不会压在行上。
        renderBadge()
        // 与 [updateItems] 同一条纪律：内容没变就不通知快照，避免无谓的整树重组。
        if (!sameItems(items, this.items)) this.items = items
        pruneRowTransient(this.items)
        val anchor = capsuleAnchor()
        sideRight = anchor.centerX >= DensityProvider.screenWidthPx / 2
        val newHeight = (anchor.bottom - anchor.top).coerceAtLeast(1)
        if (newHeight != pageHeightPx) pageHeightPx = newHeight
        val newRowHeight = rowHeightFor(pageHeightPx)
        if (newRowHeight != rowHeightPx) rowHeightPx = newRowHeight
        // 有通知行时保留内容驱动宽（LaunchedEffect 收敛；键盘让位/球移动不撑宽），
        // 首帧或结果胶囊（无通知行）用固定公式兜底。
        val newWidth = if (items.any { it is NotificationItem } && capsuleWidthPx > 0) {
            capsuleWidthPx
        } else {
            fixedCapsuleWidth()
        }
        if (newWidth != capsuleWidthPx) capsuleWidthPx = newWidth
        // 用户正在拖动时不要抢当前页。
        if (!pressed) {
            // 新内容接管当前页：先摘掉 settle 回调，否则旧动画结束后会把滚动拉回旧目标。
            // 多条同时到达时，它还可能把 currentIndex 改到已被重建的列表之外。
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

        // 常驻单窗：建一次，两态复用（不 addView，无系统窗口过渡动画）。
        if (!ensureWindowRoot()) {
            Log.e(TAG, "ball/capsule window unavailable")
            return false
        }
        val existing = view ?: return false
        if (!contentReady) composeView?.setContent { WindowContent() }
        // 尺寸与位置必须一次写完：只改尺寸会让贴屏侧那条边（x 由宽推）漂移。
        applyCapsuleGeometry(existing)
        visuallyHidden = false
        applyBallTouchFlags(existing, wm, hidden = false)
        hideFallback?.let(mainHandler::removeCallbacks)
        hideFallback = null
        // 收起形变/旧进场动画可能还在跑：先停掉，否则它们的 endAction 会把姿态写回去。
        existing.animate().cancel()
        collapsing = false
        pendingHideCallback = null
        existing.visibility = View.VISIBLE
        if (!wasCapsule) {
            if (originReady) {
                // 起点姿态同步落好再进入这一帧：几何已写、pivot 用目标宽，形变方向稳定。
                startMorphIn(existing)
            } else {
                // 新窗首帧：LP 原点还没实测，位置写不进窗口。先保持隐形，
                // 由布局回调测完原点后再起形变（[startMorphIfPending]）——
                // 否则第一帧会画在 addView 的 (0,0)，下一帧再跳过去。
                entering = false
                enterPending = true
                existing.alpha = 0f
                existing.scaleX = 1f
                existing.scaleY = 1f
            }
        } else {
            // 已展开刷新：姿态归一位，不重播展开动画。
            existing.alpha = 1f
            existing.scaleX = 1f
            existing.scaleY = 1f
            entering = false
            enterPending = false
        }
        existing.postDelayed({
            if (view === existing) {
                if (contentReady) onReady?.invoke() else onUnavailable?.invoke()
            }
        }, 600L)
        // 胶囊比球高：键盘已在屏上时按胶囊底边重算抬升（球态那份抬升不够，胶囊会压在键盘上）。
        if (imeBottomPx > 0) animateImeLift(computeImeLift(imeBottomPx).toFloat())
        return true
    }

    /** 常驻单窗：首次按当前状态建，之后两态复用（6A）。false = 未授权 / 系统拒绝。 */
    private fun ensureWindowRoot(): Boolean {
        if (view != null) return true
        contentReady = false
        // 新窗口实例：原点等首次布局回调重新实测（旧实例的原点可能来自别的方向/窗口）。
        originReady = false
        // 与球窗同构：FrameLayout 做根、ComposeView 做子级。真机上直接拿 ComposeView 当窗口根
        // 时偶发不组合（胶囊空白），所以一律套一层；lifecycle owner 在骨架里只挂根一处。
        val (compose, created) = FloatBallLifecycleOwner.createWindowRoot(context) { WindowContent() }
        // 徽标（球态未读角标）：叠在 Compose 之上，只在 BALL 态显示。
        val badge = TextView(context).apply {
            setTextColor(AndroidColor.WHITE)
            textSize = 9f
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xFFE53935.toInt())
            }
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        created.addView(
            badge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(17),
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dp(1)
            },
        )
        badgeView = badge
        created.alpha = 0f
        created.setOnTouchListener { v, event -> handleTouch(v, event) }
        // 展开动画的一次性触发：enterPending 守卫保证 morph 只启动一次，
        // 四个时机（布局、post、420ms 兜底、EnterFallbackMs）谁先就绪谁触发。
        fun startMorphIfPending(v: View) {
            // 球态挂窗不做胶囊形变（球直接全尺寸出现）。
            if (!isCapsule) return
            if (enterPending) {
                enterPending = false
                startMorphIn(v)
            }
        }
        created.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width <= 0 || v.height <= 0) return@addOnLayoutChangeListener
            if (!originReady) {
                lpOriginY = screenOriginY(v)
                originReady = true
            }
            applyWindowBounds()
            startMorphIfPending(v)
        }
        if (!wm.safeAddView(created, currentLayoutParams())) {
            Log.e(TAG, "ball/capsule addView failed (overlay permission or window rejected)")
            return false
        }
        view = created
        composeView = compose
        entering = isCapsule
        enterPending = isCapsule
        // addView 有时会同步跑完布局，post 一次保证展开动画一定启动（不依赖 origin 测量）。
        created.post {
            val v = view
            if (v === created) {
                applyWindowBounds()
                startMorphIfPending(v)
            }
        }
        // 安全网：极端时序下动画/定位未跑，也保证窗口可见且尺寸正确。
        mainHandler.postDelayed({
            val v = view
            if (v === created) {
                applyWindowBounds()
                if (!contentReady) composeView?.setContent { WindowContent() }
                if (v.alpha < 0.99f) {
                    v.animate().cancel()
                    v.alpha = 1f
                    v.scaleX = 1f
                    v.scaleY = 1f
                }
                entering = false
                // onReady/onUnavailable 是胶囊态语义：球态（BALL）不触发。
                if (isCapsule) {
                    if (contentReady) {
                        onReady?.invoke()
                    } else {
                        // 再给一次重组机会；仍失败就让服务收起胶囊、恢复球。
                        mainHandler.postDelayed({
                            if (view === created) {
                                if (contentReady) onReady?.invoke() else onUnavailable?.invoke()
                            }
                        }, 500L)
                    }
                }
            }
        }, 420L)
        mainHandler.postDelayed({
            val v = view
            if (v === created && !originReady) {
                lpOriginY = screenOriginY(v)
                originReady = true
                applyWindowBounds()
            }
            if (v === created) startMorphIfPending(v)
        }, EnterFallbackMs)
        return true
    }

    /** 当前状态的窗口 LP：BALL = 球方窗，CAPSULE = 胶囊窗（含余量）。 */
    private fun currentLayoutParams(): WindowManager.LayoutParams =
        if (isCapsule) {
            capsuleLayoutParams(capsuleWidthPx + centerMarginPx, windowHeightPx + glowMarginPx * 2)
        } else {
            ballLayoutParams(ballPx.coerceAtLeast(1))
        }

    /** 切回球态：尺寸换回球方窗、定位到 posX/posY、恢复球面姿态。 */
    private fun enterBallState() {
        val v = view ?: return
        state = VisualState.BALL
        renderBadge()
        val target = ballPx.coerceAtLeast(1)
        val lp = v.layoutParams as? WindowManager.LayoutParams
        if (lp != null && (lp.width != target || lp.height != target)) {
            lp.width = target
            lp.height = target
            wm.safeUpdateViewLayout(v, lp)
        }
        // 球态缩放一律绕球心：胶囊态残留的贴边 pivot 会让按压时球朝屏边缩。
        v.pivotX = target / 2f
        v.pivotY = target / 2f
        applyWindowBounds()
    }

    /**
     * 收起胶囊：先播横向收窄 + 淡出，播完再切回球态/球方窗（单窗常驻，不 addView）。
     * 内容保留到动画结束——否则窗口先变空白再播退场，等于没有动画。
     */
    fun hideCapsule(onHidden: (() -> Unit)? = null) {
        bodyArbiter.cancel()
        hitTarget = HitTarget.Body
        val v = view
        if (v == null || !isCapsule) {
            // 未挂窗 / 已经是球态：幂等收尾（重复 hide、服务重入）。
            resetTransientState()
            onHidden?.invoke()
            return
        }
        // 收起中再次收起：只替换回调，不叠加第二个动画/兜底。
        if (onHidden != null) pendingHideCallback = onHidden
        if (collapsing) return
        // 已经不可见（锁屏 / 面板占用）：没必要播收起动画，直接收尾并保持隐藏。
        if (lockHidden || panelHidden) {
            collapsing = true
            completeCollapse(v)
            return
        }
        collapsing = true
        // 进场形变先收束，避免和收起的 scale/alpha 抢同一组属性。
        cancelEnterFeedback()
        v.animate().cancel()
        // 贴球侧那条边是形变支点：向球收，不向屏心缩。
        v.pivotX = if (sideRight) v.width.toFloat() else 0f
        v.pivotY = v.height / 2f
        v.animate()
            .scaleX(collapsedScale())
            .alpha(0f)
            .setDuration(collapseAnimMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { completeCollapse(v) }
            .start()
        // 兜底：锁屏/面板路径会把这条动画 cancel 掉，状态机也必须收回球态。
        val fallback = Runnable { completeCollapse(v) }
        hideFallback = fallback
        mainHandler.postDelayed(fallback, collapseAnimMs + 120L)
    }

    /** 收起收尾：切回球态。锁屏 / 面板占用时保持不可见不可点，不把球重新点亮。 */
    private fun completeCollapse(v: View) {
        if (!collapsing) return
        collapsing = false
        hideFallback?.let(mainHandler::removeCallbacks)
        hideFallback = null
        val callback = pendingHideCallback
        pendingHideCallback = null
        if (view !== v) {
            callback?.invoke()
            return
        }
        enterBallState()
        // 收起后抬升量回到「球底」口径：胶囊态那份抬升会把球顶得过高。
        if (imeBottomPx > 0) animateImeLift(computeImeLift(imeBottomPx).toFloat())
        if (lockHidden || panelHidden) {
            // 隐藏态（锁屏 / 面板）：切回球态也不亮起，保持不可见不可点。
            visuallyHidden = true
            applyBallTouchFlags(v, wm, hidden = true)
            v.visibility = View.VISIBLE
            v.alpha = 0f
            settleBallWindowAlpha(v, wm)
        } else {
            applyBallTouchFlags(v, wm, hidden = false)
            v.visibility = View.VISIBLE
            // 球从收缩态回弹，与展开形变同一档语感（不再硬切出现）。
            animateRestoreIn(v)
        }
        resetTransientState()
        scheduleIdle()
        callback?.invoke()
    }

    fun detachNow() {
        bodyArbiter.cancel()
        ballArbiter.cancel()
        collapsing = false
        hideFallback?.let(mainHandler::removeCallbacks)
        hideFallback = null
        // 摘窗不再回放服务侧的收起记账（prune / refresh / 换边）：窗口都拆了，那些都由服务侧
        // 的显隐流程接手，迟到的回调只会在拆窗后反手再拉一次窗口。
        pendingHideCallback = null
        val v = view
        view = null
        resetTransientState()
        v?.let {
            it.animate().cancel()
            context.removeWindow(it)
        }
        // 摘窗后不再保留 ComposeView / lifecycle owner：常驻复用只在“收起”期间。
        composeView = null
        badgeView = null
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
        entering = false
        enterPending = false
        pressed = false
        dragging = false
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
        settleAnimator?.cancel()
        settleAnimator = null
        velocityTracker?.recycle()
        velocityTracker = null
        mainHandler.removeCallbacksAndMessages(null)
        hideFallback?.let(mainHandler::removeCallbacks)
        hideFallback = null
    }

    /** 球移动 / 键盘让位 / 转屏后重新贴边：由本窗口自己的统一坐标重推锚点与几何。 */
    fun updateCapsuleAnchor() {
        if (!isCapsule) return
        val anchor = capsuleAnchor()
        val newHeight = (anchor.bottom - anchor.top).coerceAtLeast(1)
        if (newHeight != pageHeightPx) pageHeightPx = newHeight
        val newRowHeight = rowHeightFor(pageHeightPx)
        if (newRowHeight != rowHeightPx) rowHeightPx = newRowHeight
        sideRight = anchor.centerX >= DensityProvider.screenWidthPx / 2
        // 有通知行时保留内容驱动宽（LaunchedEffect 收敛），键盘让位/转屏不撑宽；
        // 结果胶囊（无通知行）用固定公式。
        if (items.none { it is NotificationItem } || capsuleWidthPx <= 0) {
            capsuleWidthPx = fixedCapsuleWidth()
        }
        view?.let { applyCapsuleGeometry(it) }
    }

    // ---- 多胶囊行状态 ----

    private fun resetRowDrag() {
        rowDragId = null
        rowDragDx = 0f
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

    private fun handleTouch(v: View, event: MotionEvent): Boolean {
        // 按状态分流：BALL = 球拖动/点按/长按；CAPSULE = 行命中/滚动/横滑。
        if (!isCapsule) return ballArbiter.onTouch(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            settleAnimator?.cancel()
            settleAnimator = null
            hitTarget = resolveHitTarget(v, event.x, event.y)
            // 点按 / 横滑都路由到按下所在的那一行。
            tappedItemId = rowItem(event.y)?.id
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
        val row = rowItem(y) ?: return HitTarget.None
        val rowWidth = (rowWidthsPx[row.id] ?: capsuleWidthPx).coerceAtLeast(1)
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
        if (dragAxis == DragAxis.NONE &&
            (abs(dragDx) > touchSlop || abs(dragDy) > touchSlop)
        ) {
            // 竖向 = 滚动，横向 = 收起 / 滑掉；先跨 slop 的方向定轴。
            dragAxis = if (abs(dragDx) > abs(dragDy)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
            // 一旦进入拖动就撤掉按压形变：滚动 / 横滑不该保持缩小的样子。
            pressedRowId = null
            applyPressScale(false)
            if (dragAxis == DragAxis.HORIZONTAL) resetRowDrag()
            if (dragAxis == DragAxis.VERTICAL) showScrollThumb()
        }
        val v = view ?: return
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

    /**
     * 多胶囊横滑：朝球侧整串收起（窗口整体位移），反向只动按下那颗胶囊。
     *
     * 反向用 1:1 跟手，像滑掉一条通知；阈值过半即删除，其余胶囊原地重排。
     */
    private fun onStackRowDragged(v: View) {
        val towardEdge = if (sideRight) dragDx >= 0f else dragDx <= 0f
        if (towardEdge) {
            if (rowDragId != null) resetRowDrag()
            v.translationX = dragDx * CollapseFollowRatio
            val fade = (abs(dragDx) / collapseThresholdPx).coerceIn(0f, 1f)
            v.alpha = 1f - fade * 0.25f
        } else {
            v.translationX = 0f
            v.alpha = 1f
            rowDragId = tappedItemId
            rowDragDx = dragDx
        }
    }

    private fun finishBodyDrag() {
        val v = view
        val dx = dragDx
        val axis = dragAxis
        val vy = flingVelocityY()
        // 松手前记住被横滑的那颗：resetRowDrag 之后还要用它决定删哪一条。
        val swipedId = rowDragId ?: currentTapId()
        dragDx = 0f
        dragDy = 0f
        dragAxis = DragAxis.NONE
        resetRowDrag()
        if (v != null) {
            v.alpha = 1f
            v.translationX = 0f
        }
        when (axis) {
            DragAxis.VERTICAL -> {
                menuItemId = null
                settleScroll(vy)
            }

            DragAxis.HORIZONTAL -> {
                menuItemId = null
                val towardEdge = if (sideRight) dx >= collapseThresholdPx else dx <= -collapseThresholdPx
                val awayFromEdge = if (sideRight) dx <= -collapseThresholdPx else dx >= collapseThresholdPx
                when {
                    towardEdge -> dispatchRowGesture(rowGesturesState.swipeToward, swipedId)
                    awayFromEdge -> dispatchRowGesture(rowGesturesState.swipeAway, swipedId)
                }
            }

            DragAxis.NONE -> Unit
        }
    }

    /**
     * 横滑行为分发（[CapsuleGestureBehavior]，由胶囊设置决定）：
     * 收起 = 整窗收起（保留队列），忽略该行 = 滑掉按下那一行，无 = 回弹不做事。
     */
    private fun dispatchRowGesture(behavior: CapsuleGestureBehavior, swipedId: String?) {
        when (behavior) {
            CapsuleGestureBehavior.COLLAPSE -> onCollapsed?.invoke()
            CapsuleGestureBehavior.IGNORE_ROW -> swipedId?.let { onSwipedAway?.invoke(it) }
            else -> Unit
        }
    }

    private fun flingVelocityY(): Float {
        val tracker = velocityTracker ?: return 0f
        tracker.computeCurrentVelocity(1000)
        return tracker.yVelocity
    }

    /**
     * 滚动栈松手：按速度做一次投影，再夹进 [StackMetrics.maxScrollPx]。
     * 没有溢出 / 已经到位就不播动画，避免轻点也起一个 ValueAnimator。
     */
    private fun settleScroll(vy: Float) {
        val metrics = stackMetrics
        if (metrics.maxScrollPx <= 0) {
            offsetPx = 0f
            return
        }
        val projected = if (abs(vy) >= FlingVelocityPxPerSec) {
            offsetPx + vy * FlingProjectionSec
        } else {
            offsetPx
        }
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
            // 动画途中队列可能变短（TTL / 系统移除）：每帧都按最新范围夹一次。
            addUpdateListener { offsetPx = stackMetrics.clamp(it.animatedValue as Float) }
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

    /** 收起形变时长：与浮球家族退场同档。 */
    private val collapseAnimMs: Long
        get() = motionMillis(MotionFamily.Floating).toLong()

    // ---- 定位 ----

    private fun fixedCapsuleWidth(): Int {
        val screenW = DensityProvider.screenWidthPx
        val cap = min(DensityProvider.dp2px(MaxWidthDp), (screenW * WidthRatio).roundToInt())
        return max(pageHeightPx, cap.coerceAtMost(screenW - DensityProvider.dp2px(16f)))
    }

    /** 堆叠单行高：跟着球高走，但不让大球把多行胶囊撑成整屏。 */
    private fun rowHeightFor(anchorHeightPx: Int): Int =
        anchorHeightPx.coerceIn(
            DensityProvider.dp2px(RowHeightMinDp),
            DensityProvider.dp2px(RowHeightMaxDp),
        )

    /**
     * 胶囊态几何：窗口尺寸 + 位置**一次写完**（[WindowManager.LayoutParams] 同一份）。
     *
     * 分开写（先尺寸、后位置）会让贴屏侧那条边漂移：x 由宽反推，宽变了 x 不跟着变，
     * 内容就会离屏缘一段再被下一次定位拉回去——真机表现就是「展开/切换跳一下」。
     * 锚点每次现算（[capsuleAnchor]），键盘抬升/球移动的逐帧写窗直接跟随，不用快照。
     */
    private fun applyCapsuleGeometry(v: View) {
        // 球态窗口尺寸只有球方窗一种：胶囊几何只能在胶囊态落窗。
        if (!isCapsule) return
        if (capsuleWidthPx <= 0) return
        val contentHeight = windowHeightPx
        // 首帧前 rowHeight 还没测出来：别把窗口写成 0 高。
        if (contentHeight <= 0) return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val screenW = DensityProvider.screenWidthPx
        val windowWidth = capsuleWidthPx + centerMarginPx
        val windowHeight = contentHeight + glowMarginPx * 2
        val live = capsuleAnchor()
        // 侧别跟着锚点走（转屏/换边后镜像一次），内容区始终贴屏侧。
        sideRight = live.centerX >= screenW / 2
        val x = if (sideRight) {
            (live.right - capsuleWidthPx - centerMarginPx)
                .coerceIn(0, (screenW - windowWidth).coerceAtLeast(0))
        } else {
            live.left.coerceIn(0, (screenW - windowWidth).coerceAtLeast(0))
        }
        // 多行更高：球贴近底边时窗口会伸出屏外，把顶边夹回屏内（胶囊与球之间留出空隙）。
        // 窗口上下各有辉光余量，顶边夹取按内容高算，余量向屏外溢出无害（透明穿透）。
        val top = min(live.top, (DensityProvider.screenHeightPx - contentHeight).coerceAtLeast(0))
        val nextX = x
        val nextY = ((top - glowMarginPx) - lpOriginY).coerceAtLeast(0)
        // 原点未实测前先落尺寸；位置等布局回调测完原点再写，避免按 0 原点写错一帧。
        val writePosition = originReady
        if (lp.width == windowWidth && lp.height == windowHeight &&
            (!writePosition || (lp.x == nextX && lp.y == nextY))
        ) {
            return
        }
        lp.width = windowWidth
        lp.height = windowHeight
        if (writePosition) {
            lp.x = nextX
            lp.y = nextY
        }
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun screenOriginY(v: View): Int {
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        return (location[1] - ((v.layoutParams as? WindowManager.LayoutParams)?.y ?: 0))
            .coerceIn(0, DensityProvider.dp2px(MaxOriginDp))
    }

    private fun collapsedScale(): Float {
        if (capsuleWidthPx <= 0) return 0.3f
        return (pageHeightPx.toFloat() / capsuleWidthPx).coerceIn(0.15f, 1f)
    }

    /**
     * 展开形变：绕贴球侧那条边把整窗从球宽拉到内容宽。
     *
     * pivot 必须用**目标**窗口宽：`updateViewLayout` 只是请求布局，此刻 `v.width` 还是
     * 球方窗宽度，拿它当 pivot 会让胶囊在缩放途中横向滑一段。
     */
    private fun startMorphIn(v: View) {
        entering = true
        enterPending = false
        val targetW = (capsuleWidthPx + centerMarginPx).coerceAtLeast(1).toFloat()
        val targetH = (windowHeightPx + glowMarginPx * 2).coerceAtLeast(1).toFloat()
        v.pivotX = if (sideRight) targetW else 0f
        v.pivotY = targetH / 2f
        v.scaleX = collapsedScale()
        v.scaleY = 1f
        v.alpha = 0.25f
        v.animate()
            .scaleX(1f)
            .alpha(1f)
            .setDuration(motionMillis(MotionFamily.FloatingMorph).toLong())
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { if (view === v) entering = false }
            .start()
        mainHandler.postDelayed(
            { if (view === v) entering = false },
            motionMillis(MotionFamily.FloatingMorph) + 80L,
        )
    }

    private fun cancelEnterFeedback() {
        // 展开动画在按下时立即收束，避免点击/拖动时还在缩放。
        if (!entering && !enterPending) return
        enterPending = false
        val v = view ?: return
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        v.alpha = 1f
        entering = false
    }

    /**
     * 按压缩放反馈（§5.3，与主球同一档位）。
     *
     * pivot 落在展开侧：收起方向与进场形变一致，不朝锚点球的方向缩。
     * 行的按压缩放画在行自己的 `graphicsLayer` 上；这里只负责把根视图复位，
     * 复位时的 pivot 用当前窗口宽（胶囊态），球态 pivot 由 [enterBallState] 收回球心。
     */
    private fun applyPressScale(pressed: Boolean) {
        val v = view ?: return
        if (!v.isAttachedToWindow) return
        val scale = if (pressed) FloatBallDefaults.PressScale else 1f
        v.pivotX = if (sideRight) v.width.toFloat() else 0f
        v.scaleX = scale
        v.scaleY = scale
    }

    // ---- Compose ----

    /** 单窗内容根：按状态切换 BALL 球面 / CAPSULE 胶囊栈。 */
    @Composable
    private fun WindowContent() {
        when (state) {
            VisualState.BALL -> BallContent()
            VisualState.CAPSULE -> CapsuleContent()
        }
    }

    @Composable
    private fun BallContent() {
        BallWindowContent(ballPx) { size -> BallFace(modifier = Modifier.size(size)) }
    }

    @Composable
    private fun BallFace(modifier: Modifier = Modifier) {
        val densityContext = LocalContext.current
        val size = with(LocalDensity.current) { ballPx.toDp() }
        // 自定义球面图标：非空且文件存在时全球面（去掉圆底/描边），否则回退默认圆底+动作图标。
        val customIconPath = settings.customIconPath
        if (customIconPath != null && File(customIconPath).exists()) {
            CustomBallIcon(
                context = densityContext,
                model = File(customIconPath),
                scale = settings.customIconScale,
                offsetX = settings.customIconOffsetX,
                offsetY = settings.customIconOffsetY,
                alpha = settings.customIconAlpha,
                // 不可见（面板/胶囊打开、锁屏、软隐藏）时一律暂停动图：此时没人看得到球，
                // 但窗口仍挂着 —— 不暂停就是 121fps 的空转重绘。可见时再按静置开关决定。
                paused = visuallyHidden || (idled && settings.customIconPauseWhenIdle),
                modifier = modifier.size(size),
            )
            return
        }
        val accent = settings.colorSpec.toMarkColor()
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            // 判定区彩蛋提示：拖动中球心入判定区时描边转主色并轻微放大（与按压同一档语感）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val hintScale = if (dropHint) 1.12f else 1f
                        scaleX = hintScale
                        scaleY = hintScale
                    }
                    .background(accent.copy(alpha = 0.92f), CircleShape)
                    .border(
                        2.dp,
                        if (dropHint) accent
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val icon = fixedIconState
                    ?: settings.tapAction?.let { actionIcon(it) }
                    ?: Icons.Default.TouchApp
                when (icon) {
                    is ImageVector -> Image(
                        imageVector = icon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.size(size / 2),
                    )

                    is ImageBitmap -> Image(
                        bitmap = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )

                    is TextIcon -> TextIconGlyph(
                        text = icon.text,
                        size = size / 2,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )

                    else -> Unit
                }
            }
        }
    }

    @Composable
    private fun CapsuleContent() {
        SideGestureTheme {
            val list = items
            if (list.isEmpty() || pageHeightPx <= 0) return@SideGestureTheme
            val density = LocalDensity.current
            if (rowHeightPx <= 0) return@SideGestureTheme
            val rowHeight = with(density) { rowHeightPx.toDp() }
            CapsuleStack(list = list, rowHeight = rowHeight)
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
            val screenW = DensityProvider.screenWidthPx
            // 行宽上限按**整行**（文本列+图标+间距+内距）计：文本列上限再扣掉装饰宽，
            // 避免「文本列 + 图标/内距」实际超过行宽上限（dp 封顶，窄屏按比例兜底）。
            val rowDecoration = iconPx + rowGapPx + rowPadPx * 2
            val maxRowPx = min(
                screenW * NarrowWidthRatio,
                DensityProvider.dp2px(MaxWidthCapDp).toFloat(),
            ).roundToInt()
            val maxContentPx = maxRowPx - rowDecoration
            // 按实际内容宽：内容宽 ≤ 上限 → 全显贴内容（正文不滚）；
            // 内容宽 > 上限 → 文本列封顶上限，正文由 MarqueeText 自动滚动看全文。
            return contentW.coerceAtMost(maxContentPx)
        }
        // 首帧窗口宽按公式兜底，Compose 测量出通知行目标宽后再收窄/加宽（一次收敛）。
        // 有通知行时只按内容驱动（不叠加 fixedCapsuleWidth 兜底下限，否则短内容被撑到 ~48% 屏宽）；
        // 无通知行（结果胶囊）保持固定公式。key 带朝向：转屏后按新屏宽立即重推行宽，不等下一条通知。
        LaunchedEffect(list, landscape) {
            val notifMax = list.filterIsInstance<NotificationItem>()
                .maxOfOrNull { notificationContentWidthPx(it) + iconPx + rowGapPx + rowPadPx * 2 }
                ?: 0
            // 内容驱动宽不能比球窄：短文案的胶囊比球还小会像“缩了一下”，
            // 也让展开形变的起点（球宽）失去参照。
            val desired = if (list.any { it is NotificationItem }) {
                notifMax.coerceAtLeast(pageHeightPx)
            } else {
                fixedCapsuleWidth()
            }
            if (desired != capsuleWidthPx) {
                capsuleWidthPx = desired
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
                    // 结果行：保持「贴内容」夹窗口宽。测量结果按内容缓存（条目少，重组不重复测）。
                    val notifContentWidth = if (item is NotificationItem) {
                        remember(item.id, item.title, item.text) {
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
            arrivalAlert?.let { plan ->
                CapsuleArrivalOverlay(
                    plan = plan,
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
        plan: AlertPlayPlan,
        rowHeightPx: Int,
        metrics: StackMetrics,
        contentColor: Color,
        modifier: Modifier = Modifier,
    ) {
        val density = LocalDensity.current
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
        LaunchedEffect(plan) {
            elapsed.snapTo(0f)
            elapsed.animateTo(
                plan.totalMillis.toFloat(),
                tween(durationMillis = plan.totalMillis, easing = LinearEasing),
            )
            withFrameNanos { }
            arrivalAlert = null
        }
        Canvas(modifier = modifier) {
            if (elapsed.value >= plan.totalMillis) return@Canvas
            val t = (elapsed.value / plan.cycleMillis) % 1f
            val gapPx = metrics.gapPx
            val rows = items.size
            // 全窗坐标系：内容区在贴屏侧齐平、中侧与上下让出余量；行靠球侧对齐，
            // 逐行按实测宽算 x（窄行不再画到窗缘）。
            val rowLeft = { index: Int ->
                if (sideRight) (capsuleWidthPx + centerMarginPx - (rowWidths[index])).toFloat() else 0f
            }
            val topOrigin = glowMarginPx.toFloat()
            when (plan.effect) {
                // 本体脉冲：3 层嵌套描边辉光呼吸，外→内逐层加亮，核心最后画保证锐利。
                // 辉光宽度与光晕强度是用户旋钮（光晕 0 = 只剩核心描边）。
                NotificationAlertEffect.CapsulePulse -> {
                    val spec = plan.spec as PulseSpec
                    val boost = plan.brightness.lerpInRange(PulseTuning.BoostMin, PulseTuning.BoostMax)
                    val peak = (RowBorderAlpha + boost * sin(Math.PI.toFloat() * t)).coerceAtMost(1f)
                    val corePx = spec.glowWidthDp.dp.toPx()
                    val layers = buildList {
                        listOf(
                            PulseTuning.GlowWidthScale2 to PulseTuning.GlowAlphaFraction2,
                            PulseTuning.GlowWidthScale1 to PulseTuning.GlowAlphaFraction1,
                        ).forEach { (widthScale, alphaFraction) ->
                            val width = corePx * widthScale * spec.haloScale
                            val alpha = peak * alphaFraction * spec.haloScale
                            if (width > 0f && alpha > 0f) add(width to alpha)
                        }
                        add(corePx to peak)
                    }
                    for (index in 0 until rows) {
                        val path = rowPaths.getOrNull(index) ?: continue
                        translate(
                            left = rowLeft(index),
                            top = topOrigin + index * (rowHeightPx + gapPx).toFloat() + offsetPx,
                        ) {
                            for ((width, alpha) in layers) {
                                drawPath(
                                    path = path,
                                    color = contentColor.copy(alpha = alpha),
                                    style = Stroke(width = width),
                                )
                            }
                        }
                    }
                }

                // 边框流光：主亮带缓出扫过描边，后随更宽更暗的拖尾（先画拖尾再画主带）。
                // 拖尾在行进方向后方（反向扫动时在主带右侧）；拖尾 0 = 无拖尾。
                // 渐变 stop 必须严格递增：可见跨度不足时跳过该带，峰值点强制落在带内。
                NotificationAlertEffect.CapsuleShimmer -> {
                    val spec = plan.spec as ShimmerSpec
                    val peak = plan.brightness.lerpInRange(ShimmerTuning.PeakAlphaMin, ShimmerTuning.PeakAlphaMax)
                    val eased = t.easedOut(ShimmerTuning.SweepEaseExp)
                    val pos = if (spec.reverse) 1f - eased else eased
                    val band = plan.amount.lerpInRange(
                        FloatBallDefaults.AlertShimmerBandWidthMin / 100f,
                        FloatBallDefaults.AlertShimmerBandWidthMax / 100f,
                    )
                    val trailSpan = band * spec.trailScale
                    val mainStart = (pos - band / 2f).coerceIn(0f, 1f)
                    val mainEnd = (pos + band / 2f).coerceIn(0f, 1f)
                    val mainSpan = mainEnd - mainStart
                    val peakPos = if (mainSpan > 0f) {
                        mainStart + mainSpan * ((pos - mainStart) / mainSpan).coerceIn(0.15f, 0.85f)
                    } else {
                        mainStart
                    }
                    val mainBrush = Brush.horizontalGradient(
                        mainStart to contentColor.copy(alpha = 0f),
                        peakPos to contentColor.copy(alpha = peak),
                        mainEnd to contentColor.copy(alpha = 0f),
                    )
                    val trailBrush = if (spec.reverse) {
                        val trailEnd = (mainEnd + trailSpan).coerceIn(0f, 1f)
                        val span = trailEnd - mainEnd
                        if (span > 0.01f) {
                            Brush.horizontalGradient(
                                mainEnd to contentColor.copy(alpha = 0f),
                                mainEnd + span / 2f to contentColor.copy(
                                    alpha = peak * ShimmerTuning.TrailAlphaFraction,
                                ),
                                trailEnd to contentColor.copy(alpha = 0f),
                            )
                        } else {
                            null
                        }
                    } else {
                        val trailStart = (mainStart - trailSpan).coerceIn(0f, 1f)
                        val span = mainStart - trailStart
                        if (span > 0.01f) {
                            Brush.horizontalGradient(
                                trailStart to contentColor.copy(alpha = 0f),
                                trailStart + span / 2f to contentColor.copy(
                                    alpha = peak * ShimmerTuning.TrailAlphaFraction,
                                ),
                                mainStart to contentColor.copy(alpha = 0f),
                            )
                        } else {
                            null
                        }
                    }
                    val stroke = Stroke(width = 1.5.dp.toPx())
                    for (index in 0 until rows) {
                        val path = rowPaths.getOrNull(index) ?: continue
                        translate(
                            left = rowLeft(index),
                            top = topOrigin + index * (rowHeightPx + gapPx).toFloat() + offsetPx,
                        ) {
                            trailBrush?.let { drawPath(path = path, brush = it, style = stroke) }
                            if (mainSpan > 0.01f) drawPath(path = path, brush = mainBrush, style = stroke)
                        }
                    }
                }

                else -> Unit
            }
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
     * 自动横向滚动文本：正文完整宽 > 视口宽时无限往复（marquee），与手势无关；
     * 视口内能放下就不滚（短内容全显，不裁）。
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
                        repeatMode = RepeatMode.Reverse,
                    ),
                )
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    // requiredWidth 绕过父约束：文本宽 = 测量自然宽，offset 左移时右侧仍显示
                    // 剩余文本（否则 Text 被夹到视口宽，滚动时右缘移出视口 → 空白/截断）。
                    modifier = Modifier
                        .requiredWidth(with(LocalDensity.current) { layout.size.width.toDp() })
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
                    .clip(CircleShape)
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

    private companion object {
        const val TAG = LOG_TAG

        /** 键盘让位缓动时长：足够平滑，又不拖泥带水。 */
        const val IME_LIFT_ANIM_MS = 220L

        const val EnterFallbackMs = 260L
        const val MaxOriginDp = 200f

        /** 堆叠模式单行高：跟随球高，但夹在 48–56dp，大球不会把多行胶囊撑成整屏。 */
        const val RowHeightMinDp = 48f
        const val RowHeightMaxDp = 56f

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

        /** 反向横滑的视觉上限（dp）：行位移橡皮筋钳到这个值，窗口中侧余量按此 + 辉光留白。 */
        const val DragRubberCapDp = 72f

        /** 橡皮筋阻尼：超过收起阈值后的位移按此比例递减跟进。 */
        const val DragRubberDamping = 0.35f
    }

    /**
     * 反向横滑的视觉位移：阈值内 1:1 跟手，阈值后按 [DragRubberDamping] 递减，
     * 钳在 [DragRubberCapDp] 内——窗口中侧余量按此留白，行不再被窗口边缘硬裁。
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
