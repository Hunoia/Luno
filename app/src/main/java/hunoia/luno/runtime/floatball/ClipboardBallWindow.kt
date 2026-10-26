package hunoia.luno.runtime.floatball

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import hunoia.luno.R
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.quicklaunch.icon.SearchIconCache
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 剪贴板球：TYPE_APPLICATION_OVERLAY 胶囊小窗，剪贴板内容（文本/图片）变化时在贴边
 * 记忆位弹出「图标 + 内容预览」（配色随主球）。窗口 WRAP_CONTENT：宽度随内容自适应，
 * 重排后按记忆侧重新贴边。
 * 点击（[onTapped]）由 Service 分享，长按 pin/取消 pin（[onPinChanged]，钉住期间不自动
 * 收起），尾部图标点击展开/收起全文（[onExpandedChanged]），按住即拖、抬手横向贴最近边，
 * [onDragReleased] 后由 Service 读 [normalizedPosition] 落盘。自动收起计时归 Service。
 * 动效一律 View 层（服务进程 ComposeView 重组不上屏，见 FloatBallWindow.applyVisual 注释）。
 */
internal class ClipboardBallWindow(private val context: android.content.Context) {

    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: View? = null

    /** 正在播退场动画的窗（view 已置空）：服务销毁时 detachNow 也要能把它摘掉。 */
    private var exitingView: View? = null

    /** Compose 态：内容更新走 mutableStateOf 重组，普通字段不重绘。 */
    private var content by mutableStateOf<ClipboardContent?>(null)

    /** 钉住：暂停自动收起，内容不跟着剪贴板变化。 */
    private var pinned by mutableStateOf(false)

    /** 展开全文（尾部图标切换）：展开期间由 Service 暂停计时。 */
    private var expanded by mutableStateOf(false)

    /** 钉住期间来了新内容：显示小圆点，取消钉住时切到最新内容。 */
    private var pending by mutableStateOf(false)

    /**
     * 链接信息（见 [ClipboardLinkInfo]）：单条命中规则时显示目标应用图标/名，
     * 多条链接显示条数角标。Service 在每次 show 前按规则表算好传入；
     * 窗口本身不碰规则与包名解析（服务进程 Compose 重组不上屏，只吃现成值）。
     */
    private var linkInfo by mutableStateOf<ClipboardLinkInfo?>(null)

    private var settings: FloatBallSettings = FloatBallSettings()

    /** LP 坐标系原点在屏幕上的 y（状态栏下沿），attach 后实测（与球窗同套路）。 */
    private var lpOriginY = 0

    private var posX = 0f
    private var posY = 0f

    /** 贴哪侧边（内容重排重新贴边、进出场滑动方向都看它）。 */
    private var pinnedRight = true

    /** 进场动画在飞：期间按压/拖动/长按不介入（同一 ViewPropertyAnimator 会互相打断）。 */
    private var entering = false

    /** 等待布局回调补播进场：WRAP_CONTENT 的宽度布局后才有，进场位移要按真实宽度算。 */
    private var enterPending = false

    /** 本次窗口的 LP 原点是否已实测。实测前绝不写 lp.y（否则会把原点差当成窗口位置）。 */
    private var originReady = false

    /** 点击球（未超 touchSlop 抬手；长按已触发则吞掉后续 tap）。 */
    var onTapped: (() -> Unit)? = null

    /** 长按 pin / 取消 pin（钉住期间不自动收起，计时门控在 Service）。 */
    var onPinChanged: ((Boolean) -> Unit)? = null

    /** 展开状态变化：展开期间暂停自动收起。 */
    var onExpandedChanged: ((Boolean) -> Unit)? = null

    /** 拖动抬手：贴边已在本窗内结算，Service 读 [normalizedPosition] 落盘。 */
    var onDragReleased: (() -> Unit)? = null

    /** 手指按压状态：Service 用它在按住/拖动期间暂停自动收起计时。 */
    var onPressStateChanged: ((Boolean) -> Unit)? = null

    /** 用户正在拖动：期间不再按“当前贴边侧”重钉 x（否则永远拖不到另一侧）。 */
    private var userDragging = false

    /** 本次手势落在尾部展开/收起图标上（窗口层判定，不进拖动仲裁）。 */
    private var iconGesture = false

