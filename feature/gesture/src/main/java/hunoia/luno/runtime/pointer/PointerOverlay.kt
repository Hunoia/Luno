package hunoia.luno.runtime.pointer

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.bridge.ui.applyOverlayViewTreeOwners
import hunoia.luno.bridge.ui.windowManager
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.ui.theme.SideGestureTheme
import hunoia.luno.ui.theme.MotionFadeOut
import hunoia.luno.ui.component.PointerCursor

/** 指针窗口宿主：由无障碍服务实现（成员全部来自 [hunoia.luno.runtime.GestureHost]）。 */
interface PointerOverlayHost : androidx.lifecycle.LifecycleOwner,
    androidx.lifecycle.ViewModelStoreOwner,
    androidx.savedstate.SavedStateRegistryOwner {
    val context: Context
}

/**
 * 全屏指针窗口（滑动触发/面板点选/悬浮球路径）：窗口自己收触摸，拖动映射给光标，
 * UP 时在光标落点回调一次点击然后关闭。
 */
class PointerOverlay(private val host: PointerOverlayHost) {
    private var overlayView: View? = null
    private var lastTouch = Offset.Unspecified
    private var clickPulseKey = 0

    fun show(
        settings: GestureSettings.Pointer,
        onPointerAction: (Int, Int) -> Unit,
        onDismiss: () -> Unit,
    ) {
        closeImmediately()
        val wm = host.context.windowManager()
        // 跟随时先不画：首次 ACTION_DOWN 才是触发点，先闪一个初始位置会误导。
        val cursorPosition = mutableStateOf<Offset?>(
            if (settings.followTrigger) null else pointerInitialPosition(settings)
        )
        val clickPulse = mutableStateOf(clickPulseKey)
        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val down = Offset(event.rawX, event.rawY)
                        if (settings.followTrigger) {
                            cursorPosition.value = pointerFollowPosition(down, settings)
                        }
                        lastTouch = down
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val current = Offset(event.rawX, event.rawY)
                        val previous = lastTouch
                        val cursor = cursorPosition.value
                        if (previous != Offset.Unspecified && cursor != null) {
                            cursorPosition.value = movePointerCursor(cursor, current - previous, settings)
                        }
                        lastTouch = current
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        view.performClick()
                        val target = cursorPosition.value
                        close()
                        clickPulseKey += 1
                        clickPulse.value = clickPulseKey
                        if (target == null) {
                            onDismiss()
                        } else {
                            onPointerAction(target.x.toInt(), target.y.toInt())
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        close()
                        onDismiss()
                        true
                    }
                    else -> true
                }
            }
            setContent {
                SideGestureTheme {
                    cursorPosition.value?.let { position ->
                        PointerCursor(
                            position = position,
                            modifier = Modifier,
                            settings = settings,
                            clickPulseKey = clickPulse.value,
                        )
                    }
                }
            }
        }
        wm.addView(composeView, createLayoutParams())
        overlayView = composeView
    }

    /** 退场：立即不可点，光标淡出 120ms 后拆窗（show 的同步重建路径仍走 [closeImmediately]）。 */
    fun close() {
        val view = overlayView ?: return
        overlayView = null
        lastTouch = Offset.Unspecified
        (view.layoutParams as? WindowManager.LayoutParams)?.let { lp ->
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            runCatching { host.context.windowManager().updateViewLayout(view, lp) }
        }
        view.animate()
            .alpha(0f)
            .setDuration(MotionFadeOut.toLong())
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { runCatching { host.context.windowManager().removeViewImmediate(view) } }
            .start()
    }

    fun closeImmediately() {
        val view = overlayView ?: return
        overlayView = null
        lastTouch = Offset.Unspecified
        runCatching { host.context.windowManager().removeViewImmediate(view) }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = DensityProvider.screenWidthPx
            height = DensityProvider.screenHeightPx
            gravity = Gravity.START or Gravity.TOP
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }
}
