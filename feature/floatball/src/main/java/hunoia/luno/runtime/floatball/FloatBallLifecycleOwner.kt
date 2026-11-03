package hunoia.luno.runtime.floatball

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
 * 给 `ComposeView` 用的最小生命周期宿主：`ComposeView` 在 `onAttachedToWindow` 时会向上找
 * `ViewTreeLifecycleOwner`/`ViewTreeViewModelStoreOwner`/`ViewTreeSavedStateRegistryOwner`，
 * 三者缺一就抛 `IllegalStateException`；`FloatBallService` 是普通 Service 一个都没有，必须自己补。
 * 生命周期默认只推到 CREATED；[createAndAttachTo] 的 active=true 推到 RESUMED。
 *
 * CREATED 下重组不会真正上屏：悬浮球的动作图标/颜色、瞬态胶囊的内容更新都需要
 * 状态驱动重组，所以主球与胶囊一律用 active=true。
 */
class FloatBallLifecycleOwner private constructor() :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    /**
     * 必须在 attach 之前调一次。顺序有讲究：`SavedStateRegistryController.create()` 要求 owner 处于
     * `INITIALIZED`，`performRestore()` 也只在 `INITIALIZED` 下合法，两者都做完才能推进到 `CREATED`。
     * Kotlin 属性初始化自上而下，所以推进语句只能放方法里，不能写成
     * `LifecycleRegistry(this).apply { ... }` —— 那会让 create() 看到已 CREATED 的生命周期并抛
     * "Restarter must be created only during owner's initialization stage"。
     */
    fun restore(active: Boolean = false) {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        // 瞬态胶囊：CREATED 下 LaunchedEffect/produceState 不会执行（应用图标永远加载不出来），
        // 状态驱动的内容更新也不会应用；推到 RESUMED 让 effects 与重组真正跑。
        if (active) lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }

    companion object {
        fun createAndAttachTo(view: android.view.View, active: Boolean = false) {
            val owner = FloatBallLifecycleOwner()
            owner.restore(active)
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeViewModelStoreOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            view.addOnAttachStateChangeListener(
                object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewDetachedFromWindow(v: android.view.View) {
                        owner.destroy()
                        v.removeOnAttachStateChangeListener(this)
                    }

                    override fun onViewAttachedToWindow(v: android.view.View) = Unit
                },
            )
        }
    }
}