    // 长按检测：仲裁器只分「拖/点」，长按在窗口层补。
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        longPressFired = true
        togglePin()
    }

    private val arbiter = FloatBallTouchArbiter(
        onSlideDragged = { dx, dy ->
            // 进场未播完也要能拖：直接收掉进场动画再跟手，不要拦住拖动。
            view?.let { cancelEnter(it) }
            userDragging = true
            onDragged(dx, dy)
        },
        onTapped = { if (!longPressFired) onTapped?.invoke() },
        onPressChanged = { pressed ->
            onPressStateChanged?.invoke(pressed)
            view?.let { v ->
                cancelEnter(v)
                val scale = if (pressed) PressScale else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(PRESS_MS).start()
            }
            if (pressed) {
                longPressFired = false
                mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            } else {
                mainHandler.removeCallbacks(longPressRunnable)
            }
        },
        onDragEnded = {
            // 松手才决定归哪侧：拖动中 x 必须跟手，不能提前回贴。
            userDragging = false
            snapToNearestEdge()
            applyWindowBounds()
            onDragReleased?.invoke()
        },
    )

    private val screenWidthPx: Int get() = DensityProvider.screenWidthPx
    private val screenHeightPx: Int get() = DensityProvider.screenHeightPx

    fun updateSettings(next: FloatBallSettings) {
        settings = next
    }

    /** 是否处于 pin 状态（Service 计时门控用）。 */
    fun isPinned(): Boolean = pinned

    /** 是否处于展开态（Service 计时门控用）。 */
    fun isExpanded(): Boolean = expanded

    /** 钉住期间有新内容到达：显示小圆点（由 Service 控制）。 */
    fun setPendingMarker(value: Boolean) {
        pending = value
    }

    private fun togglePin() {
        pinned = !pinned
        onPinChanged?.invoke(pinned)
    }

    private fun toggleExpanded() {
        if (content !is ClipboardContent.Text) return
        expanded = !expanded
        onExpandedChanged?.invoke(expanded)
    }

    /**
     * 尾部图标点击走窗口层判定：Compose 内不加 clickable，避免子级消费触摸导致拖动仲裁失效。
     * 命中区为胶囊右缘 [PillIconHitWidthDp] 宽的区域。
     */
    private fun handleTouch(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            iconGesture = !userDragging &&
                content is ClipboardContent.Text &&
                event.x >= v.width - DensityProvider.dp2px(PillIconHitWidthDp)
        }
        if (iconGesture) {
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    iconGesture = false
                    toggleExpanded()
                }
                MotionEvent.ACTION_CANCEL -> iconGesture = false
                else -> Unit
            }
            return true
        }
        return arbiter.onTouch(event)
    }

    /**
     * 弹出/刷新：不可见则挂窗，从贴边侧滑入 + 淡入；已可见只换文本并重新贴边
     * （内容变了胶囊宽度会变）。[x]/[y] 为记忆位（归一化），x 也可视作侧别记忆，
     * y 落默认（屏高 [DefaultYRatio]）。返回是否成功挂窗。
     */
    fun show(
        x: Float?,
        y: Float?,
        next: ClipboardContent,
        initialPinned: Boolean = false,
        nextLink: ClipboardLinkInfo? = null,
    ): Boolean {
        content = next
        linkInfo = nextLink
        // 新内容一律从收起态开始（上一条的展开态不带到下一条）。
        expanded = false
        // 已可见（新内容覆盖）：内容变了宽高也变，靠布局回调重新贴边。
        if (view != null) return true
        // 新窗从干净状态开始：待更新标记不带到下一条；pin 仅由「锁屏恢复」显式带入。
        pinned = initialPinned
        pending = false
        iconGesture = false
        resolvePosition(x, y)
        // 每个新窗都重新测原点：测量必须在写 lp.y 之前。
        originReady = false
        val created = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            // 瞬态窗推到 RESUMED：应用图标与内容更新都需要 effects/重组真正运行。
            FloatBallLifecycleOwner.createAndAttachTo(this, active = true)
            setContent { PillContent() }
        }
        created.setOnTouchListener { v, event -> handleTouch(v, event) }
        created.alpha = 0f
        created.scaleX = 1f
        created.scaleY = 1f
        // WRAP_CONTENT 的尺寸只有布局后才知道：原点、贴边、进场都等布局回调结算。
        // 关键顺序：第一次 layout 时 lp.y 仍为 0，此时 getLocationOnScreen().y 就是 LP 原点；
        // 先测原点再写 lp.y，否则「已写入的 y」会被当成原点算出负数，第二次开窗就被推到屏底。
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
        if (!wm.safeAddView(created, pillLayoutParams())) {
            Log.e(TAG, "clipboard ball addView failed (overlay permission or window rejected)")
            return false
        }
        view = created
        entering = true
        enterPending = true
        // 兜底：layout 回调不来时（极少数）用当前实测位置（lp.y 仍为 0）自行补位。
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
        }, ENTER_FALLBACK_MS)
        return true
    }

    /** 实测 LP 原点（状态栏下沿）：限定 0..[MaxOriginDp]，防任何竞态测出负数把窗口推飞。 */
    private fun screenOriginY(v: View): Int {
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        return location[1].coerceIn(0, DensityProvider.dp2px(MaxOriginDp))
    }

    /** 收掉在飞的进场动画（拖拽/按压需要即时响应时）；已完成则无操作。 */
    private fun cancelEnter(v: View) {
        if (!entering && !enterPending) return
        enterPending = false
        v.animate().cancel()
        v.alpha = 1f
        v.translationX = 0f
        v.scaleX = 1f
        v.scaleY = 1f
        entering = false
    }

    /** 量到实际宽度后的进场：从贴边侧滑入 + 淡入。 */
    private fun startEnter(v: View) {
        v.translationX = slideOffsetX()
        v.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(ENTER_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { if (view === v) entering = false }
            .start()
        // 兜底：动画被系统掐掉时也别把 entering 永久钉在 true（否则拖不动）。
        mainHandler.postDelayed({ if (view === v) entering = false }, ENTER_MS + 80L)
    }

    /** 收起：向贴边侧滑出 + 淡出后整窗摘除。 */
    fun hide(onHidden: (() -> Unit)? = null) {
        val v = view
        view = null
        entering = false
        enterPending = false
        iconGesture = false
        // 窗口即将拆掉：立刻清掉 pin/展开/待更新，避免 Service 在隐藏期间读到陈旧状态。
        pinned = false
        expanded = false
        pending = false
        linkInfo = null
        mainHandler.removeCallbacks(longPressRunnable)
        if (v == null) {
            onHidden?.invoke()
            return
        }
        // view 已置空，slideOffsetX() 会退到兜底宽度；出屏位移要用实际宽度。
        val width = v.width.takeIf { it > 0 } ?: fallbackWidthPx()
        // 包含侧边距，否则滑出后还留 8dp 在屏上。
        val distance = (width + sideMarginPx()).toFloat()
        val exitX = if (pinnedRight) distance else -distance
        v.animate().cancel()
        applyBallTouchFlags(v, wm, hidden = true)
        // 退出动画期间也要留着句柄：服务在此期间销毁时 detachNow 才能把窗摘掉。
        exitingView = v
        v.animate()
            .translationX(exitX)
            .alpha(0f)
            .setDuration(HIDE_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
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
        iconGesture = false
        pinned = false
        expanded = false
        pending = false
        linkInfo = null
        content = null
        mainHandler.removeCallbacks(longPressRunnable)
        v?.let(context::removeWindow)
        exiting?.let {
            it.animate().cancel()
            context.removeWindow(it)
        }
    }

    /**
     * 当前位置（归一化 0..1，y 含状态栏补偿），Service 落盘用。
     * x 只记侧别（0 = 左缘 / 1 = 右缘）：宽度封顶后贴边的 x 可能落在 0.5 以下，
     * 用 x 反推侧别会在“宽度恰好半屏”时把胶囊翻到另一侧。
     */
    fun normalizedPosition(): Pair<Float, Float> = Pair(
        if (pinnedRight) 1f else 0f,
        (posY / screenHeightPx).coerceIn(0f, 1f),
    )

    // ---- 定位 ----

    private fun resolvePosition(x: Float?, y: Float?) {
        pinnedRight = (x ?: 1f) >= 0.5f
        posX = pinnedX()
        // posY 是屏幕坐标的窗顶（writeBallPosition 会减 LP 原点），不要再提前减一次。
        posY = clampY((y ?: DefaultYRatio) * screenHeightPx)
        Log.i(
            TAG,
            "clip show x=$x y=$y screen=${screenWidthPx}x$screenHeightPx " +
                "origin=$lpOriginY pos=($posX,$posY)",
        )
    }

    /** 贴边后的窗顶 x（两侧留 [PillSideMarginDp]，不贴死屏幕边）。 */
    private fun pinnedX(): Float {
        val margin = sideMarginPx()
        return if (pinnedRight) {
            (screenWidthPx - currentWidthPx() - margin).toFloat()
        } else {
            margin.toFloat()
        }
    }

    private fun sideMarginPx(): Int = DensityProvider.dp2px(PillSideMarginDp)

    private fun onDragged(dx: Float, dy: Float) {
        mainHandler.removeCallbacks(longPressRunnable)
        val margin = sideMarginPx().toFloat()
        val maxX = (screenWidthPx - currentWidthPx() - sideMarginPx()).toFloat()
        posX = (posX + dx).coerceIn(margin, max(margin, maxX))
        posY = clampY(posY + dy)
        applyWindowBounds()
    }

    /** 抬手横向贴最近边（y 保持不动）。 */
    private fun snapToNearestEdge() {
        pinnedRight = posX + currentWidthPx() / 2f >= screenWidthPx / 2f
    }

    private fun maxY(): Float = max(lpOriginY, screenHeightPx - currentHeightPx()).toFloat()

    /** 纵向只夹在屏内（不额外限制位置）。 */
    private fun clampY(y: Float): Float {
        val lower = lpOriginY.toFloat()
        val upper = maxY().coerceAtLeast(lower)
        return y.coerceIn(lower, upper)
    }

    private fun applyWindowBounds() {
        val v = view ?: return
        // 原点未实测前不写 lp.y：写早了会把本次写入的 y 当成原点，第二次开窗就会被推到屏底。
        if (!originReady) return
        // 拖动中保持跟手的 x，不要按当前侧重钉；松手后 onDragEnded 再贴边。
        if (!userDragging) posX = pinnedX()
        posY = clampY(posY)
        writeBallPosition(v, wm, posX, posY, lpOriginY)
    }

    /** 当前窗宽高（WRAP_CONTENT 挂上后才有值；未量到时用最小胶囊宽兜底）。 */
    private fun currentWidthPx(): Int =
        view?.width?.takeIf { it > 0 } ?: fallbackWidthPx()

    private fun currentHeightPx(): Int =
        view?.height?.takeIf { it > 0 } ?: DensityProvider.dp2px(FallbackHeightDp)

    private fun fallbackWidthPx(): Int = DensityProvider.dp2px(FallbackWidthDp)

    /** 进出场横向位移基准：贴哪边就从哪边滑（出屏方向，含侧边距）。 */
    private fun slideOffsetX(): Float {
        val distance = currentWidthPx() + sideMarginPx()
        return if (pinnedRight) distance.toFloat() else -distance.toFloat()
    }

    // ---- Compose ----

    @Composable
    private fun PillContent() {
        SideGestureTheme {
            PillFace()
        }
    }

    @Composable
    private fun PillFace() {
        val accentColor = gestureButtonMarkColor(settings.color)
        // 深色底用白字、浅色底用黑字：accent 可由用户任意选色，不能写死 onSurface。
        val contentColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
        val maxPillWidth = with(LocalDensity.current) {
            val config = LocalConfiguration.current
            val ratio = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                PillWidthRatioLandscape
            } else {
                PillWidthRatioPortrait
            }
            (screenWidthPx * ratio).toDp()
        }
        val pill = if (expanded) {
            // 展开后是多行正文，大圆角会把两侧文字裁碎：换成普通卡片圆角。
            RoundedCornerShape(PillExpandedCornerDp.dp)
        } else {
            RoundedCornerShape(percent = 50)
        }
        Row(
            modifier = Modifier
                .widthIn(max = maxPillWidth)
                .clip(pill)
                .background(accentColor.copy(alpha = PillBackgroundAlpha))
                .border(1.dp, contentColor.copy(alpha = 0.22f), pill)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 单条链接命中分发规则时，粘贴图标换成目标应用图标（一眼看出会发往哪里）。
            val targetPackage = linkInfo?.targetPackage
            if (targetPackage != null) {
                LinkAppIcon(packageName = targetPackage, tint = contentColor.copy(alpha = 0.9f))
            } else {
                Icon(
                    modifier = Modifier.size(16.dp),
                    // pin 状态用图钉替掉粘贴图标，不加额外角标。
                    imageVector = if (pinned) Icons.Default.PushPin else Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.9f),
                )
            }
            when (val item = content) {
                // weight(fill=false)：长文本只占剩余宽度，右端的圆点/展开图标不会被挤到裁剪区外。
                is ClipboardContent.Text -> Text(
                    text = item.text,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor.copy(alpha = 0.95f),
                    maxLines = if (expanded) PillExpandedLines else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                is ClipboardContent.Image -> AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(PillImageDp.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                null -> Unit
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
            if (pending) {
                Box(
                    modifier = Modifier
                        .size(PendingDotDp.dp)
                        .background(contentColor.copy(alpha = 0.9f), CircleShape),
                )
            }
            if (content is ClipboardContent.Text) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.7f),
                )
            }
        }
    }

    /** 单条链接命中规则时的目标应用图标（与通知胶囊同一套 SearchIconCache 通路）。 */
    @Composable
    private fun LinkAppIcon(packageName: String, tint: Color) {
        val sizePx = DensityProvider.dp2px(LinkIconDp)
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
                modifier = Modifier.size(LinkIconDp.dp),
            )
        } else {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                tint = tint,
            )
        }
    }

    /** 尾部的链接角标：「N 条」或目标应用名，弱底色小胶囊。 */
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

    /** 胶囊窗 LP：TYPE_APPLICATION_OVERLAY、WRAP_CONTENT（宽度随内容）、LEFT|TOP 手工定位。 */
    private fun pillLayoutParams() = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = android.graphics.PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.LEFT or Gravity.TOP
        x = 0
        y = 0
    }

    private companion object {
        const val TAG = LOG_TAG

        const val ENTER_MS = 250L
        const val HIDE_MS = 200L
        const val PRESS_MS = 100L

        /** Compose 首帧布局兜底：超时仍未量到尺寸就按当前实测位置自行起进场。 */
        const val ENTER_FALLBACK_MS = 300L

        /** LP 原点合法性上限（dp）：状态栏/分屏偏移远小于此值，防测到负数/异常值。 */
        const val MaxOriginDp = 200f

        /** 长按 pin / 取消 pin 门槛（ms）。 */
        const val LONG_PRESS_MS = 500L

        /** 按压缩放（同胶囊）。 */
        const val PressScale = 0.95f

        /** 未量到尺寸前的兜底（dp）。 */
        const val FallbackWidthDp = 120f
        const val FallbackHeightDp = 38f

        /** 两侧留白（dp）：不贴死屏幕边，进场位移也会加上它。 */
        const val PillSideMarginDp = 8f

        /** 内容宽度上限（屏宽比例）：竖屏 50%，横屏 20%（横屏太宽很潼）。 */
        const val PillWidthRatioPortrait = 0.5f
        const val PillWidthRatioLandscape = 0.2f

        /** 胶囊底色透明度（保留主球 accent 色，但不要太透）。 */
        const val PillBackgroundAlpha = 0.85f

        /** 展开正文最多行数；展开后的卡片圆角。 */
        const val PillExpandedLines = 4
        const val PillExpandedCornerDp = 16f

        /** 图片缩略图边长 / 钉住期间的「有新内容」小圆点直径（dp）。 */
        const val PillImageDp = 28f
        const val PendingDotDp = 6f

        /** 链接目标应用图标边长（dp）。 */
        const val LinkIconDp = 18f

        /** 尾部展开图标的点击命中区宽度（dp）：图标 + 右侧内边距。 */
        const val PillIconHitWidthDp = 44f

        /** 未记忆时的默认落点（屏高比例，右缘）。 */
        const val DefaultYRatio = 0.5f
    }
}

/**
 * 胶囊要展示的链接信息：认出几条链接 + 单条命中规则时的目标应用。
 * 由 [FloatBallService] 按链接分发规则算好传入，窗口只负责画。
 */
internal data class ClipboardLinkInfo(
    val count: Int,
    val targetPackage: String? = null,
    val targetLabel: String? = null,
)
