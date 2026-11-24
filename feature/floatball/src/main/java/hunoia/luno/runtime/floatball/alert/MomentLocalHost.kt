package hunoia.luno.runtime.floatball.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.model.AlertPlaybackPolicy
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.runtime.floatball.alert.engine.AlertPlanFactory
import hunoia.luno.runtime.floatball.alert.engine.toPlayPlan
import hunoia.luno.ui.theme.MotionTuning
import hunoia.luno.ui.theme.toAlertFamily
import kotlin.random.Random

/**
 * 悬浮球服务未运行时的“时刻动效”兜底宿主（同进程一次性舞台）。
 *
 * 只画舞台层：锁屏 / 触钮动效里如果有胶囊层会被跳过——调用进程没有胶囊窗。
 * 并发策略仍按预设自己的 `playbackPolicy`；待播上限 1 条。锁屏回调在“可以锁屏”时
 * 只调一次；锁屏态窗口保持黑屏，等 `ACTION_SCREEN_OFF`（或 1.5s 兜底）再拆。
 */
internal object MomentLocalHost {

    private class AnchorHolder(@Volatile var anchor: AlertAnchor)

    private class Pending(
        val plan: AlertPlayPlan,
        val anchorProvider: () -> AlertAnchor,
        val onFinished: (() -> Unit)?,
    )

    private var stage: AlertStageWindow? = null
    private var pending: Pending? = null
    private var onFinished: (() -> Unit)? = null
    private var touchAnchor: AnchorHolder? = null

    private val handler = Handler(Looper.getMainLooper())
    private var holdReceiver: BroadcastReceiver? = null
    private var holdRelease: Runnable? = null
    private var holdContext: Context? = null

    private const val LockHoldMs = 1_500L
    private const val LockLogTag = "MomentLocalHost"

    fun playScreenOff(context: Context, onFinished: () -> Unit) {
        val action = TuneConfig.latestAction().floatBall
        val config = action.lockAlert
        val actionFallbackId = action.actionAlert.takeIf { it.enabled }
            ?.presetFor("accessibility.lockScreen", null)
        val fallback = config.presetId.isNullOrBlank() && actionFallbackId != null
        val presetId = config.presetId ?: actionFallbackId
        if ((!config.enabled && !fallback) || presetId.isNullOrBlank()) {
            Log.i(LockLogTag, "local lock skipped enabled=${config.enabled} preset=$presetId")
            return onFinished()
        }
        val intensityPercent = if (fallback) {
            action.actionAlert.intensityPercent
        } else {
            config.intensityPercent
        }
        val speedPercent = if (fallback) {
            action.actionAlert.speedPercent
        } else {
            config.speedPercent
        }
        val (plan, provider) = build(context, presetId, intensityPercent, speedPercent) {
            AlertAnchor(
                centerX = DensityProvider.screenWidthPx / 2f,
                centerY = DensityProvider.screenHeightPx / 2f,
                capsulePx = 0,
            )
        } ?: run {
            Log.i(LockLogTag, "local lock unavailable preset=$presetId")
            return onFinished()
        }
        // 锁屏优先：不排在到达动效后面；结束点由 closing 的压黑时刻决定。
        start(
            context = context,
            plan = plan.copy(closing = true, playbackPolicy = AlertPlaybackPolicy.Replace),
            provider = provider,
            finished = onFinished,
        )
    }

    fun playTouch(context: Context, buttonId: String, x: Float, y: Float) {
        val config = TuneConfig.latestAction().floatBall.buttonAlert
        if (!config.enabled) return
        val presetId = config.presetFor(buttonId) ?: return
        val holder = AnchorHolder(AlertAnchor(centerX = x, centerY = y, capsulePx = 0))
        touchAnchor = holder
        val (plan, provider) = build(context, presetId, config.intensityPercent, config.speedPercent) {
            holder.anchor
        } ?: return
        start(context, plan, provider, null)
    }

    fun moveTouch(x: Float, y: Float) {
        touchAnchor?.anchor = AlertAnchor(centerX = x, centerY = y, capsulePx = 0)
    }

    private fun build(
        context: Context,
        presetId: String,
        intensityPercent: Int,
        speedPercent: Int,
        provider: () -> AlertAnchor,
    ): Pair<AlertPlayPlan, () -> AlertAnchor>? {
        val appContext = context.applicationContext
        if (!appContext.canDrawOverlays()) return null
        val capsule = TuneConfig.latestAction().floatBall.notificationCapsule
        val preset = AlertPlanFactory.presets(capsule.alertLibrary)[presetId] ?: return null
        val profile = MotionTuning.profile
        val plan = AlertPlanFactory.buildFromPreset(
            preset = preset,
            presetId = presetId,
            library = capsule.alertLibrary,
            intensityScale = intensityPercent / 100f,
            speedScale = speedPercent / 100f,
            tierFor = { kind -> profile.speedOf(kind.toAlertFamily()).factor },
            seed = Random.nextInt(),
        ).toPlayPlan()
        if (plan.stageLayers.isEmpty()) return null
        return plan to provider
    }

    private fun start(
        context: Context,
        plan: AlertPlayPlan,
        provider: () -> AlertAnchor,
        finished: (() -> Unit)?,
    ) {
        val appContext = context.applicationContext
        val host = stage ?: AlertStageWindow(appContext).also { stage = it }
        if (host.isShowing) {
            when (plan.playbackPolicy) {
                AlertPlaybackPolicy.Skip -> {
                    finished?.invoke()
                    return
                }
                AlertPlaybackPolicy.Queue -> {
                    if (pending != null) {
                        finished?.invoke()
                        return
                    }
                    pending = Pending(plan, provider, finished)
                    return
                }
                AlertPlaybackPolicy.Replace -> {
                    host.cancel()
                    onFinished?.invoke()
                    onFinished = null
                    pending = null
                }
            }
        }
        onFinished = finished
        val shown = host.show(
            plan = plan,
            anchor = provider(),
            colorSpec = TuneConfig.latestAction().floatBall.notificationCapsule.alert.color,
            secondColor = ColorSpec.Unset,
            onFinished = {
                val done = onFinished
                onFinished = null
                done?.invoke()
                if (plan.closing) {
                    // 回调 = 已全黑，可锁屏；窗口继续压黑，等息屏广播再拆。
                    holdUntilScreenOff(appContext)
                    return@show
                }
                val next = pending ?: return@show
                pending = null
                start(
                    appContext,
                    next.plan,
                    next.anchorProvider,
                    next.onFinished,
                )
            },
            anchorProvider = provider,
        )
        if (!shown) {
            onFinished = null
            finished?.invoke()
        }
    }

    /**
     * 锁屏黑屏保持：息屏广播一到就拆窗；广播丢失 / 锁屏失败由 1.5s 兜底拆除，
     * 绝不让一个全屏黑窗盖住锁屏或桌面。
     */
    private fun holdUntilScreenOff(context: Context) {
        releaseHold()
        holdContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                releaseHold()
            }
        }
        runCatching {
            context.applicationContext.registerReceiver(
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
            )
        }.onSuccess {
            holdReceiver = receiver
        }
        val release = Runnable { releaseHold() }
        holdRelease = release
        handler.postDelayed(release, LockHoldMs)
    }

    private fun releaseHold() {
        holdRelease?.let(handler::removeCallbacks)
        holdRelease = null
        val context = holdContext
        holdContext = null
        holdReceiver?.let { receiver ->
            runCatching { context?.unregisterReceiver(receiver) }
        }
        holdReceiver = null
        stage?.cancel()
    }
}