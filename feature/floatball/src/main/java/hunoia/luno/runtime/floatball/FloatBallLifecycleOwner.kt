package hunoia.luno.runtime.floatball

import android.content.Context
import android.graphics.Color as AndroidColor
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
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

        /**
         * 服务进程浮窗的同构窗口骨架：`FrameLayout` 做窗口根、`ComposeView` 做子级，返回两者。
         *
         * 主球 / 内容球胶囊 / 剪贴板悬浮窗都在这里组装，避免三处各写一份而悄悄分叉。
         * 真机上直接拿 `ComposeView` 当窗口根会偶发不组合（胶囊空白），所以一律套一层
         * `FrameLayout`；需要叠在 Compose 之上的 View（主球的数字徽标）由调用方自行 `addView`，
         * 顺序即 z 序。
         */
        fun createWindowRoot(
            context: Context,
            content: @Composable () -> Unit,
        ): Pair<ComposeView, FrameLayout> {
            val compose = ComposeView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                createAndAttachTo(this, active = true)
                setContent(content)
            }
            val root = FrameLayout(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                addView(
                    compose,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            return compose to root
        }
    }
}
