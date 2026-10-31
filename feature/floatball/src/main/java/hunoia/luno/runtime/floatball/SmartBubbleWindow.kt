package hunoia.luno.runtime.floatball

import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.floatball.R
import hunoia.luno.runtime.floatball.inbox.BallItem
import hunoia.luno.runtime.floatball.inbox.ClipboardImageItem
import hunoia.luno.runtime.floatball.inbox.ClipboardTextItem
import hunoia.luno.runtime.floatball.inbox.NotificationItem
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.icon.SearchIconCache
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 统一共享气泡：通知与剪贴板共用一个小窗。
 *
 * 窗口 WRAP_CONTENT + LEFT|TOP，由 [BubbleGeometry] 贴着球当前可见边定位；
 * 内容更新走 Compose 态，进场/按压/退场走 View 层动画。手势在窗口层仲裁：
 * 正文点按 / 长按 / 上下滑，右侧计数 chip 与展开图标各有独立命中区，
 * 避免 Compose 子级消费触摸破坏拖动仲裁。
 */
internal class SmartBubbleWindow(private val context: android.content.Context) {

    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var exitingView: View? = null

    private var current by mutableStateOf<BallItem?>(null)
    private var currentIndex by mutableIntStateOf(0)
    private var total by mutableIntStateOf(0)
    private var linkInfo by mutableStateOf<ClipboardLinkInfo?>(null)
    private var maxWidthPx by mutableIntStateOf(0)

    private var settings: FloatBallSettings = FloatBallSettings()
    private var anchor = BubbleAnchor(0, 0, 0, 0)
    private var lpOriginY = 0
    private var navBarInsetPx = 0
    private var originReady = false
    private var entering = false
    private var enterPending = false

    private var hitTarget = HitTarget.BODY
    private var hitStartX = 0f
    private var hitStartY = 0f
    private var hitMoved = false
    private var dragDy = 0f

    private val bodyArbiter = FloatBallTouchArbiter(
        onSlideDragged = { _, dy -> onBodyDragged(dy) },
        onTapped = { onBodyTapped?.invoke() },
        onPressChanged = { pressed ->
            onPressStateChanged?.invoke(pressed)
            view?.let { v ->
                cancelEnter(v)
                v.animate()
                    .scaleX(if (pressed) PressScale else 1f)
                    .scaleY(if (pressed) PressScale else 1f)
                    .setDuration(PRessMs)
                    .start()
            }
            if (pressed) {
                dragDy = 0f
            }
        },
        onDragEnded = { finishBodyDrag() },
        onLongPress = { onLongPress?.invoke() },
    )

    /** 正文点按（通知直达 / 剪贴板分享）。 */
    var onBodyTapped: (() -> Unit)? = null

    /** 正文长按（仅剪贴板：钉住 / 取消钉住）。 */
    var onLongPress: (() -> Unit)? = null

    /** 计数 chip：切到下一项。 */
    var onCountTapped: (() -> Unit)? = null

    /** 下滑：关闭并删除当前项（钉住项只隐藏）。 */
    var onSwipedAway: (() -> Unit)? = null

    /** 按住状态：暂停 / 恢复自动收起。 */
    var onPressStateChanged: ((Boolean) -> Unit)? = null

    fun updateSettings(next: FloatBallSettings) {
        settings = next
    }

    /** 异步补链接信息：识别在 IO 线程完成后回填，当前项已切换则忽略。 */
    fun updateLink(link: ClipboardLinkInfo?) {
        linkInfo = link
    }

    fun isPinned(): Boolean = current?.pinned == true

