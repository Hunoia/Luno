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
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.runtime.condition.toConditionContext
import kotlin.math.roundToInt
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
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
import hunoia.luno.runtime.condition.TimeConditionTicker
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.runtime.environment.ConditionSources
import hunoia.luno.runtime.environment.EnvironmentTracker
import hunoia.luno.runtime.overlay.GestureOverlayCallbacks
import hunoia.luno.runtime.overlay.ButtonWindowController
import hunoia.luno.runtime.overlay.MainOverlayController
import hunoia.luno.runtime.overlay.TransientOverlayController
import hunoia.luno.runtime.settings.SettingsStore
import hunoia.luno.runtime.volume.VolumeScrubRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
            refreshGestureButtons()
        },
    )
    val mainOverlayController = MainOverlayController(host)
    val buttonWindowController = ButtonWindowController(host.context)
    val transientOverlayController = TransientOverlayController(host.context)
    private var refreshJob: Job? = null
    private val scopeJobs = mutableListOf<Job>()

    private val broadcastObserver = BroadcastObserver(
        context = host.context,
        onScreenOff = {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "screen off")
            environmentTracker.isNowInLockScreenPage = true
            host.runtimePanelOverlay.closeImmediately()
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.SCREEN))
        },
        onScreenOn = {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "screen on")
            environmentTracker.onScreenEvent(ScreenEventType.SCREEN_ON)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.SCREEN))
        },
        onUserPresent = {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "user present")
            environmentTracker.isNowInLockScreenPage = false
            refreshGestureButtons()
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
            )
        },
        onToggleQuickAppLauncher = { hunoia.luno.quicklaunch.QuickAppLauncherActivity.start(host.context) },
        onShowQuickSearch = { hunoia.luno.quicklaunch.QuickSearchActivity.start(host.context) },
        onShowVolumeScrub = { config -> volumeScrubRuntime.show(config) },
        onHideGestureButton = { button, delayMs ->
            if (button != null) buttonHideRuntime.hideTemporarily(button, delayMs)
        },
    )

    /** 同规则单飞：在跑的规则 id 集合（跨规则并行不受影响）。 */
    private val runningRules = mutableSetOf<String>()

    private val sequenceExecutor = SequenceExecutor(
        runSingleAction = { action, entryId, ctx ->
            actionDispatcher.runStepAction(action, entryId, ctx)
        },
    )

    private val automationEngine = AutomationEngine(
        // 跨规则并行、同规则单飞：同一规则已有实例在跑时，新触发记 skip（RULE_BUSY）。
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
                        val records = sequenceExecutor.runSteps(rule.effect.effectiveSteps(), ctx)
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
        onEvent = { event ->
            host.coroutineScope.launch { ConfigProvider.appendAutomationEvent(event) }
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
    )

    private val volumeScrubRuntime = VolumeScrubRuntime(
        context = host.context,
        onStateChanged = { refreshGestureButtons() },
    )

    private var started = false
    private var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null

    private val callbacks: GestureOverlayCallbacks = object : GestureOverlayCallbacks {
        override fun onSubGestureModeChanged(inSubGesture: Boolean, center: Offset, radiusPx: Int) {
            if (inSubGesture) transientOverlayController.attachSubGestureOverlay(center, radiusPx)
            else transientOverlayController.detachSubGestureOverlay()
        }

        override fun onVolumeScrubOverlayRequested(config: hunoia.luno.config.model.VolumeScrubConfig) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "volume scrub overlay requested")
            volumeScrubRuntime.show(config)
        }

        override fun onActionPanelOverlayChanged(show: Boolean) {
            if (show) transientOverlayController.attachActionPanelOverlay()
            else transientOverlayController.detachActionPanelOverlay()
        }

        override fun onActionPanelVisibilityChanged(visible: Boolean) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "action panel visible=$visible")
            mainOverlayController.setPanelVisible(visible)
        }

        override fun onAction(
            action: hunoia.luno.action.model.StoredAction,
            sourceButton: GestureButton?,
            sourceOverride: GestureButtonActionSettingsOverride?,
            touchPosition: Offset,
            decision: hunoia.luno.gesture.GestureDecision.Trigger?,
        ) {
            onGestureDecision(decision, action, sourceButton, sourceOverride, touchPosition)
        }
    }

    /**
     * 手势决策唯一入口：Decision 到此不再被截留。
     * 今天恒放行（记录 + 立即分发）；后续仲裁/改写只改这里。
     */
    private fun onGestureDecision(
        decision: hunoia.luno.gesture.GestureDecision.Trigger?,
        action: hunoia.luno.action.model.StoredAction,
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride?,
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
            sourceOverride,
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

    /** 分发落定订阅：手势链路的分发完成后驱动 GESTURE 域评估（替代入口直调）。 */
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
        environmentTracker.onAccessibilityEvent(event)
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "window changed: pkg=${event.packageName}")
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
        runtimeSettingsStore.stop()
        timeConditionTicker.stop()
        batteryTracker.unregister()
        audioVolumeTracker.unregister()
        conditionSources.stop()
        scopeJobs.forEach { it.cancel() }
        scopeJobs.clear()
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "GestureCoordinator release")
        mainOverlayController.release()
        buttonWindowController.release()
        transientOverlayController.detachAll()
        broadcastObserver.unregister()
        volumeScrubRuntime.onDestroy()
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
            isRingerSilent = audioVolumeTracker.isRingerSilent,
            volumePercent = audioVolumeTracker.volumePercent,
            consumePulses = consumePulses,
            peekPulses = peekPulses,
        )

    private fun volumePollNeeded(rules: List<hunoia.luno.config.model.AutomationRule>? = null): Boolean {
        val list = rules ?: runtimeSettingsStore.snapshot().automationRules
        return list.any { rule -> rule.condition.hasVolumeCondition() }
    }

    private fun refreshGestureButtons(delayMs: Long = 100L, automationKeys: Set<String> = emptySet()) {
        refreshJob?.cancel()
        refreshJob = host.coroutineScope.launch {
            if (delayMs > 0L) delay(delayMs)
            automationRuntime.evaluate(automationKeys)
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
