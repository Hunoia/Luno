package hunoia.luno.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import hunoia.luno.config.model.AdvancedSettings
import kotlinx.coroutines.CoroutineScope

/**
 * 运行态宿主的最小契约：任何 Surface / 触发源都能从这里拿到 context、无障碍服务、
 * 协程作用域与配置快照，而不需要知道具体是哪个宿主实现。
 *
 * 归 `:core:runtime`：它不引用任何 overlay / 面板 / 手势类型，所以 `:feature:*`
 * 可以放心依赖它；带 Surface 编排能力的宽接口（`GestureHost`）留在对应 feature。
 */
interface RuntimeHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    val context: Context
    val accessibilityService: AccessibilityService
    val coroutineScope: CoroutineScope
    val advancedSettings: AdvancedSettings?
}
