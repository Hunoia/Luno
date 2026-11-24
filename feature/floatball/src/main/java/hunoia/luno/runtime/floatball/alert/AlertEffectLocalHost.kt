package hunoia.luno.runtime.floatball.alert

import android.content.Context
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.core.effect.AlertConcurrency
import hunoia.luno.core.effect.AlertEffectHandle
import hunoia.luno.core.effect.AlertEffectRequest
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.runtime.floatball.alert.engine.AlertPlanFactory
import hunoia.luno.runtime.floatball.alert.engine.toPlayPlan
import hunoia.luno.ui.theme.MotionTuning
import hunoia.luno.ui.theme.toMotionFamily
import kotlin.random.Random

/**
 * 调用进程托管的轻量效果宿主（architecture §1.5 第 7 条登记例外）。
 *
 * 服务未运行时，外部调用（`:app` 进程）用本宿主播放一次效果窗：全屏舞台 + 屏缘 / 球锚点 /
 * 全屏着色。**只支持舞台层**；胶囊宿主层需要胶囊窗，调用进程没有，直接跳过并记日志。
 * 窗口一次性、播完即摘，不驻留、不新增常驻进程。
 */
internal object AlertEffectLocalHost {

    private var stage: AlertStageWindow? = null

    /** QUEUE 的待播请求（上限 1）；播完当前效果后自动起播。 */
    private var pending: AlertEffectRequest? = null

    fun play(context: Context, request: AlertEffectRequest): AlertEffectHandle? {
        val appContext = context.applicationContext
        if (!appContext.canDrawOverlays()) return null
        val settings = TuneConfig.latestAction().floatBall.notificationCapsule
        val profile = MotionTuning.profile
        val renderPlan = AlertPlanFactory.buildFromRequest(
            request = request,
            library = settings.alertLibrary,
            appPackage = request.content?.iconPackage,
            tierFor = { category -> profile.speedOf(category.toMotionFamily()).factor },
            seed = request.seed ?: Random.nextInt(),
        )
        val plan = renderPlan.toPlayPlan()
        if (plan.stageLayers.isEmpty()) return null

        val host = stage ?: AlertStageWindow(appContext).also { stage = it }
        if (host.isShowing) {
            when (request.concurrency) {
                AlertConcurrency.Reject -> return null
                AlertConcurrency.Queue -> {
                    if (pending != null) return null
                    pending = request
                    return LocalHandle(request.id) {
                        if (pending?.id == request.id) pending = null
                    }
                }
                AlertConcurrency.Replace -> {
                    host.cancel()
                    pending = null
                }
            }
        }
        val anchor = request.anchor?.let { rect ->
            AlertAnchor(
                centerX = (rect.left + rect.right) / 2f,
                centerY = (rect.top + rect.bottom) / 2f,
                capsulePx = (rect.bottom - rect.top).toInt().coerceAtLeast(0),
            )
        } ?: AlertAnchor(
            centerX = DensityProvider.screenWidthPx / 2f,
            centerY = DensityProvider.screenHeightPx / 2f,
            capsulePx = 0,
        )
        val shown = host.show(
            plan = plan,
            anchor = anchor,
            colorSpec = settings.alert.color,
            onFinished = {
                val next = pending ?: return@show
                pending = null
                play(appContext, next)
            },
        )
        if (!shown) return null
        return LocalHandle(request.id) {
            pending = null
            host.cancel()
        }
    }
}

/** 本地句柄：取消 = 立即摘窗（幂等）。 */
private class LocalHandle(
    override val requestId: String,
    private val onCancel: () -> Unit,
) : AlertEffectHandle {
    override fun cancel() = onCancel()
}
