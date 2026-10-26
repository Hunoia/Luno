package hunoia.luno.runtime.floatball

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.quicklaunch.icon.SearchIconCache
import hunoia.luno.runtime.condition.NotificationPulse
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 通知胶囊：TYPE_APPLICATION_OVERLAY 小窗，通知到达时屏幕底部居中弹出
 * 「应用图标 + 标题 + 正文」。窗口 WRAP_CONTENT + BOTTOM|CENTER_HORIZONTAL 重力：
 * 尺寸随内容自适应（正文为空只剩标题行则变矮），换内容重排后重力自动保持居中，
 * 全程无手工坐标。点击（[onTapped]）由 Service 发 contentIntent 或启动应用，
 * 自动收起计时归 Service。低频出现，走整拆整建（show 挂窗、hide 滑出后摘窗）；
 * 可见期间来新通知只换 Compose 态内容，不重播进场。
 * 动效一律 View 层（服务进程 ComposeView 重组不上屏，见 FloatBallWindow.applyVisual 注释）。
 */
internal class NotificationCapsuleWindow(private val context: android.content.Context) {

    private val wm: WindowManager get() = context.windowManager()
    private var view: View? = null

    /** 正在播退场动画的窗（view 已置空）：服务销毁时 detachNow 也要能把它摘掉。 */
    private var exitingView: View? = null

    // Compose 态：内容更新走 mutableStateOf 重组（镜像窗同款前提），普通字段不重绘。
    private var current by mutableStateOf<CapsuleItem?>(null)

    /** 整份设置只为了拿主球 accent 色（胶囊配色与剪贴胶囊同源）。 */
    private var settings: FloatBallSettings = FloatBallSettings()

    /** 底部安全边距的导航栏部分：show 时从系统资源读，读不到按兜底。 */
    private var navBarInsetPx = 0

    /** 进场动画在飞：期间按压不播缩放（同一 ViewPropertyAnimator，会取消进场卡在半透明）。 */
    private var entering = false

    /** 等待布局回调补播进场：WRAP_CONTENT 的高度布局后才有。 */
    private var enterPending = false

    /** 正文是否展开（上滑切换）：Compose 态，展开只改 maxLines / 圆角，高度即时变化。 */
    private var expanded by mutableStateOf(false)

    /** 本次手势累计的纵向拖动（屏幕 px，向下为正）；松手后清零。 */
    private var dragDy = 0f

    /** 手势开始时的展开态：上滑实时展开/收起预览的基准，松手即提交。 */
    private var expandedAtGestureStart = false

    /** 窗口 y 偏移动画（拖动回弹/收起回落）；避免和 View 动画共用一套状态。 */
    private var offsetAnimator: ValueAnimator? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 长按已触发：抬手不再当作点击（否则「按住看完再松手」会误开通知）。 */
    private var longPressFired = false
    private val longPressRunnable = Runnable { longPressFired = true }

    /** 点击胶囊（未超 touchSlop 抬手）。 */
    var onTapped: (() -> Unit)? = null

    /** 按住状态：Service 用来在按压/拖动期间暂停自动收起计时。 */
    var onPressStateChanged: ((Boolean) -> Unit)? = null

    /** 下滑越过后由 Service 记抑制 key 并走正常收起。 */
    var onSwipedAway: (() -> Unit)? = null

    /** 展开状态变化：展开期间由 Service 暂停计时，收起后重新计时。 */
    var onExpandedChanged: ((Boolean) -> Unit)? = null

    private val arbiter = FloatBallTouchArbiter(
        onSlideDragged = { _, dy ->
            view?.let { v ->
                // 进场未播完也要能拖：先收掉进场动画，再跟手。
                cancelEnter(v)
                dragDy += dy
                applyDrag(v)
            }
        },
        onTapped = { if (!longPressFired) onTapped?.invoke() },
        onPressChanged = { pressed ->
            // 先上报按压态：即使还在进场动画里，计时也应暂停。
            onPressStateChanged?.invoke(pressed)
            if (pressed) {
                longPressFired = false
                mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            } else {
                mainHandler.removeCallbacks(longPressRunnable)
            }
            view?.let { v ->
                if (pressed) {
                    // 按压从静止态开始：收掉进场/回弹/窗口偏移动画并归位，拖动基准才稳定。
                    cancelEnter(v)
                    v.animate().cancel()
                    offsetAnimator?.cancel()
                    offsetAnimator = null
                    moveWindowToOffset(v, bottomOffsetPx())
                    v.translationY = 0f
                    v.alpha = 1f
                    dragDy = 0f
                    expandedAtGestureStart = expanded
                }
                val scale = if (pressed) CapsulePressScale else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(PRESS_MS).start()
            }
        },
        onDragEnded = { settleDrag() },
    )

