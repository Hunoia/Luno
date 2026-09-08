package hunoia.luno.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import hunoia.luno.pointer.PointerOverlayHost
import hunoia.luno.runtime.overlay.QuickAppLauncherOverlay
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import kotlinx.coroutines.CoroutineScope

interface GestureHost : PointerOverlayHost {
    val accessibilityService: AccessibilityService
    val coroutineScope: CoroutineScope

    val quickAppLauncherOverlay: QuickAppLauncherOverlay
    val runtimePanelOverlay: RuntimePanelOverlay

    fun nowInLauncher(): Boolean
    fun requestEnableFrozenPackage(packageName: String, onResult: (Boolean) -> Unit)
    fun getCurrentPackageName(): String
}
