package hunoia.luno.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import hunoia.luno.runtime.overlay.QuickAppLauncherOverlay
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import kotlinx.coroutines.CoroutineScope

interface GestureHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    val context: Context
    val accessibilityService: AccessibilityService
    val coroutineScope: CoroutineScope

    val quickAppLauncherOverlay: QuickAppLauncherOverlay
    val runtimePanelOverlay: RuntimePanelOverlay

    fun nowInLauncher(): Boolean
    fun requestEnableDisabledPackage(packageName: String, onResult: (Boolean) -> Unit)
    fun getCurrentPackageName(): String
}
