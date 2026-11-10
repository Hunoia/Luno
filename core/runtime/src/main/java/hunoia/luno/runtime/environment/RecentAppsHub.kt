package hunoia.luno.runtime.environment

import hunoia.luno.core.domain.RecentApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 最近应用历史与前景：条目带 userId，主应用与分身不再混为一条。 */
object RecentAppsHub {
    private val _history = MutableStateFlow<List<RecentApp>>(emptyList())
    private val _foreground = MutableStateFlow<RecentApp?>(null)

    /** 最近应用（旧 → 新，末尾最新，含当前前台）。 */
    val history: StateFlow<List<RecentApp>> = _history

    /** 当前前台实例（PreviousAppTracker 维护，未知时为 null）：消费者用它过滤，不猜「末尾=前台」。 */
    val foreground: StateFlow<RecentApp?> = _foreground

    fun update(history: List<RecentApp>) {
        _history.value = history
    }

    fun updateForeground(app: RecentApp) {
        _foreground.value = app
    }

    fun clear() {
        _history.value = emptyList()
        _foreground.value = null
    }
}
