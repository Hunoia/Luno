package hunoia.luno.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import hunoia.luno.action.model.StoredAction
import hunoia.luno.runtime.overlay.RuntimePanelOverlay
import hunoia.luno.runtime.overlay.MiniWindowAdjustOverlay
import kotlinx.coroutines.CoroutineScope

interface GestureHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    val context: Context
    val accessibilityService: AccessibilityService
    val coroutineScope: CoroutineScope
    val advancedSettings: hunoia.luno.config.model.AdvancedSettings?

    val runtimePanelOverlay: RuntimePanelOverlay
    val miniWindowAdjustOverlay: MiniWindowAdjustOverlay

    fun nowInLauncher(): Boolean
    fun requestEnableDisabledPackage(packageName: String, onResult: (Boolean) -> Unit)
    fun getCurrentPackageName(): String

    /** 供 overlay 等场景直接执行一个已构造好的动作。 */
    fun dispatchAction(action: StoredAction)
}
