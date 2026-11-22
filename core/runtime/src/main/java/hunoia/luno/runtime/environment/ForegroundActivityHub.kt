package hunoia.luno.runtime.environment

import hunoia.luno.core.domain.ForegroundActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 当前前台界面的进程级数据源（`:service` 进程内）。
 *
 * 由 `GestureCoordinator` 在 `TYPE_WINDOW_STATE_CHANGED` 事件里写入（主线程），
 * 写入侧负责过滤自家窗口事件：面板 / 悬浮球自己的窗口不更新这里，
 * 面板打开时状态保持为面板背后的真实应用（sticky，无事件不回退）。
 *
 * 消费者：当前界面网格部件与当前界面悬浮窗，实时跟随前台切换。
 */
object ForegroundActivityHub {
    private val _current = MutableStateFlow<ForegroundActivity?>(null)

    /** 最近一次非自家应用的新窗口信息；服务刚起还没有事件时为 null。 */
    val current: StateFlow<ForegroundActivity?> = _current

    /** 同值去重：频繁的同窗口事件（部分 ROM 反复投递）不触发下游重组。 */
    fun update(packageName: String, activityClass: String) {
        val next = ForegroundActivity(packageName, activityClass)
        if (_current.value != next) _current.value = next
    }
}
