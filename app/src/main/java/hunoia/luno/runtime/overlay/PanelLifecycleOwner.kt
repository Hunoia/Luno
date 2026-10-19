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
 *
 * 球窗同款三件套（LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner），
 * 但推到 RESUMED 且**不**随 detach 自动销毁 —— 窗口被系统摘除后重挂时组合要原样
 * 复用（`DisposeOnViewTreeLifecycleDestroyed` 策略），销毁时机由宿主 overlay
 * 对象控制（正常关闭 / 挂载失败回滚）。
 *
 * 为什么不并入球的 FloatBallLifecycleOwner：球窗策略不同（只到 CREATED、
 * detach 即销毁，因为球没有 collectAsStateWithLifecycle / 动画这类需求），
 * 是特化而非重复，合进来等于给球改行为。
 */
internal class PanelLifecycleOwner :
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
