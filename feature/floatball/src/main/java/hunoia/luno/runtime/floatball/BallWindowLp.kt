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

/** 球窗 / 胶囊窗共用的窗口装配件：参数全是显式传入，不携带状态。 */

/** 球窗 LP：TYPE_APPLICATION_OVERLAY、ballPx 见方、LEFT|TOP。不加 LAYOUT_NO_LIMITS ——
 *  球必须完整在屏内，靠正常窗口边界 + 夹取就够。
 *
 *  带 LAYOUT_IN_SCREEN：LP 坐标固定以物理屏左上角为原点，不随状态栏显隐在
 *  「状态栏下沿 / 屏幕顶」之间切换 —— 否则状态栏一隐，系统把窗口整体上移，
 *  下一帧复测原点再写回 LP，表现为球上下跳一下再回原位。 */
@SuppressLint("RtlHardcoded")
internal fun ballLayoutParams(ballPx: Int) = WindowManager.LayoutParams().apply {
    type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    format = PixelFormat.TRANSLUCENT
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    // 贴物理屏边：默认 cutout 模式会把越入挖孔的窗口整体挪进安全区，
    // 横屏挖孔在左侧时表现为球 / 胶囊离左边空一段（面板窗已显式 ALWAYS，所以它正常）。
    layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    width = ballPx
    height = ballPx
    gravity = Gravity.LEFT or Gravity.TOP
    x = 0
    y = 0
}

/** 共享胶囊窗 LP：固定宽高（高 = 球尺寸），LEFT|TOP 手工按球的锚定边贴边。
 *  与球窗同样带 LAYOUT_IN_SCREEN，保证两窗 LP 原点一致。 */
@SuppressLint("RtlHardcoded")
internal fun capsuleLayoutParams(widthPx: Int, heightPx: Int) = WindowManager.LayoutParams().apply {
    type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    format = PixelFormat.TRANSLUCENT
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    width = widthPx
    height = heightPx
    gravity = Gravity.LEFT or Gravity.TOP
    x = 0
    y = 0
}

/**
 * 软隐藏第一步：hidden=true 立即置不可点（防误触，视觉淡出由调用方在 view alpha 上播，
 * lp alpha 收底延后到淡出完成）；hidden=false 恢复可点并把 lp alpha 抬回 1（视觉淡入
 * 由调用方从 0 起播）。旗标无变化不写窗：软隐藏路径会被面板事件反复触发。
 */
internal fun applyBallTouchFlags(view: View, wm: WindowManager, hidden: Boolean) {
    val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
    val alreadyHidden = (lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
    if (hidden) {
        if (alreadyHidden) return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    } else {
        if (!alreadyHidden && lp.alpha == 1f) return
        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        lp.alpha = 1f
    }
    wm.safeUpdateViewLayout(view, lp)
}

/** 淡出完成的收底：lp alpha 归 0（旗标已在隐藏第一步置过）。 */
internal fun settleBallWindowAlpha(view: View, wm: WindowManager) {
    val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
    if (lp.alpha == 0f) return
    lp.alpha = 0f
    wm.safeUpdateViewLayout(view, lp)
}

/**
 * 实测 LP 坐标系原点在屏幕上的 y（LAYOUT_IN_SCREEN 下应为 0；部分 ROM 仍以状态栏下沿
 * 为原点，作为兜底保留）。测得后总是回调；调用方自己判断是否需要重写窗口。
 */
internal fun postMeasureLpOriginY(
    currentView: () -> View?,
    onMeasured: (Int) -> Unit,
) {
    val target = currentView() ?: return
    target.post {
        val live = currentView() ?: return@post
        val location = IntArray(2)
        live.getLocationOnScreen(location)
        val measured = location[1] - ((live.layoutParams as? WindowManager.LayoutParams)?.y ?: 0)
        onMeasured(measured)
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
