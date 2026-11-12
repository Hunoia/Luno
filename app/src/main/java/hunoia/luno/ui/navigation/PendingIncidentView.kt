package hunoia.luno.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 待处理的「打开故障记录」请求（主进程内）。
 *
 * 故障通知由任意进程发出，点击后带 Intent extra 拉起 [hunoia.luno.ui.MainActivity]；
 * Activity 收到后写入这里，导航图消费一次跳运行日志页，日志页再消费一次展开故障记录。
 */
object PendingIncidentView {

    private val _requested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    fun request() {
        _requested.value = true
    }

    fun consume() {
        _requested.value = false
    }
}
