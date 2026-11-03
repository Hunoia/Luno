package hunoia.luno.runtime.pointer

import hunoia.luno.bridge.system.Accessibility
import hunoia.luno.config.model.GestureSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 指针会话状态机，两条进入路径：
 * - [beginBridge]：按住类触发，光标画在主 overlay 里（[PointerSession]），这里只登记会话，
 *   UP 后经 [performBridgeActionAt] 在落点点击；
 * - [showBridge]/[show]：滑动/面板点选/悬浮球/动作分发，挂全屏 [PointerOverlay] 接管触摸，
 *   UP 后经 [performActionAt] 点击。
 * 关窗后延迟 80ms 再点击：等 WindowManager 真正移除窗口，避免点击落在自己的 overlay 上。
 */
class PointerRuntime(
    private val host: PointerOverlayHost,
    private val scope: CoroutineScope,
    private val gestureSettingsProvider: () -> GestureSettings?,
    private val onStateChanged: () -> Unit,
) {
    private var overlay: PointerOverlay? = null
    var isActive: Boolean = false
        private set

    /** 动作分发入口（internal.pointer）：按全局指针设置开启。 */
    fun show(): Boolean {
        if (!begin()) return false
        showOverlay(gestureSettingsProvider()?.pointer ?: GestureSettings.Pointer())
        return true
    }

    fun beginBridge(): Boolean {
        // 面板内路径：设置由 PointerSession 自持，这里只登记会话。
        return begin()
    }

    fun showBridge(settings: GestureSettings.Pointer): Boolean {
        if (!begin()) return false
        showOverlay(settings)
        return true
    }

    private fun showOverlay(settings: GestureSettings.Pointer) {
        val o = overlay ?: PointerOverlay(host).also { overlay = it }
        o.show(
            settings = settings,
            onPointerAction = { x, y ->
                performActionAt(x, y)
            },
            onDismiss = { end() },
        )
    }

    private fun begin(): Boolean {
        if (isActive) return false
        isActive = true
        onStateChanged()
        return true
    }

    fun end() {
        if (!isActive && overlay == null) return
        isActive = false
        overlay?.closeImmediately()
        onStateChanged()
    }

    fun performActionAt(x: Int, y: Int) {
        overlay?.closeImmediately()
        if (!isActive) {
            isActive = true
            onStateChanged()
        }
        clickAt(x, y)
    }

    fun performBridgeActionAt(x: Int, y: Int) {
        if (!isActive) {
            isActive = true
            onStateChanged()
        }
        clickAt(x, y)
    }

    private fun clickAt(x: Int, y: Int) {
        scope.launch {
            delay(80)
            val service = host as? android.accessibilityservice.AccessibilityService
            if (service != null) {
                Accessibility.click(service, x, y)
            }
            end()
        }
    }

    fun onDestroy() {
        overlay?.closeImmediately()
    }
}
