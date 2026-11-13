package hunoia.luno.facade

import android.content.Context

/**
 * 剪贴板悬浮窗的显式入口（页面 → 常驻服务）。
 *
 * 悬浮窗由 `:service` 的浮球宿主承载；页面不能直接依赖该服务实现，只提交
 * 「显示哪一条」这一意图。条目 id 对应的内容由服务侧从 ClipboardCenter 解析，
 * 避免跨进程搬运图片路径。
 */
interface ClipFloatFacade {

    /**
     * 按剪贴板历史条目解析内容。
     * 条目已不存在 / 悬浮窗权限缺失时静默失败（服务侧记录日志）。
     */
    fun show(context: Context, entryId: String)
}
