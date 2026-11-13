package hunoia.luno.runtime.floatball

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import hunoia.luno.core.LunoLog as Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.inbox.BallItem
import hunoia.luno.runtime.floatball.inbox.NotificationItem
import hunoia.luno.runtime.floatball.inbox.SuggestionItem
import hunoia.luno.runtime.floatball.inbox.SuggestionKind
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
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

/**
 * 统一胶囊：从所属球的位置展开，收起时恢复该球。
 *
 * - 通知胶囊展示普通通知；建议胶囊展示验证码 / 链接。
 * - 上下滑是连续轮播；横滑朝球侧 = 收起，反向 = 移除当前项。
 * - 点按 = 执行主动作；建议项长按在胶囊内换成动作行（验证码：复制/忽略；
 *   链接：打开/复制/分享/忽略）。
 */
internal class SmartBubbleWindow(private val context: android.content.Context) {

    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var composeView: ComposeView? = null
    private var exitingView: View? = null

    /** Compose 是否真正跑过一帧；用于兜底重挂内容（窗口 View 偶发不组合）。 */
    @Volatile
    private var contentReady = false

    private var items by mutableStateOf<List<BallItem>>(emptyList())
    private var currentIndex by mutableIntStateOf(0)
    private var offsetPx by mutableFloatStateOf(0f)
    private var pageHeightPx by mutableIntStateOf(0)
    private var capsuleWidthPx by mutableIntStateOf(0)

    /** 长按后当前页展开的动作行；null = 普通内容。 */
    private var menuItemId by mutableStateOf<String?>(null)

    /** 复制成功后短暂显示「已复制」的条目。 */
    private var copiedItemId by mutableStateOf<String?>(null)

    private var settings by mutableStateOf(FloatBallSettings())
    private var anchor = BubbleAnchor(0, 0, 0, 0)
    private var sideRight = false
    private var lpOriginY = 0
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
    private var lastReportedId: String? = null

    private val bodyArbiter = FloatBallTouchArbiter(
        onSlideDragged = { dx, dy -> onBodyDragged(dx, dy) },
        onTapped = { handleTap() },
        onPressChanged = { pressed ->
            this.pressed = pressed
            onPressStateChanged?.invoke(pressed)
            cancelEnterFeedback()
            if (pressed) {
                dragDx = 0f
                dragDy = 0f
                dragAxis = DragAxis.NONE
            }
        },
        onDragEnded = { finishBodyDrag() },
        onLongPress = { handleLongPress() },
    )

    /** 正文点按（通知直达 / 建议主动作）。 */
    var onBodyTapped: (() -> Unit)? = null

    /** 正文长按（通知无动作；建议在窗内展开动作行）。 */
    var onLongPress: (() -> Unit)? = null

    /** 建议动作行 / 主动作回调。 */
    var onSuggestionAction: ((String, SuggestionAction) -> Unit)? = null

    /** 轮播停到某一页（id）：服务据此更新收件箱当前项。 */
    var onPageSelected: ((String) -> Unit)? = null

    /** 朝球所在侧横滑：只收起，保留队列项、不标记 dismissed。 */
    var onCollapsed: (() -> Unit)? = null

    /** 反向横滑：移除当前项并翻下一条（多条）/ 收起（末条/单条）。 */
    var onSwipedAway: (() -> Unit)? = null

    /** 按住状态：暂停 / 恢复自动收起。 */
    var onPressStateChanged: ((Boolean) -> Unit)? = null

    /** 胶囊首帧真正画出来：服务这时才隐藏球窗。 */
    var onReady: (() -> Unit)? = null

    /** 组合失败/窗口不可用：服务收起胶囊、恢复球，避免“球隐藏 + 空白窗”。 */
    var onUnavailable: (() -> Unit)? = null

    fun updateSettings(next: FloatBallSettings) {
        settings = next
    }

    fun isPinned(): Boolean = items.getOrNull(currentIndex)?.pinned == true

    /** 复制成功的短暂反馈：显示「已复制」。 */
    fun showCopied(itemId: String) {
        menuItemId = null
        copiedItemId = itemId
    }

