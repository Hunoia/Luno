package hunoia.luno.ui.navigation

import hunoia.luno.ui.home.MainTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「打开主页并切到某个 tab」的一次性请求。
 *
 * 桌面部件 / 动作面板 / 提醒通知只能带 Intent extra 跨进程拉起 MainActivity，
 * 由 Activity 写入这里，导航图消费一次即清空。
 * 用进程内 StateFlow 而不是塞进 SavedState：它是一次性导航意图，不该在进程重建后重放。
 */
object PendingHomeTab {

    private val _requested = MutableStateFlow<MainTab?>(null)
    val requested: StateFlow<MainTab?> = _requested.asStateFlow()

    fun request(tab: MainTab) {
        _requested.value = tab
    }

    fun consume() {
        _requested.value = null
    }
}
