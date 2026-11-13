package hunoia.luno.runtime.floatball

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import hunoia.luno.bridge.ui.hapticLongPress
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.config.model.ClipFloatAlphaRange
import hunoia.luno.config.model.ClipFloatHeightRange
import hunoia.luno.config.model.ClipFloatPlacement
import hunoia.luno.config.model.ClipFloatSettings
import hunoia.luno.config.model.ClipFloatWidthRange
import hunoia.luno.config.model.ClipKind
import hunoia.luno.floatball.R
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.runtime.surface.SurfaceHandle
import hunoia.luno.runtime.surface.SurfaceKind
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.pressClickable
import hunoia.luno.ui.theme.MotionFamily
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.theme.containerShape
import hunoia.luno.ui.theme.motionMillis
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** 悬浮窗里展示的一条剪贴板内容；图片文件已解析成本地副本。 */
internal data class ClipFloatContent(
    val id: String,
    val kind: ClipKind,
    val text: String,
    val imagePath: String?,
    val mime: String?,
    val imageFile: File?,
)

/**
 * 剪贴板悬浮窗：用户显式钉到屏幕的一条文本 / 图片。
 *
 * 交互口径：
 * - 内容区拖动 = 移动窗口；四角圆点拖动 = 调整宽高；双指捏合 = 等比缩放；
 * - 长按不动 = 展开动作菜单；长按后拖动 = 拖出投递（文本插入 / 图片粘贴），松手关窗；
 * - 透明度只作用于容器底色，正文 / 图标保持不透明。
 *
 * 窗口是临时 Surface：息屏 / 锁屏由宿主关闭，解锁不自动恢复；位置 / 尺寸只影响下一次打开。
 */
