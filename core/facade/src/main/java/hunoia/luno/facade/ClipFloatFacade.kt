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

    /**
     * 直接钉一段**不在历史里**的文本到屏幕上（识屏工具条「悬浮显示」）。
     *
     * 识屏拿到的是一段临时取到的文字，进历史即污染记录；这里只传内容，窗口是临时的、
     * 关掉就没了。文本为空 / 悬浮窗权限缺失时静默失败。
     */
    fun showText(context: Context, text: String, label: String? = null) {}

    /**
     * 直接钉一张**不在历史里**的图片到屏幕上（识屏工具条的图片「悬浮显示」）。
     *
     * 图片以文件字节传入：识屏只有一张裁剪位图，没有可共享的 URI。服务侧落一份临时副本
     * 后挂窗，关窗即删。
     */
    fun showImage(context: Context, jpeg: ByteArray, label: String? = null) {}
}
