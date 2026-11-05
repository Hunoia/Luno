package hunoia.luno.runtime.dispatch

import hunoia.luno.core.domain.ActivityOptionsSource

/**
 * 活动列表查询的进程级安装点（契约在 `:core:domain`）。
 *
 * 未安装时动作编辑页的 Activity 选择器退化为空列表，不影响其它功能。
 */
object ActivityOptions {

    @Volatile
    private var source: ActivityOptionsSource? = null

    fun install(source: ActivityOptionsSource?) {
        this.source = source
    }

    val current: ActivityOptionsSource? get() = source
}
