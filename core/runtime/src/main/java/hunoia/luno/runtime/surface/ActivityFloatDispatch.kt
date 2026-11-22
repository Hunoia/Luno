package hunoia.luno.runtime.surface

import android.content.Context

/**
 * 当前界面悬浮窗的进程级命令口。
 *
 * 同 [ClipFloatDispatch]：面板部件（`:service`）与设置页（主进程）都不允许反向依赖
 * 浮球宿主模块；`:app` 在进程入口安装实现（拉起服务），调用方只表达意图。
 * 无需传内容——卡片实时读 `ForegroundActivityHub`，钉悬浮只是「开窗」这一条命令。
 * 未安装（宿主未初始化）时静默忽略。
 */
object ActivityFloatDispatch {

    @Volatile
    var show: ((context: Context) -> Unit)? = null
}
