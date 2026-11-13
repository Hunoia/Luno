package hunoia.luno.runtime.surface

import android.content.Context

/**
 * 剪贴板悬浮窗的进程级命令口。
 *
 * 面板部件跑在 `:service`、页面跑在主进程，两边都不允许反向依赖浮球宿主模块；
 * `:app` 在进程入口安装实现（拉服务 + 带条目 id），调用方只表达意图。
 * 未安装（宿主未初始化）时静默忽略。
 */
object ClipFloatDispatch {

    @Volatile
    var show: ((context: Context, entryId: String) -> Unit)? = null
}
