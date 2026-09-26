package hunoia.luno.bridge.window

import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

fun android.content.Context.updateLayout(view: View, lp: WindowManager.LayoutParams) {
    try {
        val wm = ContextCompat.getSystemService(this, WindowManager::class.java)!!
        wm.updateViewLayout(view, lp)
    } catch (ignored: Exception) {
    }
}

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