package hunoia.luno.domain.system

import android.content.Context
import hunoia.luno.config.ConfigProvider
import hunoia.luno.runtime.floatball.FloatBallService

/** 悬浮球服务启停门面：UI 不直接依赖 runtime 的服务实现。 */
object FloatBallUseCase {

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        if (enabled) FloatBallService.start(appContext) else FloatBallService.stop(appContext)
    }

    /** 服务已在跑时的重评 nudge（悬浮窗授权回来用）：onStartCommand 里重评窗口显隐。 */
    fun refresh(context: Context) {
        FloatBallService.start(context.applicationContext)
    }

    /** 总闸打开后按落盘配置恢复共同宿主：球 / 通知 / 剪贴板任一开启才需要服务。 */
    suspend fun restoreIfEnabled(context: Context) {
        val appContext = context.applicationContext
        val initial = ConfigProvider.getInitialSettings()
        if (!initial.gestureEnabled) return
        val ball = ConfigProvider.getActionSettings().floatBall
        if (ball.enabled || ball.notificationCapsule.enabled || ball.clipboardBall.enabled) {
            FloatBallService.start(appContext)
        }
    }
}
