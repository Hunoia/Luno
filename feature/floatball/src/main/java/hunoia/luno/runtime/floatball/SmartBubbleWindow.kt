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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
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
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.floatball.R
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

/**
 * 统一胶囊：从所属球的位置展开，收起时恢复该球。
 *
 * - 通知胶囊展示未命中的普通通知；结果胶囊展示分发规则的胶囊档结果。
 * - 结果胶囊上下滑是连续轮播；通知胶囊每条一颗独立胶囊，同屏行数可配，超出竖向滚动。
 * - 横滑朝球侧 = 收起，反向 = 移除按下那一行。
 * - 点按 = 执行主动作；结果项长按在胶囊内换成动作行（捕获值：复制/忽略；
 *   链接：打开/复制/分享/忽略）。
 */
internal class SmartBubbleWindow(private val context: android.content.Context) {

    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var composeView: ComposeView? = null

    /** hide() 的兜底任务：show() 复用时必须撤销，否则会把刚亮起的胶囊重新按下去。 */
    private var hideFallback: Runnable? = null

    /** Compose 是否真正跑过一帧；用于兜底重挂内容（窗口 View 偶发不组合）。 */
    @Volatile
    private var contentReady = false

    private var items by mutableStateOf<List<BallItem>>(emptyList())
    private var currentIndex by mutableIntStateOf(0)
    private var offsetPx by mutableFloatStateOf(0f)

    /** 锚点球高度：普通模式的行高，也是 [collapsedScale] 的形变基准。 */
    private var pageHeightPx by mutableIntStateOf(0)

    /** 堆叠模式单行高：跟着球高走但夹在 [RowHeightMinDp]–[RowHeightMaxDp]，不随大球膨胀。 */
    private var rowHeightPx by mutableIntStateOf(0)
    private var capsuleWidthPx by mutableIntStateOf(0)

    /**
     * 堆叠模式（通知胶囊）：每条通知一颗独立胶囊，超出可见行数时整串竖向滚动。
     * 不翻页、不显示页数徽标；单条时高度与普通模式一致。
     */
    private var stacked by mutableStateOf(false)

    /** 最近一次按下所在行（堆叠模式点按 / 横滑都路由到具体那条通知）。 */
    private var tappedItemId: String? = null

    /** 多胶囊：按下那颗的缩放反馈；手势升级为拖动时立即清掉，滚动不再保持缩小态。 */
    private var pressedRowId by mutableStateOf<String?>(null)

    /** 多胶囊横滑：反向滑动只位移按下的那颗；朝球侧收起时才动整窗。 */
    private var rowDragId by mutableStateOf<String?>(null)
    private var rowDragDx by mutableFloatStateOf(0f)

    /** 滚动条只在拖动 / 惯性期间出现，停下后延时隐藏。 */
    private var scrollThumbVisible by mutableStateOf(false)
    private var scrollThumbHide: Runnable? = null

    /** 滚动栈几何：窗口高、可滚动距离、按下点 → 行号共用同一份口径。 */
    private val stackMetrics: StackMetrics
        get() = StackMetrics(
            rowHeightPx = rowHeightPx,
            itemCount = items.size,
            visibleRows = settings.notificationCapsule.visibleRows.coerceIn(
                FloatBallDefaults.CapsuleVisibleRowsMin,
                FloatBallDefaults.CapsuleVisibleRowsMax,
            ),
            gapPx = DensityProvider.dp2px(RowGapDp),
        )

