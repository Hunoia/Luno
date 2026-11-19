package hunoia.luno.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「动态快捷方式导入页」的跨 Activity 导航请求。
 *
 * 临时默认桌面的 HOME Activity 与主界面同进程：HOME 入口只负责把请求登记进来并拉回
 * [hunoia.luno.ui.MainActivity]，由 [hunoia.luno.ui.SideGestureApp] 消费一次后跳设置页。
 */
object PendingDynamicShortcuts {

    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    fun request() {
        _requested.value = true
    }

    fun consume() {
        _requested.value = false
    }
}
