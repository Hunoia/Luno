package hunoia.luno.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 待处理的「从代办面板部件打开编辑页」请求（主进程内）。
 *
 * 网格面板是独立窗口（服务侧 overlay），只能带 Intent extra 跨进程拉起
 * [hunoia.luno.ui.MainActivity]；Activity 收到后写入这里，导航图消费一次即清空。
 * 用进程内 StateFlow 而不是塞进 SavedState：它是一次性导航意图，不该在进程重建后重放。
 */
object PendingTodoEdit {

    private val _todoId = MutableStateFlow<String?>(null)
    val todoId: StateFlow<String?> = _todoId.asStateFlow()

    fun request(value: String) {
        _todoId.value = value.takeIf { it.isNotBlank() }
    }

    fun consume() {
        _todoId.value = null
    }
}