    fun updateSettings(next: FloatBallSettings) {
        settings = next
    }

    /** 正文是否处于展开态（Service 侧计时门控用）。 */
    fun isExpanded(): Boolean = expanded

    /** 弹出/刷新：不可见则挂窗播进场；已可见只换内容（新通知覆盖 + Service 重新计时）。返回是否成功挂窗。 */
    fun show(pulse: NotificationPulse): Boolean {
        current = CapsuleItem(pulse.packageName, pulse.title, pulse.text)
        val existing = view
        if (existing != null) return true
        // 新窗总是从收起态开始（上一条的展开态不带到下一条）。
        expanded = false
        navBarInsetPx = readNavBarInsetPx()
        val created = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // 瞬态窗推到 RESUMED：应用图标（produceState）与内容更新都要 effects/重组真正运行。
            FloatBallLifecycleOwner.createAndAttachTo(this, active = true)
            setContent { CapsuleContent() }
        }
        created.setOnTouchListener { _, event -> arbiter.onTouch(event) }
        created.alpha = 0f
        created.scaleX = 1f
        created.scaleY = 1f
        // 进场：从自身高度处滑入 + 淡入。WRAP_CONTENT 的高度布局后才知道，
        // View.post 早于下一帧 measure/layout（height 还是 0，滑入会变成纯淡入）。
        created.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.height <= 0 || !enterPending) return@addOnLayoutChangeListener
            enterPending = false
            v.translationY = v.height.toFloat()
            v.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(ENTER_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { entering = false }
                .start()
        }
        if (!wm.safeAddView(created, capsuleLayoutParams(bottomOffsetPx()))) {
            Log.e(TAG, "notification capsule addView failed (overlay permission or window rejected)")
            return false
        }
        view = created
        entering = true
        enterPending = true
        return true
    }

    /** 收起：淡出 + 滑回底部后整窗摘除（内容保留到淡完，别在半路清空闪白）。 */
    fun hide(onHidden: (() -> Unit)? = null) {
        val v = view
        view = null
        entering = false
        enterPending = false
        dragDy = 0f
        longPressFired = false
        mainHandler.removeCallbacks(longPressRunnable)
        if (v == null) {
            onHidden?.invoke()
            return
        }
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
        applyBallTouchFlags(v, wm, hidden = true)
        // 退出动画期间也留着句柄：服务在此期间销毁时 detachNow 才能把窗摘掉。
        exitingView = v
        // 收起不做 translation（根 View 位移会被窗口 surface 边界裁切）：
        // 从当前窗口偏移回落到贴底位置并淡出，全程完整可见。
        animateWindowOffset(v, currentOffset(v), bottomOffsetPx(), HIDE_MS)
        v.animate()
            .alpha(0f)
            .setDuration(HIDE_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                offsetAnimator?.cancel()
                offsetAnimator = null
                if (exitingView === v) exitingView = null
                context.removeWindow(v)
                onHidden?.invoke()
            }
            .start()
    }

    /** 不播动画直接摘窗：服务销毁等即时路径（含正在播退场动画的窗）。 */
    fun detachNow() {
        val v = view
        val exiting = exitingView
        view = null
        exitingView = null
        entering = false
        enterPending = false
        dragDy = 0f
        expanded = false
        longPressFired = false
        offsetAnimator?.cancel()
        offsetAnimator = null
        mainHandler.removeCallbacks(longPressRunnable)
        current = null
        v?.let(context::removeWindow)
        exiting?.let {
            it.animate().cancel()
            context.removeWindow(it)
        }
    }

    /** 收掉在飞的进场动画（拖拽/按压需要即时响应时）；已完成则无操作。 */
    private fun cancelEnter(v: View) {
        if (!entering && !enterPending) return
        enterPending = false
        v.animate().cancel()
        v.alpha = 1f
        v.translationY = 0f
        v.scaleX = 1f
        v.scaleY = 1f
        entering = false
    }

    /**
     * 拖动跟手：
     * - 下滑：窗口整体下移（`lp.y`，不是 view.translation，避开 surface 裁切），越接近
     *   关闭阈值越透明；
     * - 上滑：**胶囊不动**，越过阈值即实时向上展开（高度变高、底边锁定），拉回则收起。
     */
    private fun applyDrag(v: View) {
        if (dragDy > 0f) {
            moveWindowToOffset(v, (bottomOffsetPx() - dragDy).roundToInt().coerceAtLeast(0))
            v.alpha = (1f - dragDy / dismissThresholdPx(v)).coerceIn(DragMinAlpha, 1f)
        } else {
            // 上滑是展开手势，窗口保持贴底不动，只靠高度增长向上展开。
            moveWindowToOffset(v, bottomOffsetPx())
            v.alpha = 1f
        }
        // 下滑不参与展开：正向拖动时 preview 自然回落到手势起始态。
        previewExpanded(v, dragDy)
    }

    /**
     * 按「手势开始时状态 + 是否越过上滑阈值」实时切换展开态（拉回可取消）。
     * 切换后回退阈值降为 [ExpandHysteresisRatio] 倍，避免在阈值附近抖动导致反复展开/收起。
     */
    private fun previewExpanded(v: View, dy: Float) {
        val threshold = expandThresholdPx(v)
        val switched = expanded != expandedAtGestureStart
        val past = if (switched) dy <= -threshold * ExpandHysteresisRatio else dy <= -threshold
        val next = expandedAtGestureStart xor past
        if (next == expanded) return
        expanded = next
        onExpandedChanged?.invoke(next)
    }

    /** 松手结算：下滑足够 → 交给 Service 收起；上滑的展开态已在拖动中预览并提交。 */
    private fun settleDrag() {
        val v = view ?: return
        val dy = dragDy
        dragDy = 0f
        val dismissThreshold = dismissThresholdPx(v)
        if (dy >= dismissThreshold) {
            val dismiss = onSwipedAway
            if (dismiss != null) {
                v.animate().cancel()
                dismiss.invoke()
            } else {
                restoreAfterDrag(v)
            }
        } else {
            restoreAfterDrag(v)
        }
    }

    /** 回弹到贴底位置并恢复不透明。 */
    private fun restoreAfterDrag(v: View) {
        v.scaleX = 1f
        v.scaleY = 1f
        animateWindowOffset(v, currentOffset(v), bottomOffsetPx(), RESTORE_MS)
        v.animate()
            .alpha(1f)
            .setDuration(RESTORE_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** 当前窗口 y 偏移；读不到 LP 时按贴底位置。 */
    private fun currentOffset(v: View): Int =
        (v.layoutParams as? WindowManager.LayoutParams)?.y ?: bottomOffsetPx()

    /** 改窗口 y 偏移（真正的窗口位移，不被 surface 裁切）。 */
    private fun moveWindowToOffset(v: View, offsetPx: Int) {
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        if (lp.y == offsetPx) return
        lp.y = offsetPx
        wm.safeUpdateViewLayout(v, lp)
    }

    /** 把一个窗口 y 偏移动画到目标值；起止相同时直接落位。 */
    private fun animateWindowOffset(v: View, from: Int, to: Int, durationMs: Long) {
        offsetAnimator?.cancel()
        offsetAnimator = null
        if (from == to) {
            moveWindowToOffset(v, to)
            return
        }
        offsetAnimator = ValueAnimator.ofInt(from, to).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { moveWindowToOffset(v, it.animatedValue as Int) }
            start()
        }
    }

    /** 下滑关闭阈值：屏高低比例和固定 dp 取大，小胶囊也能可靠触发。 */
    private fun dismissThresholdPx(v: View): Float =
        max(DensityProvider.dp2px(CapsuleDismissMinDp).toFloat(), v.height * CapsuleDismissHeightRatio)

    /** 上滑展开阈值：比关闭门槛小，避免展开也要大幅拖。 */
    private fun expandThresholdPx(v: View): Float =
        max(DensityProvider.dp2px(CapsuleExpandMinDp).toFloat(), v.height * CapsuleExpandHeightRatio)

    private fun readNavBarInsetPx(): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val nav = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        return max(nav, DensityProvider.dp2px(CapsuleMinNavBarDp))
    }

    /** BOTTOM 重力下的窗底偏移：导航栏 + 间距。 */
    private fun bottomOffsetPx(): Int =
        navBarInsetPx + DensityProvider.dp2px(CapsuleBottomMarginDp)

    // ---- Compose ----

    @Composable
    private fun CapsuleContent() {
        SideGestureTheme {
            current?.let { CapsuleFace(item = it) }
        }
    }

    @Composable
    private fun CapsuleFace(item: CapsuleItem) {
        val maxPillWidth = with(LocalDensity.current) {
            (DensityProvider.screenWidthPx - DensityProvider.dp2px(CapsuleSideMarginDp * 2)).toDp()
        }
        val accentColor = gestureButtonMarkColor(settings.color)
        // accent 任意选色：深底白字、浅底黑字，不写死 onSurface。
        val contentColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
        val pill = if (expanded) {
            // 展开后是多行正文，大圆角会把两侧文字裁得很碎：换成普通卡片圆角。
            RoundedCornerShape(CapsuleExpandedCornerDp.dp)
        } else {
            RoundedCornerShape(percent = 50)
        }
        Row(
            modifier = Modifier
                .widthIn(max = maxPillWidth)
                .clip(pill)
                .background(accentColor.copy(alpha = CapsuleBackgroundAlpha))
                .border(1.dp, contentColor.copy(alpha = 0.22f), pill)
                .padding(horizontal = CapsuleInnerPaddingDp.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(packageName = item.packageName)
            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor.copy(alpha = 0.95f),
                    maxLines = if (expanded) CapsuleExpandedTitleLines else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.82f),
                        maxLines = if (expanded) CapsuleExpandedTextLines else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    private fun AppIcon(packageName: String) {
        val sizePx = DensityProvider.dp2px(CapsuleIconDp)
        val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
            val cached = SearchIconCache.get(packageName, sizePx)
            if (cached != null) {
                value = cached
            } else {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        SearchIconCache.ensureAll(context, listOf(packageName), sizePx)
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
                modifier = Modifier.size(CapsuleIconDp.dp),
            )
        } else {
            Box(modifier = Modifier.size(CapsuleIconDp.dp))
        }
    }

    /** 胶囊当前内容；Compose 态字段，show 更新触发重组。 */
    private data class CapsuleItem(val packageName: String, val title: String, val text: String)

    private companion object {
        const val TAG = LOG_TAG

        const val ENTER_MS = 250L
        const val HIDE_MS = 200L
        const val PRESS_MS = 100L
        const val RESTORE_MS = 180L

        /** 长按门槛：超过即视为「按住阅读」，松手不开通知。 */
        const val LONG_PRESS_MS = 450L

        /** 下滑关闭/上滑展开阈值：固定 dp 与高度的比例取大。 */
        const val CapsuleDismissMinDp = 56f
        const val CapsuleDismissHeightRatio = 0.4f
        const val CapsuleExpandMinDp = 24f
        const val CapsuleExpandHeightRatio = 0.25f

        /** 已切换展开态后的回退阈值比例：滞回，防阈值附近反复切换。 */
        const val ExpandHysteresisRatio = 0.6f

        /** 拖动时保留的最低透明度：低于它就“看不见但还在”，松手回弹会闪。 */
        const val DragMinAlpha = 0.35f

        /** 展开后的最大行数：标题与正文。 */
        const val CapsuleExpandedTitleLines = 2
        const val CapsuleExpandedTextLines = 6

        const val CapsuleIconDp = 24f
        const val CapsuleInnerPaddingDp = 16f
        const val CapsuleSideMarginDp = 16f

        /** 底色透明度（与剪贴胶囊同口径，保留 accent 色但不要过透）。 */
        const val CapsuleBackgroundAlpha = 0.85f

        /** 展开后的圆角（dp）：多行正文不用大圆角。 */
        const val CapsuleExpandedCornerDp = 16f
        const val CapsuleBottomMarginDp = 10f
        const val CapsuleMinNavBarDp = 12f
        const val CapsulePressScale = 0.95f
    }
}
