package hunoia.luno.runtime.floatball

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.runtime.overlay.safeUpdateViewLayout
import kotlin.math.roundToInt

/** 主球窗与镜像分身窗共用的窗口装配件：参数全是显式传入，不携带状态。 */

/** 球窗 LP：TYPE_APPLICATION_OVERLAY、ballPx 见方、LEFT|TOP。不加 LAYOUT_NO_LIMITS ——
 *  球必须完整在屏内，靠正常窗口边界 + 夹取就够。 */
@SuppressLint("RtlHardcoded")
internal fun ballLayoutParams(ballPx: Int) = WindowManager.LayoutParams().apply {
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

/** 尺寸变化落到现有窗（updateSettings 的 sizeDp 变更路径）。 */
internal fun applyBallSize(view: View, wm: WindowManager, ballPx: Int) {
    (view.layoutParams as? WindowManager.LayoutParams)?.let {
        it.width = ballPx
        it.height = ballPx
        wm.safeUpdateViewLayout(view, it)
    }
}

/** 锁屏 / 息屏/面板隐藏：整窗不可见也不可点；恢复后的姿态（静置计时等）由调用方补。 */
internal fun applyBallHiddenFlags(view: View, wm: WindowManager, hidden: Boolean) {
    val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
    // 无变化不写窗：软隐藏路径会被面板事件反复触发，别为重复状态付 updateViewLayout。
    val alreadyHidden = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
    val targetAlpha = if (hidden) 0f else 1f
    if (alreadyHidden == hidden && lp.alpha == targetAlpha) return
    if (hidden) {
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    } else {
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    }
    lp.alpha = targetAlpha
    wm.safeUpdateViewLayout(view, lp)
}

/**
 * 实测 LP 坐标系原点在屏幕上的 y（2038 悬浮窗以状态栏下沿为原点）：窗口摆出来
 * 才知道真实屏幕落点。[currentView]/[currentKnown] 都传字段读取器，post 时取最新值，
 * 测得变化才回调。
 */
internal fun postMeasureLpOriginY(
    currentView: () -> View?,
    currentKnown: () -> Int,
    onMeasured: (Int) -> Unit,
) {
    val target = currentView() ?: return
    target.post {
        val live = currentView() ?: return@post
        val location = IntArray(2)
        live.getLocationOnScreen(location)
        val measured = location[1] - ((live.layoutParams as? WindowManager.LayoutParams)?.y ?: 0)
        if (measured != currentKnown()) onMeasured(measured)
    }
}

/**
 * 球窗坐标落 LP：`screenX` / `screenDisplayY` 是屏幕空间的球顶位置（显示 y 已含
 * 键盘让位），LP y = 屏幕值 − [lpOriginY]，不越 LP 原点；不变更不写窗。
 */
internal fun writeBallPosition(
    view: View,
    wm: WindowManager,
    screenX: Float,
    screenDisplayY: Float,
    lpOriginY: Int,
) {
    val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
    val nextX = screenX.roundToInt()
    val nextY = (screenDisplayY - lpOriginY).roundToInt().coerceAtLeast(0)
    if (lp.x == nextX && lp.y == nextY) return
    lp.x = nextX
    lp.y = nextY
    wm.safeUpdateViewLayout(view, lp)
}

/** 球窗 Compose 外壳：全屏 Box 居中放 [face]，尺寸随 ballPx。 */
@Composable
internal fun BallWindowContent(ballPx: Int, face: @Composable (Dp) -> Unit) {
    SideGestureTheme {
        val density = LocalDensity.current
        val size = with(density) { ballPx.toDp() }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            face(size)
        }
    }
}
