package hunoia.luno.bridge.window

import android.view.View
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import hunoia.luno.bridge.DensityProvider

val rootSize: IntSize
    get() = IntSize(DensityProvider.screenWidthPx, DensityProvider.screenHeightPx)

fun android.content.Context.removeWindow(view: View) {
    val wm = ContextCompat.getSystemService(this, WindowManager::class.java)!!
    try {
        wm.removeViewImmediate(view)
    } catch (ignored: Exception) {
    }
}

fun android.content.Context.removeWindows(views: Collection<View>) {
    views.forEach { view ->
        removeWindow(view)
    }
}
