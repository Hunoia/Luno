package hunoia.luno.runtime.floatball

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.removeWindow
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.runtime.overlay.safeAddView
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import hunoia.luno.ui.component.gestureButtonMarkColor
import hunoia.luno.ui.theme.SideGestureTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 主球的对侧镜像分身：**钉在对侧屏幕边缘的半圆描边**（alpha
 * [FloatBallDefaults.MirrorDimAlpha]），独立 2038 小窗。
 *
 * 半圆的实现是**内容出窗**：窗口贴边完整在屏内（部分 ROM 会把越界 LP 夹回屏内，
 * LP 出屏方案不可靠），圆绘制向屏幕边缘偏移半个球径，超出 surface 的部分被裁掉，
 * 可见的就是半圆。窗口命中区是完整方形，靠内的半格是「空的」但可点 —— 等效于
 * 给半圆一圈宽松的命中余量。
 *
 * 交互：
 * - **按下**：立即交换位置（不触发动作）—— [onPressed]；若手指继续移动，
 *   位移增量经 [onDragDeltas] 接力给主球（拖刚换过来的那颗球），抬手由
 *   [onDragReleased] 收尾（贴边结算 + 落盘）；
 * - **点击**（未超 touchSlop 抬手）：补触发 [onTapped]（Service 派发动作）；
 *
 * y 跟随主球（含键盘让位，由 Service 同步 [updateImeLift]）。
 */
