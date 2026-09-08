package hunoia.luno.runtime.overlay

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import hunoia.luno.bridge.DensityProvider
import hunoia.luno.config.model.GestureButton
import kotlin.math.roundToInt

internal object WindowLayoutFactory {

    private val rootSize: IntSize
        get() = IntSize(DensityProvider.screenWidthPx, DensityProvider.screenHeightPx)

    fun mainOverlayLayoutParams(): WindowManager.LayoutParams {
        val size = rootSize
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            width = size.width
            height = size.height
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
        }
    }

    fun gestureButtonLayoutParams(touchEnabled: Boolean): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.RGBA_8888
            setTouchFlags(touchEnabled)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
        }
    }

    fun actionPanelOverlayLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.RGBA_8888
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    fun subGestureLayoutParams(center: Offset, radiusPx: Int): WindowManager.LayoutParams {
        val screenWidth = DensityProvider.screenWidthPx
        val screenHeight = DensityProvider.screenHeightPx
        val radius = radiusPx.coerceAtLeast(DensityProvider.dp2px(48f))
        val size = (radius * 2).coerceAtMost(maxOf(screenWidth, screenHeight))
        val validCenter = if (center.x.isFinite() && center.y.isFinite()) center
            else Offset(screenWidth / 2f, screenHeight / 2f)
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.RGBA_8888
            width = size.coerceAtMost(screenWidth)
            height = size.coerceAtMost(screenHeight)
            x = (validCenter.x - width / 2f).roundToInt().coerceIn(0, (screenWidth - width).coerceAtLeast(0))
            y = (validCenter.y - height / 2f).roundToInt().coerceIn(0, (screenHeight - height).coerceAtLeast(0))
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            @SuppressLint("RtlHardcoded")
            gravity = Gravity.LEFT or Gravity.TOP
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    fun updateGestureButton(lp: WindowManager.LayoutParams, button: GestureButton) {
        val rootWidth = DensityProvider.screenWidthPx
        val rootHeight = DensityProvider.screenHeightPx
        updateGestureButton(lp, button, rootWidth, rootHeight)
    }

    fun updateGestureButton(lp: WindowManager.LayoutParams, button: GestureButton, rootWidth: Int, rootHeight: Int) {
        val left = (rootWidth * button.bounds.x).roundToInt().coerceIn(0, rootWidth)
        val top = (rootHeight * button.bounds.y).roundToInt().coerceIn(0, rootHeight)
        val right = (rootWidth * (button.bounds.x + button.bounds.width)).roundToInt().coerceIn(left, rootWidth)
        val bottom = (rootHeight * (button.bounds.y + button.bounds.height)).roundToInt().coerceIn(top, rootHeight)
        lp.width = (right - left).coerceAtLeast(1)
        lp.height = (bottom - top).coerceAtLeast(1)
        lp.x = left
        lp.y = top
        @SuppressLint("RtlHardcoded")
        lp.gravity = Gravity.LEFT or Gravity.TOP
    }

    private fun WindowManager.LayoutParams.setTouchFlags(touchEnabled: Boolean) {
        flags = if (touchEnabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }
}
