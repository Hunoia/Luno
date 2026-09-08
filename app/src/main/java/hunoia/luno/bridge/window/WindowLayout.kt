package hunoia.luno.bridge.window

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import hunoia.luno.bridge.DensityProvider

val rootSize: IntSize
    get() = IntSize(DensityProvider.screenWidthPx, DensityProvider.screenHeightPx)

fun WindowManager.LayoutParams.updateMainView() {
    val rootSize = rootSize
    width = rootSize.width
    height = rootSize.height
}

fun WindowManager.LayoutParams.setBasic(touchEnabled: Boolean) {
    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    format = PixelFormat.RGBA_8888
    setFlags(touchEnabled)
    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    @SuppressLint("RtlHardcoded")
    gravity = Gravity.LEFT or Gravity.TOP
}

fun WindowManager.LayoutParams.setFlags(touchEnabled: Boolean) {
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