class FloatBallMirrorWindow(
    private val context: android.content.Context,
) {
    private val wm: WindowManager get() = context.windowManager()

    private var view: View? = null
    private var ballPx = 0
    // Compose 态：MirrorFace 的描边偏移/颜色依赖重组，普通字段不会触发重绘
    // （真机「换位后半圆方向不对」的根因）。
    private var settings: FloatBallSettings = FloatBallSettings()

    private var posX = 0f
    private var posY = 0f

    /** LP 坐标系原点在屏幕上的 y（状态栏下沿），attach 后实测。 */
    private var lpOriginY = 0

    private var lockHidden = false

    /** 键盘让位量（px），由 Service 从主球同步。 */
    private var imeLiftPx = 0f

    // ---- 回调 ----

    /** 按下分身：立即交换位置（不触发动作）。 */
    var onPressed: (() -> Unit)? = null

    /** 点击（未超 touchSlop 抬手）：补触发动作。 */
    var onTapped: (() -> Unit)? = null

    /** 按下后继续拖动：每帧位移增量，接力给主球。 */
    var onDragDeltas: ((dx: Float, dy: Float) -> Unit)? = null

    /** 接力拖抬手。 */
    var onDragReleased: (() -> Unit)? = null

    private val arbiter = FloatBallTouchArbiter(
        onSlideDragged = { dx, dy -> onDragDeltas?.invoke(dx, dy) },
        onTapped = { onTapped?.invoke() },
        onPressChanged = { pressed -> if (pressed) onPressed?.invoke() },
        onDragEnded = { onDragReleased?.invoke() },
    )

    private val screenWidthPx: Int get() = DensityProvider.screenWidthPx
    private val screenHeightPx: Int get() = DensityProvider.screenHeightPx

    // ---- 生命周期 ----

    fun updateSettings(next: FloatBallSettings) {
        val sizeChanged = next.sizeDp != settings.sizeDp
        settings = next
        if (sizeChanged) {
            ballPx = DensityProvider.dp2px(next.sizeDp)
            view?.let { lpSize(it) }
        }
        snapToMirror()
    }

    fun attach(): Boolean {
        if (view != null) return true
        ballPx = DensityProvider.dp2px(settings.sizeDp)
        val created = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            FloatBallLifecycleOwner.createAndAttachTo(this)
            setContent { MirrorContent() }
        }
        created.setOnTouchListener { _, event ->
            arbiter.onTouch(event)
        }
        val lp = createLayoutParams()
        if (!wm.safeAddView(created, lp)) {
            Log.e(TAG, "mirror ball addView failed (overlay permission or window rejected)")
            return false
        }
        view = created
        // 常态 alpha 必须在 attach 时就位：applyVisual 只在按压/静置事件跑，
        // 不预置的话分身会以全不透明示人（真机反馈「比主球还实」的根因）。
        created.alpha = FloatBallDefaults.MirrorDimAlpha
        snapToMirror()
        created.post {
            val attached = view ?: return@post
            val location = IntArray(2)
            attached.getLocationOnScreen(location)
            val measured = location[1] - ((attached.layoutParams as? WindowManager.LayoutParams)?.y ?: 0)
            if (measured != lpOriginY) {
                lpOriginY = measured
                applyWindowBounds()
            }
        }
        return true
    }

    fun detach() {
        view?.let(context::removeWindow)
        view = null
    }

    /** 锁屏 / 息屏：与主球同进退。 */
    fun setHidden(hidden: Boolean) {
        lockHidden = hidden
        applyHiddenFlags()
    }

    private fun applyHiddenFlags() {
        val current = view ?: return
        val lp = current.layoutParams as? WindowManager.LayoutParams ?: return
        if (lockHidden) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            lp.alpha = 0f
        } else {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            lp.alpha = 1f
        }
        wm.safeUpdateViewLayout(current, lp)
    }

    // ---- 定位 ----

    /** 当前分身窗顶边屏幕坐标（px）。Service 换位时读取。 */
    fun currentPosition(): Pair<Float, Float> = posX to posY

    /**
     * 键盘让位同步（Service 从主球读后传入）。
     */
    fun updateImeLift(liftPx: Float) {
        if (liftPx == imeLiftPx) return
        imeLiftPx = liftPx
        posY = clampY(settings.y * screenHeightPx - imeLiftPx)
        applyWindowBounds()
    }

    /** 按当前设置重算镜像位置（主球换位/搬球落定后由 Service 调）。 */
    fun snapToMirror() {
        posX = pinnedX()
        posY = clampY(settings.y * screenHeightPx - imeLiftPx)
        applyWindowBounds()
        // 半圆偏移必须走 View 层：服务进程托管的 ComposeView 重组不上屏
        // （见 FloatBallWindow.applyVisual 注释），Compose 里画偏移会停在旧帧。
        view?.translationX = contentShiftPx()
    }

    /** 主球是否在屏左半（按球心判）。 */
    private fun mainOnLeft(): Boolean =
        settings.x * screenWidthPx + ballPx / 2f < screenWidthPx / 2f

    /** 主球在左半屏 → 分身贴右缘，反之贴左缘。 */
    private fun mirrorOnRight(): Boolean = mainOnLeft()

    /**
     * 半圆姿态顶边 x：窗口贴对侧边缘**完整在屏内**（top-left = maxX 或 0），
     * 半圆靠内容出窗裁切。
     */
    private fun pinnedX(): Float =
        if (mirrorOnRight()) max(0, screenWidthPx - ballPx).toFloat() else 0f

    private fun clampY(y: Float): Float {
        val maxY = max(lpOriginY, screenHeightPx - ballPx)
        return y.coerceIn(lpOriginY.toFloat(), maxY.toFloat())
    }

    // ---- 窗口定位（与 FloatBallWindow 同套路：屏幕坐标 − LP 原点） ----

    private fun applyWindowBounds() {
        val current = view ?: return
        val lp = current.layoutParams as? WindowManager.LayoutParams ?: return
        val maxY = max(lpOriginY, screenHeightPx - ballPx)
        posY = posY.coerceIn(lpOriginY.toFloat(), maxY.toFloat())
        val nextX = posX.roundToInt()
        val nextY = (posY - lpOriginY).roundToInt().coerceAtLeast(0)
        if (lp.x == nextX && lp.y == nextY) return
        lp.x = nextX
        lp.y = nextY
        wm.safeUpdateViewLayout(current, lp)
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

    @SuppressLint("RtlHardcoded")
    private fun createLayoutParams() = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        width = ballPx
        height = ballPx
        gravity = Gravity.LEFT or Gravity.TOP
        x = 0
        y = 0
    }

    // ---- 外观 ----

    /** 描边圆向屏幕边缘偏移半个球径：出窗部分被 surface 裁掉，可见即半圆。 */
    private fun contentShiftPx(): Float =
        if (mirrorOnRight()) ballPx / 2f else -ballPx / 2f

    /**
     * 交换的淡入淡出：淡出 → 用 [freshSettings] 重算到新对侧 → 淡回常态 alpha。
     * settings 必须由 Service 显式推入 —— 分身窗自己的副本要等配置流绕一圈才新，
     * 用它算钉边会钉回旧侧（真机「换位后位置不对」的根因）。
     */
    fun crossFadeSwap(freshSettings: FloatBallSettings? = null, onDone: (() -> Unit)? = null) {
        freshSettings?.let { settings = it }
        val target = view ?: run {
            onDone?.invoke()
            return
        }
        target.animate().cancel()
        target.animate()
            .alpha(0f)
            .setDuration(150L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                snapToMirror()
                val current = view
                if (current == null) {
                    onDone?.invoke()
                    return@withEndAction
                }
                current.animate()
                    .alpha(FloatBallDefaults.MirrorDimAlpha)
                    .setDuration(150L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { onDone?.invoke() }
                    .start()
            }
            .start()
    }
    // ---- Compose ----

    @Composable
    private fun MirrorContent() {
        SideGestureTheme {
            val density = LocalDensity.current
            val size = with(density) { ballPx.toDp() }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                MirrorFace(modifier = Modifier.size(size))
            }
        }
    }

    @Composable
    private fun MirrorFace(modifier: Modifier = Modifier) {
        val size = with(LocalDensity.current) { ballPx.toDp() }
        val accent = gestureButtonMarkColor(settings.color)
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.06f), CircleShape)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        CircleShape,
                    ),
            )
        }
    }

    private companion object {
        const val TAG = "LunoLauncher"
    }
}
