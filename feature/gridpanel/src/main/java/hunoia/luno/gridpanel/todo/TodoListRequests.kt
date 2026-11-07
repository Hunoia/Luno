package hunoia.luno.gridpanel.todo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 待处理的「从代办面板部件打开代办列表页」请求（主进程内）。
 *
 * 网格面板是独立窗口（服务侧 overlay），只能带 Intent extra 跨进程拉起
 * MainActivity；Activity 收到后写入这里，导航图消费一次即清空。
 * 用进程内 StateFlow 而不是塞进 SavedState：它是一次性导航意图，不该在进程重建后重放。
 */
object TodoListRequests {

    /** 待处理请求：[todoId] 为空 = 只打开列表页；非空 = 再打开该条编辑弹窗。 */
    data class PendingTodoOpen(val todoId: String? = null)

    private val _open = MutableStateFlow<PendingTodoOpen?>(null)
    val open: StateFlow<PendingTodoOpen?> = _open.asStateFlow()

    fun request(todoId: String? = null) {
        _open.value = PendingTodoOpen(todoId)
    }

    fun consume() {
        _open.value = null
    }
}
