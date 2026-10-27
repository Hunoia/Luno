package hunoia.luno.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 待处理的「从链接新建规则」请求（主进程内）。
 *
 * `:service` 的识屏卡片发起时只能带 Intent extra 跨进程；[hunoia.luno.ui.MainActivity]
 * 收到后写入这里，导航图消费一次即清空。用进程内 StateFlow 而不是塞进 SavedState：
 * 它是一次性导航意图，不该在进程重建后重放。
 */
object PendingLinkRule {

    private val _pattern = MutableStateFlow<String?>(null)
    val pattern: StateFlow<String?> = _pattern.asStateFlow()

    fun request(value: String) {
        _pattern.value = value.trim().ifEmpty { null }
    }

    fun consume() {
        _pattern.value = null
    }
}
