package hunoia.luno.runtime.overlay

import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import hunoia.luno.BuildConfig
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import hunoia.luno.bridge.window.removeWindow
import hunoia.luno.runtime.GestureHost

class MainOverlayController(
    private val host: GestureHost,
) {
    private var composeView: ComposeView? = null

    fun replaceContent(content: @Composable () -> Unit) {
        val wm = ContextCompat.getSystemService(host.context, WindowManager::class.java)!!
        composeView?.let { host.context.removeWindow(it) }
        val lp = WindowLayoutFactory.mainOverlayLayoutParams()
        val view = ComposeView(host.context).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setContent { content() }
        }
        wm.addView(view, lp)
        composeView = view
    }

    fun updateLayout() {
        val view = composeView ?: return
        val wm = ContextCompat.getSystemService(host.context, WindowManager::class.java)!!
        val lp = WindowLayoutFactory.mainOverlayLayoutParams()
        val currentLp = view.layoutParams as WindowManager.LayoutParams
        currentLp.width = lp.width
        currentLp.height = lp.height
        try { wm.updateViewLayout(view, currentLp) } catch (_: Exception) { }
    }

    fun release() {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "MainOverlayController release")
        composeView?.let { host.context.removeWindow(it) }
        composeView = null
    }
}