    /**
     * 显示 / 刷新气泡。已可见时只更新内容并重新贴边，不重播进场。
     */
    fun show(
        item: BallItem,
        index: Int,
        totalCount: Int,
        anchor: BubbleAnchor,
        link: ClipboardLinkInfo?,
    ): Boolean {
        current = item
        currentIndex = index.coerceAtLeast(0)
        total = totalCount.coerceAtLeast(1)
        this.anchor = anchor
        linkInfo = link
        maxWidthPx = provisionalMaxWidth(anchor)

        val existing = view
        if (existing != null) {
            existing.requestLayout()
            existing.post { applyWindowBounds() }
            return true
        }
        originReady = false
        val created = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            // 瞬态窗推到 RESUMED：内容、图标与展开态需要 effects / 重组真正运行。
            FloatBallLifecycleOwner.createAndAttachTo(this, active = true)
            setContent { BubbleContent() }
            alpha = 0f
            scaleX = 0.96f
            scaleY = 0.96f
        }
        created.setOnTouchListener { v, event -> handleTouch(v, event) }
        created.setOnApplyWindowInsetsListener { v, insets ->
            navBarInsetPx = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            v.onApplyWindowInsets(insets)
        }
        created.requestApplyInsets()
        created.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width <= 0 || v.height <= 0) return@addOnLayoutChangeListener
            if (!originReady) {
                lpOriginY = screenOriginY(v)
                originReady = true
            }
            applyWindowBounds()
            if (enterPending) {
                enterPending = false
                startEnter(v)
            }
        }
        if (!wm.safeAddView(created, bubbleLayoutParams())) {
            Log.e(TAG, "smart bubble addView failed (overlay permission or window rejected)")
            current = null
            return false
        }
        view = created
        entering = true
        enterPending = true
        mainHandler.postDelayed({
            val v = view
            if (v === created && !originReady) {
                lpOriginY = screenOriginY(v)
                originReady = true
                applyWindowBounds()
                if (enterPending) {
                    enterPending = false
                    startEnter(v)
                }
            }
        }, EnterFallbackMs)
        return true
    }

    fun hide(onHidden: (() -> Unit)? = null) {
        val v = view
        view = null
        entering = false
        enterPending = false
        hitTarget = HitTarget.BODY
        current = null
        linkInfo = null
        dragDy = 0f
        mainHandler.removeCallbacksAndMessages(null)
        if (v == null) {
            onHidden?.invoke()
            return
        }
        applyBallTouchFlags(v, wm, hidden = true)
        exitingView = v
        v.animate().cancel()
        v.animate()
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setDuration(HideMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (exitingView === v) exitingView = null
                context.removeWindow(v)
                onHidden?.invoke()
            }
            .start()
    }

    fun detachNow() {
        val v = view
        val exiting = exitingView
        view = null
        exitingView = null
        entering = false
        enterPending = false
        current = null
        linkInfo = null
        dragDy = 0f
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
        maxWidthPx = provisionalMaxWidth(anchor)
        view?.requestLayout()
        view?.post { applyWindowBounds() }
    }

    // ---- 手势 ----

    private fun handleTouch(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            hitTarget = resolveHitTarget(v, event.x)
            if (hitTarget != HitTarget.BODY) {
                hitStartX = event.rawX
                hitStartY = event.rawY
                hitMoved = false
                cancelEnter(v)
                onPressStateChanged?.invoke(true)
                v.animate().scaleX(PressScale).scaleY(PressScale).setDuration(PRessMs).start()
                return true
            }
        }
        if (hitTarget != HitTarget.BODY) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - hitStartX
                    val dy = event.rawY - hitStartY
                    if (dx * dx + dy * dy > touchSlop * touchSlop) hitMoved = true
                }

                MotionEvent.ACTION_UP -> {
                    onPressStateChanged?.invoke(false)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(PRessMs).start()
                    if (!hitMoved) {
                        when (hitTarget) {
                            HitTarget.COUNT -> onCountTapped?.invoke()
                            HitTarget.BODY -> Unit
                        }
                    }
                    hitTarget = HitTarget.BODY
                }

                MotionEvent.ACTION_CANCEL -> {
                    onPressStateChanged?.invoke(false)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(PRessMs).start()
                    hitTarget = HitTarget.BODY
                }
            }
            return true
        }
        return bodyArbiter.onTouch(event)
    }

    private fun resolveHitTarget(v: View, x: Float): HitTarget {
        val count = if (total > 1) DensityProvider.dp2px(CountHitDp) else 0
        val countStart = v.width - count
        return when {
            total > 1 && x >= countStart -> HitTarget.COUNT
            else -> HitTarget.BODY
        }
    }

    private fun onBodyDragged(dy: Float) {
        dragDy += dy
        val v = view ?: return
        if (dragDy > 0f) {
            val fade = (dragDy / DismissThresholdPx).coerceIn(0f, 1f)
            v.alpha = 1f - fade * 0.45f
        }
    }

    private fun finishBodyDrag() {
        val v = view
        val dy = dragDy
        dragDy = 0f
        if (v != null) {
            v.alpha = 1f
            v.translationY = 0f
        }
        when {
            dy >= DismissThresholdPx -> onSwipedAway?.invoke()
        }
    }

    private val touchSlop: Float
        get() = DensityProvider.dp2px(12f).toFloat()

    // ---- 定位 ----

    private fun applyWindowBounds() {
        val v = view ?: return
        if (!originReady || v.width <= 0 || v.height <= 0) return
        val safeBottom = (DensityProvider.screenHeightPx - navBarInsetPx).coerceAtLeast(0)
        val placement = BubbleGeometry.place(
            ball = anchor,
            bubbleWidthPx = v.width,
            bubbleHeightPx = v.height,
            screenWidthPx = DensityProvider.screenWidthPx,
            screenHeightPx = DensityProvider.screenHeightPx,
            safeTopPx = lpOriginY,
            safeBottomPx = safeBottom,
            marginPx = DensityProvider.dp2px(MarginDp),
            gapPx = DensityProvider.dp2px(GapDp),
            minWidthPx = DensityProvider.dp2px(MinWidthDp),
            maxWidthPx = DensityProvider.dp2px(MaxWidthDp),
        )
        if (maxWidthPx != placement.maxWidthPx) maxWidthPx = placement.maxWidthPx
        writeBallPosition(v, wm, placement.x.toFloat(), placement.y.toFloat(), lpOriginY)
    }

    private fun provisionalMaxWidth(anchor: BubbleAnchor): Int {
        val placement = BubbleGeometry.place(
            ball = anchor,
            bubbleWidthPx = 0,
            bubbleHeightPx = 0,
            screenWidthPx = DensityProvider.screenWidthPx,
            screenHeightPx = DensityProvider.screenHeightPx,
            safeTopPx = lpOriginY,
            safeBottomPx = DensityProvider.screenHeightPx,
            marginPx = DensityProvider.dp2px(MarginDp),
            gapPx = DensityProvider.dp2px(GapDp),
            minWidthPx = DensityProvider.dp2px(MinWidthDp),
            maxWidthPx = DensityProvider.dp2px(MaxWidthDp),
        )
        return placement.maxWidthPx
    }

    private fun screenOriginY(v: View): Int {
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        return (location[1] - ((v.layoutParams as? WindowManager.LayoutParams)?.y ?: 0))
            .coerceIn(0, DensityProvider.dp2px(MaxOriginDp))
    }

    private fun startEnter(v: View) {
        v.translationX = 0f
        v.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(EnterMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { if (view === v) entering = false }
            .start()
        mainHandler.postDelayed({ if (view === v) entering = false }, EnterMs + 80L)
    }

    private fun cancelEnter(v: View) {
        if (!entering && !enterPending) return
        enterPending = false
        v.animate().cancel()
        v.alpha = 1f
        v.scaleX = 1f
        v.scaleY = 1f
        entering = false
    }

    // ---- Compose ----

    @Composable
    private fun BubbleContent() {
        SideGestureTheme {
            BubbleFace()
        }
    }

    @Composable
    private fun BubbleFace() {
        val item = current ?: return
        val accent = gestureButtonMarkColor(settings.color)
        val contentColor = if (accent.luminance() > 0.5f) Color.Black else Color.White
        val density = LocalDensity.current
        val maxWidth = with(density) {
            maxWidthPx.takeIf { it > 0 }?.toDp() ?: MaxWidthDp.dp
        }
        val minWidth = if (maxWidth < MinWidthDp.dp) maxWidth else MinWidthDp.dp
        val shape = RoundedCornerShape(24.dp)
        Row(
            modifier = Modifier
                .widthIn(min = minWidth, max = maxWidth)
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(accent.copy(alpha = BubbleBackgroundAlpha))
                .border(1.dp, contentColor.copy(alpha = 0.22f), shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BubbleIcon(item = item, contentColor = contentColor)
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title.ifBlank { stringResource(R.string.smart_ball_notification) },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.95f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.pinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.smart_ball_pinned),
                            tint = contentColor.copy(alpha = 0.75f),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
                if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.82f),
                        maxLines = CollapsedLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val link = linkInfo
            if (link != null) {
                when {
                    link.count > 1 -> LinkChip(
                        text = stringResource(R.string.clipboard_link_count, link.count),
                        color = contentColor,
                    )

                    link.targetLabel != null -> LinkChip(
                        text = link.targetLabel,
                        color = contentColor,
                    )
                }
            }
            if (total > 1) {
                Text(
                    text = stringResource(R.string.smart_ball_count, currentIndex + 1, total),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.9f),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(contentColor.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }

    @Composable
    private fun BubbleIcon(item: BallItem, contentColor: Color) {
        when (item) {
            is ClipboardImageItem -> AsyncImage(
                model = item.uri,
                contentDescription = stringResource(R.string.smart_ball_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )

            is NotificationItem -> AppIcon(packageName = item.packageName, tint = contentColor)

            is ClipboardTextItem -> {
                val target = linkInfo?.targetPackage
                if (target != null) {
                    AppIcon(packageName = target, tint = contentColor)
                } else {
                    Icon(
                        imageVector = if (item.pinned) Icons.Default.PushPin else Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun AppIcon(packageName: String, tint: Color) {
        val sizePx = DensityProvider.dp2px(22)
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
                modifier = Modifier.size(22.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                tint = tint.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp),
            )
        }
    }

    @Composable
    private fun LinkChip(text: String, color: Color) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(color.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }

    private enum class HitTarget { BODY, COUNT }

    private companion object {
        const val TAG = LOG_TAG
        const val EnterMs = 220L
        const val HideMs = 160L
        const val PRessMs = 100L
        const val EnterFallbackMs = 260L
        const val MaxOriginDp = 200f
        const val PressScale = 0.96f

        const val MinWidthDp = 200f
        const val MaxWidthDp = 360f
        const val MarginDp = 8f
        const val GapDp = 8f
        const val CountHitDp = 44f
        const val BubbleBackgroundAlpha = 0.88f
        const val CollapsedLines = 2

        val DismissThresholdPx: Float = DensityProvider.dp2px(44f).toFloat()
    }
}

/**
 * 气泡要展示的链接信息：认出几条链接 + 单条命中规则时的目标应用。
 * 由 [FloatBallService] 按链接分发规则算好传入，窗口只负责画。
 */
internal data class ClipboardLinkInfo(
    val count: Int,
    val targetPackage: String? = null,
    val targetLabel: String? = null,
)