internal class ClipFloatWindow(
    private val context: Context,
    private val host: Host,
) : SurfaceHandle {

    interface Host {
        fun onInsert(content: ClipFloatContent)

        fun onSend(content: ClipFloatContent)

        fun onSendAt(content: ClipFloatContent, x: Int, y: Int)

        fun onCopy(content: ClipFloatContent)

        fun onClose()

        fun onSettingsChanged(settings: ClipFloatSettings)
    }

    override val surfaceId: String = SURFACE_ID
    override val surfaceKind: SurfaceKind = SurfaceKind.TRANSIENT

    private val wm: WindowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private var root: FrameLayout? = null
    private val contentState = mutableStateOf<ClipFloatContent?>(null)
    private var settings: ClipFloatSettings = ClipFloatSettings()
    private var attached = false

    fun isShowing(): Boolean = attached && contentState.value != null

    @SuppressLint("RtlHardcoded")
    private fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        width = widthPx()
        height = heightPx()
        gravity = Gravity.LEFT or Gravity.TOP
        alpha = 1f
    }

    fun show(newContent: ClipFloatContent, newSettings: ClipFloatSettings) {
        settings = newSettings.coerced()
        if (attached) {
            contentState.value = newContent
            val v = root ?: return
            val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
            lp.width = widthPx()
            lp.height = heightPx()
            wm.safeUpdateViewLayout(v, lp)
            v.post { applyGeometry(persist = false) }
            return
        }
        contentState.value = newContent
        val compose = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // 服务进程没有 Activity：ComposeView 在 attach 时向上找这三个 owner，缺一即崩。
            FloatBallLifecycleOwner.createAndAttachTo(this, active = true)
            setContent {
                SideGestureTheme {
                    val content = contentState.value ?: return@SideGestureTheme
                    ClipFloatCard(
                        content = content,
                        initialAlpha = settings.alpha,
                        onMoveStart = ::beginMove,
                        onMoveTo = ::moveTo,
                        onMoveEnd = { finishMove() },
                        onResizeStart = ::beginResize,
                        onResizeTo = ::resizeTo,
                        onResizeEnd = { finishResize() },
                        onScale = ::applyScale,
                        onScaleEnd = { finishResize() },
                        onSendDrop = { screen ->
                            close()
                            host.onSendAt(content, screen.x.roundToInt(), screen.y.roundToInt())
                        },
                        onInsert = { host.onInsert(content) },
                        onSend = { host.onSend(content) },
                        onCopy = { host.onCopy(content) },
                        onAlpha = { alpha ->
                            settings = settings.copy(alpha = alpha).coerced()
                        },
                        onAlphaFinished = { host.onSettingsChanged(settings) },
                        onClose = { close() },
                        onMenuChanged = {
                            root?.post { clampPosition() }
                        },
                    )
                }
            }
        }
        // 与悬浮球 / 胶囊同构：FrameLayout 做窗口根、owner 同时挂根与 Compose。
        val created = FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            FloatBallLifecycleOwner.createAndAttachTo(this, active = true)
        }
        root = created
        if (!wm.safeAddView(created, layoutParams())) {
            root = null
            contentState.value = null
            return
        }
        attached = true
        created.post { applyGeometry(persist = false) }
    }

    fun onConfigurationChanged() {
        if (attached) applyGeometry(persist = false)
    }

    fun close() {
        if (!attached) {
            contentState.value = null
            return
        }
        attached = false
        contentState.value = null
        root?.let { context.removeWindow(it) }
        root = null
        host.onClose()
    }

    // ---- SurfaceHandle ----

    override fun softHide() {
        if (!attached) return
        setTouchable(false)
        root?.alpha = 0f
    }

    override fun restore() {
        if (!attached) return
        root?.alpha = 1f
        setTouchable(true)
    }

    private fun setTouchable(enabled: Boolean) {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val notTouchable = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        if (enabled != notTouchable) return
        lp.flags = if (enabled) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        wm.safeUpdateViewLayout(v, lp)
    }

    override fun detach() {
        if (attached) close()
    }

    // ---- 几何 ----

    private fun screenBounds(): Pair<Int, Int> {
        val bounds = wm.currentWindowMetrics.bounds
        return bounds.width() to bounds.height()
    }

    private fun currentViewSize(): Pair<Int, Int> =
        (root?.width ?: 0) to (root?.height ?: 0)

    private fun dp2px(dp: Float): Int = (dp * density).roundToInt()

    private fun widthPx(): Int = dp2px(settings.widthDp)

    private fun heightPx(): Int = dp2px(settings.heightDp)

    /** 尺寸量出来后再落坐标：归一化坐标按「可用移动范围」换算，跨分辨率不越界。 */
    private fun applyGeometry(persist: Boolean) {
        val v = root ?: return
        val (screenW, screenH) = screenBounds()
        if (v.width <= 0 || v.height <= 0) {
            if (v.isAttachedToWindow) v.post { applyGeometry(persist) }
            return
        }
        val placement = settings.placement(isLandscape())
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.width = widthPx()
        lp.height = heightPx()
        val maxX = max(0, screenW - lp.width)
        val maxY = max(0, screenH - lp.height)
        lp.x = (placement.x * maxX).roundToInt().coerceIn(0, maxX)
        lp.y = (placement.y * maxY).roundToInt().coerceIn(0, maxY)
        wm.safeUpdateViewLayout(v, lp)
        if (persist) host.onSettingsChanged(settings)
    }

    // ---- 拖动（绝对定位，避免窗口跟手后的局部坐标反馈抖动） ----

    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartScreen: Offset? = null
    private var dragScreenW = 0
    private var dragScreenH = 0

    private fun beginMove(screen: Offset) {
        val lp = root?.layoutParams as? WindowManager.LayoutParams ?: return
        dragStartX = lp.x
        dragStartY = lp.y
        dragStartScreen = screen
        // 每帧查 currentWindowMetrics 会拖慢跟手；起点缓存一次屏宽高即可。
        val (w, h) = screenBounds()
        dragScreenW = w
        dragScreenH = h
    }

    private fun moveTo(screen: Offset) {
        val start = dragStartScreen ?: return
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val dx = (screen.x - start.x).roundToInt()
        val dy = (screen.y - start.y).roundToInt()
        lp.x = (dragStartX + dx).coerceIn(0, max(0, dragScreenW - lp.width))
        lp.y = (dragStartY + dy).coerceIn(0, max(0, dragScreenH - lp.height))
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun finishMove() {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val maxX = max(0, screenW - lp.width)
        val maxY = max(0, screenH - lp.height)
        val threshold = (SNAP_DP * density).roundToInt()
        val targetX = when {
            lp.x <= threshold -> 0
            maxX - lp.x <= threshold -> maxX
            else -> lp.x
        }
        val targetY = when {
            lp.y <= threshold -> 0
            maxY - lp.y <= threshold -> maxY
            else -> lp.y
        }
        if (targetX != lp.x || targetY != lp.y) {
            animateTo(v, lp.x, lp.y, targetX, targetY)
        }
        settings = settings.withPlacement(
            landscape = isLandscape(),
            placement = normalize(targetX, targetY, maxX, maxY),
        )
        host.onSettingsChanged(settings)
    }

    /** 菜单展开会改内容尺寸：夹一次当前坐标，避免长出屏幕。 */
    private fun clampPosition() {
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val maxX = max(0, screenW - lp.width)
        val maxY = max(0, screenH - lp.height)
        val x = lp.x.coerceIn(0, maxX)
        val y = lp.y.coerceIn(0, maxY)
        if (x == lp.x && y == lp.y) return
        lp.x = x
        lp.y = y
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun animateTo(v: View, fromX: Int, fromY: Int, toX: Int, toY: Int) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = motionMillis(MotionFamily.FloatingSnap).toLong()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val lp = v.layoutParams as? WindowManager.LayoutParams ?: return@addUpdateListener
                lp.x = (fromX + (toX - fromX) * f).roundToInt()
                lp.y = (fromY + (toY - fromY) * f).roundToInt()
                wm.safeUpdateViewLayout(v, lp)
            }
            start()
        }
    }

    // ---- 四角缩放 ----

    private var resizeCorner: Int = CORNER_BR;
    private var resizeStartW = 0;
    private var resizeStartH = 0;
    private var resizeStartX = 0;
    private var resizeStartY = 0;
    private var resizeStartScreen: Offset? = null;

    private fun beginResize(corner: Int, screen: Offset) {
        val lp = root?.layoutParams as? WindowManager.LayoutParams ?: return
        resizeCorner = corner
        resizeStartW = lp.width
        resizeStartH = lp.height
        resizeStartX = lp.x
        resizeStartY = lp.y
        resizeStartScreen = screen
    }

    private fun resizeTo(corner: Int, screen: Offset) {
        val start = resizeStartScreen ?: return
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val dx = (screen.x - start.x).roundToInt()
        val dy = (screen.y - start.y).roundToInt()
        val (minW, minH) = minSizePx()
        val maxW = screenW.coerceAtLeast(minW)
        val maxH = screenH.coerceAtLeast(minH)

        var w = resizeStartW
        var h = resizeStartH
        var x = resizeStartX
        var y = resizeStartY
        when (corner) {
            CORNER_TL -> { w -= dx; h -= dy; x += dx; y += dy }
            CORNER_TR -> { w += dx; h -= dy; y += dy }
            CORNER_BL -> { w -= dx; h += dy; x += dx }
            else -> { w += dx; h += dy }
        }
        w = w.coerceIn(minW, maxW)
        h = h.coerceIn(minH, maxH)
        // 左上 / 左侧拖拽时保持对角固定：尺寸被夹取后，位置跟着修正。
        if (corner == CORNER_TL || corner == CORNER_BL) {
            x = resizeStartX + (resizeStartW - w)
        }
        if (corner == CORNER_TL || corner == CORNER_TR) {
            y = resizeStartY + (resizeStartH - h)
        }
        lp.x = x.coerceIn(0, max(0, screenW - w))
        lp.y = y.coerceIn(0, max(0, screenH - h))
        lp.width = w
        lp.height = h
        settings = settings.copy(
            widthDp = px2dp(w),
            heightDp = px2dp(h),
        )
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun finishResize() {
        settings = settings.coerced()
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (screenW, screenH) = screenBounds()
        val maxX = max(0, screenW - lp.width)
        val maxY = max(0, screenH - lp.height)
        settings = settings.withPlacement(
            landscape = isLandscape(),
            placement = normalize(lp.x.coerceIn(0, maxX), lp.y.coerceIn(0, maxY), maxX, maxY),
        )
        host.onSettingsChanged(settings)
    }

    private fun applyScale(zoom: Float) {
        val nextW = (settings.widthDp * zoom).coerceIn(
            ClipFloatWidthRange.start,
            ClipFloatWidthRange.endInclusive,
        )
        val nextH = (settings.heightDp * zoom).coerceIn(
            ClipFloatHeightRange.start,
            ClipFloatHeightRange.endInclusive,
        )
        if (abs(nextW - settings.widthDp) < 0.01f && abs(nextH - settings.heightDp) < 0.01f) return
        settings = settings.copy(widthDp = nextW, heightDp = nextH)
        val v = root ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.width = widthPx()
        lp.height = heightPx()
        val (screenW, screenH) = screenBounds()
        lp.x = lp.x.coerceIn(0, max(0, screenW - lp.width))
        lp.y = lp.y.coerceIn(0, max(0, screenH - lp.height))
        wm.safeUpdateViewLayout(v, lp)
    }

    private fun minSizePx(): Pair<Int, Int> =
        dp2px(ClipFloatWidthRange.start) to dp2px(ClipFloatHeightRange.start)

    private fun px2dp(px: Int): Float = px / density

    private fun normalize(x: Int, y: Int, maxX: Int, maxY: Int): ClipFloatPlacement =
        ClipFloatPlacement(
            x = if (maxX <= 0) 0f else (x.toFloat() / maxX).coerceIn(0f, 1f),
            y = if (maxY <= 0) 0f else (y.toFloat() / maxY).coerceIn(0f, 1f),
        )

    private fun isLandscape(): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private companion object {
        const val SURFACE_ID = "clip-float"
        const val SNAP_DP = 24f
        const val CORNER_TL = 0
        const val CORNER_TR = 1
        const val CORNER_BL = 2
        const val CORNER_BR = 3
    }
}