    /** 清除「已复制」反馈（链接复制等不自动翻页的场景）。 */
    fun clearCopied() {
        copiedItemId = null
    }

    /** 只刷新列表（不抢当前页、不重置拖动偏移）：新内容在手动浏览时入队用。 */
    fun updateItems(items: List<BallItem>, index: Int) {
        if (items.isEmpty()) return
        this.items = items
        if (menuItemId != null && items.none { it.id == menuItemId }) menuItemId = null
        if (copiedItemId != null && items.none { it.id == copiedItemId }) copiedItemId = null
        if (currentIndex > items.lastIndex) {
            currentIndex = index.coerceIn(0, items.lastIndex)
            offsetPx = 0f
            lastReportedId = items[currentIndex].id
        }
    }

    /**
     * 显示 / 刷新胶囊。已可见时更新列表与当前项，不重播展开动画。
     */
    fun show(
        items: List<BallItem>,
        index: Int,
        anchor: BubbleAnchor,
        originY: Int = 0,
    ): Boolean {
        if (items.isEmpty()) return false
        this.items = items
        this.anchor = anchor
        val originProvided = originY > 0
        if (originProvided) {
            lpOriginY = originY
        }
        sideRight = anchor.centerX >= DensityProvider.screenWidthPx / 2
        val newHeight = (anchor.bottom - anchor.top).coerceAtLeast(1)
        if (newHeight != pageHeightPx) pageHeightPx = newHeight
        val newWidth = fixedCapsuleWidth()
        if (newWidth != capsuleWidthPx) capsuleWidthPx = newWidth
        // 用户正在拖动时不要抢当前页。
        if (!pressed) {
            currentIndex = index.coerceIn(0, items.lastIndex)
            offsetPx = 0f
            lastReportedId = items[currentIndex].id
            menuItemId = null
        } else {
            currentIndex = currentIndex.coerceIn(0, items.lastIndex)
        }
        if (menuItemId != null && items.getOrNull(currentIndex)?.id != menuItemId) menuItemId = null
        if (copiedItemId != null && items.none { it.id == copiedItemId }) copiedItemId = null

        val existing = view
        if (existing != null &&
            existing.isAttachedToWindow &&
            existing.width > 0 &&
            existing.height > 0 &&
            (entering || existing.alpha > 0.05f)
        ) {
            if (!contentReady) composeView?.setContent { CapsuleContent() }
            updateWindowLayout(existing)
            existing.post { applyWindowBounds() }
            existing.postDelayed({
                if (view === existing) {
                    if (contentReady) onReady?.invoke() else onUnavailable?.invoke()
                }
            }, 600L)
            return true
        }
        if (existing != null) {
            // 窗口被系统移除/卡在不可见态但字段未清：丢弃后重建。
            view = null
            exitingView = null
            existing.animate().cancel()
            context.removeWindow(existing)
        }

        originReady = originProvided
        contentReady = false
        val compose = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            FloatBallLifecycleOwner.createAndAttachTo(this, active = true)
            setContent { CapsuleContent() }
        }
        // 与球窗同构：FrameLayout 做根、ComposeView 做子级，lifecycle owner 同时挂根和
        // Compose 上。真机上直接拿 ComposeView 当窗口根时偶发不组合（胶囊空白）。
        val created = FrameLayout(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            FloatBallLifecycleOwner.createAndAttachTo(this, active = true)
            alpha = 0f
        }
        created.setOnTouchListener { v, event -> handleTouch(v, event) }
        created.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width <= 0 || v.height <= 0) return@addOnLayoutChangeListener
            if (!originReady) {
                lpOriginY = screenOriginY(v)
                originReady = true
            }
            applyWindowBounds()
            if (enterPending) {
                enterPending = false
                startMorphIn(v)
            }
        }
        if (!wm.safeAddView(created, capsuleLayoutParams(capsuleWidthPx, pageHeightPx))) {
            Log.e(TAG, "capsule addView failed (overlay permission or window rejected)")
            this.items = emptyList()
            return false
        }
        view = created
        composeView = compose
        entering = true
        enterPending = true
        // addView 有时会同步跑完布局，post 一次保证展开动画一定启动（不依赖 origin 测量）。
        created.post {
            val v = view
            if (v === created) {
                applyWindowBounds()
                if (enterPending) {
                    enterPending = false
                    startMorphIn(v)
                }
            }
        }
        // 安全网：极端时序下展开动画/定位未跑，也保证胶囊可见且尺寸正确。
        mainHandler.postDelayed({
            val v = view
            if (v === created) {
                applyWindowBounds()
                if (!contentReady) composeView?.setContent { CapsuleContent() }
                if (v.alpha < 0.99f) {
                    v.animate().cancel()
                    v.alpha = 1f
                    v.scaleX = 1f
                    v.scaleY = 1f
                }
                entering = false
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
        }, 420L)
        mainHandler.postDelayed({
            val v = view
            if (v === created && !originReady) {
                lpOriginY = screenOriginY(v)
                originReady = true
                applyWindowBounds()
            }
            if (v === created && enterPending) {
                enterPending = false
                startMorphIn(v)
            }
        }, EnterFallbackMs)
        return true
    }

    fun hide(onHidden: (() -> Unit)? = null) {
        bodyArbiter.cancel()
        val v = view
        view = null
        entering = false
        enterPending = false
        pressed = false
        hitTarget = HitTarget.Body
        items = emptyList()
        menuItemId = null
        copiedItemId = null
        offsetPx = 0f
        contentReady = false
        composeView = null
        dragDx = 0f
        dragDy = 0f
        dragAxis = DragAxis.NONE
        settleAnimator?.cancel()
        settleAnimator = null
        velocityTracker?.recycle()
        velocityTracker = null
        mainHandler.removeCallbacksAndMessages(null)
        if (v == null) {
            onHidden?.invoke()
            return
        }
        applyBallTouchFlags(v, wm, hidden = true)
        exitingView = v
        v.animate().cancel()
        v.pivotX = if (sideRight) v.width.toFloat() else 0f
        v.pivotY = v.height / 2f
        v.animate()
            .scaleX(collapsedScale())
            .alpha(0f)
            .setDuration(motionMillis(MotionFamily.Floating).toLong())
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (exitingView === v) exitingView = null
                context.removeWindow(v)
                onHidden?.invoke()
            }
            .start()
        // 退场动画被意外 cancel 时兜底：移除旧窗并恢复球，避免“球隐藏 + 空白窗”。
        mainHandler.postDelayed({
            if (exitingView === v) {
                exitingView = null
                v.animate().cancel()
                context.removeWindow(v)
                onHidden?.invoke()
            }
        }, motionMillis(MotionFamily.Floating) + 120L)
    }

    fun detachNow() {
        bodyArbiter.cancel()
        val v = view
        val exiting = exitingView
        view = null
        exitingView = null
        entering = false
        enterPending = false
        pressed = false
        items = emptyList()
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
        v?.let {
            it.animate().cancel()
            context.removeWindow(it)
        }
        exiting?.let {
            it.animate().cancel()
            context.removeWindow(it)
        }
    }

    /** 球移动 / 键盘让位 / 转屏后重新贴边。 */
    fun updateAnchor(anchor: BubbleAnchor) {
        this.anchor = anchor
        val newHeight = (anchor.bottom - anchor.top).coerceAtLeast(1)
        val newSideRight = anchor.centerX >= DensityProvider.screenWidthPx / 2
        val heightChanged = newHeight != pageHeightPx
        if (heightChanged) pageHeightPx = newHeight
        sideRight = newSideRight
        val newWidth = fixedCapsuleWidth()
        if (newWidth != capsuleWidthPx) capsuleWidthPx = newWidth
        view?.let { v ->
            if (heightChanged || newWidth != v.width) updateWindowLayout(v)
            v.pivotX = if (sideRight) v.width.toFloat() else 0f
            v.pivotY = v.height / 2f
            v.post { applyWindowBounds() }
        }
    }

    // ---- 手势 ----

    private fun handleTouch(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            settleAnimator?.cancel()
            settleAnimator = null
            hitTarget = resolveHitTarget(v, event.x)
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

    private fun recycleVelocity() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun handleTap() {
        val item = items.getOrNull(currentIndex)
        when (val target = hitTarget) {
            is HitTarget.Menu -> {
                hitTarget = HitTarget.Body
                if (item is SuggestionItem) {
                    val actions = menuActions(item)
                    val action = actions.getOrNull(target.index) ?: return
                    if (action == SuggestionAction.CLOSE) {
                        menuItemId = null
                    } else {
                        menuItemId = null
                        onSuggestionAction?.invoke(item.id, action)
                    }
                }
            }

            HitTarget.Action -> {
                hitTarget = HitTarget.Body
                if (item is SuggestionItem) {
                    onSuggestionAction?.invoke(item.id, item.primaryAction())
                } else {
                    onBodyTapped?.invoke()
                }
            }

            HitTarget.Count -> {
                hitTarget = HitTarget.Body
                advanceOne()
            }

            HitTarget.Body -> {
                if (menuItemId != null) {
                    menuItemId = null
                    return
                }
                hitTarget = HitTarget.Body
                onBodyTapped?.invoke()
            }
        }
    }

    private fun handleLongPress() {
        val item = items.getOrNull(currentIndex)
        if (item is SuggestionItem) {
            menuItemId = item.id
            copiedItemId = null
        } else {
            onLongPress?.invoke()
        }
    }

    private fun resolveHitTarget(v: View, x: Float): HitTarget {
        val item = items.getOrNull(currentIndex)
        if (item is SuggestionItem && menuItemId == item.id) {
            val count = menuActions(item).size.coerceAtLeast(1)
            val index = ((x / v.width.coerceAtLeast(1)) * count).toInt()
            return HitTarget.Menu(index.coerceIn(0, count - 1))
        }
        if (item is SuggestionItem) {
            val actionStart = v.width - DensityProvider.dp2px(ActionHitDp)
            if (x >= actionStart) return HitTarget.Action
        }
        val countWidth = if (items.size > 1) DensityProvider.dp2px(CountHitDp) else 0
        return when {
            items.size > 1 && x >= v.width - countWidth -> HitTarget.Count
            else -> HitTarget.Body
        }
    }

    private fun onBodyDragged(dx: Float, dy: Float) {
        dragDx += dx
        dragDy += dy
        if (dragAxis == DragAxis.NONE &&
            (abs(dragDx) > touchSlop || abs(dragDy) > touchSlop)
        ) {
            dragAxis = if (abs(dragDx) > abs(dragDy)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
            if (dragAxis == DragAxis.HORIZONTAL) offsetPx = 0f
        }
        val v = view ?: return
        when (dragAxis) {
            DragAxis.VERTICAL -> {
                // 连续跟手：允许一次滑过多条；两端直接停住，不露出空白槽位。
                offsetPx += dy
                normalizeOffset()
            }

            DragAxis.HORIZONTAL -> {
                v.translationX = dragDx * CollapseFollowRatio
                val fade = (abs(dragDx) / collapseThresholdPx).coerceIn(0f, 1f)
                v.alpha = 1f - fade * 0.25f
            }

            DragAxis.NONE -> Unit
        }
    }

    private fun normalizeOffset() {
        val h = pageHeightPx.toFloat()
        if (h <= 0f || items.size <= 1) {
            offsetPx = 0f
            return
        }
        while (offsetPx <= -h && currentIndex < items.lastIndex) {
            currentIndex += 1
            offsetPx += h
        }
        while (offsetPx >= h && currentIndex > 0) {
            currentIndex -= 1
            offsetPx -= h
        }
        if (currentIndex == 0 && offsetPx > 0f) {
            offsetPx = 0f
        }
        if (currentIndex == items.lastIndex && offsetPx < 0f) {
            offsetPx = 0f
        }
    }

    private fun finishBodyDrag() {
        val v = view
        val dx = dragDx
        val axis = dragAxis
        val vy = flingVelocityY()
        dragDx = 0f
        dragDy = 0f
        dragAxis = DragAxis.NONE
        if (v != null) {
            v.alpha = 1f
            v.translationX = 0f
        }
        when (axis) {
            DragAxis.VERTICAL -> {
                menuItemId = null
                settleVertical(vy)
            }

            DragAxis.HORIZONTAL -> {
                offsetPx = 0f
                menuItemId = null
                val towardEdge = if (sideRight) dx >= collapseThresholdPx else dx <= -collapseThresholdPx
                val awayFromEdge = if (sideRight) dx <= -collapseThresholdPx else dx >= collapseThresholdPx
                when {
                    towardEdge -> onCollapsed?.invoke()
                    awayFromEdge -> onSwipedAway?.invoke()
                }
            }

            DragAxis.NONE -> Unit
        }
    }

    private fun flingVelocityY(): Float {
        val tracker = velocityTracker ?: return 0f
        tracker.computeCurrentVelocity(1000)
        return tracker.yVelocity
    }

    private fun settleVertical(vy: Float) {
        val h = pageHeightPx.toFloat()
        if (h <= 0f || items.size <= 1) {
            offsetPx = 0f
            return
        }
        var targetOffset = 0f
        var targetIndex = currentIndex
        val fling = abs(vy) >= FlingVelocityPxPerSec
        if (fling) {
            if (vy < 0f && currentIndex < items.lastIndex) {
                targetOffset = -h
                targetIndex = currentIndex + 1
            } else if (vy > 0f && currentIndex > 0) {
                targetOffset = h
                targetIndex = currentIndex - 1
            }
        }
        if (targetOffset == 0f) {
            when {
                offsetPx <= -h / 2f && currentIndex < items.lastIndex -> {
                    targetOffset = -h
                    targetIndex = currentIndex + 1
                }

                offsetPx >= h / 2f && currentIndex > 0 -> {
                    targetOffset = h
                    targetIndex = currentIndex - 1
                }
            }
        }
        animateSettle(targetOffset, targetIndex)
    }

    private fun advanceOne() {
        if (items.size <= 1 || currentIndex >= items.lastIndex) return
        animateSettle(-pageHeightPx.toFloat(), currentIndex + 1)
    }

    private fun animateSettle(targetOffset: Float, targetIndex: Int) {
        settleAnimator?.cancel()
        val from = offsetPx
        val animator = ValueAnimator.ofFloat(from, targetOffset).apply {
            duration = motionMillis(MotionFamily.FloatingSnap).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { offsetPx = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) = Unit

                override fun onAnimationEnd(animation: Animator) {
                    if (settleAnimator !== animation) return
                    settleAnimator = null
                    if (targetIndex != currentIndex) {
                        currentIndex = targetIndex.coerceIn(0, items.lastIndex)
                    }
                    offsetPx = 0f
                    menuItemId = null
                    notifyPageSelected()
                }
            })
        }
        settleAnimator = animator
        animator.start()
    }

    private fun notifyPageSelected() {
        val item = items.getOrNull(currentIndex) ?: return
        if (item.id == lastReportedId) return
        lastReportedId = item.id
        onPageSelected?.invoke(item.id)
    }

    private val touchSlop: Float
        get() = DensityProvider.dp2px(12f).toFloat()

    private val collapseThresholdPx: Float
        get() = DensityProvider.dp2px(CollapseThresholdDp).toFloat()

    // ---- 定位 ----

    private fun fixedCapsuleWidth(): Int {
        val screenW = DensityProvider.screenWidthPx
        val cap = min(DensityProvider.dp2px(MaxWidthDp), (screenW * WidthRatio).roundToInt())
        return max(pageHeightPx, cap.coerceAtMost(screenW - DensityProvider.dp2px(16f)))
    }

    private fun updateWindowLayout(v: View) {
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        if (lp.width == capsuleWidthPx && lp.height == pageHeightPx) return
        lp.width = capsuleWidthPx
        lp.height = pageHeightPx
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun applyWindowBounds() {
        val v = view ?: return
        if (!originReady || v.width <= 0 || v.height <= 0) return
        val screenW = DensityProvider.screenWidthPx
        val x = if (sideRight) {
            (anchor.right - capsuleWidthPx).coerceIn(0, (screenW - capsuleWidthPx).coerceAtLeast(0))
        } else {
            anchor.left.coerceIn(0, (screenW - capsuleWidthPx).coerceAtLeast(0))
        }
        writeBallPosition(v, wm, x.toFloat(), anchor.top.toFloat(), lpOriginY)
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

    private fun startMorphIn(v: View) {
        v.pivotX = if (sideRight) v.width.toFloat() else 0f
        v.pivotY = v.height / 2f
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

    // ---- Compose ----

    @Composable
    private fun CapsuleContent() {
        SideGestureTheme {
            val list = items
            if (list.isEmpty() || pageHeightPx <= 0) return@SideGestureTheme
            val density = LocalDensity.current
            val pageHeight = with(density) { pageHeightPx.toDp() }
            val base = currentIndex
            Box(modifier = Modifier.fillMaxSize()) {
                // 每页独立按相对位置摆放：base 页落在窗口原点，前后各留一页跟手。
                for (i in (base - 1)..(base + 1)) {
                    val item = list.getOrNull(i) ?: continue
                    CapsulePage(
                        item = item,
                        index = i,
                        total = list.size,
                        pageHeight = pageHeight,
                        menuOpen = menuItemId == item.id,
                        copied = copiedItemId == item.id,
                        modifier = Modifier.offset {
                            IntOffset(
                                0,
                                ((i - base) * pageHeightPx + offsetPx).roundToInt(),
                            )
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun CapsulePage(
        item: BallItem,
        index: Int,
        total: Int,
        pageHeight: androidx.compose.ui.unit.Dp,
        menuOpen: Boolean,
        copied: Boolean,
        modifier: Modifier = Modifier,
    ) {
        val accent = settings.colorSpec.toMarkColor()
        val contentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
        val density = LocalDensity.current
        val iconSize = with(density) {
            min(pageHeight.value - 16f, 24f).coerceAtLeast(16f).dp
        }
        val shape = CircleShape
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(pageHeight)
                .onSizeChanged { if (it.width > 0 && it.height > 0) contentReady = true }
                .clip(shape)
                .background(accent.copy(alpha = BubbleBackgroundAlpha))
                .border(1.dp, contentColor.copy(alpha = 0.22f), shape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (menuOpen && item is SuggestionItem) {
                SuggestionMenuRow(
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
                        index = index,
                        total = total,
                    )

                    is SuggestionItem -> SuggestionContent(
                        item = item,
                        contentColor = contentColor,
                        iconSize = iconSize,
                        index = index,
                        total = total,
                        copied = copied,
                    )
                }
            }
        }
    }

    @Composable
    private fun RowScope.NotificationContent(
        item: NotificationItem,
        contentColor: Color,
        iconSize: androidx.compose.ui.unit.Dp,
        index: Int,
        total: Int,
    ) {
        AppIcon(packageName = item.packageName, tint = contentColor, size = iconSize)
        Column(
            modifier = Modifier.weight(1f),
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
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (total > 1) {
            CountChip(index = index, total = total, contentColor = contentColor)
        }
    }

    @Composable
    private fun RowScope.SuggestionContent(
        item: SuggestionItem,
        contentColor: Color,
        iconSize: androidx.compose.ui.unit.Dp,
        index: Int,
        total: Int,
        copied: Boolean,
    ) {
        SuggestionIcon(item = item, contentColor = contentColor, size = iconSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = suggestionTitle(item),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (copied) {
                    stringResource(R.string.suggestion_copied)
                } else {
                    suggestionBody(item)
                },
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = if (copied) 1f else 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (total > 1) {
            CountChip(index = index, total = total, contentColor = contentColor)
        } else if (copied) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(
                text = suggestionActionLabel(item),
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
    private fun CountChip(index: Int, total: Int, contentColor: Color) {
        Text(
            text = stringResource(R.string.smart_ball_count, index + 1, total),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.9f),
            maxLines = 1,
            modifier = Modifier
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.16f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }

    @Composable
    private fun SuggestionMenuRow(
        item: SuggestionItem,
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
                        tint = if (action == SuggestionAction.IGNORE) {
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
    private fun suggestionTitle(item: SuggestionItem): String = when (item.kind) {
        SuggestionKind.CODE -> stringResource(R.string.suggestion_code)
        SuggestionKind.LINK -> item.targetLabel ?: stringResource(R.string.suggestion_link)
    }

    @Composable
    private fun suggestionBody(item: SuggestionItem): String = when (item.kind) {
        SuggestionKind.CODE -> item.code.orEmpty()
        SuggestionKind.LINK -> {
            if (item.linkCount > 1) {
                stringResource(R.string.suggestion_link_count, item.linkCount)
            } else {
                item.primaryUrl.orEmpty()
            }
        }
    }

    @Composable
    private fun suggestionActionLabel(item: SuggestionItem): String = when (item.kind) {
        SuggestionKind.CODE -> stringResource(R.string.suggestion_copy)
        SuggestionKind.LINK -> item.targetLabel?.let {
            stringResource(R.string.suggestion_open_target, it)
        } ?: stringResource(R.string.suggestion_open)
    }

    @Composable
    private fun actionDescription(action: SuggestionAction): String = when (action) {
        SuggestionAction.OPEN -> stringResource(R.string.suggestion_open)
        SuggestionAction.COPY -> stringResource(R.string.suggestion_copy)
        SuggestionAction.SHARE -> stringResource(R.string.suggestion_share)
        SuggestionAction.IGNORE -> stringResource(R.string.suggestion_ignore)
        SuggestionAction.CLOSE -> stringResource(R.string.suggestion_close)
    }

    private fun actionIcon(action: SuggestionAction) = when (action) {
        SuggestionAction.OPEN -> Icons.AutoMirrored.Filled.OpenInNew
        SuggestionAction.COPY -> Icons.Default.ContentCopy
        SuggestionAction.SHARE -> Icons.Default.Share
        SuggestionAction.IGNORE -> Icons.Default.Block
        SuggestionAction.CLOSE -> Icons.Default.Close
    }

    private fun menuActions(item: SuggestionItem): List<SuggestionAction> = when (item.kind) {
        SuggestionKind.CODE -> listOf(
            SuggestionAction.COPY,
            SuggestionAction.IGNORE,
            SuggestionAction.CLOSE,
        )

        SuggestionKind.LINK -> listOf(
            SuggestionAction.OPEN,
            SuggestionAction.COPY,
            SuggestionAction.SHARE,
            SuggestionAction.IGNORE,
            SuggestionAction.CLOSE,
        )
    }

    @Composable
    private fun SuggestionIcon(
        item: SuggestionItem,
        contentColor: Color,
        size: androidx.compose.ui.unit.Dp,
    ) {
        val target = item.targetPackage
        if (item.kind == SuggestionKind.LINK && target != null) {
            AppIcon(packageName = target, tint = contentColor, size = size)
        } else {
            Icon(
                imageVector = if (item.kind == SuggestionKind.CODE) {
                    Icons.Default.Lock
                } else {
                    Icons.Default.Link
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
        data object Body : HitTarget
        data object Action : HitTarget
        data object Count : HitTarget
        data class Menu(val index: Int) : HitTarget
    }

    private enum class DragAxis { NONE, VERTICAL, HORIZONTAL }

    private companion object {
        const val TAG = LOG_TAG
        const val EnterFallbackMs = 260L
        const val MaxOriginDp = 200f
        const val PressScale = 0.97f

        const val MaxWidthDp = 180f
        const val WidthRatio = 0.48f
        const val CountHitDp = 44f
        const val ActionHitDp = 72f
        const val CollapseThresholdDp = 48f
        const val CollapseFollowRatio = 0.22f
        const val FlingVelocityPxPerSec = 900f
        const val BubbleBackgroundAlpha = 0.88f
    }
}

/** 建议胶囊的动作。 */
internal enum class SuggestionAction {
    OPEN,
    COPY,
    SHARE,
    IGNORE,
    CLOSE,
}

/** 建议项的主动作：验证码复制，链接打开。 */
internal fun SuggestionItem.primaryAction(): SuggestionAction = when (kind) {
    SuggestionKind.CODE -> SuggestionAction.COPY
    SuggestionKind.LINK -> SuggestionAction.OPEN
}
