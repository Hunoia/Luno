package hunoia.luno.runtime.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 浮窗组合的最小生命周期宿主（网格面板 / 快速启动器浮窗共用）。
 * 推到 RESUMED 且不随 detach 自动销毁：窗口被系统摘除后重挂时组合要原样复用，
 * 销毁时机由宿主 overlay 控制。不并入球的 owner：球窗只到 CREATED、detach 即销毁。
 */
class PanelLifecycleOwner :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    /** attach 前调：restore 与推进 CREATED 的顺序约束见 FloatBallLifecycleOwner。 */
    fun create() {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}
