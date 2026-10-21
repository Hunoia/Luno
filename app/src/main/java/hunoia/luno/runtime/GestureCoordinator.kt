package hunoia.luno.runtime

import android.app.WallpaperManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import hunoia.luno.BuildConfig
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.actionPanelId
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.runtime.condition.toConditionContext
import kotlin.math.roundToInt
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.quicklaunch.query.AppSearch
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import java.util.Locale
import hunoia.luno.action.dispatcher.NewActionDispatcher
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.runtime.environment.PreviousAppTracker
import hunoia.luno.runtime.condition.AudioVolumeTracker
import hunoia.luno.runtime.condition.hasVolumeCondition
import hunoia.luno.runtime.automation.AutomationEngine
import hunoia.luno.runtime.automation.AutomationEvent
import hunoia.luno.runtime.automation.AutomationEventKey
import hunoia.luno.runtime.automation.AutomationPulseHub
import hunoia.luno.runtime.automation.AutomationRuntime
import hunoia.luno.runtime.automation.ExecutionReason
import hunoia.luno.runtime.automation.SequenceExecutor
import hunoia.luno.runtime.automation.SkipReason
import hunoia.luno.runtime.button.ButtonHideRuntime
import hunoia.luno.runtime.button.ButtonRefreshCoordinator
import hunoia.luno.runtime.condition.BatteryTracker
import hunoia.luno.runtime.condition.DndTracker
import hunoia.luno.runtime.condition.MusicPlaybackTracker
import hunoia.luno.runtime.condition.TimeConditionTicker
import hunoia.luno.runtime.condition.TorchStateTracker
import hunoia.luno.runtime.condition.hasMusicCondition
import hunoia.luno.runtime.condition.hasTorchCondition
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.runtime.environment.ConditionSources
import hunoia.luno.runtime.environment.EnvironmentTracker
import hunoia.luno.runtime.overlay.GestureOverlayCallbacks
import hunoia.luno.runtime.overlay.ButtonWindowController
import hunoia.luno.runtime.overlay.MainOverlayController
import hunoia.luno.runtime.overlay.TransientOverlayController
import hunoia.luno.runtime.settings.SettingsStore
import hunoia.luno.runtime.volume.VolumeScrubRuntime
import hunoia.luno.runtime.pointer.PointerRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** 冷启动/悬浮窗重建期间容器的面板列表未灌满时，揭示事件的等待重试参数。 */
private const val BALL_PANEL_REVEAL_MAX_ATTEMPTS = 40
private val BALL_PANEL_REVEAL_RETRY_MS = 250.milliseconds

