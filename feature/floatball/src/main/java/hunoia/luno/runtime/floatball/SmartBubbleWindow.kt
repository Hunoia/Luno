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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import hunoia.luno.runtime.floatball.alert.PulseTuning
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
 * 统一胶囊：从所属球的位置展开，收起时恢复该球。两颗胶囊同构：多行栈。
 *
 * - 通知胶囊展示未命中的普通通知；结果胶囊展示分发规则的胶囊档结果。
 * - 每条一颗独立胶囊，同屏行数按各自设置，超出竖向滚动；行宽贴内容、靠球一侧齐平。
 * - 横滑两方向与行长按执行可配的内置行为（[CapsuleRowGestures]，默认 = 出厂行为：
 *   朝球收起 / 反向忽略该行 / 结果行长按弹操作菜单），点按 = 执行主动作。
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

    fun updateSettings(next: FloatBallSettings) {
        val previousRows = effectiveVisibleRows()
        settings = next
        // 同屏行数变了必须立即改窗口高：否则新视口会被旧窗口裁掉 / 留白。
        if (previousRows == effectiveVisibleRows()) return
        offsetPx = stackMetrics.clamp(offsetPx)
        view?.let { v ->
            updateWindowLayout(v)
            v.post { applyWindowBounds() }
        }
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
        view?.let { updateWindowLayout(it) }
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
        // 多行更高：球贴近底边时窗口会伸出屏外，把顶边夹回屏内（胶囊与球之间留出空隙）。
        val height = if (v.height > 0) v.height else windowHeightPx
        val top = min(anchor.top, (DensityProvider.screenHeightPx - height).coerceAtLeast(0))
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
     */
    @Composable
    private fun CapsuleStack(list: List<BallItem>, rowHeight: androidx.compose.ui.unit.Dp) {
        val accent = settings.colorSpec.toMarkColor()
        val contentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
        val density = LocalDensity.current
        val iconSize = with(density) {
            min(rowHeight.value - 16f, 24f).coerceAtLeast(16f).dp
        }
        val maxRowWidth = with(density) { capsuleWidthPx.toDp() }
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
                    .offset { IntOffset(0, offsetPx.roundToInt()) },
                horizontalAlignment = if (sideRight) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(RowGapDp.dp),
            ) {
                list.forEach { item ->
                    Row(
                        modifier = Modifier
                            .widthIn(max = maxRowWidth)
                            .height(rowHeight)
                            .onSizeChanged { onRowMeasured(item.id, it.width) }
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
            // 到达提示动效（本体脉冲 / 边框流光）：盖在所有胶囊之上描边，不挡触摸与内容。
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
            when (plan.effect) {
                // 本体脉冲：3 层嵌套描边辉光呼吸，外→内逐层加亮，核心最后画保证锐利。
                NotificationAlertEffect.CapsulePulse -> {
                    val boost = plan.intensity.lerpInRange(PulseTuning.BoostMin, PulseTuning.BoostMax)
                    val peak = (RowBorderAlpha + boost * sin(Math.PI.toFloat() * t)).coerceAtMost(1f)
                    val corePx = PulseTuning.CoreWidthDp.dp.toPx()
                    val layers = listOf(
                        corePx * PulseTuning.GlowWidthScale2 to peak * PulseTuning.GlowAlphaFraction2,
                        corePx * PulseTuning.GlowWidthScale1 to peak * PulseTuning.GlowAlphaFraction1,
                        corePx to peak,
                    )
                    for (index in 0 until rows) {
                        val path = rowPaths.getOrNull(index) ?: continue
                        translate(left = 0f, top = index * (rowHeightPx + gapPx).toFloat() + offsetPx) {
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
                // 渐变 stop 必须严格递增：可见跨度不足时跳过该带，峰值点强制落在带内。
                NotificationAlertEffect.CapsuleShimmer -> {
                    val peak = plan.intensity.lerpInRange(ShimmerTuning.PeakAlphaMin, ShimmerTuning.PeakAlphaMax)
                    val pos = t.easedOut(ShimmerTuning.SweepEaseExp)
                    val band = ShimmerTuning.BandWidth
                    val trail = ShimmerTuning.TrailWidth
                    val mainStart = (pos - band / 2f).coerceIn(0f, 1f)
                    val mainEnd = (pos + band / 2f).coerceIn(0f, 1f)
                    val trailStart = (mainStart - trail).coerceIn(0f, 1f)
                    val mainSpan = mainEnd - mainStart
                    val trailSpan = mainStart - trailStart
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
                    val trailBrush = Brush.horizontalGradient(
                        trailStart to contentColor.copy(alpha = 0f),
                        trailStart + trailSpan / 2f to contentColor.copy(
                            alpha = peak * ShimmerTuning.TrailAlphaFraction,
                        ),
                        mainStart to contentColor.copy(alpha = 0f),
                    )
                    val stroke = Stroke(width = 1.5.dp.toPx())
                    for (index in 0 until rows) {
                        val path = rowPaths.getOrNull(index) ?: continue
                        translate(left = 0f, top = index * (rowHeightPx + gapPx).toFloat() + offsetPx) {
                            if (trailSpan > 0.01f) drawPath(path = path, brush = trailBrush, style = stroke)
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
    ) {
        AppIcon(packageName = item.packageName, tint = contentColor, size = iconSize)
        Column(
            // fill=false：文字短时行贴内容收缩，文字长时吃满剩余空间并省略。
            modifier = Modifier.weight(1f, fill = false),
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
        const val ActionHitDp = 72f
        const val CollapseThresholdDp = 48f
        const val CollapseFollowRatio = 0.22f
        const val FlingVelocityPxPerSec = 900f
        const val BubbleBackgroundAlpha = 0.88f

        /** 常驻胶囊描边的基线透明度：本体脉冲在其上叠加呼吸增量。 */
        const val RowBorderAlpha = 0.22f
    }
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