/**
 * 卡片内容 + 手势。
 *
 * 拖动 / 缩放 / 拖出走 `pointerInteropFilter` 的原始 MotionEvent（与悬浮球同一套
 * 仲裁思路），不用 Compose 局部坐标逐帧累加；菜单行 / 滑杆仍是普通 Compose 点击。
 */
@Composable
private fun ClipFloatCard(
    content: ClipFloatContent,
    initialAlpha: Float,
    onMoveStart: (Offset) -> Unit,
    onMoveTo: (Offset) -> Unit,
    onMoveEnd: () -> Unit,
    onResizeStart: (Int, Offset) -> Unit,
    onResizeTo: (Int, Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onScale: (Float) -> Unit,
    onScaleEnd: () -> Unit,
    onSendDrop: (Offset) -> Unit,
    onInsert: () -> Unit,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onAlpha: (Float) -> Unit,
    onAlphaFinished: () -> Unit,
    onClose: () -> Unit,
    onMenuChanged: (Boolean) -> Unit,
) {
    var menuOpen by remember(content.id) { mutableStateOf(false) }
    var alpha by remember(content.id) { mutableStateOf(initialAlpha) }
    val view = LocalView.current
    val touchArbiter = remember(content.id) {
        ClipFloatTouchArbiter(
            view = view,
            onLongPress = {
                menuOpen = true
                onMenuChanged(true)
            },
            onMoveStart = onMoveStart,
            onMoveTo = onMoveTo,
            onMoveEnd = onMoveEnd,
            onSendDrop = onSendDrop,
            onScale = onScale,
            onScaleEnd = onScaleEnd,
        )
    }

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = containerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (menuOpen) {
                    // 菜单占满整卡：两列排布让 5 个动作 + 透明度在 180dp 内完整可见。
                    Row(Modifier.fillMaxWidth()) {
                        MenuRow(
                            text = stringResource(R.string.clip_float_insert),
                            onClick = onInsert,
                            modifier = Modifier.weight(1f),
                        )
                        MenuRow(
                            text = stringResource(R.string.clip_float_send),
                            onClick = onSend,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.fillMaxWidth()) {
                        MenuRow(
                            text = stringResource(R.string.clip_float_copy),
                            onClick = onCopy,
                            modifier = Modifier.weight(1f),
                        )
                        MenuRow(
                            text = stringResource(R.string.clip_float_close),
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LunoTextSlider(
                        value = alpha,
                        onValueChange = {
                            alpha = it
                            onAlpha(it)
                        },
                        onValueChangeFinished = onAlphaFinished,
                        text = stringResource(R.string.clip_float_alpha),
                        valueRange = ClipFloatAlphaRange,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInteropFilter { touchArbiter.onTouch(it) },
                    ) {
                        when (content.kind) {
                            ClipKind.TEXT -> Text(
                                text = content.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 12,
                                overflow = TextOverflow.Ellipsis,
                            )
                            ClipKind.IMAGE -> AsyncImage(
                                model = content.imageFile,
                                contentDescription = stringResource(R.string.clip_float_image),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        // 四角缩放点：菜单展开时隐藏，避免盖住菜单行；平时盖在内容之上，命中优先于移窗。
        if (!menuOpen) {
            ResizeHandle(Alignment.TopStart, CORNER_TL, view, onResizeStart, onResizeTo, onResizeEnd)
            ResizeHandle(Alignment.TopEnd, CORNER_TR, view, onResizeStart, onResizeTo, onResizeEnd)
            ResizeHandle(Alignment.BottomStart, CORNER_BL, view, onResizeStart, onResizeTo, onResizeEnd)
            ResizeHandle(Alignment.BottomEnd, CORNER_BR, view, onResizeStart, onResizeTo, onResizeEnd)
        }
    }
}

@Composable
private fun BoxScope.ResizeHandle(
    alignment: Alignment,
    corner: Int,
    view: View,
    onStart: (Int, Offset) -> Unit,
    onResize: (Int, Offset) -> Unit,
    onEnd: () -> Unit,
) {
    var startRaw by remember { mutableStateOf<Offset?>(null) }
    Box(
        modifier = Modifier
            .align(alignment)
            .size(48.dp)
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRaw = Offset(event.rawX, event.rawY)
                        startRaw?.let { onStart(corner, it) }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        startRaw?.let { onResize(corner, Offset(event.rawX, event.rawY)) }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (startRaw != null) onEnd()
                        startRaw = null
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun MenuRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressClickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

private const val CORNER_TL = 0
private const val CORNER_TR = 1
private const val CORNER_BL = 2
private const val CORNER_BR = 3

/**
 * 悬浮窗拖动仲裁：只用原始 MotionEvent（与悬浮球同一套思路）。
 *
 * - 单指超过 touchSlop → 移窗；松手结算；
 * - 未超 slop 按住 500ms → 长按菜单，之后的移动进入「拖出投递」；
 * - 双指 → 等比缩放；
 * - 长按后移动超过 8dp → 松手把屏幕坐标交给宿主插入 / 粘贴。
 */
private class ClipFloatTouchArbiter(
    private val view: View,
    private val onLongPress: () -> Unit,
    private val onMoveStart: (Offset) -> Unit,
    private val onMoveTo: (Offset) -> Unit,
    private val onMoveEnd: () -> Unit,
    private val onSendDrop: (Offset) -> Unit,
    private val onScale: (Float) -> Unit,
    private val onScaleEnd: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
    private val sendSlop = 8f * view.resources.displayMetrics.density

    private var startRaw = Offset.Zero
    private var currentRaw = Offset.Zero
    private var lastSpan = 0f
    private var dragging = false
    private var longPressed = false
    private var sending = false
    private var pinching = false

    private val longPressRunnable = Runnable {
        if (!dragging && !pinching) {
            longPressed = true
            view.hapticLongPress()
            onLongPress()
        }
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRaw = Offset(event.rawX, event.rawY)
                currentRaw = startRaw
                dragging = false
                longPressed = false
                sending = false
                pinching = false
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    handler.removeCallbacks(longPressRunnable)
                    if (dragging) {
                        dragging = false
                        onMoveEnd()
                    }
                    pinching = true
                    longPressed = false
                    sending = false
                    lastSpan = span(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pinching) {
                    val currentSpan = span(event)
                    if (lastSpan > 0f && currentSpan > 0f) {
                        onScale(currentSpan / lastSpan)
                    }
                    lastSpan = currentSpan
                    return true
                }
                currentRaw = Offset(event.rawX, event.rawY)
                if (longPressed) {
                    if (!sending && (currentRaw - startRaw).getDistance() > sendSlop) {
                        sending = true
                    }
                    return true
                }
                if (dragging) {
                    onMoveTo(currentRaw)
                    return true
                }
                if ((currentRaw - startRaw).getDistance() > slop) {
                    dragging = true
                    handler.removeCallbacks(longPressRunnable)
                    // 以越过阈值的当前点为起点，窗口不会补跳 slop 的位移。
                    onMoveStart(currentRaw)
                    onMoveTo(currentRaw)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (pinching) {
                    onScaleEnd()
                    pinching = false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (pinching) {
                    onScaleEnd()
                    pinching = false
                } else if (dragging) {
                    onMoveEnd()
                } else if (sending) {
                    onSendDrop(currentRaw)
                }
                reset()
            }
        }
        return true
    }

    private fun reset() {
        startRaw = Offset.Zero
        currentRaw = Offset.Zero
        lastSpan = 0f
        dragging = false
        longPressed = false
        sending = false
        pinching = false
    }

    private fun span(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }
}

private const val LONG_PRESS_MS = 500L
