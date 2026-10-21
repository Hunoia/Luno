package hunoia.luno.runtime.environment

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 最近应用快照（进程内单例）。[PreviousAppTracker] 在 a11y 事件里维护历史，
 * 消费者从这里读：不跨进程、不加权限。[history] 按新旧排序，末尾最新，含当前前台。
 */
object RecentAppsHub {
    private val _history = MutableStateFlow<List<String>>(emptyList())

    /** 最近应用包名（旧 → 新，末尾最新，含当前前台）。 */
    val history: StateFlow<List<String>> = _history

    fun update(history: List<String>) {
        _history.value = history
    }

    fun clear() {
        _history.value = emptyList()
    }
}
