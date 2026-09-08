package hunoia.luno.runtime.overlay

import hunoia.luno.bridge.window.applyOverlayViewTreeOwners
import hunoia.luno.bridge.window.overlayLayoutParams
import hunoia.luno.bridge.window.windowManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.ui.theme.AnimOverlayFade

interface RuntimePanelOverlayHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    val context: Context
}

class RuntimePanelScope internal constructor(
    val close: () -> Unit,
    val updatePanelSize: (width: Int, height: Int) -> Unit,
    val onCloseAnimated: () -> Unit,
    val onRegisterCloseAnimated: ((() -> Unit) -> Unit)?,
)

class RuntimePanelOverlay(private val host: RuntimePanelOverlayHost) {
    private var overlayView: View? = null
    private var isShowing = false
    private var isHiding = false
    private var hasPanelSize = false
    private var triggerCloseAnimated: (() -> Unit)? = null

    fun show(content: @Composable RuntimePanelScope.() -> Unit) {
        if (isShowing || isHiding) return
        removeOverlayView()
        isShowing = true
        val wm = host.context.windowManager()
        val lp = createLayoutParams()

        val composeView = ComposeView(host.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            applyOverlayViewTreeOwners(host)

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    close()
                    performClick()
                    true
                } else false
            }

            setContent {
                RuntimePanelScope(
                    close = ::close,
                    updatePanelSize = { width, height ->
                        updatePanelLayout(wm, this, lp, width, height)
                    },
                    onCloseAnimated = {
                        removeOverlayView(this)
                    },
                    onRegisterCloseAnimated = { callback ->
                        triggerCloseAnimated = callback
                    },
                ).content()
            }
        }

        wm.addView(composeView, lp)
        overlayView = composeView
    }

    fun close() {
        val view = overlayView ?: return
        if (isHiding) return
        isShowing = false
        isHiding = true
        if (triggerCloseAnimated != null) {
            triggerCloseAnimated?.invoke()
        } else {
            animateOut(view)
        }
    }

    fun closeImmediately() {
        removeOverlayView()
    }

    private fun animateOut(view: View) {
        view.animate()
            .alpha(0f)
            .setDuration(AnimOverlayFade)
            .withEndAction { removeOverlayView(view) }
    }

    private fun removeOverlayView(view: View? = overlayView) {
        val target = view ?: return
        target.animate().cancel()
        target.alpha = 1f
        if (overlayView === target) overlayView = null
        isShowing = false
        isHiding = false
        hasPanelSize = false
        triggerCloseAnimated = null
        val wm = host.context.windowManager()
        runCatching { wm.removeView(target) }
    }

    private fun updatePanelLayout(
        wm: WindowManager,
        view: View,
        lp: WindowManager.LayoutParams,
        width: Int,
        height: Int,
    ) {
        if (isHiding) return
        if (width <= 0 || height <= 0) return
        val screenWidth = DensityProvider.screenWidthPx
        val screenHeight = DensityProvider.screenHeightPx
        val nextWidth = width.coerceIn(1, screenWidth)
        val nextHeight = height.coerceIn(1, screenHeight)
        val nextX = ((screenWidth - nextWidth) / 2).coerceAtLeast(0)
        val nextY = (screenHeight - nextHeight - BottomMarginPx).coerceAtLeast(0)
        hasPanelSize = true
        if (lp.width == nextWidth && lp.height == nextHeight && lp.x == nextX && lp.y == nextY) {
            return
        }

        lp.width = nextWidth
        lp.height = nextHeight
        lp.x = nextX
        lp.y = nextY
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val screenWidth = DensityProvider.screenWidthPx
        val screenHeight = DensityProvider.screenHeightPx
        val panelWidth = (screenWidth * 0.9f).toInt()
        val panelHeight = estimatePanelHeightPx().coerceAtMost(screenHeight)

        return overlayLayoutParams().apply {
            width = panelWidth
            height = panelHeight
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            gravity = Gravity.START or Gravity.TOP
            x = ((screenWidth - panelWidth) / 2).coerceAtLeast(0)
            y = (screenHeight - panelHeight - BottomMarginPx).coerceAtLeast(0)
        }
    }

    private fun estimatePanelHeightPx(): Int {
        return DensityProvider.dp2px(336)
    }

    private companion object {
        const val BottomMarginPx = 180
    }
}
