package hunoia.luno.runtime.floatball

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.getSystemService
import hunoia.luno.R
import hunoia.luno.action.model.GRID_PANEL_TYPE_ID
import hunoia.luno.action.model.StoredAction
import hunoia.luno.bridge.system.ClipboardContent
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.readClipboardContentDetailed
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AppLaunchSource
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.core.TouchButtonSideEvent
import hunoia.luno.config.model.LinkDispatchSettings
import hunoia.luno.config.model.LinkTapMode
import hunoia.luno.domain.clipboard.ClipboardCenter
import hunoia.luno.link.LinkDispatcher
import hunoia.luno.link.LinkUrls
import hunoia.luno.ui.link.LinkSelectionActivity
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.runtime.automation.AutomationNotificationListener
import hunoia.luno.runtime.automation.AutomationPulseHub
import hunoia.luno.runtime.automation.NotificationContentIntents
import hunoia.luno.runtime.condition.NotificationPulse
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.service.SideGestureService
import hunoia.luno.shizuku.ShizukuClipboard
import hunoia.luno.shizuku.ShizukuManager
import hunoia.luno.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import hunoia.luno.core.LOG_TAG

/**
 * 悬浮球宿主，跑在独立 `:service` 进程（前台通知常驻）：球显示不依赖无障碍开关，
 * 不能挂到 `SideGestureService` 的窗口上。动作经同进程静态弱引用直连 `forwardBallTap`；
 * 2038 窗口拿不到 insets 推播，键盘让位靠轮询 IME 底边（[IME_POLL_MS]）。
 */
class FloatBallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null
    private var imeJob: Job? = null

    private var settings: FloatBallSettings = FloatBallSettings()

    /** 应用手势总闸（combine 出来的 gestureEnabled，总闸关则球与分身都不出）。 */
    private var gestureEnabledSnapshot = true

    private lateinit var ballWindow: FloatBallWindow
    private lateinit var mirrorWindow: FloatBallMirrorWindow
    private lateinit var capsuleWindow: NotificationCapsuleWindow
    private lateinit var clipboardBallWindow: ClipboardBallWindow
    private var broadcastObserver: BroadcastObserver? = null

    /** 球是否已真正挂上窗口。false = 未授权或被系统拒绝。 */
    private var ballVisible = false

    /** 胶囊当前是否在屏（含进场/退场动画期间）。 */
    private var capsuleShowing = false
    private var capsuleTimerJob: Job? = null

    /** 胶囊延迟上屏的任务：新通知到达时取消上一拍，避免旧通知延迟上屏盖新的。 */
    private var capsuleShowJob: Job? = null

    /** 最近一条弹出过的通知：点击胶囊时发 contentIntent / 回退启动应用。 */
    private var lastPulse: NotificationPulse? = null

    /**
     * 用户主动滑掉的 key（插入顺序 LRU + 时间窗）：刚滑掉的通知在窗口期内即使原地更新
     * 也不再弹出来（否则「滑掉 -> 更新 -> 又弹」很恼人）。
     */
    private val dismissedCapsuleKeys = LinkedHashMap<String, Long>()

    /** 剪贴板球当前是否在屏（含动画期间）与当前展示内容（点击分享/同内容重置计时）。 */
    private var clipboardBallShowing = false
    private var clipboardTimerJob: Job? = null
    private var clipboardBallContent: ClipboardContent? = null
    private var clipboardListenerRegistered = false

    /** 钉住期间又来了新内容：胶囊显示圆点，取消钉住时切到最新内容。 */
    private var clipboardPending = false

    /** 链接分发配置（随 actionSettings 流更新）：胶囊识别链接、点击直达都读它。 */
    private var linkDispatch: LinkDispatchSettings = LinkDispatchSettings()

    /** 锁屏/临时隐藏等被动收起时暂存的钉住内容：解锁/恢复后原样弹回。 */
    private var pinnedClipboardContent: ClipboardContent? = null

    /**
     * 轮询兜底：HyperOS 不向后台应用投递 OnPrimaryClipChangedListener（实测复制零回调），
     * 亮屏期间每 2s 读一次剪贴板对比内容变化；读依赖无障碍豁免，读不到就永远不弹。
     */
    private var clipboardPollJob: Job? = null
    private var lastSeenClip: ClipboardContent? = null

    /** 面板类浮层打开计数（>0 即隐藏球）。 */
    private var panelCount = 0

    /** 临时隐藏截止时间（uptimeMillis），0 = 无。只作用窗口，不碰 `enabled` 存量。 */
    private var tempHideUntilMs = 0L
    private var tempHideJob: Job? = null

    private val powerManager by lazy { getSystemService<PowerManager>() }
    private val keyguardManager by lazy { getSystemService<KeyguardManager>() }
    private val windowManager by lazy { getSystemService<android.view.WindowManager>() }

    // ---- 跟随持机手（试验） ----

    private val touchSideSubscriber: (TouchButtonSideEvent) -> Unit = { event ->
        onTouchSide(event.leftSide)
    }

    override fun onCreate() {
        super.onCreate()
        ballWindow = FloatBallWindow(this, onImeLiftChanged = { lift ->
            mirrorWindow.updateImeLift(lift)
        }).apply {
            onTap = { onBallTapped() }
            onDragFinished = { x, y -> persistBallPosition(x, y) }
        }
        mirrorWindow = FloatBallMirrorWindow(this).apply {
            onPressed = { onMirrorPressed() }
            onTapped = { onMirrorTapped() }
            onDragDeltas = { dx, dy -> onMirrorDragDeltas(dx, dy) }
            onDragReleased = { onMirrorDragReleased() }
        }
        capsuleWindow = NotificationCapsuleWindow(this).apply {
            onTapped = { onCapsuleTapped() }
            onSwipedAway = { onCapsuleSwipedAway() }
            // 按住/拖动期间暂停自动收起；展开正文期间也暂停（否则还没读完就消失）。
            onPressStateChanged = { pressed ->
                if (pressed) {
                    capsuleTimerJob?.cancel()
                    capsuleTimerJob = null
                } else if (capsuleShowing && !this@FloatBallService.capsuleWindow.isExpanded()) {
                    scheduleCapsuleHide(settings.notificationCapsule.autoHideMs)
                }
            }
            onExpandedChanged = { expanded ->
                if (expanded) {
                    capsuleTimerJob?.cancel()
                    capsuleTimerJob = null
                } else if (capsuleShowing) {
                    scheduleCapsuleHide(settings.notificationCapsule.autoHideMs)
                }
            }
        }
        clipboardBallWindow = ClipboardBallWindow(this).apply {
            onTapped = { onClipboardBallTapped() }
            onDragReleased = { persistClipboardBallPosition() }
            // 按住/拖动/展开/钉住期间暂停自动收起，松手/收起后重新计时。
            onPressStateChanged = { pressed ->
                if (pressed) pauseClipboardTimer() else resumeClipboardTimerIfIdle()
            }
            onExpandedChanged = { expanded ->
                if (expanded) pauseClipboardTimer() else resumeClipboardTimerIfIdle()
            }
            onPinChanged = { pinned -> onClipboardBallPinChanged(pinned) }
        }
        startInForeground()

        broadcastObserver = BroadcastObserver(
            context = this,
            onScreenOff = {
                // 息屏停掉 IME 兜底轮询：屏幕不可见时键盘让位无意义，
                // 别让前台服务以 2Hz 空转唤醒（亮屏解锁由 applyLockState 补启）。
                imeJob?.cancel()
                imeJob = null
                clipboardPollJob?.cancel()
                clipboardPollJob = null
                ballWindow.setHidden(true)
                mirrorWindow.setHidden(true)
                hideCapsule()
                hideClipboardBall(preservePin = true)
                // 息屏期间不读剪贴板：重置基线，解锁后由 seedClipboardBaseline 重新对齐。
                lastSeenClip = null
            },
            onScreenOn = ::applyLockState,
            onUserPresent = ::applyLockState,
        ).also { it.register() }

        observeSettings()
        ensureImeObserver()
        observePanels()
        Events.subscribe(TouchButtonSideEvent::class, touchSideSubscriber)
        // 通知胶囊：与监听器同进程静态直连；勿扰变化即收当前胶囊。
        AutomationPulseHub.addNotificationObserver(notificationObserver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 还有功能要宿主就不退（球关了胶囊/剪贴板球可能还开着）；真全关走
            // settings 收集处的自停，那边有三开关的完整状态。
            if (settings.enabled || settings.notificationCapsule.enabled ||
                settings.clipboardBall.enabled
            ) {
                refreshWindows()
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }
        // 显式 nudge 的重评入口（悬浮窗授权回来等）：服务可能已在跑但停在无权限/
        // 总闸关的隐藏态。首次启动时 settings 尚未流到，评一次默认值无害，
        // 真实 collect 到达会再刷。
        refreshWindows()
        return START_STICKY
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        imeJob?.cancel()
        broadcastObserver?.unregister()
        broadcastObserver = null
        Events.unsubscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.unsubscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
        Events.unsubscribe(TouchButtonSideEvent::class, touchSideSubscriber)
        Events.unsubscribe(FloatBallTempHideEvent::class, tempHideSubscriber)
        AutomationPulseHub.removeNotificationObserver(notificationObserver)
        unregisterClipboardListener()
        tempHideJob?.cancel()
        tempHideJob = null
        capsuleTimerJob?.cancel()
        capsuleTimerJob = null
        capsuleShowJob?.cancel()
        capsuleShowJob = null
        clipboardTimerJob?.cancel()
        clipboardTimerJob = null
        clipboardPollJob?.cancel()
        clipboardPollJob = null
        tempHideUntilMs = 0L
        panelCount = 0
        ballWindow.detach()
        mirrorWindow.detach()
        capsuleWindow.detachNow()
        clipboardBallWindow.detachNow()
        scope.cancel()
        super.onDestroy()
    }

    // ---- 配置 ----

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            combine(
                ConfigProvider.actionSettings,
                ConfigProvider.initialSettings,
            ) { action, initial -> FloatBallRuntimeConfig(action, initial.gestureEnabled) }
                .collect { config ->
                    val newSettings = config.action.floatBall
                    // 临时隐藏期间球被手动/规则打开（enabled false→true）：显示意图优先，作废本轮隐藏
                    if (newSettings.enabled && !settings.enabled) cancelTempHide()
                    settings = newSettings
                    linkDispatch = config.action.linkDispatch
                    gestureEnabledSnapshot = config.gestureEnabled
                    ballWindow.updateSettings(config.action.floatBall)
                    mirrorWindow.updateSettings(config.action.floatBall)
                    capsuleWindow.updateSettings(config.action.floatBall)
                    clipboardBallWindow.updateSettings(config.action.floatBall)
                    applyClipboardListener()
                    refreshWindows()
                    // 自停不变式：悬浮球、通知胶囊、剪贴板球全关，前台服务没有存在意义。
                    if (!settings.enabled && !settings.notificationCapsule.enabled &&
                        !settings.clipboardBall.enabled
                    ) {
                        stopSelf()
                    }
                }
        }
    }

    /** 统一的窗口挂/摘入口：shouldShow = 总闸 && 球开关 && 悬浮窗权限 && 非临时隐藏 && 面板计数==0。显隐过渡全部带动画。 */
    private fun refreshWindows() {
        // 瞬态成员的重评放最前：主球侧下面有早退分支（面板开合/临时隐藏都从这走），
        // 放尾部会漏掉「在屏但已不被允许」的收起路径。
        refreshCapsule()
        refreshClipboardBall()
        // 锁屏等被动收起前钉住的剪贴板球，条件恢复后弹回（含 pin 状态）。
        restorePinnedClipboardIfNeeded()
        // 门控（权限/总闸）可能刚变化，轮询按需启停（幂等，只查 job 状态）。
        ensureClipboardPoller()
        val enabled = settings.enabled && canDrawOverlays() && gestureEnabledSnapshot
        val panelHidden = panelCount > 0
        val tempHidden = tempHideUntilMs > SystemClock.uptimeMillis()
        val shouldShow = enabled && !panelHidden && !tempHidden
        if (!shouldShow) {
            if (ballVisible) {
                if (enabled) {
                    // 面板/临时隐藏是短态：软隐藏（窗口保留、不可见不可点），
                    // 免掉整拆整建（removeView→新 ComposeView→首帧组合→addView），
                    // 面板开合频繁（音量刮擦每次都发面板事件）时差距最明显。
                    ballWindow.setPanelHidden(true)
                    mirrorWindow.setPanelHidden(true)
                } else {
                    // 总闸/开关/权限关：长态不出，淡出后摘窗回收。
                    ballVisible = false
                    ballWindow.detach(animated = true)
                    mirrorWindow.detach(animated = true)
                }
            }
            return
        }
        if (!ballVisible) {
            ballVisible = ballWindow.attachAnimated()
            if (ballVisible) {
                ballWindow.setHidden(isScreenLocked())
                if (settings.mirrorBall) mirrorWindow.attach(animated = true)
                mirrorWindow.setHidden(isScreenLocked())
                // 新窗口的 insets 推播能力未知：兜底轮询按需补启（息屏期间被停过也一样）。
                ensureImeObserver()
            }
        } else {
            // 软隐藏 → 恢复：窗口还在，直接亮回（带回场动画），不走重建。
            ballWindow.setPanelHidden(false)
            mirrorWindow.setPanelHidden(false)
            if (settings.mirrorBall) {
                if (!mirrorWindow.attach(animated = true)) return
                mirrorWindow.setHidden(isScreenLocked())
                mirrorWindow.snapToMirror()
            } else {
                mirrorWindow.detach()
            }
        }
    }

    // ---- 面板联动（面板打开即隐藏球） ----

    private val panelShownSubscriber: (PanelShownEvent) -> Unit = {
        panelCount++
        refreshWindows()
    }

    private val panelDismissedSubscriber: (PanelDismissedEvent) -> Unit = {
        if (panelCount > 0) panelCount--
        refreshWindows()
    }

    private fun observePanels() {
        Events.subscribe(PanelShownEvent::class, panelShownSubscriber)
        Events.subscribe(PanelDismissedEvent::class, panelDismissedSubscriber)
        Events.subscribe(FloatBallTempHideEvent::class, tempHideSubscriber)
    }

    // ---- 临时隐藏（显隐动作的 hideForMs 模式，只摘窗口） ----

    private val tempHideSubscriber: (FloatBallTempHideEvent) -> Unit = { event ->
        scheduleTempHide(event.durationMs)
    }

    /**
     * 只摘窗口不碰 `enabled` 存量：恢复定时器在内存里，进程被杀（应用更新/系统回收）即失效，
     * 球按落盘 enabled 照常恢复。重复隐藏取更晚的截止时间。
     */
    private fun scheduleTempHide(durationMs: Long) {
        val deadline = SystemClock.uptimeMillis() + durationMs
        if (deadline <= tempHideUntilMs) return
        tempHideUntilMs = deadline
        tempHideJob?.cancel()
        tempHideJob = scope.launch {
            delay(deadline - SystemClock.uptimeMillis())
            tempHideUntilMs = 0L
            // 隐藏期间复制的内容不补弹：恢复前先把基线对齐到当前剪贴板。
            seedClipboardBaseline()
            refreshWindows()
        }
        refreshWindows()
    }

    private fun cancelTempHide() {
        if (tempHideUntilMs == 0L) return
        tempHideJob?.cancel()
        tempHideJob = null
        tempHideUntilMs = 0L
        refreshWindows()
    }

    // ---- 通知胶囊 ----

    private val notificationObserver: (NotificationPulse) -> Unit = { pulse ->
        // 监听器在 binder 线程回调：切主线程再动窗口。
        scope.launch { onNotificationArrived(pulse) }
    }

    /**
     * 瞬态浮层共用门控：悬浮窗权限 / 总闸 / 非锁屏。
     * 面板、勿扰、临时隐藏（`internal.floatBallVisibility` 的 hideForMs）都不参与：
     * 那些是主悬浮球的语义，套到几秒就消失的胶囊/剪贴板球上只会误伤（还会丢 pin）。
     */
    private fun transientOverlayAllowed(): Boolean =
        canDrawOverlays() &&
            gestureEnabledSnapshot &&
            !isScreenLocked()

    /** 胶囊可见条件：与球同套联动 + 自身开关。 */
    private fun capsuleAllowed(): Boolean =
        settings.notificationCapsule.enabled && transientOverlayAllowed()

    private fun onNotificationArrived(pulse: NotificationPulse) {
        if (!capsuleAllowed()) return
        if (pulse.packageName in settings.notificationCapsule.blocklist) return
        if (isCapsuleDismissed(pulse.key)) {
            Log.i(TAG, "capsule skip dismissed pkg=${pulse.packageName} key=${pulse.key}")
            return
        }
        // 只弹「通知栏里真的能看到」的：常驻/前台服务/分组汇总（正文往往是「1」）/仅本地
        // 都不是用户会去点的普通通知。自动化侧按条件过滤 ongoing，胶囊这里再按 flags 收紧。
        val blockedFlags = Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE or
            Notification.FLAG_GROUP_SUMMARY or
            Notification.FLAG_LOCAL_ONLY
        val shadeVisible = !pulse.ongoing &&
            pulse.clearable &&
            (pulse.flags and blockedFlags) == 0 &&
            pulse.importance != NotificationManager.IMPORTANCE_NONE
        if (!shadeVisible) {
            // 通知正文是隐私（验证码/私聊）：日志只留判定所需的元数据，不落 title/text。
            Log.i(TAG, "capsule skip pkg=${pulse.packageName} flags=0x${pulse.flags.toString(16)} " +
                "ongoing=${pulse.ongoing} clearable=${pulse.clearable} imp=${pulse.importance}")
            return
        }
        // 延迟一拍再确认仍在通知栏：一次性/立即撤回的通知（复制、分享后的系统提示等）不弹。
        capsuleShowJob?.cancel()
        capsuleShowJob = scope.launch {
            delay(CAPSULE_SETTLE_MS)
            if (!capsuleAllowed()) return@launch
            if (!AutomationNotificationListener.isPosted(pulse.key)) {
                Log.i(TAG, "capsule skip gone pkg=${pulse.packageName} key=${pulse.key}")
                return@launch
            }
            Log.i(TAG, "capsule show pkg=${pulse.packageName} flags=0x${pulse.flags.toString(16)} " +
                "imp=${pulse.importance}")
            if (!capsuleWindow.show(pulse)) return@launch
            lastPulse = pulse
            capsuleShowing = true
            scheduleCapsuleHide(settings.notificationCapsule.autoHideMs)
        }
    }

    /** 自动收起计时：新通知覆盖重计时（show 前服务层重算 allowed，不允许直接吞掉）。 */
    private fun scheduleCapsuleHide(delayMs: Long) {
        capsuleTimerJob?.cancel()
        capsuleTimerJob = scope.launch {
            delay(
                delayMs.coerceIn(
                    FloatBallDefaults.CapsuleAutoHideMsMin,
                    FloatBallDefaults.CapsuleAutoHideMsMax,
                ),
            )
            hideCapsule()
        }
    }

    private fun hideCapsule() {
        capsuleShowJob?.cancel()
        capsuleShowJob = null
        capsuleTimerJob?.cancel()
        capsuleTimerJob = null
        if (!capsuleShowing) return
        capsuleShowing = false
        capsuleWindow.hide()
    }

    /** 面板/临时隐藏/锁屏等联动入口共用：在屏但不允许了就收起（收起后不补播旧通知）。 */
    private fun refreshCapsule() {
        if (capsuleShowing && !capsuleAllowed()) hideCapsule()
    }

    /** 用户下滑关闭：记抑制 key（同 key 更新窗口期内不再弹），再走正常收起。 */
    private fun onCapsuleSwipedAway() {
        markCapsuleDismissed(lastPulse?.key)
        hideCapsule()
    }

    private fun markCapsuleDismissed(key: String?) {
        if (key == null) return
        if (!settings.notificationCapsule.dismissSuppressEnabled) {
            dismissedCapsuleKeys.remove(key)
            return
        }
        dismissedCapsuleKeys.remove(key)
        dismissedCapsuleKeys[key] = SystemClock.uptimeMillis()
        while (dismissedCapsuleKeys.size > CAPSULE_DISMISSED_KEYS_MAX) {
            val oldest = dismissedCapsuleKeys.keys.firstOrNull() ?: break
            dismissedCapsuleKeys.remove(oldest)
        }
    }

    private fun isCapsuleDismissed(key: String?): Boolean {
        if (key == null) return false
        val capsule = settings.notificationCapsule
        if (!capsule.dismissSuppressEnabled) return false
        val at = dismissedCapsuleKeys[key] ?: return false
        val windowMs = capsule.dismissSuppressMs.coerceIn(
            FloatBallDefaults.CapsuleDismissSuppressMsMin,
            FloatBallDefaults.CapsuleDismissSuppressMsMax,
        )
        if (SystemClock.uptimeMillis() - at < windowMs) return true
        dismissedCapsuleKeys.remove(key)
        return false
    }

    private fun onCapsuleTapped() {
        val pulse = lastPulse ?: return
        hideCapsule()
        // contentIntent 是具体会话/页面的 PendingIntent（比裸启动应用准）；
        // 无 intent 或后台启动被 ROM 拦时回退为按包名启动应用（与手势同链路）。
        scope.launch {
            val contentIntent = NotificationContentIntents.get(pulse.key)
            if (contentIntent != null) {
                val sent = runCatching { contentIntent.send() }.isSuccess
                if (sent) return@launch
            }
            runCatching {
                QuickLaunchFacade.launchAppDirect(
                    context = this@FloatBallService,
                    packageName = pulse.packageName,
                    className = "",
                    source = AppLaunchSource.UNKNOWN,
                )
            }.onFailure { Log.w(TAG, "capsule app launch failed: ${it.message}") }
        }
    }

    // ---- 剪贴板球 ----

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // 读剪贴板可能走 Shizuku/IO：回调线程只做转发。
        scope.launch { onClipboardChanged() }
    }

    /** 监听器随开关注册/注销（注册有旗标防重复；关闭即收在屏的球）。 */
    private fun applyClipboardListener() {
        val manager = getSystemService<ClipboardManager>()
        if (settings.clipboardBall.enabled && !clipboardListenerRegistered) {
            // 刚开启：清空基线，首次读取只对齐基线不补弹（开关前就有的旧内容不弹）。
            lastSeenClip = null
            if (manager != null) {
                runCatching { manager.addPrimaryClipChangedListener(clipboardListener) }
                    .onFailure { Log.w(TAG, "register clipboard listener failed: ${it.message}") }
                clipboardListenerRegistered = true
                Log.i(TAG, "clipboard listener registered")
            }
        } else if (!settings.clipboardBall.enabled && clipboardListenerRegistered) {
            unregisterClipboardListener()
            // 功能关闭：暂存的钉住内容也一并作废，避免下次开启时意外弹回。
            pinnedClipboardContent = null
            hideClipboardBall()
        }
        ensureClipboardPoller()
    }

    private fun unregisterClipboardListener() {
        if (!clipboardListenerRegistered) return
        runCatching {
            getSystemService<ClipboardManager>()?.removePrimaryClipChangedListener(clipboardListener)
        }
        clipboardListenerRegistered = false
    }

    /** 剪贴板球可见条件：与胶囊同套联动 + 自身开关。 */
    private fun clipboardBallAllowed(): Boolean =
        settings.clipboardBall.enabled && transientOverlayAllowed()

    private suspend fun onClipboardChanged() {
        if (!clipboardBallAllowed()) {
            // 诊断日志：区分「系统没回调」与「门控吞掉」。
            Log.i(
                TAG,
                "clip changed but not allowed: switch=${settings.clipboardBall.enabled} " +
                    "overlay=${canDrawOverlays()} gesture=$gestureEnabledSnapshot " +
                    "panel=$panelCount tempHide=${tempHideUntilMs > SystemClock.uptimeMillis()} " +
                    "lock=${isScreenLocked()} dnd=${AutomationPulseHub.dndActive}",
            )
            return
        }
        // 读取偶发失败（Shizuku binder 抖动）时短重试一次，减少漏检。
        val info = readClipForBall() ?: run {
            delay(CLIP_READ_RETRY_MS)
            readClipForBall()
        }
        if (info == null) {
            // 新 ROM 后台读取唯一路径是 Shizuku 桥；两者都拿不到就不弹。
            Log.i(TAG, "clipboard ball: read unavailable (shizuku off or empty clip)")
            return
        }
        if (onClipboardContent(info)) lastSeenClip = info
    }

    /** 读剪贴板（剪贴板球专用）：Shizuku 在跑走 shell 桥（新 ROM 唯一后台路径），否则应用内直读（仅前台时刻有效）。 */
    private suspend fun readClipForBall(): ClipboardContent? {
        val status = ShizukuManager.currentStatus()
        if (status.binderAlive && status.permissionGranted) {
            val viaShizuku = withContext(Dispatchers.IO) {
                runCatching { ShizukuClipboard.readContent() }
                    .onFailure { Log.w(TAG, "shizuku clipboard read failed: ${it::class.simpleName} ${it.message}") }
                    .getOrNull()
            }
            if (viaShizuku != null) return viaShizuku
        }
        // 无障碍/应用内直读也是 binder 调用：不在主线程读。
        return withContext(Dispatchers.IO) { readClipboardContentDetailed(this@FloatBallService) }
    }

    /** 变化处理共用入口（监听器即时路径 + 轮询兜底路径）。返回值 = 这次变化已处理（可推进已读基线）。 */
    private fun onClipboardContent(content: ClipboardContent): Boolean {
        if (!clipboardBallAllowed()) return false
        // 敏感文本 / 空文本 / 关掉图片开关时的图片：当作已处理，不弹也不参与 pending。
        if (content is ClipboardContent.Text && (content.sensitive || content.text.isBlank())) {
            return true
        }
        if (content is ClipboardContent.Image && !settings.clipboardBall.respondToImages) {
            return true
        }
        // 剪贴板页面部件的历史捕获时机（另一处是部件打开时自读）：这里只记非敏感文本，
        // 图片与敏感文本不入历史（见 ClipboardCenter.record 的过滤）。
        ClipboardCenter.record(content)
        // 钉住期间冻结内容：只标记「有新内容」，取消钉住时再切到最新。
        if (clipboardBallWindow.isPinned()) {
            if (content != clipboardBallContent) {
                clipboardPending = true
                clipboardBallWindow.setPendingMarker(true)
            }
            return true
        }
        if (content == clipboardBallContent && clipboardBallShowing) {
            // 同内容重复复制：只重置计时，不重播进场。
            scheduleClipboardHide(settings.clipboardBall.autoHideMs)
            return true
        }
        clipboardBallContent = content
        if (!clipboardBallWindow.show(
                settings.clipboardBall.x,
                settings.clipboardBall.y,
                content,
                nextLink = linkInfoOf(content),
            )
        ) {
            // 挂窗被系统拒绝：不动 showing，下次复制再试。
            clipboardBallContent = null
            return true
        }
        clipboardBallShowing = true
        clipboardPending = false
        clipboardBallWindow.setPendingMarker(false)
        Log.i(TAG, "clipboard ball show (${contentLogLabel(content)})")
        scheduleClipboardHide(settings.clipboardBall.autoHideMs)
        return true
    }

    private fun contentLogLabel(content: ClipboardContent): String = when (content) {
        is ClipboardContent.Text -> "text, ${content.text.length} chars"
        is ClipboardContent.Image -> "image, ${content.mimeType}"
    }

    /**
     * 把基线对齐到当前剪贴板（不弹球）：解锁/解除勿扰时用，避免把门控期间复制的内容
     * 当成新内容补弹。只读一次，无内容/读不到就没基线（下次读取自然重新建立）。
     */
    private fun seedClipboardBaseline() {
        if (!settings.clipboardBall.enabled || isScreenLocked()) return
        scope.launch {
            val info = readClipForBall() ?: return@launch
            if (info is ClipboardContent.Text && info.text.isBlank()) return@launch
            lastSeenClip = info
            // 在屏的是钉住内容且基线已更新：给 pending 圆点，取消钉住时可切到最新。
            if (clipboardBallShowing &&
                clipboardBallWindow.isPinned() &&
                info != clipboardBallContent
            ) {
                clipboardPending = true
                clipboardBallWindow.setPendingMarker(true)
            }
        }
    }

    /** 轮询兜底按需启停：开关开 + 亮屏未锁 + 门控允许（权限/总闸/勿扰）才跑。 */
    private fun ensureClipboardPoller() {
        val shouldRun = settings.clipboardBall.enabled &&
            canDrawOverlays() &&
            gestureEnabledSnapshot &&
            !isScreenLocked()
        if (shouldRun && clipboardPollJob?.isActive != true) {
            clipboardPollJob?.cancel()
            clipboardPollJob = scope.launch {
                while (isActive) {
                    delay(CLIP_POLL_MS)
                    pollClipboard()
                }
            }
        } else if (!shouldRun) {
            clipboardPollJob?.cancel()
            clipboardPollJob = null
        }
    }

    /** 读一次剪贴板：首轮读到的存量只作基线不弹（避免启用瞬间弹旧内容），之后内容变了才弹。 */
    private suspend fun pollClipboard() {
        // 门控期间不读也不推进基线：否则「面板开着时复制」会被当成已看过的旧内容永久吞掉。
        if (!clipboardBallAllowed()) return
        val info = readClipForBall()
        if (info == null || (info is ClipboardContent.Text && info.text.isBlank())) {
            if (info == null && lastSeenClip == null && clipboardBallContent == null) {
                val s = ShizukuManager.currentStatus()
                Log.i(
                    TAG,
                    "clipboard poll: read unavailable (shizuku alive=${s.binderAlive} " +
                        "perm=${s.permissionGranted} or empty clip)",
                )
            }
            return
        }
        val previous = lastSeenClip
        if (previous == null) {
            // 首个成功读取只作基线（用户在服务启动前就存在的剪贴板不补弹）。
            lastSeenClip = info
            return
        }
        if (previous == info) return
        if (onClipboardContent(info)) lastSeenClip = info
    }

    private fun pauseClipboardTimer() {
        clipboardTimerJob?.cancel()
        clipboardTimerJob = null
    }

    private fun resumeClipboardTimerIfIdle() {
        if (!clipboardBallShowing) return
        if (clipboardBallWindow.isPinned() || clipboardBallWindow.isExpanded()) return
        scheduleClipboardHide(settings.clipboardBall.autoHideMs)
    }

    /** 长按 pin/取消 pin：钉住暂停计时；取消钉住时若有 pending 内容则切换并重新计时。 */
    private fun onClipboardBallPinChanged(pinned: Boolean) {
        if (pinned) {
            pauseClipboardTimer()
            return
        }
        if (clipboardPending) {
            clipboardPending = false
            clipboardBallWindow.setPendingMarker(false)
            val next = lastSeenClip
            if (next != null && next != clipboardBallContent) {
                clipboardBallContent = next
                clipboardBallWindow.show(
                    settings.clipboardBall.x,
                    settings.clipboardBall.y,
                    next,
                    nextLink = linkInfoOf(next),
                )
            }
        }
        resumeClipboardTimerIfIdle()
    }

    /** 自动收起计时：新复制覆盖重计时（show 前服务层重算 allowed，不允许直接吞掉）。 */
    private fun scheduleClipboardHide(delayMs: Long) {
        clipboardTimerJob?.cancel()
        clipboardTimerJob = scope.launch {
            delay(
                delayMs.coerceIn(
                    FloatBallDefaults.ClipboardBallAutoHideMsMin,
                    FloatBallDefaults.ClipboardBallAutoHideMsMax,
                ),
            )
            hideClipboardBall()
        }
    }

    private fun hideClipboardBall(preservePin: Boolean = false) {
        clipboardTimerJob?.cancel()
        clipboardTimerJob = null
        if (!clipboardBallShowing) return
        // 被动收起（锁屏/临时隐藏）前先记住钉住内容，恢复后原样弹回。
        if (preservePin && clipboardBallWindow.isPinned()) {
            pinnedClipboardContent = clipboardBallContent
        }
        clipboardBallShowing = false
        clipboardBallContent = null
        clipboardPending = false
        clipboardBallWindow.setPendingMarker(false)
        clipboardBallWindow.hide()
    }

    /** 面板/临时隐藏/锁屏/勿扰联动入口共用：在屏但不允许了就收起（收起后不补播旧内容）。 */
    private fun refreshClipboardBall() {
        if (clipboardBallShowing && !clipboardBallAllowed()) hideClipboardBall(preservePin = true)
    }

    /**
     * 被锁屏/隐藏打断的钉住球：条件恢复后原样弹回并保持 pin。
     * 期间剪贴板若有更新，用 pending 圆点提示，取消钉住时可切到最新。
     */
    private fun restorePinnedClipboardIfNeeded() {
        val content = pinnedClipboardContent ?: return
        if (clipboardBallShowing) return
        if (!clipboardBallAllowed()) return
        if (!clipboardBallWindow.show(
                settings.clipboardBall.x,
                settings.clipboardBall.y,
                content,
                initialPinned = true,
                nextLink = linkInfoOf(content),
            )
        ) {
            return
        }
        pinnedClipboardContent = null
        clipboardBallShowing = true
        clipboardBallContent = content
        clipboardPending = false
        clipboardBallWindow.setPendingMarker(false)
        pauseClipboardTimer()
        val latest = lastSeenClip
        if (latest != null && latest != content) {
            clipboardPending = true
            clipboardBallWindow.setPendingMarker(true)
        }
    }

    /** 认出文本里的链接（仅当链接分发开启；图片不参与）。 */
    private fun linksOf(content: ClipboardContent): List<String> {
        if (!linkDispatch.enabled) return emptyList()
        val text = (content as? ClipboardContent.Text)?.text ?: return emptyList()
        return LinkUrls.extractUrls(text)
    }

    /** 胶囊展示用的链接信息：多条给条数，单条命中给目标应用（未命中不给）。 */
    private fun linkInfoOf(content: ClipboardContent): ClipboardLinkInfo? {
        val links = linksOf(content)
        if (links.isEmpty()) return null
        if (links.size > 1) return ClipboardLinkInfo(links.size)
        val url = links.first()
        val rule = LinkUrls.firstMatch(url, linkDispatch.rules) ?: return null
        val target = LinkDispatcher.resolvedTargetOf(this, rule, url) ?: return null
        return ClipboardLinkInfo(1, target.packageName, target.label)
    }

    private fun onClipboardBallTapped() {
        val content = clipboardBallContent ?: return
        val links = linksOf(content)
        if (links.isNotEmpty()) {
            onClipboardLinkTapped(links, content)
            return
        }
        shareClipboardContent(content)
    }

    /**
     * 胶囊点击命中链接：单条按规则直达（「命中链接时的点击行为」允许时），多条转选择页，
     * 未命中按「未命中规则时」回退；直达失败也走同一条回退。
     */
    private fun onClipboardLinkTapped(links: List<String>, content: ClipboardContent) {
        if (links.size > 1) {
            val launched = runCatching { startActivity(LinkSelectionActivity.intent(this, links)) }
                .onFailure { Log.w(TAG, "link selection launch failed: ${it.message}") }
                .isSuccess
            if (launched) hideClipboardBall()
            return
        }
        val url = links.first()
        val rule = LinkUrls.firstMatch(url, linkDispatch.rules)
        if (rule == null) {
            LinkDispatcher.fallback(this, url, linkDispatch.missAction)
            hideClipboardBall()
            return
        }
        if (linkDispatch.capsuleTap == LinkTapMode.SHARE) {
            // 命中但点击行为选了分享：保持既有行为（分享整段原文）。
            shareClipboardContent(content)
            return
        }
        scope.launch {
            val dispatched = LinkDispatcher.dispatch(this@FloatBallService, url, linkDispatch)
            if (!dispatched) {
                Log.w(TAG, "link dispatch failed, fallback: ${url.take(80)}")
                LinkDispatcher.fallbackFor(this@FloatBallService, url, rule, linkDispatch)
            }
            hideClipboardBall()
        }
    }

    private fun shareClipboardContent(content: ClipboardContent) {
        // 分享面板从服务上下文起：NEW_TASK 必须加在 chooser 本体。
        // 启动失败（如后台弹出未授权）保留球可重试。
        val launched = runCatching {
            val send = when (content) {
                is ClipboardContent.Text -> Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, content.text)
                }
                is ClipboardContent.Image -> Intent(Intent.ACTION_SEND).apply {
                    type = content.mimeType
                    putExtra(Intent.EXTRA_STREAM, content.uri)
                    clipData = android.content.ClipData.newUri(
                        contentResolver,
                        "clipboard",
                        content.uri,
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(
                Intent.createChooser(send, null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            true
        }.getOrDefault(false)
        if (launched) {
            hideClipboardBall()
        } else {
            Log.w(TAG, "clipboard share launch failed")
        }
    }

    /** 剪贴板球位置落盘（拖动贴边后）：字段级合并到最新存量，同 persistBallPosition。 */
    private fun persistClipboardBallPosition() {
        val (nx, ny) = clipboardBallWindow.normalizedPosition()
        settings = settings.copy(clipboardBall = settings.clipboardBall.copy(x = nx, y = ny))
        scope.launch {
            runCatching {
                ConfigProvider.updateActionSettings { current ->
                    current.copy(
                        floatBall = current.floatBall.copy(
                            clipboardBall = current.floatBall.clipboardBall.copy(x = nx, y = ny),
                        ),
                    )
                }
            }.onFailure { Log.w(TAG, "persist clipboard ball position failed: ${it.message}") }
        }
    }

    // ---- 点按派发 ----

    private fun onBallTapped() {
        val action = settings.tapAction ?: return
        dispatchTapAction(action, ballCenterX(), ballCenterY())
    }

    /** 球心屏幕坐标（px）。读窗口实位而非落盘存量：自动挪球被打断时两者会背离，
     *  锚点按实位算，面板才不会弹到球的对面。 */
    private fun ballCenter(): Pair<Int, Int> {
        val half = DensityProvider.dp2px(settings.sizeDp) / 2
        val (px, py) = ballWindow.currentPosition()
        return (px.roundToInt() + half) to (py.roundToInt() + half)
    }

    private fun ballCenterX(): Int = ballCenter().first

    private fun ballCenterY(): Int = ballCenter().second

    /** 按锚点派发 tapAction（主球与分身共用）：网格面板直发，其余进容器。 */
    private fun dispatchTapAction(action: StoredAction, anchorX: Int, anchorY: Int) {
        // 网格面板动作不进容器：面板浮窗自包含（配置在 service 侧读取，不依赖
        // a11y 运行态），球侧只转交入口 —— 无障碍没开照样弹，这是「无 a11y 可用」的主路径。
        // 触钮/子手势绑它的路径仍走容器 executor，同一个 GridPanelOverlay.show 入口。
        if (action.typeId == GRID_PANEL_TYPE_ID) {
            SideGestureService.current?.showGridPanelDirect(action, anchorX, anchorY)
            return
        }
        SideGestureService.current?.forwardBallTap(action, anchorX, anchorY, settings.color)
    }

    // ---- 分身（mirrorBall） ----

    /** 按下分身：立即交换位置（不触发动作），锚点记录在按下时的分身球心，点击抬手时补派发。 */
    private fun onMirrorPressed() {
        if (!ballVisible) return
        val sizePx = DensityProvider.dp2px(settings.sizeDp)
        val (mx, my) = mirrorWindowPosition()
        pendingMirrorAnchorX = mx + sizePx / 2
        pendingMirrorAnchorY = my + sizePx / 2
        // 接力拖待命：抬手若未拖动就是纯点按，无副作用。
        ballWindow.beginExternalDrag()
        swapWithMirror(mx, my)
    }

    /** 点击分身（未超 touchSlop 抬手）：补触发动作（交换已在按下时完成）。 */
    private fun onMirrorTapped() {
        if (!ballVisible) return
        ballWindow.endExternalDrag()
        val action = settings.tapAction ?: return
        dispatchTapAction(action, pendingMirrorAnchorX, pendingMirrorAnchorY)
    }

    /** 分身拖动接力：位移增量转给主球。 */
    private fun onMirrorDragDeltas(dx: Float, dy: Float) {
        ballWindow.externalDragBy(dx, dy)
    }

    /** 分身抬手：主球贴边结算 + 落盘。 */
    private fun onMirrorDragReleased() {
        ballWindow.endExternalDrag()
    }

    private var pendingMirrorAnchorX = 0
    private var pendingMirrorAnchorY = 0
    /** 分身窗当前顶边屏幕坐标（px）。 */
    private fun mirrorWindowPosition(): Pair<Int, Int> {
        val pos = mirrorWindow.currentPosition()
        return pos.first.roundToInt() to pos.second.roundToInt()
    }

    /** 交换：主球新位置 = 对侧贴边（屏内语义），写回存量，双方交叉淡入淡出。 */
    private fun swapWithMirror(mirrorTopLeftX: Int, mirrorTopLeftY: Int) {
        val wasIdled = ballWindow.isIdled()
        val newX = (mirrorTopLeftX.toFloat() / DensityProvider.screenWidthPx)
            .coerceIn(0f, 1f)
        val newY = (mirrorTopLeftY.toFloat() / DensityProvider.screenHeightPx)
            .coerceIn(0f, 1f)
        persistBallPosition(newX, newY)
        ballWindow.crossFadeSwap(
            mirrorTopLeftX.toFloat(),
            mirrorTopLeftY.toFloat(),
            endIdled = wasIdled,
        ) { mirrorWindow.crossFadeSwap(settings) }
    }

    // ---- 键盘让位 ----

    private fun observeIme() {
        imeJob?.cancel()
        imeJob = scope.launch {
            while (isActive) {
                delay(IME_POLL_MS)
                // 窗口已收到事件型 IME insets：此 ROM 支持推播，兜底轮询使命完成，
                // 整个停掉（此前只 continue，服务存活期间永远 2Hz 唤醒）。
                // 判在隐藏类短态之前：面板/临时隐藏乃至摘窗期间确认了推播也照停，
                // 别为迟早退出的轮询白付唤醒；重挂由 ensureImeObserver 按需补启。
                if (ballWindow.imeInsetsAvailable()) break
                if (!ballVisible || panelCount > 0 ||
                    tempHideUntilMs > SystemClock.uptimeMillis()
                ) continue
                val imeBottom = runCatching {
                    windowManager?.currentWindowMetrics
                        ?.windowInsets
                        ?.getInsets(android.view.WindowInsets.Type.ime())
                        ?.bottom
                }.getOrNull() ?: 0
                ballWindow.updateIme(imeBottom)
                mirrorWindow.updateImeLift(ballWindow.currentImeLiftPx())
            }
        }
    }

    /** 兜底轮询的按需启动：只在球挂窗、且事件型 insets 尚未确认时跑（息屏侧由 screen off 停）。 */
    private fun ensureImeObserver() {
        if (imeJob?.isActive == true) return
        if (!ballVisible || ballWindow.imeInsetsAvailable()) return
        observeIme()
    }

    /**
     * 直接规则：手势碰了哪侧，球就即时移到哪侧（不投票、不留状态）。球已在该侧则
     * 不动（同侧触发零扰动，手动调的高度保留）；被拖动/按压/上一次挪球占用时本次
     * 跳过，下一次触发自然重试。落盘延到球真正落位（onLanded）：接受挪球就按目标
     * 落盘的话，淡出被打断会让存量停在新侧、实位停在旧侧 —— 面板锚点按存量算会弹到
     * 球对面，分身按存量反推会跟球同侧。
     */
    private fun onTouchSide(leftSide: Boolean) {
        if (!settings.followHand || !ballVisible) return
        if (leftSide == ballWindow.isOnLeft()) return
        val anchorY = (if (leftSide) settings.leftAnchorY else settings.rightAnchorY) ?: settings.y
        ballWindow.repositionTo(leftSide, anchorY) {
            val (px, py) = ballWindow.currentPosition()
            persistBallPosition(
                px / DensityProvider.screenWidthPx,
                py / DensityProvider.screenHeightPx,
            )
            // 分身立即换到新对侧：不等落盘回声的话，回声到达前与球短暂同侧。
            mirrorWindow.crossFadeSwap(settings)
        }
    }

    /**
     * 球位置落盘：手动拖动/分身换位/跟手挪球共用，顺带记忆该侧高度锚点
     * （下次跟手换侧落到记忆高度）。
     */
    private fun persistBallPosition(x: Float, y: Float) {
        val left = x < 0.5f
        settings = settings.copy(
            x = x,
            y = y,
            leftAnchorY = if (left) y else settings.leftAnchorY,
            rightAnchorY = if (!left) y else settings.rightAnchorY,
        )
        scope.launch {
            runCatching {
                ConfigProvider.updateActionSettings { current ->
                    // 字段级合并到最新存量：整对象按内存快照覆盖会把主进程的并发
                    // 修改（大小/颜色/动作）连同旧值一起写回去。
                    current.copy(
                        floatBall = current.floatBall.copy(
                            x = x,
                            y = y,
                            leftAnchorY = if (left) y else current.floatBall.leftAnchorY,
                            rightAnchorY = if (!left) y else current.floatBall.rightAnchorY,
                        ),
                    )
                }
            }.onFailure { Log.w(TAG, "persist ball position failed: ${it.message}") }
        }
    }

    // ---- 屏幕状态 ----

    private fun applyLockState() {
        val hidden = isScreenLocked()
        ballWindow.setHidden(hidden)
        mirrorWindow.setHidden(hidden)
        if (hidden) {
            hideCapsule()
            hideClipboardBall(preservePin = true)
        }
        // 亮屏解锁后按需补启：IME 兜底轮询与剪贴板兜底轮询共用这个入口。
        if (!hidden) {
            ensureImeObserver()
            // 锁屏期间复制的内容不补弹：解锁时先把基线对齐到当前剪贴板。
            seedClipboardBaseline()
            // 锁屏前钉住的球原样弹回（含 pin 状态）。
            restorePinnedClipboardIfNeeded()
        }
        ensureClipboardPoller()
    }

    private fun isScreenLocked(): Boolean {
        val poweredOff = powerManager?.isInteractive == false
        val locked = runCatching { keyguardManager?.isKeyguardLocked == true }.getOrDefault(false)
        return poweredOff || locked
    }

    // ---- 前台通知 ----

    private fun startInForeground() {
        val manager = getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "悬浮球", NotificationManager.IMPORTANCE_LOW).apply {
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("悬浮球待命")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = LOG_TAG
        private const val CHANNEL_ID = "float_ball"
        private const val NOTIFICATION_ID = 2002
        const val ACTION_STOP = "hunoia.luno.action.STOP_FLOAT_BALL"

        /** IME 底边轮询间隔（事件型 insets 的兜底；支持推播的 ROM 由窗口监听即时处理）。 */
        private const val IME_POLL_MS = 500L

        /** 剪贴板兜底轮询间隔：亮屏期间读一次对比内容变化（监听器在 HyperOS 上不投递）。 */
        private const val CLIP_POLL_MS = 2_000L

        /** 剪贴板读取失败时的短重试延时（Shizuku binder 抖动）。 */
        private const val CLIP_READ_RETRY_MS = 250L

        /** 通知胶囊的「仍在通知栏」确认延时：一次性/立即撤回的通知不弹。 */
        private const val CAPSULE_SETTLE_MS = 300L

        /** 用户滑掉后同 key 更新不再弹出的 LRU 容量（时间窗见设置）。 */
        private const val CAPSULE_DISMISSED_KEYS_MAX = 8

        /** 设置开关变更 / 开机自启都走这里。必须是 startForegroundService。 */
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, FloatBallService::class.java))
            }.onFailure {
                Log.e(TAG, "start float ball FAILED", it)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, FloatBallService::class.java).setAction(ACTION_STOP),
                )
            }.onFailure {
                Log.e(TAG, "stop float ball FAILED", it)
            }
        }
    }
}

private data class FloatBallRuntimeConfig(
    val action: ActionSettings,
    val gestureEnabled: Boolean,
)
