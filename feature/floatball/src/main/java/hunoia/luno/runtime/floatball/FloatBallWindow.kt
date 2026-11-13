package hunoia.luno.runtime.floatball

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import hunoia.luno.core.LunoLog as Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.systemBarsSafeAreaOrNull
import hunoia.luno.bridge.ui.SystemBarsSafeArea
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.inbox.BadgeState
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.TextIconGlyph
import hunoia.luno.ui.component.actionIcon
import hunoia.luno.ui.theme.toMarkColor
import hunoia.luno.ui.theme.MotionFamily
import hunoia.luno.ui.theme.MotionPress
import hunoia.luno.ui.theme.MotionSettle
import hunoia.luno.ui.theme.motionMillis
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
    /** 窗口收到 IME insets 事件：服务侧立即轮询一次绝对值（事件值仅作触发信号，不直接采信）。 */
    private val onImeInsetsHint: (Int) -> Unit = {},
    /** 内容球固定图标（通知球 / 建议球）；null = 按单击动作的图标。 */
    private val fixedIcon: Any? = null,
    /** 无徽标时的内容描述；null = 智能球文案。 */
    private val idleDescription: String? = null,
    /** 有未读时的内容描述生成器；null = 智能球未读文案。 */
    private val badgeDescription: ((Int) -> String)? = null,
) {
    private val wm: WindowManager get() = context.windowManager()

    private var view: View? = null
    private var rootView: FrameLayout? = null
    private var composeView: ComposeView? = null
    private var badgeView: TextView? = null
    private var ballPx = 0

    /** Compose 会读取动作图标 / 颜色：必须是快照状态，否则配置更新不重组。 */
    private var settings by mutableStateOf(FloatBallSettings())

    /** 当前朝向：决定读 portrait 还是 landscape 落点。 */
    private var landscape = false

    private val arbiter = FloatBallTouchArbiter(
        onSlideDragged = { dx, dy -> onDragged(dx, dy) },
        onTapped = { onTap?.invoke() },
        onPressChanged = { pressed ->
            this.pressed = pressed
            // 抬手统一重启静置计时：点按结束尤其必须 —— 否则计时器停在按下时，球不再变暗。
            if (!pressed) scheduleIdle()
            // 隐藏路径的 arbiter.cancel 会走到这里：那一下 applyVisual 会把在飞的
            // 挪球淡出连 endAction 掐掉（传送被跳过），抑制之。
            if (!visualSuppressed) applyVisual()
        },
        onDragEnded = { onDragEnded() },
        onLongPress = {
            view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        },
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

    /** 系统栏安全区（屏幕绝对坐标，只约束上下）。取不到时沿用上一次。 */
    private var barSafeArea = SystemBarsSafeArea()

    /**
     * LP 坐标系原点在屏幕上的 y。带 LAYOUT_IN_SCREEN 后应为 0；个别 ROM 仍以
     * 状态栏下沿为原点，保留实测兜底。状态栏显隐会同时改原点与安全区，因此
     * 变化时必须先测原点再读安全区，避免两段修正把球推得跳一下。
     */
    private var lpOriginY = 0

    /** insets 变化后的合并复测：先测 LP 原点，再读安全区，最后只写一次窗口。
     *  状态栏显隐会同时改变两者；先读安全区会用旧原点把球多推一次，表现为跳一下再回弹。 */
    private val remeasureSystemInsets = Runnable {
        postMeasureLpOriginY({ view }) { measured ->
            lpOriginY = measured
            applySystemInsets()
        }
    }

    /** 实测 LP 原点（布局原点随状态栏显隐 / 转屏变化），测完回写并重夹一次。 */
    private fun remeasureLpOriginY() {
        postMeasureLpOriginY({ view }) { measured ->
            lpOriginY = measured
            applyWindowBounds()
        }
    }

    private var dragging = false
    private var pressed = false
    private var idled = false

    /** 自动挪球的「淡出→传送」是否在飞：期间拒绝新的传送，避免两段 endAction 抢写位置。 */
    private var transferring = false

    /** 在飞的换边传送目标与回调：淡出被隐藏/回场打断时改为立即结算，不再丢传送。 */
    private var pendingTransferX = 0f
    private var pendingTransferY = 0f
    private var pendingTransferLanded: (() -> Unit)? = null

    /** 气泡在屏期间抑制静置变暗/半出屏，避免气泡锚点与球之间留白。 */
    private var idleSuppressed = false

    /** 胶囊展开期间球窗整体隐去：胶囊从球的位置原地长出，视觉上仍是同一个物体。 */
    private var capsuleActive = false

    /** 隐藏路径置位：抑制 arbiter.cancel 经 onPressChanged 触发的 applyVisual。 */
    private var visualSuppressed = false

    // ---- 回调 ----

    /** 抬手点按（未超 touchSlop）。Service 决定派发动作还是开动作面板。 */
    var onTap: (() -> Unit)? = null

    /** 长按（≥500ms 且未移动）：打开 / 展开共享气泡。 */
    var onLongPress: (() -> Unit)? = null

    /** 开始拖动：服务侧据此立即收起气泡，且本轮不再自动重开。 */
    var onDragStarted: (() -> Unit)? = null

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
        if (!sizeChanged) return
        val nextPx = sizePx(next)
        if (nextPx == ballPx) return
        if (view == null) {
            ballPx = nextPx
            return
        }
        // 面球尺寸由 Compose 组合驱动，而服务进程托管的 ComposeView 重组不上屏：
        // 改尺寸只能拆窗重挂（新窗首组合直接按新球径画）。拆前记住纵向落点，
        // 重挂后按原侧别贴边重吸附（归一 x 乘新窗尺寸会留缝）。
        val yRatio = if (screenHeightPx > 0) posY / screenHeightPx else settings.position(landscape).y
        // 拆窗重挂会经 attach 读落盘侧别；这里以拆前实位为准，避免存量落后于内存态时换边。
        val wasLeft = isOnLeft()
        detach()
        ballPx = nextPx
        attachAnimated()
        posY = yRatio * screenHeightPx
        snapToNearestEdge(wasLeft)
        // 只重摆静置平移，不碰复场的 alpha/scale 动画。
        view?.translationX = if (idled) idleShiftPx() else 0f
        applyWindowBounds()
    }

    /** 转屏：换用该朝向记忆的落点。返回是否真的换了朝向。 */
    fun setOrientation(landscape: Boolean): Boolean {
        if (this.landscape == landscape) return false
        this.landscape = landscape
        if (view == null) return true
        // 转屏路径可能先由运行态观察者触发（早于 Service.onConfigurationChanged 的失效）：
        // 这里自己失效一次，避免按旧朝向的缓存宽高算贴边位。
        DensityProvider.invalidateMetrics()
        // 安全区必须在算落点之前就位：否则新朝向的贴边 y 会被旧朝向的状态栏高度先
        // 夹到新位置，安全区随后变小也不会回弹，留下一条假间距。
        // 这里只读不夹：旧 posX 在新屏宽下夹出来的中间值没有意义。
        barSafeArea = readBarSafeArea() ?: barSafeArea
        // 侧别必须按目标朝向自己那份落点判：旧朝向的像素 x 比新屏宽会在屏宽变化时
        // 翻转侧别（竖屏贴右 → 横屏变左）；y 同样取该朝向记忆值。
        cancelSettle()
        posX = savedEdgeX(landscape)
        posY = settings.position(landscape).y * screenHeightPx
        applyWindowBounds()
        // 静置外移量与侧别/屏宽耦合，旧朝向的 translationX 会在新朝向上表现为
        // 「偏离边缘 / 半颗球被裁」，必须按新位重摆。
        realignVisualAfterReposition()
        // LP 原点要等系统按新朝向重排后测才准，转屏后补测一次。
        view?.post { remeasureLpOriginY() }
        return true
    }

    /**
     * 球当前可见矩形（屏幕坐标）。静置半出屏时用平移后的实际可见边，
     * 气泡据此贴边，不会飘到球和屏幕之间。
     */
    internal fun visibleRectOnScreen(): BubbleAnchor? {
        if (view == null) return null
        val displayY = max(lpOriginY.toFloat(), posY - imeLiftPx)
        val shift = if (idled) idleShiftPx() else 0f
        val left = (posX + shift).roundToInt()
        val top = displayY.roundToInt()
        return BubbleAnchor(left, top, left + ballPx, top + ballPx)
    }

    /** 球上徽标：只在气泡隐藏时显示；[badge] 为 null / 不可见时清空。 */
    internal fun setBadge(badge: BadgeState?) {
        val tv = badgeView ?: return
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

    /** 建窗口。false = 没建起来（未授权 / 系统拒绝）。 */
    fun attach(): Boolean {
        if (view != null) return true
        ballPx = sizePx(settings)
        val compose = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            // RESUMED：球图标/颜色由快照状态驱动，CREATED 下重组不会上屏。
            FloatBallLifecycleOwner.createAndAttachTo(this, active = true)
            setContent { BallContent() }
        }
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
        val root = FrameLayout(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                badge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    dp(17),
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    topMargin = dp(1)
                    marginStart = dp(1)
                },
            )
        }
        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelSettle()
                    cancelIdle()
                }
            }
            arbiter.onTouch(event)
        }
        // IME 让位统一用 display 绝对参考系（服务侧 currentWindowMetrics 轮询）。
        // 小窗口的 onApplyWindowInsets 给的是「窗口自身被键盘重叠的量」，参考系随窗口
        // 位置漂移：球被抬升 → 窗口上移 → insets 变小 → 抬升量变小 → 循环，表现为
        // 键盘弹出后上下抖动。事件在这里只作为「立即轮询一次」的触发信号。
        root.setOnApplyWindowInsetsListener { v, insets ->
            // 状态栏 / 导航栏尺寸变化同样会让窗口 LP 原点移动（横屏状态栏显隐尤其明显），
            // 事件值是窗口参考系、会随球的位置漂移，不能直接采信：合并一拍后按绝对
            // 参考系（currentWindowMetrics）复测安全区与 LP 原点。
            handler.removeCallbacks(remeasureSystemInsets)
            handler.postDelayed(remeasureSystemInsets, SYSTEM_INSETS_REMEASURE_MS)
            // IME 的 insets 值参考系会随窗口位置漂移，只当作「立即复测」的信号；
            // 隐藏（bottom 归零）同样要触发复测，否则只能等兜底轮询；
            // 但事件值只作信号，是否代表推播可用由服务侧按 ime>0 判定。
            onImeInsetsHint(insets.getInsets(WindowInsets.Type.ime()).bottom)
            v.onApplyWindowInsets(insets)
        }
        root.requestApplyInsets()
        // owner 必须也在 root 上：Compose 建 recomposer 时查的是 view.rootView（最外层）
        // 而非 ComposeView 自己 —— 只设在 Compose 上，root 树查不到 owner 会直接崩
        //（ViewTreeLifecycleOwner not found from FrameLayout），服务循环崩溃、球永远出不来。
        FloatBallLifecycleOwner.createAndAttachTo(root, active = true)
        val lp = createLayoutParams()
        if (!wm.safeAddView(root, lp)) {
            Log.e(TAG, "ball addView failed (overlay permission or window rejected)")
            return false
        }
        view = root
        rootView = root
        composeView = compose
        badgeView = badge
        // attach 当下 rootWindowInsets 必然为空：等下一次遍历再补一次 best-effort，
        // 主路径是上面的 insets 监听。
        root.post { applySystemInsets() }
        // x/y 都取该朝向落盘的落点：x 只保留侧别后贴边重吸附（归一 x 乘新球径会留缝）。
        // 必须先定 posX/posY 再摆静置平移，否则 idleShiftPx 会按 posX 初值（0 = 左）算方向。
        posX = savedEdgeX(landscape)
        posY = settings.position(landscape).y * screenHeightPx
        applyWindowBounds()
        scheduleIdle()
        // detach 不清 idled：锁屏隐藏规则/总闸这类拆窗重建（enabled false→true），
        // 直接把静置终态摆上（不走 applyVisual 的动画 —— 否则解锁瞬间能看见一次
        // 「全亮 → 变暗半出屏」的静置过渡）；进程级重启是新对象，照常醒着起步。
        if (idled) {
            root.alpha = FloatBallDefaults.IdleDimAlpha
            root.translationX = idleShiftPx()
        }
        // 实测 LP 原点（状态栏高度）：窗口摆出来才知道真实屏幕落点。
        postMeasureLpOriginY({ view }) { measured ->
            lpOriginY = measured
            applyWindowBounds()
        }
        return true
    }

    /**
     * 摘窗。animated=true 先淡出再移除（规则/总闸/权限的重建路径不再生硬消失）；
     * view 字段立即置空 —— 淡出中的旧窗由局部引用收尾，期间 attach 走全新挂窗不冲突。
     */
    fun detach(animated: Boolean = false) {
        val current = view
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
        handler.removeCallbacks(remeasureSystemInsets)
        // 窗口既已摘除，交互/换位中的标志全部作废：淡出的 withEndAction 在 cancel 时
        // 不运行，transferring 残留 true 会把重挂后的 repositionTo 永久挡死。
        transferring = false
        pendingTransferLanded = null
        dragging = false
        pressed = false
        // 摘窗即复位：下次 attach 的新窗口按可见起步，lock/panel 状态由服务侧重新推。
        lockHidden = false
        panelHidden = false
        visuallyHidden = false
        capsuleActive = false
        idleSuppressed = false
        view = null
        rootView = null
        composeView = null
        badgeView = null
        if (current == null) return
        if (!animated) {
            context.removeWindow(current)
            return
        }
        applyBallTouchFlags(current, wm, hidden = true)
        current.animate()
            .alpha(0f)
            .setDuration(motionMillis(MotionFamily.Floating).toLong())
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { context.removeWindow(current) }
            .start()
    }

    /** 锁屏 / 息屏：整个窗口不可见也不可点，解锁恢复。 */
    fun setHidden(hidden: Boolean) {
        lockHidden = hidden
        applyHiddenFlags()
    }

    /**
     * 面板/临时隐藏：软隐藏 —— 窗口保留（组合与 Recomposer 不销毁），只置不可见不可点。
     * 相比摘窗，免掉每次面板开合的 removeView→新 ComposeView→首帧组合→addView 整链。
     * 显隐过渡统一带动画：隐藏淡出、恢复走 [animateRestoreIn] 回场。
     */
    fun setPanelHidden(hidden: Boolean) {
        panelHidden = hidden
        applyHiddenFlags()
    }

    private var lockHidden = false
    private var panelHidden = false

    /** 软隐藏的视觉态（lp 旗标 + view alpha 动画的权威）：状态没变不重播过渡。 */
    private var visuallyHidden = false

    private fun applyHiddenFlags() {
        val current = view ?: return
        val hidden = lockHidden || panelHidden || capsuleActive
        if (hidden == visuallyHidden) return
        visuallyHidden = hidden
        if (hidden) {
            // 窗口不可点后事件流就断了：重置在途按压/拖动（与 detach 的 arbiter.cancel 对齐），
            // 免得恢复后停在 pressed 视觉态。idled 刻意保留 —— 恢复后回到原本的静置变暗姿态。
            // 期间抑制 onPressChanged 的 applyVisual：恰逢换位/挪球淡出时，那一下会把
            // 动画连 endAction 一起取消，传送被跳过。让淡出飞完，落位照常结算。
            visualSuppressed = true
            arbiter.cancel()
            cancelIdleTimer()
            visualSuppressed = false
            // 在飞的换边淡出马上会被下面的隐藏淡出取消：先立即结算传送，
            // 否则 endAction 被取消、换边静默丢失（触钮弹面板路径必现）。
            settlePendingTransferNow()
            // 立即不可点防误触，视觉淡出 150ms，播完 lp alpha 收底。
            applyBallTouchFlags(current, wm, hidden = true)
            current.animate()
                .alpha(0f)
                .setDuration(motionMillis(MotionFamily.Floating).toLong())
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { settleBallWindowAlpha(current, wm) }
                .start()
        } else {
            // 淡出中途反向：取消淡出（其收底 endAction 不跑，lp alpha 还是 1），直接回场。
            current.animate().cancel()
            applyBallTouchFlags(current, wm, hidden = false)
            animateRestoreIn(current)
            scheduleIdle()
        }
    }

    /** 面板关闭后的回场：与 [attachAnimated] 同款 250ms overshoot，落在既有窗口上。
     *  终态 alpha 跟随 idled —— 隐藏前在静置变暗，恢复后仍是变暗姿态。 */
    private fun animateRestoreIn(target: View) {
        visualAnimator?.cancel()
        // 在飞的换边淡出被本动画取消时 endAction 不会运行：立即结算传送（而不是丢弃，
        // 那会让关面板后球停在旧侧且无人补做），再从新位回场。
        settlePendingTransferNow()
        target.alpha = 0f
        target.scaleX = 0.85f
        target.scaleY = 0.85f
        // 消费隐形结算的落窗：此刻 alpha 已置 0，瞬移不可见；平移按静置姿态重摆，
        // 否则残留的是旧位的半出屏方向。
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
        // 让位只作用于显示位置，基准 posY 不动；封顶在球顶正好到安全区上界
        // （横屏 LP 原点为 0 时，若按 LP 原点封顶，键盘会把球顶上状态栏）。
        val minY = BallPlacement.bounds(
            screenWidthPx,
            screenHeightPx,
            ballPx,
            barSafeArea,
            lpOriginY,
        ).minY.roundToInt()
        return max(0, overflow).coerceAtMost(max(0, (posY).roundToInt() - minY))
    }

    // ---- 触摸 → 定位 ----

    private fun onDragged(dx: Float, dy: Float) {
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
    }

    private fun onDragEnded() {
        if (!dragging) return
        dragging = false
        finishDrag()
    }

    /** 贴边结算 + 落盘（拖动收尾）。调用前应已置 dragging=false。 */
    private fun finishDrag() {
        // 拖动中键盘可能起落过：结算一次让位，再夹取。
        imeLiftPx = computeImeLift(imeBottomPx).toFloat()
        applyWindowBounds()
        // 松手后球贴到最近边缘（完整球在屏内，不留缝）；用短程缓动而不是瞬移。落盘记终点。
        if (screenWidthPx > 0 && screenHeightPx > 0) {
            val targetX = BallPlacement.edgeX(isOnLeft(), screenWidthPx, ballPx)
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

    /** 当前球窗顶边屏幕坐标（px）。跟手挪球/气泡锚点由 Service 读取。 */
    fun currentPosition(): Pair<Float, Float> = posX to posY

    /**
     * 自动挪球（跟随持机手）：淡出 → 传送 → 淡回。
     * 拖动/按压/换位中让位给用户并返回 false（调用方稍后重试）；淡出期间被
     * 用户接管拖动则放弃传送（[onLanded] 不回调）。目标与当前几乎一致时不动画直接算成功。
     * [onLanded] 在球真正落到目标位时回调（无动画路径同步、淡出路径异步）；被打断
     * 则不回调 —— 调用方据此把落盘推迟到实位落定，避免存量与实位背离。
     */
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
        // 静置变暗态跨跟手挪球保留：触点在触钮上，球只是被动搬位置 —— 传送落位后
        // applyVisual 按新边缘重摆半出屏姿态，不唤醒。
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
                // 淡出期间用户上手拖动：放弃传送恢复可见，球留在用户手里。
                if (dragging) {
                    applyVisual()
                    return@withEndAction
                }
                settleTransfer()
            }
            .start()
        return true
    }

    /** 传送落位：写到目标、重摆姿态并回调 [repositionTo] 的 onLanded。 */
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

    /** 隐形结算：打断路径只记账（改基准坐标 + 回调落盘），**不写窗口** ——
     *  窗口此时还全亮，写 LP 会瞬移到新侧闪一帧；真正的落窗推迟到回场动画
     *  把 alpha 置 0 之后（[animateRestoreIn] 消费 [transferPendingLayout]）。 */
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

    /** 隐形结算后尚未落窗：回场动画置 alpha=0 后消费。 */
    private var transferPendingLayout = false

    /** 气泡在屏时保持球的完整姿态（B 方案不采用）：显示/收起气泡时由 Service 调用。 */
    fun setIdleSuppressed(suppressed: Boolean) {
        if (idleSuppressed == suppressed) return
        idleSuppressed = suppressed
        if (suppressed) cancelIdle() else scheduleIdle()
    }

    /** 胶囊展开/收起：展开时球窗淡出并不可点，收起后恢复。 */
    fun setCapsuleActive(active: Boolean) {
        if (capsuleActive == active) return
        capsuleActive = active
        applyHiddenFlags()
        if (active) cancelIdle() else scheduleIdle()
    }

    /** 主球当前是否在屏左半（按窗口实位、球心判，与静置外移方向同口径）。 */
    fun isOnLeft(): Boolean = BallPlacement.isOnLeft(posX, screenWidthPx, ballPx)

    /** 该朝向落盘落点对应的贴边 x（侧别按目标朝向自己的归一值判，不做跨朝向像素比较）。 */
    private fun savedEdgeX(landscape: Boolean): Float =
        BallPlacement.edgeX(settings.position(landscape).x, screenWidthPx, ballPx)

    /** 贴边重吸附：x 吸到 [left] 侧安全区边缘（完整球在屏内），y 不动。 */
    private fun snapToNearestEdge(left: Boolean = isOnLeft()) {
        if (screenWidthPx <= 0) return
        posX = BallPlacement.edgeX(left, screenWidthPx, ballPx)
    }

    /**
     * 位置被外部改写（转屏 / 尺寸重建）后把姿态直接落定：静置外移量与侧别、屏宽耦合，
     * 旧朝向的 translationX 在新朝向上会把球推到「离边缘一段距离」或半个球出屏。
     * 不走 [applyVisual] 的动画 —— 转屏本身已经重排过一帧，再播一次位移会看到回弹。
     */
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

    /** 主球窗实测的 LP 原点（屏幕 y）：胶囊窗复用它，避免两窗各自测量产生偏差。 */
    fun lpOriginYPx(): Int = lpOriginY

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

    // ---- 静置 ----

    private fun scheduleIdle() {
        idleRunnable?.let(handler::removeCallbacks)
        idleRunnable = null
        if (idleSuppressed || capsuleActive) {
            if (idled) {
                idled = false
                applyVisual()
            }
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

    /** 仅移除静置计时，不动 idled 标志：隐藏/挪球等要保留变暗姿态的路径用。 */
    private fun cancelIdleTimer() {
        idleRunnable?.let(handler::removeCallbacks)
        idleRunnable = null
    }

    /** 移除静置计时并退出变暗态。重启交给抬手（onPressChanged=false）。 */
    private fun cancelIdle() {
        cancelIdleTimer()
        if (idled) {
            idled = false
            applyVisual()
        }
    }

    // ---- 外观（View 层） ----

    /** 静置姿态的横向外移量（按当前贴边方向）。 */
    private fun idleShiftPx(): Float =
        BallPlacement.idleShiftPx(isOnLeft(), ballPx, FloatBallDefaults.IdleOutRatio)

    /** 当前该有的不透明度：软隐藏/胶囊态全隐，静置半暗，否则全亮。 */
    private fun visualAlpha(): Float = when {
        capsuleActive -> 0f
        visuallyHidden -> 0f
        idled -> FloatBallDefaults.IdleDimAlpha
        else -> 1f
    }

    /** 按压/静置的 alpha、scale 走 View 层而非 Compose：服务进程托管的 ComposeView 重组不上屏。 */
    private var visualAnimator: ViewPropertyAnimator? = null

    private fun applyVisual() {
        val target = view ?: return
        // 淡出（repositionTo）与本方法共用同一个 ViewPropertyAnimator
        // 实例，走到这里意味着在飞的淡出已被下面的 cancel 中止且 withEndAction 不会
        // 运行 —— transferring 一并收掉，否则按压球打断淡出后它会永久卡在 true。
        transferring = false
        val scale = if (pressed) FloatBallDefaults.PressScale else 1f
        // 软隐藏期间一律隐身：隐藏淡出被挪球/换位动画打断时，落位重绘不得把球画回来。
        val alpha = visualAlpha()
        // 静置时球横向移出一截到屏幕外，方向 = 最近边缘。偏移走 View 平移：
        // 部分 ROM 会把越界 LP 夹回屏内，LP 方案不可靠。窗口留在贴边位，
        // 命中区仍是窗口方形，覆盖可见半圆。
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

    // ---- 窗口定位 ----

    private fun createLayoutParams() = ballLayoutParams(ballPx)

    /**
     * 安全区复测：`currentWindowMetrics` 的 display 绝对坐标，与 IME 让位同源
     * （`:core:bridge` 的 [systemBarsSafeAreaOrNull]）。不用小窗自己的 rootWindowInsets
     * —— 那是「窗口被系统栏重叠的量」，参考系随窗口位置漂移。
     */
    private fun applySystemInsets() {
        updateBarSafeArea(readBarSafeArea() ?: return)
    }

    /** 读屏幕绝对坐标的系统栏安全区；拿不到返回 null，由调用方沿用上一次的值。 */
    private fun readBarSafeArea(): SystemBarsSafeArea? {
        val next = wm.systemBarsSafeAreaOrNull()
        if (next == null) Log.w(TAG, "system bar insets unavailable")
        return next
    }

    /** 安全区变化时重夹一次：球不该停在状态栏 / 导航栏下面。 */
    private fun updateBarSafeArea(next: SystemBarsSafeArea) {
        if (next == barSafeArea) return
        barSafeArea = next
        applyWindowBounds()
    }

    /**
     * 夹取 + 写 LP：`posX`/`posY` 是屏幕空间的球顶位置，LP y = 屏幕值 − [lpOriginY]。
     * 键盘让位只进显示值，不碰基准 posY，否则键盘起落会把基准永久顶离原地。
     */
    private fun applyWindowBounds() {
        val current = view ?: return
        val bounds = BallPlacement.bounds(screenWidthPx, screenHeightPx, ballPx, barSafeArea, lpOriginY)
        posX = posX.coerceIn(bounds.minX, bounds.maxX)
        posY = posY.coerceIn(bounds.minY, bounds.maxY)
        // 显示位置 = 基准 − 让位，但不越过 LP 原点。
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

    /** 视觉尺寸夹到 [FloatBallDefaults.MinSizeDp]..[FloatBallDefaults.MaxSizeDp]：命中区 = 窗口，不得低于 48dp。 */
    private fun sizePx(settings: FloatBallSettings): Int = DensityProvider.dp2px(
        settings.sizeDp.coerceIn(FloatBallDefaults.MinSizeDp, FloatBallDefaults.MaxSizeDp),
    )

    // ---- Compose ----

    @Composable
    private fun BallContent() {
        BallWindowContent(ballPx) { size -> BallFace(modifier = Modifier.size(size)) }
    }

    @Composable
    private fun BallFace(modifier: Modifier = Modifier) {
        val size = with(LocalDensity.current) { ballPx.toDp() }
        val accent = settings.colorSpec.toMarkColor()
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
                val icon = fixedIcon
                    ?: actionIcon(settings.tapAction ?: StoredAction.of(Action.NONE_TYPE_ID))
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

        /** 系统栏 insets 事件的合并复测延迟：躲开键盘动画期间的高频派发。 */
        const val SYSTEM_INSETS_REMEASURE_MS = 32L
    }
}
