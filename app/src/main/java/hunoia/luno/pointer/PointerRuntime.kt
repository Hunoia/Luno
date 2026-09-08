package hunoia.luno.pointer

import androidx.compose.ui.geometry.Offset
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.bridge.accessibility.Accessibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PointerRuntime(
    private val host: PointerOverlayHost,
    private val scope: CoroutineScope,
    private val gestureSettingsProvider: () -> GestureSettings?,
    private val onStateChanged: () -> Unit,
) {
    private var overlay: PointerOverlay? = null
    private var sessionSettings: GestureSettings.Pointer? = null
    var isActive: Boolean = false
        private set

    fun show(continuousModeOverride: Boolean? = null): Boolean {
        if (!begin()) return false
        val settings = (sessionSettings ?: gestureSettingsProvider()?.pointer ?: GestureSettings.Pointer()).let {
            if (continuousModeOverride == null) it else it.copy(continuousMode = continuousModeOverride)
        }
        showOverlay(settings, Offset.Unspecified)
        return true
    }

    fun beginBridge(settings: GestureSettings.Pointer): Boolean {
        if (!begin()) return false
        sessionSettings = settings
        return true
    }

    fun showBridge(settings: GestureSettings.Pointer): Boolean {
        if (!begin()) return false
        showOverlay(settings, Offset.Unspecified)
        return true
    }

    private fun showOverlay(settings: GestureSettings.Pointer, previousPosition: Offset) {
        sessionSettings = settings
        val o = overlay ?: PointerOverlay(host).also { overlay = it }
        o.show(
            settings = settings,
            onPointerAction = { x, y, keepActive ->
                performActionAt(x, y, keepActive)
            },
            previousPosition = previousPosition,
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
        if (!isActive && overlay == null && sessionSettings == null) return
        isActive = false
        overlay?.closeImmediately()
        sessionSettings = null
        onStateChanged()
    }

    fun performActionAt(
        x: Int, y: Int, keepActive: Boolean,
    ) {
        val actionPosition = clampPointerPosition(Offset(x.toFloat(), y.toFloat()))
        overlay?.closeImmediately()
        if (!isActive) {
            isActive = true
            onStateChanged()
        }
        scope.launch {
            delay(80)
            val service = host as? android.accessibilityservice.AccessibilityService
            if (service != null) {
                Accessibility.click(service, x, y)
            }
            if (keepActive) {
                showOverlay(
                    sessionSettings ?: gestureSettingsProvider()?.pointer ?: GestureSettings.Pointer(),
                    actionPosition,
                )
            } else {
                end()
            }
        }
    }

    fun performBridgeActionAt(
        x: Int, y: Int, keepActive: Boolean,
    ) {
        val actionPosition = clampPointerPosition(Offset(x.toFloat(), y.toFloat()))
        if (!isActive) {
            isActive = true
            onStateChanged()
        }
        scope.launch {
            delay(80)
            val service = host as? android.accessibilityservice.AccessibilityService
            if (service != null) {
                Accessibility.click(service, x, y)
            }
            if (keepActive) {
                showOverlay(
                    sessionSettings ?: gestureSettingsProvider()?.pointer ?: GestureSettings.Pointer(),
                    actionPosition,
                )
            } else {
                end()
            }
        }
    }

    fun getCurrentSettings(): GestureSettings.Pointer? = sessionSettings
    fun onSettingsUpdate(settings: GestureSettings.Pointer) { sessionSettings = settings }
    fun onDestroy() { overlay?.closeImmediately() }
}
