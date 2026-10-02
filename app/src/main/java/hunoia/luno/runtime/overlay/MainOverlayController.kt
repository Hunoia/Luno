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

    /**
     * ActionPanel 是否正在显示。为 false 时窗口收成 1x1,把常驻的全屏 RGBA_8888 buffer
     * (本机 1200x2670 ≈ 12.2MB)从常驻变成按需。
     *
     * 起始就是 false:`replaceContent` 只在手势按钮配置流发射时调用,那一刻面板必然是隐藏的,
     * 所以服务启动后不需要先占着 12MB 再缩回去。
     */
    private var panelVisible: Boolean = false

    fun replaceContent(content: @Composable () -> Unit) {
        val wm = ContextCompat.getSystemService(host.context, WindowManager::class.java)!!
        composeView?.let { host.context.removeWindow(it) }
        val lp = WindowLayoutFactory.mainOverlayLayoutParams(fullScreen = panelVisible)
        val view = ComposeView(host.context).apply {
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            setContent { content() }
        }
        if (wm.safeAddView(view, lp)) {
            composeView = view
        }
    }

    /**
     * ActionPanel 显隐时调用。
     *
     * 放大必须在 `actionPanelState.visible` 翻 true **之前**完成:`ActionPanel` 靠
     * `onGloballyPositioned` 拿 `parentSize` 来摆弧形菜单项,如果窗口还是 1x1 就让内容进组合,
     * 淡入动画的头几帧会按 1x1 定位再跳回全屏。调用方(SideGestureContainer)已经按这个顺序排。
     *
     * 缩小则由调用方等到淡出动画结束后再发,这里不再自己延迟。
     */
    fun setPanelVisible(visible: Boolean) {
        if (panelVisible == visible) return
        panelVisible = visible
        applySize()
    }

    fun updateLayout() {
        if (panelVisible) applySize()
    }

    /** 只有尺寸真的变了才发跨进程 updateViewLayout。 */
    private fun applySize() {
        val view = composeView ?: return
        val wm = ContextCompat.getSystemService(host.context, WindowManager::class.java)!!
        val currentLp = view.layoutParams as? WindowManager.LayoutParams ?: return
        val desired = WindowLayoutFactory.mainOverlayLayoutParams(fullScreen = panelVisible)
        if (currentLp.width == desired.width && currentLp.height == desired.height) return
        currentLp.width = desired.width
        currentLp.height = desired.height
        currentLp.x = desired.x
        currentLp.y = desired.y
        wm.safeUpdateViewLayout(view, currentLp)
    }

    fun release() {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "MainOverlayController release")
        composeView?.let { host.context.removeWindow(it) }
        composeView = null
    }
}
