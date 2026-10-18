package hunoia.luno.runtime.floatball

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
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
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.theme.SideGestureTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 球窗口。交互、参数完全按一墨的球：
 *
 * - 按住即拖（touchSlop 分界，无长按门槛），松手**吸附到最近的左/右边缘**，
 *   球全程完整在屏内；
 * - 按下缩小到 0.9、松手回弹；
 * - 静置超时整体变暗（alpha）并缩小（scale），任意触摸立即恢复；
 * - 键盘弹出时上抬让位（[updateIme]，拖动期间不抬，结算后补算）；
 * - 锁屏不可见不可点；解锁恢复。
 *
 * 窗口尺寸 = 球直径，恒定不变；坐标语义是球窗顶边（左上角），
 * 改 [posX]/[posY] 后统一经 [syncWindowPosition] 落到 LP。
 */
class FloatBallWindow(
    private val context: android.content.Context,
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
            // 抬手统一重启静置计时：点按结束时尤其必须 —— 否则计时器
            // 死于按下，球从此再不变暗（真机首个 bug 现场就是它）。
            if (!pressed) scheduleIdle()
            applyVisual()
        },
        onDragEnded = { onDragEnded() },
    )

    // ---- 定位 ----

    /** 球窗顶边（px）。状态而非局部变量：Compose 拖动感不依赖它，但更新要落 LP。 */
    private var posX = 0f
    private var posY = 0f

    /** 键盘抬升量（px）。显示位置 = 基准位 - 抬升量。 */
    private var imeLiftPx = 0f

    /** 最近一次上报的键盘底边（px）。拖动中如收起键盘，拖完结算按此值归零。 */
    private var imeBottomPx = 0

    /** 系统栏边界（px）。取不到时为 0，夹取退化为全屏。 */
    private var bottomInsetPx = 0

    /**
     * LP 坐标系原点在屏幕上的 y。2038 悬浮窗的 LP y 不是屏幕坐标 —— 实测
     * 原点在**状态栏下沿**（parent frame 顶边），若按「y=0 是屏幕顶」算夹取，
     * 球在底部会被裁掉状态栏高度的一截。addView 后用 getLocationOnScreen 实测，
     * 测出前按 0 处理（首帧短暂偏差可接受，实测后立即纠正）。
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
        Log.i(TAG, "ball addView ok size=${ballPx}px at(${(posX).roundToInt()},${(posY).roundToInt()})")
        // 实测 LP 原点（状态栏高度）：窗口摆出来才知道真实屏幕落点。
        created.post {
            val attached = view ?: return@post
            val location = IntArray(2)
            attached.getLocationOnScreen(location)
            val measured = location[1] - ((attached.layoutParams as? WindowManager.LayoutParams)?.y ?: 0)
            if (measured != lpOriginY) {
                lpOriginY = measured
                applyWindowBounds()
            }
            Log.i(TAG, "lp origin y=$lpOriginY")
        }
        return true
    }

    fun detach() {
        arbiter.cancel()
        settleAnimator?.cancel()
        settleAnimator = null
        visualAnimator?.cancel()
        visualAnimator = null
        idleRunnable?.let(handler::removeCallbacks)
        idleRunnable = null
        view?.let(context::removeWindow)
        view = null
    }

    /** 锁屏 / 息屏：整个窗口不可见也不可点，解锁恢复。 */
    fun setHidden(hidden: Boolean) {
        val current = view ?: return
        val lp = current.layoutParams as? WindowManager.LayoutParams ?: return
        if (hidden) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            lp.alpha = 0f
        } else {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            lp.alpha = 1f
        }
        wm.safeUpdateViewLayout(current, lp)
        Log.i(TAG, "setHidden($hidden)")
        if (!hidden) scheduleIdle() else cancelIdle()
    }

    // ---- 键盘让位（一墨 A() 逻辑） ----

    /**
     * 键盘弹出时把球上抬，保证「球底 + 边距」不压到键盘顶边；
     * 收起归零。拖动期间只记账不改位置 —— 拖完由 [onDragEnded] 统一结算。
     * 轮询按值去重：值没变直接返回，不刷日志也不空转 binder。
     */
    fun updateIme(newImeBottomPx: Int) {
        if (newImeBottomPx == imeBottomPx) return
        this.imeBottomPx = newImeBottomPx
        if (dragging) {
            Log.i(TAG, "ime=$newImeBottomPx deferred (dragging)")
            return
        }
        imeLiftPx = computeImeLift(newImeBottomPx).toFloat()
        applyWindowBounds()
        Log.i(TAG, "ime=$newImeBottomPx lift=${(imeLiftPx).roundToInt()}")
    }

    private fun computeImeLift(imeBottomPx: Int): Int {
        if (imeBottomPx <= 0) return 0
        val marginPx = DensityProvider.dp2px(FloatBallDefaults.ImeMarginDp)
        val overflow = (posY).roundToInt() + ballPx + marginPx - (screenHeightPx - imeBottomPx)
        // 让位只作用于**显示位置**，基准 posY 不动；封顶在「球顶正好到 LP 原点」。
        return max(0, overflow).coerceAtMost(max(0, (posY).roundToInt() - lpOriginY))
    }

    // ---- 触摸 → 定位 ----

    private fun onDragged(dx: Float, dy: Float) {
        if (!dragging) {
            dragging = true
            cancelSettle()
            cancelIdle()
        }
        posX += dx
        posY += dy
        applyWindowBounds()
    }

    private fun onDragEnded() {
        if (!dragging) return
        dragging = false
        // 拖动中键盘可能起落过：结算一次让位，再夹取。
        imeLiftPx = computeImeLift(imeBottomPx).toFloat()
        applyWindowBounds()
        // 一墨实测行为：松手后球**贴到最近边缘**（完整球在屏内，不留缝）。
        // 吸附走短程缓动 —— 之前瞬移版被反馈「力度太大」。落盘记终点。
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            val maxX = max(0, screenWidthPx - ballPx).toFloat()
            val targetX = if (posX * 2f + ballPx < screenWidthPx) 0f else maxX
            settleToEdge(targetX)
            onDragFinished?.invoke(targetX / screenWidthPx, posY / screenHeightPx)
            Log.i(TAG, "drag end at(${(posX).roundToInt()},${(posY).roundToInt()}) -> edge(${targetX.roundToInt()}) lift=${(imeLiftPx).roundToInt()}")
        }
        scheduleIdle()
    }

    // ---- 松手贴边（缓动） ----

    private fun cancelSettle() {
        settleAnimator?.cancel()
        settleAnimator = null
    }

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
            Log.i(TAG, "idle dim on")
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

    /**
     * 按压/静置的 alpha、scale 落在 View 层（ViewPropertyAnimator）。不走
     * Compose：服务进程托管的 ComposeView 里状态推进了、重组却不上屏
     * （"idle dim on" 连打、画面不动），View 层动画在任意窗口类型下都可靠。
     */
    private var visualAnimator: ViewPropertyAnimator? = null

    private fun applyVisual() {
        val target = view ?: return
        val scale = when {
            pressed -> FloatBallDefaults.PressScale
            idled -> FloatBallDefaults.IdleShrinkScale
            else -> 1f
        }
        val alpha = if (idled) FloatBallDefaults.IdleDimAlpha else 1f
        visualAnimator?.cancel()
        visualAnimator = target.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(alpha)
            .setDuration(if (pressed) 120L else 240L)
            .setInterpolator(DecelerateInterpolator())
    }

    // ---- 窗口定位 ----

    private fun createLayoutParams() = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        // 一墨同款：NOT_FOCUSABLE | NOT_TOUCH_MODAL。不加 LAYOUT_NO_LIMITS ——
        // 球必须完整在屏内，靠正常窗口边界 + 夹取就够。
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        width = ballPx
        height = ballPx
        @SuppressLint("RtlHardcoded")
        gravity = Gravity.LEFT or Gravity.TOP
        x = 0
        y = 0
    }

    private fun lpSize(current: View) {
        current.layoutParams?.let {
            if (it is WindowManager.LayoutParams) {
                it.width = ballPx
                it.height = ballPx
                wm.safeUpdateViewLayout(current, it)
            }
        }
    }

    /** 叠加底栏边界的 best-effort。顶边不用它 —— 由 [lpOriginY] 实测更可靠。 */
    private fun applySystemInsets() {
        runCatching {
            val insets = view?.rootWindowInsets ?: return
            bottomInsetPx = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        }.onFailure { Log.w(TAG, "insets unavailable: ${it.message}") }
        Log.i(TAG, "insets bottom=$bottomInsetPx")
    }

    /**
     * 夹取 + 写 LP。坐标分两层：
     * - `posX`/`posY` 是**屏幕空间**的球顶位置（夹取、持久化都用它）；
     * - LP y = 屏幕值 − [lpOriginY]（LP 原点在状态栏下沿）。
     * 键盘让位只进显示值，不碰基准 posY —— 否则键盘起落会把基准永久顶离原地。
     */
    private fun applyWindowBounds() {
        val current = view ?: return
        val lp = current.layoutParams as? WindowManager.LayoutParams ?: return
        val maxX = max(0, screenWidthPx - ballPx)
        val maxY = max(lpOriginY, screenHeightPx - ballPx - bottomInsetPx)
        posX = posX.coerceIn(0f, maxX.toFloat())
        posY = posY.coerceIn(lpOriginY.toFloat(), maxY.toFloat())
        // 显示位置 = 基准 − 让位，但不越过 LP 原点（状态栏下沿）。
        val displayY = max(lpOriginY.toFloat(), posY - imeLiftPx)
        val nextX = (posX).roundToInt()
        val nextY = (displayY - lpOriginY).roundToInt().coerceAtLeast(0)
        if (lp.x == nextX && lp.y == nextY) return
        lp.x = nextX
        lp.y = nextY
        wm.safeUpdateViewLayout(current, lp)
    }

    // ---- Compose ----

    @Composable
    private fun BallContent() {
        SideGestureTheme {
            val density = LocalDensity.current
            val size = with(density) { ballPx.toDp() }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                BallFace(modifier = Modifier.size(size))
            }
        }
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
                val icon = actionIcon(settings.tapAction ?: StoredAction.of("internal.none"))
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
        const val TAG = "LunoLauncher"
    }
}
