package hunoia.luno.domain.system

import android.content.Context
import hunoia.luno.facade.ClipFloatFacade
import hunoia.luno.runtime.floatball.FloatBallService

/** `ClipFloatFacade` 默认实现：委托常驻浮球宿主显示剪贴板悬浮窗。 */
object ClipFloatUseCase : ClipFloatFacade {

    override fun show(context: Context, entryId: String) {
        FloatBallService.showClipFloat(context.applicationContext, entryId)
    }
}
