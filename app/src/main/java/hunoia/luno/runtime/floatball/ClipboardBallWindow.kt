package hunoia.luno.runtime.floatball

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.LOG_TAG
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.theme.SideGestureTheme
import kotlin.math.max

/**
 * 剪贴板球：TYPE_APPLICATION_OVERLAY 小圆窗，剪贴板文本变化时在贴边记忆位弹出
 * 「ContentPaste 图标」圆球（配色随主球）。点击（[onTapped]）由 Service 弹系统分享，
 * 长按（[onLongPressed]）关闭，按住即拖、抬手横向贴最近边，[onDragReleased] 后由
 * Service 读 [normalizedPosition] 落盘；自动收起计时归 Service。
 * 低频瞬态，走整拆整建（show 挂窗、hide 滑出后摘窗）；可见期间复制新内容只重算位置，
 * 图标球面无内容可换。动效一律 View 层（服务进程 ComposeView 重组不上屏，
 * 见 FloatBallWindow.applyVisual 注释）。
 */
internal class ClipboardBallWindow(private val context: android.content.Context) {

    private val wm: WindowManager get() = context.windowManager()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: View? = null

    private var settings: FloatBallSettings = FloatBallSettings()

    private val ballPx: Int get() = DensityProvider.dp2px(FloatBallDefaults.ClipboardBallSizeDp)

    /** LP 坐标系原点在屏幕上的 y（状态栏下沿），attach 后实测（与球窗同套路）。 */
    private var lpOriginY = 0

    private var posX = 0f
    private var posY = 0f

    /** 进场动画在飞：期间按压/拖动/长按不介入（同一 ViewPropertyAnimator 会互相打断）。 */
    private var entering = false

    /** 点击球（未超 touchSlop 抬手；长按已触发则吞掉后续 tap）。 */
    var onTapped: (() -> Unit)? = null

    /** 长按关闭（按下 [LONG_PRESS_MS] 未拖未抬）。 */
    var onLongPressed: (() -> Unit)? = null

    /** 拖动抬手：贴边已在本窗内结算，Service 读位置落盘。 */
    var onDragReleased: (() -> Unit)? = null

