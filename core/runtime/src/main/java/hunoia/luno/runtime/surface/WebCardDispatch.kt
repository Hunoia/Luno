package hunoia.luno.runtime.surface

import android.content.Context

/**
 * 搜索小窗的分发单例（同 [ClipFloatDispatch] / [ActivityFloatDispatch] 口径）：
 * `:feature:quickaccess` 不依赖 `:feature:floatball`，经 [App] 安装的实现转发到
 * FloatBallService。返回值 = 是否成功发起；false 时调用方回退普通打开。
 */
object WebCardDispatch {

    @Volatile
    var show: ((context: Context, url: String) -> Boolean)? = null
}
