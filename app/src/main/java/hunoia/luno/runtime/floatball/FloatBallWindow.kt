package hunoia.luno.runtime.floatball

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import hunoia.luno.action.model.StoredAction
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.component.gestureButtonMarkColor
import kotlin.math.max
import kotlin.math.roundToInt
import hunoia.luno.core.LOG_TAG
import hunoia.luno.action.model.Action

/**
 * 悬浮球窗口：按住即拖，松手吸附最近边缘且球完整在屏内；静置变暗并移出一半，触摸恢复。
 * 坐标语义是球窗顶边，改 [posX]/[posY] 后统一经 [applyWindowBounds] 落到 LP。
 */
class FloatBallWindow(
    private val context: android.content.Context,
    /** 抬升量变化回调（含动画逐帧）：服务侧转发给镜像窗，事件路径下镜像也能跟随。 */
    private val onImeLiftChanged: (Float) -> Unit = {},
) {
    private val wm: WindowManager get() = context.windowManager()

    private var view: View? = null
    private var ballPx = 0

    private var settings: FloatBallSettings = FloatBallSettings()

    private val arbiter = FloatBallTouchArbiter(
        onSlideDragged = { dx, dy -> onDragged(dx, dy) },
        onTapped = { onTap?.invoke() },
        onPressChanged = { pressed ->
            this.pressed = pressed
            // 抬手统一重启静置计时：点按结束尤其必须 —— 否则计时器停在按下时，球不再变暗。
            if (!pressed) scheduleIdle()
            applyVisual()
        },
        onDragEnded = { onDragEnded() },
    )

    // ---- 定位 ----

    /** 球窗顶边（px）。状态而非局部变量：Compose 拖动感不依赖它，但更新要落 LP。 */
    private var posX = 0f
    private var posY = 0f

    /** 键盘抬升量（px）。显示位置 = 基准位 - 抬升量。*/
    private var imeLiftPx = 0f

    /** 抬升缓动动画：键盘起落平滑跟随，避免瞬跳。*/
    private var imeAnimator: ValueAnimator? = null

    /** 最近一次上报的键盘底边（px）。拖动中如收起键盘，拖完结算按此值归零。 */
    private var imeBottomPx = 0

    /** 系统是否真的把 IME insets 推给了这个悬浮窗；为 true 时服务侧可停掉轮询。 */
    private var imeInsetsAvailable = false

    /** 系统栏边界（px）。取不到时为 0，夹取退化为全屏。 */
    private var bottomInsetPx = 0

    /**
     * LP 坐标系原点在屏幕上的 y：2038 悬浮窗以状态栏下沿为原点，按「y=0 是屏幕顶」
     * 夹取会在屏幕底部裁掉状态栏高度；addView 后 getLocationOnScreen 实测，测出前按 0。
     */
    private var lpOriginY = 0


    private var dragging = false
    private var pressed = false
    private var idled = false

    // ---- 回调 ----

    /** 抬手点按（未超 touchSlop）。Service 决定派发动作还是开动作面板。 */
    var onTap: (() -> Unit)? = null

    /** 拖动松手，带归一化 (-[(顶边 x)/W, (顶边 y)/H])。 */
    var onDragFinished: ((Float, Float) -> Unit)? = null

    private val screenWidthPx: Int get() = DensityProvider.screenWidthPx
    private val screenHeightPx: Int get() = DensityProvider.screenHeightPx

    private val handler = Handler(Looper.getMainLooper())
    private var idleRunnable: Runnable? = null
    private var settleAnimator: ValueAnimator? = null
    private var moveAnimator: ValueAnimator? = null

    // ---- 生命周期 ----

    fun updateSettings(next: FloatBallSettings) {
        val sizeChanged = next.sizeDp != settings.sizeDp
        settings = next
        if (sizeChanged) {
            ballPx = DensityProvider.dp2px(next.sizeDp)
            view?.let { lpSize(it) }
            if (view != null) applyWindowBounds()
        }
    }

    /** 建窗口。false = 没建起来（未授权 / 系统拒绝）。 */
    fun attach(): Boolean {
        if (view != null) return true
        imeInsetsAvailable = false
        ballPx = DensityProvider.dp2px(settings.sizeDp)
        val created = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            FloatBallLifecycleOwner.createAndAttachTo(this)
            setContent { BallContent() }
        }
        created.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelSettle()
                    cancelIdle()
                }
            }
            arbiter.onTouch(event)
        }
        // 键盘 insets 优先走事件：系统支持时会立即回调（比轮询快）。
        // 兼容性兜底仍保留服务侧的低频轮询（部分 ROM 不给不可获焦悬浮窗推 IME insets）。
        created.setOnApplyWindowInsetsListener { v, insets ->
            val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
            // 只有真的收到非零 IME inset 才停轮询；部分 ROM 会派发一次空 insets
            // 但之后不再更新，提前信任会丢掉键盘让位。
            if (imeBottom > 0) imeInsetsAvailable = true
            updateIme(imeBottom)
            v.onApplyWindowInsets(insets)
        }
        created.requestApplyInsets()
        val lp = createLayoutParams()
        if (!wm.safeAddView(created, lp)) {
            Log.e(TAG, "ball addView failed (overlay permission or window rejected)")
            return false
        }
        view = created
        applySystemInsets()
        posX = settings.x * screenWidthPx
        posY = settings.y * screenHeightPx
        applyWindowBounds()
        scheduleIdle()
        // 实测 LP 原点（状态栏高度）：窗口摆出来才知道真实屏幕落点。
        postMeasureLpOriginY({ view }, { lpOriginY }) {
            lpOriginY = it
            applyWindowBounds()
        }
        return true
    }

    fun detach() {
        arbiter.cancel()
        cancelImeAnim()
        settleAnimator?.cancel()
        settleAnimator = null
        moveAnimator?.cancel()
        moveAnimator = null
        visualAnimator?.cancel()
        visualAnimator = null
        idleRunnable?.let(handler::removeCallbacks)
        idleRunnable = null
        view?.let(context::removeWindow)
        view = null
        // 窗口既已摘除，交互/换位中的标志全部作废：淡出的 withEndAction 在 cancel 时
        // 不运行，crossFading 残留 true 会把重挂后的 repositionTo 永久挡死。
        crossFading = false
        dragging = false
        pressed = false
        externalDrag = false
        pendingExternalFinish = false
        // 摘窗即复位：下次 attach 的新窗口按可见起步，lock/panel 状态由服务侧重新推。
        lockHidden = false
        panelHidden = false
    }

    /** 锁屏 / 息屏：整个窗口不可见也不可点，解锁恢复。 */
    fun setHidden(hidden: Boolean) {
        lockHidden = hidden
        applyHiddenFlags()
    }

    /**
     * 面板/临时隐藏：软隐藏 —— 窗口保留（组合与 Recomposer 不销毁），只置不可见不可点。
     * 相比摘窗，免掉每次面板开合的 removeView→新 ComposeView→首帧组合→addView 整链。
     * [animateRestore] 为 true 时按 [attachAnimated] 同款弹回动画恢复。
     */
    fun setPanelHidden(hidden: Boolean, animateRestore: Boolean = false) {
        panelHidden = hidden
        applyHiddenFlags(animateRestore)
    }

    private var lockHidden = false
    private var panelHidden = false

    private fun applyHiddenFlags(animateRestore: Boolean = false) {
        val current = view ?: return
        val hidden = lockHidden || panelHidden
        applyBallHiddenFlags(current, wm, hidden)
        if (hidden) {
            // 窗口不可点后事件流就断了：重置在途按压/拖动（与 detach 的 arbiter.cancel 对齐），
            // 免得恢复后停在 pressed 视觉态。
            arbiter.cancel()
            cancelIdle()
        } else {
            if (animateRestore) animateRestoreIn(current)
            scheduleIdle()
        }
    }

    /** 面板关闭后的回场：与 [attachAnimated] 同款 250ms overshoot，落在既有窗口上。 */
    private fun animateRestoreIn(target: View) {
        visualAnimator?.cancel()
        target.alpha = 0f
        target.scaleX = 0.85f
        target.scaleY = 0.85f
        visualAnimator = target.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(250L)
            .setInterpolator(OvershootInterpolator(1.2f))
    }

    /** 挂窗并原地淡入弹回（scale 0.85→1，250ms）：面板期间窗体被移除，关闭后补回场动画。 */
    fun attachAnimated(): Boolean {
        if (!attach()) return false
        view?.let(::animateRestoreIn)
        return true
    }

    // ---- 键盘让位 ----

    /** 键盘弹出时上抬，保证「球底 + 边距 + 额外抬升」不压输入栏；拖动期间只记账，拖完由 [onDragEnded] 结算。*/
    fun updateIme(newImeBottomPx: Int) {
        if (newImeBottomPx == imeBottomPx) return
        this.imeBottomPx = newImeBottomPx
        if (dragging) return
        animateImeLift(computeImeLift(newImeBottomPx).toFloat())
    }

    /** 抬升缓动：从当前值到目标值，逐帧落窗口 + 通知镜像。目标差值极小直接落定。*/
    private fun animateImeLift(target: Float) {
        imeAnimator?.cancel()
        imeAnimator = null
        if (kotlin.math.abs(imeLiftPx - target) < 0.5f) {
            imeLiftPx = target
            applyWindowBounds()
            onImeLiftChanged(imeLiftPx)
            return
        }
        imeAnimator = ValueAnimator.ofFloat(imeLiftPx, target).apply {
            duration = IME_LIFT_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (imeAnimator !== anim) return@addUpdateListener
                imeLiftPx = anim.animatedValue as Float
                applyWindowBounds()
                onImeLiftChanged(imeLiftPx)
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
        // ImeMargin 防贴键盘顶；ImeExtraLift 再抬高一段 —— 键盘上方通常是应用的输入栏/发送按钮。
        val marginPx = DensityProvider.dp2px(FloatBallDefaults.ImeMarginDp + FloatBallDefaults.ImeExtraLiftDp)
        val overflow = (posY).roundToInt() + ballPx + marginPx - (screenHeightPx - imeBottomPx)
        // 让位只作用于显示位置，基准 posY 不动；封顶在「球顶正好到 LP 原点」。
        return max(0, overflow).coerceAtMost(max(0, (posY).roundToInt() - lpOriginY))
    }

    // ---- 触摸 → 定位 ----

    private fun onDragged(dx: Float, dy: Float) {
        if (!dragging) {
            dragging = true
            cancelSettle()
            cancelImeAnim()
            cancelIdle()
        }
        posX += dx
        posY += dy
        applyWindowBounds()
    }

    private fun onDragEnded() {
        if (!dragging) return
        dragging = false
        finishDrag()
    }

    /** 贴边结算 + 落盘（本窗拖动与分身接力拖共用）。调用前应已置 dragging=false。 */
    private fun finishDrag() {
        // 拖动中键盘可能起落过：结算一次让位，再夹取。
        imeLiftPx = computeImeLift(imeBottomPx).toFloat()
        applyWindowBounds()
        // 松手后球贴到最近边缘（完整球在屏内，不留缝）；用短程缓动而不是瞬移。落盘记终点。
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            val maxX = max(0, screenWidthPx - ballPx).toFloat()
            val targetX = if (posX * 2f + ballPx < screenWidthPx) 0f else maxX
            settleToEdge(targetX)
            onDragFinished?.invoke(targetX / screenWidthPx, posY / screenHeightPx)
        }
        scheduleIdle()
    }

    // ---- 松手贴边（缓动） ----

    private fun cancelSettle() {
        settleAnimator?.cancel()
        settleAnimator = null
    }

    /** 当前球窗顶边屏幕坐标（px）。分身换位/搬球时由 Service 读取。 */
    fun currentPosition(): Pair<Float, Float> = posX to posY

    /**
     * 自动挪球（跟随持机手）：淡出 → 传送 → 淡回（与分身换位同款视觉）。
     * 拖动/按压/换位中让位给用户并返回 false（调用方不应落盘）；淡出期间被
     * 用户接管拖动则放弃传送。目标与当前几乎一致时不动画直接算成功。
     */
    fun repositionTo(leftEdge: Boolean, yRatio: Float): Boolean {
        if (dragging || crossFading || pressed) return false
        val target = view ?: return false
        val targetX = if (leftEdge) 0f else max(0, screenWidthPx - ballPx).toFloat()
        val maxY = max(lpOriginY, screenHeightPx - ballPx - bottomInsetPx)
        val targetY = (yRatio * screenHeightPx).coerceIn(lpOriginY.toFloat(), maxY.toFloat())
        if (kotlin.math.abs(targetX - posX) < 0.5f && kotlin.math.abs(targetY - posY) < 0.5f) {
            scheduleIdle()
            return true
        }
        cancelSettle()
        cancelIdle()
        idled = false
        moveAnimator?.cancel()
        crossFading = true
        visualAnimator?.cancel()
        target.animate()
            .alpha(0f)
            .setDuration(150L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                crossFading = false
                // 淡出期间用户上手拖动：放弃传送恢复可见，球留在用户手里。
                if (dragging) {
                    applyVisual()
                    return@withEndAction
                }
                posX = targetX
                posY = targetY
                target.translationX = 0f
                applyWindowBounds()
                applyVisual()
                scheduleIdle()
            }
            .start()
        return true
    }

    /** 主球是否处于静置变暗态（交换后保持姿态用）。 */
    fun isIdled(): Boolean = idled

    /** 当前键盘让位量（px）。分身 y 跟随用。 */
    fun currentImeLiftPx(): Float = imeLiftPx

    /** IME insets 已由窗口事件送达；服务侧可跳过轮询。 */
    fun imeInsetsAvailable(): Boolean = imeInsetsAvailable

    /**
     * 交换的淡入淡出：淡出 → 传送到目标贴边位 → 淡回，[endIdled] 决定落位姿态。
     * 淡出期间分身已开始接力拖时，落点加上累计位移并直接进入拖动态。
     */
    fun crossFadeSwap(targetX: Float, targetY: Float, endIdled: Boolean, onDone: () -> Unit) {
        val target = view
        crossFading = true
        if (target == null) {
            posX = targetX
            posY = targetY
            idled = endIdled
            crossFading = false
            onDone()
            return
        }
        cancelSettle()
        visualAnimator?.cancel()
        target.animate()
            .alpha(0f)
            .setDuration(150L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                crossFading = false
                posX = targetX + externalAccX
                posY = targetY + externalAccY
                externalAccX = 0f
                externalAccY = 0f
                applyWindowBounds()
                if (pendingExternalFinish) {
                    // 接力拖在淡出期间已抬手：落位（含累计位移）后代为结算贴边 + 落盘。
                    pendingExternalFinish = false
                    idled = false
                    pressed = false
                    target.translationX = 0f
                    applyVisual()
                    onDone()
                    finishDrag()
                } else if (externalDrag) {
                    // 接力拖已开始：实心拖动态落位，后续位移走增量。
                    idled = false
                    dragging = true
                    pressed = true
                    target.translationX = 0f
                    applyVisual()
                } else {
                    idled = endIdled
                    pressed = false
                    // 落位姿态直接摆好（平移不参与动画），alpha 由 applyVisual 淡回。
                    target.translationX = if (idled) {
                        val dir = if (posX * 2f + ballPx < screenWidthPx) -1f else 1f
                        dir * FloatBallDefaults.IdleOutRatio * ballPx
                    } else 0f
                    applyVisual()
                }
                onDone()
            }
            .start()
    }

    // ---- 分身接力拖 ----

    private var externalDrag = false
    private var crossFading = false
    private var externalAccX = 0f
    private var externalAccY = 0f

    /** 分身侧按下：接力拖待命（抬手若未拖动就是纯点按，无副作用）。 */
    fun beginExternalDrag() {
        externalDrag = true
    }

    /** 分身侧每帧位移增量。淡出期间先记账，落位后实时跟手。 */
    fun externalDragBy(dx: Float, dy: Float) {
        if (!externalDrag) return
        if (crossFading) {
            externalAccX += dx
            externalAccY += dy
            return
        }
        if (!dragging) {
            dragging = true
            cancelSettle()
            cancelIdle()
            pressed = true
            applyVisual()
        }
        posX += dx
        posY += dy
        applyWindowBounds()
    }

    /** 分身侧抬手：结束接力拖；淡出未结束时由落位逻辑代为结算。 */
    fun endExternalDrag() {
        if (!externalDrag) return
        externalDrag = false
        if (crossFading) {
            // 落位后需结算贴边 + 落盘：把收尾挂到 endAction 之后。
            pendingExternalFinish = true
            return
        }
        if (dragging) {
            dragging = false
            pressed = false
            finishDrag()
        }
    }

    private var pendingExternalFinish = false

    private fun settleToEdge(targetX: Float) {
        val fromX = posX
        if (kotlin.math.abs(targetX - fromX) < 0.5f) return
        cancelSettle()
        settleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
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

    // ---- 静置 ----

    private fun scheduleIdle() {
        idleRunnable?.let(handler::removeCallbacks)
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

    /** 移除静置计时并退出变暗态。重启交给抬手（onPressChanged=false）。 */
    private fun cancelIdle() {
        idleRunnable?.let(handler::removeCallbacks)
        idleRunnable = null
        if (idled) {
            idled = false
            applyVisual()
        }
    }

    // ---- 外观（View 层） ----

    /** 按压/静置的 alpha、scale 走 View 层而非 Compose：服务进程托管的 ComposeView 重组不上屏。 */
    private var visualAnimator: ViewPropertyAnimator? = null

    private fun applyVisual() {
        val target = view ?: return
        // 淡出（repositionTo/crossFadeSwap）与本方法共用同一个 ViewPropertyAnimator
        // 实例，走到这里意味着在飞的淡出已被下面的 cancel 中止且 withEndAction 不会
        // 运行 —— crossFading 一并收掉，否则按压球打断淡出后它会永久卡在 true。
        crossFading = false
        val scale = if (pressed) FloatBallDefaults.PressScale else 1f
        val alpha = if (idled) FloatBallDefaults.IdleDimAlpha else 1f
        // 静置时球横向移出一截到屏幕外，方向 = 最近边缘。偏移走 View 平移：
        // 部分 ROM 会把越界 LP 夹回屏内，LP 方案不可靠。窗口留在贴边位，
        // 命中区仍是窗口方形，覆盖可见半圆。
        val shiftPx = if (idled) {
            val out = FloatBallDefaults.IdleOutRatio
            if (posX * 2f + ballPx < screenWidthPx) -out else out
        }           else 0f
        visualAnimator?.cancel()
        visualAnimator = target.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(alpha)
            .translationX(shiftPx * ballPx)
            .setDuration(if (pressed) 120L else 240L)
            .setInterpolator(DecelerateInterpolator())
    }

    // ---- 窗口定位 ----

    private fun createLayoutParams() = ballLayoutParams(ballPx)

    private fun lpSize(current: View) {
        applyBallSize(current, wm, ballPx)
    }

    /** 叠加底栏边界的 best-effort。顶边不用它 —— 由 [lpOriginY] 实测更可靠。 */
    private fun applySystemInsets() {
        runCatching {
            val insets = view?.rootWindowInsets ?: return
            bottomInsetPx = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        }.onFailure { Log.w(TAG, "insets unavailable: ${it.message}") }
    }

    /**
     * 夹取 + 写 LP：`posX`/`posY` 是屏幕空间的球顶位置，LP y = 屏幕值 − [lpOriginY]。
     * 键盘让位只进显示值，不碰基准 posY，否则键盘起落会把基准永久顶离原地。
     */
    private fun applyWindowBounds() {
        val current = view ?: return
        val maxX = max(0, screenWidthPx - ballPx)
        val maxY = max(lpOriginY, screenHeightPx - ballPx - bottomInsetPx)
        posX = posX.coerceIn(0f, maxX.toFloat())
        posY = posY.coerceIn(lpOriginY.toFloat(), maxY.toFloat())
        // 显示位置 = 基准 − 让位，但不越过 LP 原点。
        val displayY = max(lpOriginY.toFloat(), posY - imeLiftPx)
        writeBallPosition(current, wm, posX, displayY, lpOriginY)
    }

    // ---- Compose ----

    @Composable
    private fun BallContent() {
        BallWindowContent(ballPx) { size -> BallFace(modifier = Modifier.size(size)) }
    }

    @Composable
    private fun BallFace(modifier: Modifier = Modifier) {
        val size = with(LocalDensity.current) { ballPx.toDp() }
        val accent = gestureButtonMarkColor(settings.color)
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.92f), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val icon = actionIcon(settings.tapAction ?: StoredAction.of(Action.NONE_TYPE_ID))
                when (icon) {
                    is ImageVector -> Image(
                        imageVector = icon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.size(size / 2),
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

    private companion object {
        const val TAG = LOG_TAG

        /** 键盘让位缓动时长：足够平滑，又不拖泥带水。 */
        const val IME_LIFT_ANIM_MS = 220L
    }
}
