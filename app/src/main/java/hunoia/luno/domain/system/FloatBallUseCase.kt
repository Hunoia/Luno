package hunoia.luno.domain.system

import android.content.Context
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AlertCategory
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

    /** 试播一次通知胶囊的到达提示动效（设置页入口，见 [FloatBallService.previewAlert]）。 */
    fun previewAlert(context: Context, category: AlertCategory? = null) {
        FloatBallService.previewAlert(context.applicationContext, category)
    }

    /**
     * 总闸打开后按落盘配置恢复共同宿主：球 / 通知胶囊 / 直执行规则任一需要才起服务。
     *
     * 直执行规则也要算：它的输入（通知监听、剪贴板轮询、情景事件）与执行都在服务里，
     * 只在「球 / 胶囊开关」上判会让启用的规则静默失效。
     */
    suspend fun restoreIfEnabled(context: Context) {
        val appContext = context.applicationContext
        val initial = ConfigProvider.getInitialSettings()
        if (!initial.gestureEnabled) return
        val settings = ConfigProvider.getActionSettings()
        val ball = settings.floatBall
        val recognition = settings.recognition
        val readsAnySource = recognition.notificationReadEnabled ||
            recognition.clipboardReadEnabled ||
            recognition.sceneEnabled
        val hasDirectRule = readsAnySource && recognition.rules.any {
            it.enabled && it.isValid && it.effectiveDisplay == hunoia.luno.config.model.RecognitionDisplay.Direct
        }
        if (ball.enabled || ball.notificationCapsule.enabled || hasDirectRule) {
            FloatBallService.start(appContext)
        }
    }
}