    /** 窗口实际高度：堆叠 = 可见胶囊 + 彼此间距；普通 = 单行高。 */
    private val windowHeightPx: Int
        get() = if (stacked) stackMetrics.viewportHeightPx else pageHeightPx

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
            if (stacked) {
                // 多胶囊：只缩按下的那一颗；整窗缩放会让整串一起抖。
                pressedRowId = if (pressed) tappedItemId else null
                applyPressScale(false)
            } else {
                // 翻页模式：沿用整窗按压缩放（§5.3，与主球同一档位）。
                applyPressScale(pressed)
            }
            if (pressed) {
                dragDx = 0f
                dragDy = 0f
                dragAxis = DragAxis.NONE
                resetRowDrag()
            }
        },
        onDragEnded = { finishBodyDrag() },
        // 长按只用于结果项的动作菜单（通知没有长按行为）。
        onLongPress = { handleLongPress() },
        longPressEnabled = { !stacked },
    )

    /** 正文点按（通知直达 / 结果项主动作）。[String] 是点中的条目 id（堆叠模式路由到具体行）。 */
    var onBodyTapped: ((String) -> Unit)? = null

    /** 结果项动作行 / 主动作回调。 */
    var onResultAction: ((String, ResultAction) -> Unit)? = null

    /** 轮播停到某一页（id）：服务据此更新收件箱当前项。 */
    var onPageSelected: ((String) -> Unit)? = null

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

    fun updateSettings(next: FloatBallSettings) {
        val previousRows = settings.notificationCapsule.visibleRows
        settings = next
        if (!stacked) return
        // 同屏行数变了必须立即改窗口高：否则新视口会被旧窗口裁掉 / 留白。
        if (previousRows == next.notificationCapsule.visibleRows) return
        offsetPx = stackMetrics.clamp(offsetPx)
        view?.let { v ->
            updateWindowLayout(v)
            v.post { applyWindowBounds() }
        }
    }

    /** 通知胶囊：切换堆叠（同屏多行 + 胶囊内滚动）模式。已显示时按新几何立即重排窗口。 */
    fun setStackedMode(enabled: Boolean) {
        if (stacked == enabled) return
        stacked = enabled
        offsetPx = 0f
        if (enabled) {
            view?.let { v ->
                updateWindowLayout(v)
                v.post { applyWindowBounds() }
            }
        }
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
        // 收件箱每次变更都传 `state.copy(items = …)` 的新列表：内容没变就别再通知快照，
        // 否则 Compose 会为一次没变化的更新整树重组（三页都读同一批状态）。
        if (sameItems(items, this.items)) return
        this.items = items
        pruneRowTransient(items)
        if (menuItemId != null && items.none { it.id == menuItemId }) menuItemId = null
        if (copiedItemId != null && items.none { it.id == copiedItemId }) copiedItemId = null
        if (currentIndex > items.lastIndex) {
            currentIndex = index.coerceIn(0, items.lastIndex)
            offsetPx = 0f
            lastReportedId = items[currentIndex].id
        }
        // 堆叠模式：条数变化会改变窗口高，立即重排；滚动位置按新范围收拢。
        if (stacked) {
            offsetPx = stackMetrics.clamp(offsetPx)
            view?.let { updateWindowLayout(it) }
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
        // 与 [updateItems] 同一条纪律：内容没变就不通知快照，避免无谓的整树重组。
        if (!sameItems(items, this.items)) this.items = items
        pruneRowTransient(this.items)
        this.anchor = anchor
        val originProvided = originY > 0
        if (originProvided) {
            lpOriginY = originY
        }
        sideRight = anchor.centerX >= DensityProvider.screenWidthPx / 2
        val newHeight = (anchor.bottom - anchor.top).coerceAtLeast(1)
        if (newHeight != pageHeightPx) pageHeightPx = newHeight
        val newRowHeight = rowHeightFor(pageHeightPx)
        if (newRowHeight != rowHeightPx) rowHeightPx = newRowHeight
        val newWidth = fixedCapsuleWidth()
        if (newWidth != capsuleWidthPx) capsuleWidthPx = newWidth
        // 用户正在拖动时不要抢当前页。
        if (!pressed) {
            // 新内容接管当前页：先摘掉 settle 回调，否则旧动画结束后会把页面拉回旧目标。
            // 多条通知同时到达时，它还可能把 currentIndex 改到已被重建的列表之外。
            val settling = settleAnimator
            settleAnimator = null
            settling?.cancel()
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
            existing.height > 0
        ) {
            // 6A：收起后窗口留在 WindowManager 里（不可见 / 不可触摸），这里直接复用。
            // 只有真被系统摘掉（isAttachedToWindow = false）才重建。
            val hidden = existing.visibility != View.VISIBLE || existing.alpha <= 0.01f
            if (!contentReady) composeView?.setContent { CapsuleContent() }
            updateWindowLayout(existing)
            applyBallTouchFlags(existing, wm, hidden = false)
            // 退场动画可能还在跑：先停掉，否则它跑完会把刚复用的窗口又置为 INVISIBLE。
            hideFallback?.let(mainHandler::removeCallbacks)
            hideFallback = null
            existing.animate().cancel()
            existing.visibility = View.VISIBLE
            if (hidden) {
                existing.alpha = 0f
                existing.scaleX = 1f
                existing.scaleY = 1f
                existing.post {
                    if (view !== existing) return@post
                    applyWindowBounds()
                    startMorphIn(existing)
                }
            } else {
                // 动画被 cancel 时可能停在缩放中途：一批通知在展开动画里连续到达时，
                // 只恢复 alpha 会把胶囊永久留在缩小态，看起来就是「卡住」。统一复位形变。
                existing.alpha = 1f
                existing.scaleX = 1f
                existing.scaleY = 1f
                entering = false
                existing.post { applyWindowBounds() }
            }
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
            existing.animate().cancel()
            context.removeWindow(existing)
        }

        originReady = originProvided
        contentReady = false
        // 与球窗同构：FrameLayout 做根、ComposeView 做子级。真机上直接拿 ComposeView 当窗口根
        // 时偶发不组合（胶囊空白），所以一律套一层；lifecycle owner 在骨架里只挂根一处。
        val (compose, created) = FloatBallLifecycleOwner.createWindowRoot(context) {
            CapsuleContent()
        }
        created.alpha = 0f
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
        if (!wm.safeAddView(created, capsuleLayoutParams(capsuleWidthPx, windowHeightPx))) {
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
        resetTransientState()
        hitTarget = HitTarget.Body
        if (v == null) {
            onHidden?.invoke()
            return
        }
        // 6A：窗口常驻复用——收起只做“不可见 + 不可触摸”，保留 ComposeView 与 lifecycle
        // owner；下一次 show() 直接复用，省掉重建窗口与首帧组合（实测 20–50ms）。
        // 真正摘窗只在 detachNow()（服务销毁 / 关闭功能）时发生。
        applyBallTouchFlags(v, wm, hidden = true)
        v.animate().cancel()
        if (!v.isAttachedToWindow || v.visibility != View.VISIBLE || v.alpha <= 0.01f) {
            v.visibility = View.INVISIBLE
            v.alpha = 0f
            v.scaleX = 1f
            v.scaleY = 1f
            onHidden?.invoke()
            return
        }
        v.pivotX = if (sideRight) v.width.toFloat() else 0f
        v.pivotY = v.height / 2f
        v.animate()
            .scaleX(collapsedScale())
            .alpha(0f)
            .setDuration(motionMillis(MotionFamily.Floating).toLong())
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (view === v) {
                    v.visibility = View.INVISIBLE
                    v.scaleX = 1f
                    v.scaleY = 1f
                }
                onHidden?.invoke()
            }
            .start()
        // 退场动画被意外 cancel 时兜底：窗口隐藏，但不再摘窗（下次 show 复用）。
        val fallback = Runnable {
            if (view === v && v.visibility != View.INVISIBLE) {
                v.animate().cancel()
                v.visibility = View.INVISIBLE
                v.alpha = 0f
                v.scaleX = 1f
                v.scaleY = 1f
                onHidden?.invoke()
            }
        }
        hideFallback = fallback
        mainHandler.postDelayed(fallback, motionMillis(MotionFamily.Floating) + 120L)
    }

    fun detachNow() {
        bodyArbiter.cancel()
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
    }

    /**
     * 收起 / 摘窗前把瞬态状态一次性归零。
     *
     * [hide] 与 [detachNow] 共用这一份：两条路径以前各写一遍，增删一条就会各走各的。
     */
    private fun resetTransientState() {
        entering = false
        enterPending = false
        pressed = false
        items = emptyList()
        tappedItemId = null
        pressedRowId = null
        resetRowDrag()
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

    /** 球移动 / 键盘让位 / 转屏后重新贴边。 */
    fun updateAnchor(anchor: BubbleAnchor) {
        this.anchor = anchor
        val newHeight = (anchor.bottom - anchor.top).coerceAtLeast(1)
        val newSideRight = anchor.centerX >= DensityProvider.screenWidthPx / 2
        val heightChanged = newHeight != pageHeightPx
        if (heightChanged) pageHeightPx = newHeight
        val newRowHeight = rowHeightFor(pageHeightPx)
        val rowHeightChanged = newRowHeight != rowHeightPx
        if (rowHeightChanged) rowHeightPx = newRowHeight
        sideRight = newSideRight
        val newWidth = fixedCapsuleWidth()
        if (newWidth != capsuleWidthPx) capsuleWidthPx = newWidth
        view?.let { v ->
            if (heightChanged || rowHeightChanged || newWidth != v.width) updateWindowLayout(v)
            v.pivotX = if (sideRight) v.width.toFloat() else 0f
            v.pivotY = v.height / 2f
            v.post { applyWindowBounds() }
        }
    }

    // ---- 多胶囊行状态 ----

    private fun resetRowDrag() {
        rowDragId = null
        rowDragDx = 0f
    }

    /** 列表变化后清掉已不存在的按压 / 横滑行，避免状态指向幽灵条目。 */
    private fun pruneRowTransient(items: List<BallItem>) {
        if (pressedRowId != null && items.none { it.id == pressedRowId }) pressedRowId = null
        if (rowDragId != null && items.none { it.id == rowDragId }) resetRowDrag()
    }

    /** 滚动条：拖动 / 惯性期间显示，最后一次滚动后 [ScrollThumbHideMs] 隐藏。 */
    private fun showScrollThumb() {
        if (!stacked) return
        if (!scrollThumbVisible) scrollThumbVisible = true
        scrollThumbHide?.let(mainHandler::removeCallbacks)
        val hide = Runnable {
            scrollThumbVisible = false
            scrollThumbHide = null
        }
        scrollThumbHide = hide
        mainHandler.postDelayed(hide, ScrollThumbHideMs)
    }

    // ---- 手势 ----

    private fun handleTouch(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            settleAnimator?.cancel()
            settleAnimator = null
            hitTarget = resolveHitTarget(v, event.x)
            // 堆叠模式：点按 / 横滑都要路由到按下所在的那一行（通知）。
            tappedItemId = if (stacked) {
                items.getOrNull(stackMetrics.rowAt(event.y, offsetPx))?.id
            } else {
                items.getOrNull(currentIndex)?.id
            }
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
        val item = items.getOrNull(currentIndex)
        when (val target = hitTarget) {
            is HitTarget.Menu -> {
                hitTarget = HitTarget.Body
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
                if (item is ResultItem) {
                    onResultAction?.invoke(item.id, item.primaryAction())
                } else {
                    val id = currentTapId() ?: return
                    onBodyTapped?.invoke(id)
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
                val id = currentTapId() ?: return
                onBodyTapped?.invoke(id)
            }
        }
    }

    /** 长按：只有结果项有动作（在窗内展开动作行）；通知项无长按行为。 */
    private fun handleLongPress() {
        val item = items.getOrNull(currentIndex)
        if (item is ResultItem) {
            menuItemId = item.id
            copiedItemId = null
        }
    }

    private fun resolveHitTarget(v: View, x: Float): HitTarget {
        val item = items.getOrNull(currentIndex)
        if (item is ResultItem && menuItemId == item.id) {
            val count = menuActions(item).size.coerceAtLeast(1)
            val index = ((x / v.width.coerceAtLeast(1)) * count).toInt()
            return HitTarget.Menu(index.coerceIn(0, count - 1))
        }
        if (item is ResultItem) {
            val actionStart = v.width - DensityProvider.dp2px(ActionHitDp)
            if (x >= actionStart) return HitTarget.Action
        }
        // 堆叠模式不显示页数徽标，右侧没有 Count 命中区。
        val countWidth = if (stacked || items.size <= 1) 0 else DensityProvider.dp2px(CountHitDp)
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
            // 竖向 = 翻页 / 滚动，横向 = 收起 / 滑掉；先跨 slop 的方向定轴。
            dragAxis = if (abs(dragDx) > abs(dragDy)) DragAxis.HORIZONTAL else DragAxis.VERTICAL
            // 一旦进入拖动就撤掉按压形变：滚动 / 横滑不该保持缩小的样子。
            pressedRowId = null
            applyPressScale(false)
            if (dragAxis == DragAxis.HORIZONTAL) {
                if (stacked) resetRowDrag() else offsetPx = 0f
            }
            if (stacked && dragAxis == DragAxis.VERTICAL) showScrollThumb()
        }
        val v = view ?: return
        when (dragAxis) {
            DragAxis.VERTICAL -> if (stacked) {
                // 滚动栈：内容跟随手指，两端直接停住，不露出空白槽位。
                offsetPx = stackMetrics.clamp(offsetPx + dy)
                showScrollThumb()
            } else {
                // 连续跟手：允许一次滑过多条；两端直接停住，不露出空白槽位。
                offsetPx += dy
                normalizeOffset()
            }

            DragAxis.HORIZONTAL -> if (stacked) {
                onStackRowDragged(v)
            } else {
                v.translationX = dragDx * CollapseFollowRatio
                val fade = (abs(dragDx) / collapseThresholdPx).coerceIn(0f, 1f)
                v.alpha = 1f - fade * 0.25f
            }

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
                if (stacked) settleScroll(vy) else settleVertical(vy)
            }

            DragAxis.HORIZONTAL -> {
                if (!stacked) offsetPx = 0f
                menuItemId = null
                val towardEdge = if (sideRight) dx >= collapseThresholdPx else dx <= -collapseThresholdPx
                val awayFromEdge = if (sideRight) dx <= -collapseThresholdPx else dx >= collapseThresholdPx
                when {
                    towardEdge -> onCollapsed?.invoke()
                    awayFromEdge -> swipedId?.let { onSwipedAway?.invoke(it) }
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

    /** 堆叠单行高：跟着球高走，但不让大球把多行胶囊撑成整屏。 */
    private fun rowHeightFor(anchorHeightPx: Int): Int =
        anchorHeightPx.coerceIn(
            DensityProvider.dp2px(RowHeightMinDp),
            DensityProvider.dp2px(RowHeightMaxDp),
        )

    private fun updateWindowLayout(v: View) {
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val height = windowHeightPx
        // 首帧前 rowHeight 还没测出来：别把窗口写成 0 高。
        if (capsuleWidthPx <= 0 || height <= 0) return
        if (lp.width == capsuleWidthPx && lp.height == height) return
        lp.width = capsuleWidthPx
        lp.height = height
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
        // 堆叠更高：球贴近底边时窗口会伸出屏外，把顶边夹回屏内（胶囊与球之间留出空隙）。
        val height = if (v.height > 0) v.height else windowHeightPx
        var top = anchor.top
        if (stacked) {
            top = min(top, (DensityProvider.screenHeightPx - height).coerceAtLeast(0))
        }
        writeBallPosition(v, wm, x.toFloat(), top.toFloat(), lpOriginY)
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
        entering = true
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

    /**
     * 按压缩放反馈（§5.3，与主球同一档位）。
     *
     * pivot 落在展开侧：收起方向与进场形变一致，不朝锚点球的方向缩。
     * 只动 [View.scaleX/Y]，不与进场 / 退场动画抢属性 —— 进场的 [startMorphIn] 在按下时已被
     * [cancelEnterFeedback] 收束，退场的 [hide] 自带 `scaleX(collapsedScale())` 会把本值覆盖。
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

    @Composable
    private fun CapsuleContent() {
        SideGestureTheme {
            val list = items
            if (list.isEmpty() || pageHeightPx <= 0) return@SideGestureTheme
            val density = LocalDensity.current
            if (stacked) {
                if (rowHeightPx <= 0) return@SideGestureTheme
                val rowHeight = with(density) { rowHeightPx.toDp() }
                CapsuleStack(list = list, rowHeight = rowHeight)
                return@SideGestureTheme
            }
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

    /**
     * 通知胶囊的滚动栈：每条通知一颗独立胶囊，最多设置里的同屏条数可见，超出竖向滚动。
     *
     * 窗口只做透明承载；每颗胶囊各自裁形 / 底色 / 描边，之间留 [RowGapDp] 的间距。
     * 滚动位移直接作用在 Column 的 layout offset 上（[offsetPx] 在 lambda 里读），
     * 跟手与横滑都不触发重组。
     */
    @Composable
    private fun CapsuleStack(list: List<BallItem>, rowHeight: androidx.compose.ui.unit.Dp) {
        val accent = settings.colorSpec.toMarkColor()
        val contentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
        val density = LocalDensity.current
        val iconSize = with(density) {
            min(rowHeight.value - 16f, 24f).coerceAtLeast(16f).dp
        }
        // 胶囊角色（§2.1.1）：连续曲率胶囊，半径随高度自适应。每颗各自裁形，不共享容器。
        val shape = CapsuleShape
        val metrics = stackMetrics
        val thumbHeightPx = if (metrics.maxScrollPx > 0) {
            (metrics.viewportHeightPx.toFloat() * metrics.viewportHeightPx / metrics.contentHeightPx)
                .roundToInt()
                .coerceIn(DensityProvider.dp2px(ScrollThumbMinDp), metrics.viewportHeightPx)
        } else {
            0
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { if (it.width > 0 && it.height > 0) contentReady = true },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, offsetPx.roundToInt()) },
                verticalArrangement = Arrangement.spacedBy(RowGapDp.dp),
            ) {
                list.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .graphicsLayer {
                                // 横滑只让按下的那颗跟手；其余胶囊原地不动。
                                val dragging = item.id == rowDragId
                                translationX = if (dragging) rowDragDx else 0f
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
                            .background(accent.copy(alpha = BubbleBackgroundAlpha))
                            .border(1.dp, contentColor.copy(alpha = 0.22f), shape)
                            .padding(horizontal = RowHorizontalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (item) {
                            is NotificationItem -> NotificationContent(
                                item = item,
                                contentColor = contentColor,
                                iconSize = iconSize,
                                // total=1：不显示页数徽标（滚动栈里逐行标号没有意义）。
                                index = 0,
                                total = 1,
                            )

                            is ResultItem -> ResultContent(
                                item = item,
                                contentColor = contentColor,
                                iconSize = iconSize,
                                index = 0,
                                total = 1,
                                copied = copiedItemId == item.id,
                            )
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
        // 胶囊角色（§2.1.1）：连续曲率胶囊，半径随高度自适应。不得用 CircleShape ——
        // 矮容器上退化成半圆，端部曲率在切点突变。
        val shape = CapsuleShape
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
            if (menuOpen && item is ResultItem) {
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
                        index = index,
                        total = total,
                    )

                    is ResultItem -> ResultContent(
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
    private fun RowScope.ResultContent(
        item: ResultItem,
        contentColor: Color,
        iconSize: androidx.compose.ui.unit.Dp,
        index: Int,
        total: Int,
        copied: Boolean,
    ) {
        ResultIcon(item = item, contentColor = contentColor, size = iconSize)
        Column(
            modifier = Modifier.weight(1f),
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
        const val CountHitDp = 44f
        const val ActionHitDp = 72f
        const val CollapseThresholdDp = 48f
        const val CollapseFollowRatio = 0.22f
        const val FlingVelocityPxPerSec = 900f
        const val BubbleBackgroundAlpha = 0.88f
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