    // 长按检测：仲裁器只分「拖/点」，长按在窗口层补。
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        longPressFired = true
        view?.let { applyBallTouchFlags(it, wm, hidden = true) }
        onLongPressed?.invoke()
    }

    private val arbiter = FloatBallTouchArbiter(
        onSlideDragged = { dx, dy ->
            if (!entering) onDragged(dx, dy)
        },
        onTapped = { if (!longPressFired) onTapped?.invoke() },
        onPressChanged = { pressed ->
            if (entering) return@FloatBallTouchArbiter
            view?.let { v ->
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

    /**
     * 弹出：不可见则挂窗，从贴边侧滑入 + 淡入；已可见（新复制覆盖）只重算位置。
     * [x]/[y] 为记忆位（归一化），null 落默认（右缘、屏高 [DefaultYRatio]）。
     */
    fun show(x: Float?, y: Float?) {
        val existing = view
        if (existing != null) {
            resolvePosition(x, y)
            applyWindowBounds()
            return
        }
        val created = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            FloatBallLifecycleOwner.createAndAttachTo(this)
            setContent { BallContent() }
        }
        created.setOnTouchListener { _, event -> arbiter.onTouch(event) }
        if (!wm.safeAddView(created, ballLayoutParams(ballPx))) {
            Log.e(TAG, "clipboard ball addView failed (overlay permission or window rejected)")
            return
        }
        view = created
        resolvePosition(x, y)
        applyWindowBounds()
        postMeasureLpOriginY({ view }, { lpOriginY }) {
            lpOriginY = it
            applyWindowBounds()
        }
        // 进场：从贴边侧滑入 + 淡入。
        entering = true
        created.scaleX = 1f
        created.scaleY = 1f
        created.translationX = slideOffsetX()
        created.alpha = 0f
        created.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(ENTER_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { entering = false }
            .start()
    }

    /** 收起：向贴边侧滑出 + 淡出后整窗摘除。 */
    fun hide(onHidden: (() -> Unit)? = null) {
        val v = view
        view = null
        entering = false
        mainHandler.removeCallbacks(longPressRunnable)
        if (v == null) {
            onHidden?.invoke()
            return
        }
        v.animate().cancel()
        applyBallTouchFlags(v, wm, hidden = true)
        v.animate()
            .translationX(slideOffsetX())
            .alpha(0f)
            .setDuration(HIDE_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                context.removeWindow(v)
                onHidden?.invoke()
            }
            .start()
    }

    /** 不播动画直接摘窗：服务销毁等即时路径。 */
    fun detachNow() {
        val v = view
        view = null
        entering = false
        mainHandler.removeCallbacks(longPressRunnable)
        v?.let(context::removeWindow)
    }

    /** 当前位置（归一化 0..1，y 含状态栏补偿），Service 落盘用。 */
    fun normalizedPosition(): Pair<Float, Float> = Pair(
        (posX / screenWidthPx).coerceIn(0f, 1f),
        ((posY + lpOriginY) / screenHeightPx).coerceIn(0f, 1f),
    )

    // ---- 定位 ----

    private fun resolvePosition(x: Float?, y: Float?) {
        posX = if (x != null) {
            (x * screenWidthPx).coerceIn(0f, (screenWidthPx - ballPx).toFloat())
        } else {
            (screenWidthPx - ballPx).toFloat()
        }
        posY = ((y ?: DefaultYRatio) * screenHeightPx - lpOriginY)
            .coerceIn(lpOriginY.toFloat(), maxY())
    }

    private fun onDragged(dx: Float, dy: Float) {
        mainHandler.removeCallbacks(longPressRunnable)
        posX = (posX + dx).coerceIn(0f, (screenWidthPx - ballPx).toFloat())
        posY = (posY + dy).coerceIn(lpOriginY.toFloat(), maxY())
        applyWindowBounds()
    }

    /** 抬手横向贴最近边（y 保持不动）。 */
    private fun snapToNearestEdge() {
        posX = if (posX + ballPx / 2f < screenWidthPx / 2f) {
            0f
        } else {
            (screenWidthPx - ballPx).toFloat()
        }
    }

    private fun maxY(): Float = max(lpOriginY, screenHeightPx - ballPx).toFloat()

    private fun applyWindowBounds() {
        val v = view ?: return
        writeBallPosition(v, wm, posX, posY, lpOriginY)
    }

    /** 进出场横向位移基准：贴哪边就从哪边滑（出屏方向）。 */
    private fun slideOffsetX(): Float =
        if (posX + ballPx / 2f < screenWidthPx / 2f) -ballPx.toFloat() else ballPx.toFloat()

    // ---- Compose ----

    @Composable
    private fun BallContent() {
        SideGestureTheme {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BallFace()
            }
        }
    }

    @Composable
    private fun BallFace() {
        // 配色在组合期读取：普通字段不触发重组，球为瞬态，显示期间改色不生效可接受。
        val accentColor = gestureButtonMarkColor(settings.color)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(accentColor.copy(alpha = 0.16f), CircleShape)
                .border(1.5.dp, accentColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(IconDp.dp),
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }

    private companion object {
        const val TAG = LOG_TAG

        const val ENTER_MS = 250L
        const val HIDE_MS = 200L
        const val PRESS_MS = 100L

        /** 长按关闭门槛（ms）。 */
        const val LONG_PRESS_MS = 500L

        /** 按压缩放（同胶囊）。 */
        const val PressScale = 0.95f

        /** 图标直径（dp）。 */
        const val IconDp = 20f

        /** 未记忆时的默认落点（屏高比例，右缘）。 */
        const val DefaultYRatio = 0.7f
    }
}
