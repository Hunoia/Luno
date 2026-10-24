package hunoia.luno.bridge.ui

import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.core.LOG_TAG

val rootSize: IntSize
    get() = IntSize(DensityProvider.screenWidthPx, DensityProvider.screenHeightPx)

fun android.content.Context.removeWindow(view: View) {
    val wm = ContextCompat.getSystemService(this, WindowManager::class.java) ?: return
    // 窗口可能已被系统移除（服务销毁竞态），记日志而不是静默吞掉
    runCatching { wm.removeViewImmediate(view) }
        .onFailure { error ->
            Log.w(LOG_TAG, "removeWindow failed: ${error::class.simpleName} ${error.message}")
        }
}

fun android.content.Context.removeWindows(views: Collection<View>) {
    views.forEach { view ->
        removeWindow(view)
    }
}