class GestureCoordinator(
    private val host: GestureHost,
) {
    val runtimeSettingsStore = SettingsStore(host.coroutineScope)
    val environmentTracker = EnvironmentTracker(
        getCurrentPackageName = { host.getCurrentPackageName() },
        nowInLauncher = { host.nowInLauncher() },
        findFocusedNode = {
            host.accessibilityService.rootInActiveWindow?.findFocus(
                android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT
            )
        },
        onKeyboardStateChanged = { active ->
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "keyboard active=$active")
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.KEYBOARD))
        },
    )
    val mainOverlayController = MainOverlayController(host)
    val buttonWindowController = ButtonWindowController(host.context)
    val transientOverlayController = TransientOverlayController(host.context)
    private var refreshJob: Job? = null
    /** 待评估事件键累积：100ms 窗口内多次 refresh 取并集一次评估，否则后到的 keyed 评估会顶掉通用评估。 */
    private val pendingAutomationKeys = mutableSetOf<String>()

    /** 是否已有延迟刷新在等待（与 pendingAutomationKeys 同锁保护），用于「首个事件起算」节流。 */
    private var refreshPending = false

    /** 最近一次窗口状态事件的 Activity 类名：同包内启动新 Activity 也要能收起网格面板。 */
    private var lastWindowClassName: String? = null

    private val scopeJobs = mutableListOf<Job>()

    private val broadcastObserver = BroadcastObserver(
        context = host.context,
        onScreenOff = {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "screen off")
            environmentTracker.isNowInLockScreenPage = true
            environmentTracker.onScreenEvent(ScreenEventType.SCREEN_OFF)
            host.runtimePanelOverlay.closeImmediately()
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.SCREEN))
        },
        onScreenOn = {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "screen on")
            environmentTracker.onScreenEvent(ScreenEventType.SCREEN_ON)
            // 无通知访问权限时，勿扰变化没有监听来源；亮屏时补读一次系统值。
            dndTracker.refreshFromSystem()
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.SCREEN))
        },
        onUserPresent = {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "user present")
            environmentTracker.isNowInLockScreenPage = false
            environmentTracker.onScreenEvent(ScreenEventType.USER_UNLOCK)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.SCREEN))
        }
    )

    private val conditionSources = ConditionSources(
        context = host.context,
        onStateChanged = { refreshGestureButtons() },
        onBluetoothPulse = { direction ->
            environmentTracker.onBluetoothChanged(direction)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.BLUETOOTH))
        },
        onHeadphonesPulse = { direction ->
            environmentTracker.onHeadphonesChanged(direction)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.HEADPHONES))
        },
    )

    private val audioVolumeTracker = AudioVolumeTracker(
        context = host.context,
        onVolumeChanged = { change ->
            environmentTracker.onVolumeChanged(change)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.VOLUME))
        },
    )

    private val musicTracker = MusicPlaybackTracker(
        context = host.context,
        onChanged = { direction ->
            environmentTracker.onMusicChanged(direction)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.MUSIC))
        },
    )

    private val torchTracker = TorchStateTracker(
        context = host.context,
        onChanged = { on ->
            environmentTracker.onTorchChanged(on)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.TORCH))
        },
    )

    private val dndTracker = DndTracker(
        context = host.context,
        onChanged = { on ->
            environmentTracker.onDndChanged(on)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.DND))
        },
    )

    private val buttonHideRuntime = ButtonHideRuntime(
        scope = host.coroutineScope,
        onStateChanged = { refreshGestureButtons() },
    )

    private val batteryTracker = BatteryTracker(
        context = host.context,
        onChanged = { charging ->
            environmentTracker.onChargingChanged(charging)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.CHARGING))
        },
    )

    private val timeConditionTicker = TimeConditionTicker(
        scope = host.coroutineScope,
        rulesProvider = { runtimeSettingsStore.snapshot().automationRules },
        onTimeSignatureChanged = { refreshGestureButtons(delayMs = 0L, automationKeys = setOf(AutomationEventKey.TIME)) },
        markScheduledHit = { environmentTracker.onScheduledTimeHit() },
    )

    private val previousAppTracker = PreviousAppTracker(
        packageManager = host.context.packageManager,
        startActivity = { host.context.startActivity(it) },
        rootInActiveWindowPackageName = { host.accessibilityService.rootInActiveWindow?.packageName?.toString() },
    )

    private val actionDispatcher = NewActionDispatcher(
        host = host,
        scope = host.coroutineScope,
        previousAppTracker = previousAppTracker,
        runtimeState = { buildRuntimeState(peekPulses = true) },
        settingsSnapshot = {
            val s = runtimeSettingsStore.snapshot()
            hunoia.luno.action.dispatcher.SettingsSnapshot(
                actionSettings = s.actionSettings,
                advancedSettings = s.advancedSettings,
                gestureSettings = s.gestureSettings,
                newActionLibrarySettings = s.newActionLibrarySettings,
                automationRules = s.automationRules,
            )
        },
        onToggleQuickAppLauncher = { hunoia.luno.quicklaunch.QuickAppLauncherOverlay.show() },
        onShowQuickSearch = { hunoia.luno.quicklaunch.QuickSearchActivity.start(host.context) },
        onShowGridPanel = { panelId, anchorX, anchorY ->
            hunoia.luno.gridpanel.GridPanelOverlay.show(panelId, anchorX, anchorY)
        },
        onShowVolumeScrub = { config -> volumeScrubRuntime.show(config) },
        onShowPointer = { pointerRuntime.show() },
        onHideGestureButton = { button, delayMs ->
            if (button != null) buttonHideRuntime.hideTemporarily(button, delayMs)
        },
    )

    /** 同规则单飞：在跑的规则 id 集合（跨规则并行不受影响）。 */
    private val runningRules = mutableSetOf<String>()

    private val sequenceExecutor = SequenceExecutor(
        runSingleAction = { action, ctx ->
            actionDispatcher.runStepAction(action, ctx)
        },
    )

    private val automationEngine = AutomationEngine(
        // 同一规则已有实例在跑时，新触发记 skip（RULE_BUSY）。
        onFireRule = { rule, ctx ->
            val admitted = synchronized(runningRules) { runningRules.add(rule.id) }
            if (!admitted) {
                host.coroutineScope.launch {
                    ConfigProvider.appendAutomationEvent(
                        AutomationEvent(
                            ruleId = rule.id,
                            ruleName = rule.name,
                            timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                            fired = false,
                            reason = SkipReason.RULE_BUSY.code,
                        )
                    )
                }
            } else {
                host.coroutineScope.launch {
                    try {
                        val records = sequenceExecutor.runSteps(rule.effect.steps, ctx)
                        val reason = records.firstOrNull { !it.ok }?.let {
                            ExecutionReason.INTERRUPTED_PREFIX + it.index
                        } ?: ExecutionReason.OK
                        ConfigProvider.appendAutomationEvent(
                            AutomationEvent(
                                ruleId = rule.id,
                                ruleName = rule.name,
                                timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                                fired = true,
                                reason = reason,
                                steps = records,
                            )
                        )
                    } finally {
                        synchronized(runningRules) { runningRules.remove(rule.id) }
                    }
                }
            }
        },
        onEvents = { events ->
            // 一轮求值的跳过事件合成一次写盘（引擎侧已攒批）。
            host.coroutineScope.launch { ConfigProvider.appendAutomationEvents(events) }
        },
    )

    private val buttonRefreshCoordinator = ButtonRefreshCoordinator(
        runtimeSettingsStore = runtimeSettingsStore,
        buttonWindowController = buttonWindowController,
    )

    private val automationRuntime = AutomationRuntime(
        runtimeSettingsStore = runtimeSettingsStore,
        buildRuntimeState = ::buildRuntimeState,
        automationEngine = automationEngine,
        onVisibilityRefresh = { state -> buttonRefreshCoordinator.refresh(state) },
        discardPulses = { environmentTracker.discardPendingPulses() },
    )

    private val volumeScrubRuntime = VolumeScrubRuntime(
        context = host.context,
        onStateChanged = { refreshGestureButtons() },
    )

    private val pointerRuntime = PointerRuntime(
        host = host,
        scope = host.coroutineScope,
        gestureSettingsProvider = { runtimeSettingsStore.snapshot().gestureSettings },
        onStateChanged = { refreshGestureButtons() },
    )

    private var started = false
    private var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null

    private val callbacks: GestureOverlayCallbacks = object : GestureOverlayCallbacks {
        override fun onSubGestureModeChanged(inSubGesture: Boolean, center: Offset, radiusPx: Int) {
            if (inSubGesture) {
                transientOverlayController.attachSubGestureOverlay(center, radiusPx)
                Events.post(PanelShownEvent())
            } else {
                transientOverlayController.detachSubGestureOverlay()
                Events.post(PanelDismissedEvent())
            }
        }

        override fun onVolumeScrubOverlayRequested(config: hunoia.luno.config.model.VolumeScrubConfig) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "volume scrub overlay requested")
            volumeScrubRuntime.show(config)
        }

        override fun onPointerStart(settings: hunoia.luno.config.model.GestureSettings.Pointer): Boolean {
            return pointerRuntime.beginBridge(settings)
        }

        override fun onPointerShow(settings: hunoia.luno.config.model.GestureSettings.Pointer): Boolean {
            return pointerRuntime.showBridge(settings)
        }

        override fun onPointerEnd() {
            pointerRuntime.end()
        }

        override fun onPointerActionAtPosition(x: Int, y: Int) {
            pointerRuntime.performBridgeActionAt(x, y)
        }

        override fun onActionPanelOverlayChanged(show: Boolean) {
            if (show) {
                transientOverlayController.attachActionPanelOverlay()
                Events.post(PanelShownEvent())
            } else {
                transientOverlayController.detachActionPanelOverlay()
                Events.post(PanelDismissedEvent())
            }
        }

        override fun onActionPanelVisibilityChanged(visible: Boolean) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "action panel visible=$visible")
            mainOverlayController.setPanelVisible(visible)
        }

        override fun onAction(
            action: hunoia.luno.action.model.StoredAction,
            sourceButton: GestureButton?,
            touchPosition: Offset,
            decision: hunoia.luno.gesture.GestureDecision.Trigger?,
        ) {
            onGestureDecision(decision, action, sourceButton, touchPosition)
        }
    }

    /** 手势决策唯一入口：记录后立即分发；仲裁/改写只改这里。 */
    private fun onGestureDecision(
        decision: hunoia.luno.gesture.GestureDecision.Trigger?,
        action: hunoia.luno.action.model.StoredAction,
        sourceButton: GestureButton?,
        touchPosition: Offset,
    ) {
        if (BuildConfig.DEBUG) {
            Log.i(
                "LunoLauncher",
                "gesture decision trigger=${decision?.triggerType} dir=${decision?.actionDirection} " +
                    "button=${sourceButton?.id} action=${action.typeId}",
            )
        }
        // 手势触发动作与手势事件进总线是两条独立路径：前者立即执行，后者供自动化规则订阅。
        environmentTracker.onGesturePerformed(sourceButton?.id ?: "")
        val touch = if (touchPosition.x.isFinite() && touchPosition.y.isFinite()) {
            touchPosition.x.roundToInt() to touchPosition.y.roundToInt()
        } else {
            null
        }
        actionDispatcher.dispatch(
            action.withoutVariantKey(),
            sourceButton,
            touch,
            { buildRuntimeState(peekPulses = true).toConditionContext() },
            gestureTrigger = decision?.triggerType,
            fromGesture = true,
        )
    }

    /** 供 overlay 等场景直接执行一个已构造好的动作。 */
    fun dispatchAction(action: hunoia.luno.action.model.StoredAction) {
        actionDispatcher.dispatch(action.withoutVariantKey(), null)
    }

    /**
     * 悬浮球点按动作统一转发：所有动作广播进容器，由容器 `runGestureActions` 分类执行（与触钮同链路）。
     * 面板动作要等快照列表就绪再广播：a11y 冷启动后头几秒 `actionPanels` 为空，事件即发即忘，
     * 砸进去 = 面板静默不弹；非面板动作立即转发。
     */
    fun forwardBallTap(
        action: hunoia.luno.action.model.StoredAction,
        anchorX: Int,
        anchorY: Int,
        color: Int,
    ) {
        val panelId = action.actionPanelId()
        if (panelId == null) {
            hunoia.luno.core.Events.post(
                hunoia.luno.core.BallTapActionEvent(action, anchorX, anchorY, color),
            )
            return
        }
        host.coroutineScope.launch {
            for (attempt in 1..BALL_PANEL_REVEAL_MAX_ATTEMPTS) {
                val panels = runtimeSettingsStore.snapshot().actionSettings.actionPanels.panels
                if (panels.any { it.id == panelId } || attempt == BALL_PANEL_REVEAL_MAX_ATTEMPTS) {
                    hunoia.luno.core.Events.post(
                        hunoia.luno.core.BallTapActionEvent(action, anchorX, anchorY, color),
                    )
                    return@launch
                }
                delay(BALL_PANEL_REVEAL_RETRY_MS)
            }
        }
    }

    /** 分发落定后驱动 GESTURE 域评估。 */
    private val gestureDispatchSubscriber: (hunoia.luno.action.dispatcher.GestureDispatchEvent) -> Unit = { event ->
        if (event.fromGesture) {
            refreshGestureButtons(delayMs = 0L, automationKeys = setOf(AutomationEventKey.GESTURE))
        }
    }

    fun onSetOverlay() {
        if (started) return
        started = true
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "GestureCoordinator start")
        hunoia.luno.core.Events.subscribe(
            hunoia.luno.action.dispatcher.GestureDispatchEvent::class,
            gestureDispatchSubscriber,
        )
        runtimeSettingsStore.start()
        broadcastObserver.register()
        batteryTracker.register()
        audioVolumeTracker.sync(volumePollNeeded())
        conditionSources.start()
        // 通知脉冲：同 :service 进程静态直连；destroy 时置空，避免野回调。
        AutomationPulseHub.onNotification = { pulse ->
            environmentTracker.onNotificationPosted(pulse)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.NOTIFICATION))
        }
        // 勿扰变化由通知监听服务推入（需通知访问权限）。
        AutomationPulseHub.onInterruption = { on -> dndTracker.onSystemChanged(on) }
        dndTracker.refreshFromSystem()
        val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
            hunoia.luno.core.Events.post(hunoia.luno.core.WallpaperChangedEvent())
        }
        wallpaperColorsListener = listener
        WallpaperManager.getInstance(host.context).addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))

        mainOverlayController.replaceContent { renderMainOverlay() }

        scopeJobs += host.coroutineScope.launch {
            runtimeSettingsStore.state
                .distinctUntilChangedBy { it.gestureButtons }
                .collectLatest { state ->
                    if (BuildConfig.DEBUG) Log.i("LunoLauncher", "gesture buttons changed: count=${state.gestureButtons.size}")
                    buttonWindowController.replaceGestureButtons(state.gestureButtons)
                    refreshGestureButtons(delayMs = 0L)
                }
        }

        // 总开关（initialSettings.gestureEnabled）翻转时 gestureButtons/automationRules 都不变，
        // 上面两个 distinctUntilChangedBy 会过滤掉这次变更、不触发刷新；这里单独订阅兜底。
        scopeJobs += host.coroutineScope.launch {
            runtimeSettingsStore.state
                .distinctUntilChangedBy { it.initialSettings }
                .collect { refreshGestureButtons(delayMs = 0L) }
        }

        scopeJobs += host.coroutineScope.launch(Dispatchers.IO) {
            // 用面板真正展示的那份查询（allowRepeatPackage=false，且含禁用应用），
            // 而不是 queryApps —— 后者列表不同，预热出来的索引对不上。
            val list = DisabledAppQuery.queryQuickAppLauncherApps(host.context)
            ConfigProvider.writeQuickAppSnapshot(
                QuickAppSnapshot(
                    apps = list.apps,
                    disabledPkgs = list.disabledPkgs,
                    labelLocale = Locale.getDefault().toLanguageTag(),
                )
            )
            AppSearch.warmIndexes(list.apps)
        }

        scopeJobs += host.coroutineScope.launch {
            var firstRuleEmission = true
            runtimeSettingsStore.state
                .distinctUntilChangedBy { it.automationRules }
                .collect { state ->
                    timeConditionTicker.sync(state.automationRules)
                    audioVolumeTracker.sync(volumePollNeeded(state.automationRules))
                    musicTracker.sync(musicConditionNeeded(state.automationRules))
                    torchTracker.sync(torchConditionNeeded(state.automationRules))
                    dndTracker.refreshFromSystem()
                    if (firstRuleEmission) {
                        firstRuleEmission = false
                    } else {
                        refreshGestureButtons(delayMs = 0L)
                    }
                }
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        previousAppTracker.onAccessibilityEvent(event)
        val beforePackage = environmentTracker.currentPackageName
        environmentTracker.onAccessibilityEvent(event)
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "window changed: pkg=${event.packageName}")
            val afterPackage = environmentTracker.currentPackageName
            val eventClass = event.className?.toString()
            val samePackageNewActivity =
                eventClass != null && lastWindowClassName != null && eventClass != lastWindowClassName
            lastWindowClassName = eventClass ?: lastWindowClassName
            // 第三方小部件/通知等从网格面板里拉起新应用时，面板是覆盖层不会自动消失；
            // 以前台窗口切换为准收口（同包内换 Activity 也算，例如小部件拉起自己的主界面）。
            if (beforePackage.isNotEmpty() && afterPackage.isNotEmpty() &&
                (afterPackage != beforePackage || samePackageNewActivity)
            ) {
                hunoia.luno.gridpanel.GridPanelOverlay.onForegroundPackageChanged(
                    fromPackage = beforePackage,
                    toPackage = afterPackage,
                )
            }
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.APP))
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        val oldOrientation = environmentTracker.orientation
        if (oldOrientation != newConfig.orientation) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "orientation=${newConfig.orientation}")
            environmentTracker.onOrientationChanged(newConfig.orientation)
            mainOverlayController.updateLayout()
            refreshGestureButtons()
        }
    }

    fun onDestroy() {
        if (!started) return
        started = false
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "GestureCoordinator destroy")
        hunoia.luno.core.Events.unsubscribe(
            hunoia.luno.action.dispatcher.GestureDispatchEvent::class,
            gestureDispatchSubscriber,
        )
        AutomationPulseHub.onNotification = null
        AutomationPulseHub.onInterruption = null
        runtimeSettingsStore.stop()
        timeConditionTicker.stop()
        batteryTracker.unregister()
        audioVolumeTracker.unregister()
        musicTracker.unregister()
        torchTracker.unregister()
        conditionSources.stop()
        scopeJobs.forEach { it.cancel() }
        scopeJobs.clear()
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "GestureCoordinator release")
        mainOverlayController.release()
        buttonWindowController.release()
        transientOverlayController.detachAll()
        broadcastObserver.unregister()
        volumeScrubRuntime.onDestroy()
        pointerRuntime.onDestroy()
        previousAppTracker.onRelease()
        wallpaperColorsListener?.let { listener ->
            WallpaperManager.getInstance(host.context).removeOnColorsChangedListener(listener)
            wallpaperColorsListener = null
        }
    }

    private fun buildRuntimeState(consumePulses: Boolean = true, peekPulses: Boolean = false): GestureRuntimeState =
        environmentTracker.buildRuntimeState(
            hiddenGestureButtons = buttonHideRuntime.getSnapshot(),
            isCharging = batteryTracker.isCharging,
            batteryLevel = batteryTracker.batteryLevel,
            networkType = conditionSources.networkType,
            headphonesConnected = conditionSources.headphonesConnected,
            bluetoothAdapterOn = conditionSources.bluetoothAdapterOn,
            bluetoothAudioConnected = conditionSources.bluetoothAudioConnected,
            airplaneMode = conditionSources.airplaneMode,
            ringerMode = audioVolumeTracker.ringerMode,
            volumePercent = audioVolumeTracker.volumePercent,
            isMusicPlaying = musicTracker.isPlaying,
            isTorchOn = torchTracker.isOn,
            isDndOn = dndTracker.isOn,
            consumePulses = consumePulses,
            peekPulses = peekPulses,
        )

    private fun volumePollNeeded(rules: List<hunoia.luno.config.model.AutomationRule>? = null): Boolean {
        val list = rules ?: runtimeSettingsStore.snapshot().automationRules
        // 只看启用规则：纯禁用规则不该让音量轮询空转。
        return list.any { rule -> rule.enabled && rule.condition.hasVolumeCondition() }
    }

    private fun musicConditionNeeded(rules: List<hunoia.luno.config.model.AutomationRule>? = null): Boolean {
        val list = rules ?: runtimeSettingsStore.snapshot().automationRules
        return list.any { rule -> rule.enabled && rule.condition.hasMusicCondition() }
    }

    private fun torchConditionNeeded(rules: List<hunoia.luno.config.model.AutomationRule>? = null): Boolean {
        val list = rules ?: runtimeSettingsStore.snapshot().automationRules
        return list.any { rule -> rule.enabled && rule.condition.hasTorchCondition() }
    }

    private fun refreshGestureButtons(delayMs: Long = 100L, automationKeys: Set<String> = emptySet()) {
        synchronized(pendingAutomationKeys) {
            pendingAutomationKeys.addAll(automationKeys)
            // 已有延迟刷新在等待：键并进去，不再 cancel 重建。
            // 不能用 cancel + delay 去抖：事件间隔 <100ms 时刷新会被无限推迟。
            if (delayMs > 0L && refreshPending) return
            refreshPending = delayMs > 0L
        }
        refreshJob?.cancel()
        refreshJob = host.coroutineScope.launch {
            if (delayMs > 0L) delay(delayMs)
            // 空集=通用刷新：累积键为空才走通用分支（evaluate 空集只评估含状态叶子的规则）。
            val keys = synchronized(pendingAutomationKeys) {
                refreshPending = false
                pendingAutomationKeys.toSet().also { pendingAutomationKeys.clear() }
            }
            automationRuntime.evaluate(keys)
        }
    }

    @Composable
    private fun renderMainOverlay() {
        hunoia.luno.runtime.overlay.GestureOverlayView(
            callbacks = callbacks,
            settingsState = runtimeSettingsStore.state,
        )
    }
}
