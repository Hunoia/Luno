package hunoia.luno.bridge.window

import android.content.Context
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

fun Context.windowManager(): WindowManager =
    ContextCompat.getSystemService(this, WindowManager::class.java)!!

fun ComposeView.applyOverlayViewTreeOwners(host: Any) {
    if (host is LifecycleOwner) setViewTreeLifecycleOwner(host)
    if (host is ViewModelStoreOwner) setViewTreeViewModelStoreOwner(host)
    if (host is SavedStateRegistryOwner) setViewTreeSavedStateRegistryOwner(host)
}

fun overlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    format = PixelFormat.RGBA_8888
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
}
