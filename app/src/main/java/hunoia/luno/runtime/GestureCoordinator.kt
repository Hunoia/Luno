package hunoia.luno.runtime

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import hunoia.luno.BuildConfig
import hunoia.luno.core.Events
import hunoia.luno.core.PanelDismissedEvent
import hunoia.luno.core.PanelShownEvent
import hunoia.luno.core.TouchButtonSideEvent
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.actionPanelId
import hunoia.luno.action.model.withoutVariantKey
import hunoia.luno.runtime.condition.toConditionContext
import hunoia.luno.runtime.condition.matches
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.automation.RuleTestResult
import hunoia.luno.runtime.gesturepanel.ACTION_PANEL_FADE_OUT_MS
import kotlin.math.roundToInt
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GlobalVariables
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.RuleReentry
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.quicklaunch.query.AppSearch
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import java.util.Locale
import hunoia.luno.action.dispatcher.ActionDispatcher
import hunoia.luno.config.model.ScreenEventType
import hunoia.luno.runtime.environment.PreviousAppTracker
import hunoia.luno.runtime.condition.AudioVolumeTracker
import hunoia.luno.runtime.condition.hasVolumeCondition
import hunoia.luno.runtime.automation.AutomationEngine
import hunoia.luno.runtime.automation.AutomationEvent
import hunoia.luno.runtime.automation.AutomationEventKey
import hunoia.luno.runtime.automation.AutomationPulseHub
import hunoia.luno.runtime.automation.AutomationShareBridge
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
import hunoia.luno.bridge.system.PackageChangeReceiver
import hunoia.luno.runtime.overlay.GestureOverlayCallbacks
import hunoia.luno.runtime.overlay.ButtonWindowController
import hunoia.luno.runtime.overlay.MainOverlayController
import hunoia.luno.runtime.overlay.TransientOverlayController
import hunoia.luno.runtime.settings.SettingsStore
import hunoia.luno.runtime.volume.VolumeScrubRuntime
import hunoia.luno.runtime.pointer.PointerRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import hunoia.luno.core.LOG_TAG

/** 冷启动/悬浮窗重建期间容器的面板列表未灌满时，揭示事件的等待重试参数。 */
private const val BALL_PANEL_REVEAL_MAX_ATTEMPTS = 40

/** 键盘在屏期间的短轮询间隔：捕捉没有 a11y 事件的 IME 收起（如返回键/手势）。 */
private const val KEYBOARD_WATCH_MS = 600L

/** 跳过诊断节流窗口：同规则同原因窗口内只留一条历史，避免通知风暴写爆历史。 */
private const val SKIP_LOG_WINDOW_MS = 60_000L
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
        // 键盘状态以真实 IME 窗口为准；拿不到窗口列表（null）时追踪器退回焦点兜底。
        imeWindowVisible = {
            runCatching { host.accessibilityService.windows }.getOrNull()
                ?.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        },
        onKeyboardStateChanged = { active -> onKeyboardStateChanged(active) },
    )

    init {
        // 播种真实环境：服务重绑（应用更新/崩溃恢复）时进程全新，追踪器的默认值（竖屏/未锁屏）
        // 可能与实际相反。边沿引擎首评「prev=hit」记账，种子错了的话，重启后的第一次真实转变
        // （解锁/转回竖屏）会被当成无变化吞掉 ——「锁屏/横屏关闭 → 解锁/竖屏启用」的启用规则
        // 就再也等不到边沿，触钮/悬浮球停在关闭态。
        environmentTracker.onOrientationChanged(host.context.resources.configuration.orientation)
        val keyguard = host.context.getSystemService(KeyguardManager::class.java)
        environmentTracker.isNowInLockScreenPage = keyguard?.isKeyguardLocked == true
    }
    val mainOverlayController = MainOverlayController(host)
    val buttonWindowController = ButtonWindowController(host.context)
    val transientOverlayController = TransientOverlayController(host.context)
    private var refreshJob: Job? = null

    /** 键盘在屏期间的可见性短轮询：IME 可能无 a11y 事件地消失，只靠事件会一直停在「弹出」。 */
    private var keyboardWatchJob: Job? = null
    /** 待评估事件键累积：100ms 窗口内多次 refresh 取并集一次评估，否则后到的 keyed 评估会顶掉通用评估。 */
    private val pendingAutomationKeys = mutableSetOf<String>()

    /** 是否已有延迟刷新在等待（与 pendingAutomationKeys 同锁保护），用于「首个事件起算」节流。 */
    private var refreshPending = false

    /** 最近一次窗口状态事件的 Activity 类名：同包内启动新 Activity 也要能收起网格面板。 */
    private var lastWindowClassName: String? = null

    /** 当前可交互的应用窗口包：TYPE_WINDOWS_CHANGED 拿它做差集，发现“刚出现的新应用窗口”。 */
    private var appWindowPackages: Set<String> = emptySet()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 部件点击后的主动轮询：不再干等可能晚 0.5s 的窗口状态事件。 */
    private var widgetLaunchPoll: Runnable? = null
    private var widgetLaunchPackages: Set<String> = emptySet()
    private var widgetLaunchWindowIds: Set<Int> = emptySet()
    private var widgetLaunchDeadline = 0L

    private val scopeJobs = mutableListOf<Job>()

    private val broadcastObserver = BroadcastObserver(
        context = host.context,
        onScreenOff = {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "screen off")
            environmentTracker.isNowInLockScreenPage = true
            environmentTracker.onScreenEvent(ScreenEventType.SCREEN_OFF)
            host.runtimePanelOverlay.closeImmediately()
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.SCREEN))
        },
        onScreenOn = {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "screen on")
            environmentTracker.onScreenEvent(ScreenEventType.SCREEN_ON)
            // 无通知访问权限时，勿扰变化没有监听来源；亮屏时补读一次系统值。
            dndTracker.refreshFromSystem()
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.SCREEN))
        },
        onUserPresent = {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "user present")
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

    private val batteryTracker: BatteryTracker = BatteryTracker(
        context = host.context,
        onChanged = { chargingChanged, charging, _ ->
            // 仅充电边沿发脉冲；纯电量变化只重评状态条件（见 BatteryTracker 文档）。
            if (chargingChanged) environmentTracker.onChargingChanged(charging)
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.CHARGING))
        },
    )

    private val timeConditionTicker = TimeConditionTicker(
        scope = host.coroutineScope,
        rulesProvider = { runtimeSettingsStore.snapshot().automationRules },
        onTimeSignatureChanged = { refreshGestureButtons(delayMs = 0L, automationKeys = setOf(AutomationEventKey.TIME)) },
        markScheduledHit = { environmentTracker.onScheduledTimeHit() },
        markIntervalHit = { keys ->
            environmentTracker.onIntervalDue(keys)
            refreshGestureButtons(delayMs = 0L, automationKeys = setOf(AutomationEventKey.INTERVAL))
        },
    )

    private val previousAppTracker = PreviousAppTracker(
        context = host.context,
        packageManager = host.context.packageManager,
        scope = host.coroutineScope,
        startActivity = { host.context.startActivity(it) },
        rootInActiveWindowPackageName = { host.accessibilityService.rootInActiveWindow?.packageName?.toString() },
        ownPackageName = host.context.packageName,
    )

    // 最近应用黑名单变更时立即修剪历史并重算 RecentAppsHub，改设置当下生效。
    private val recentAppsBlocklistObserver = host.coroutineScope.launch {
        runtimeSettingsStore.state
            .map { it.actionSettings.recentAppsBlocklist.toSet() }
            .distinctUntilChanged()
            .collect { previousAppTracker.onBlocklistChanged(it) }
    }

    private val actionDispatcher = ActionDispatcher(
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
                actionLibrarySettings = s.actionLibrarySettings,
                automationRules = s.automationRules,
                globalVariables = s.globalVariables,
            )
        },
        onToggleQuickAppLauncher = { anchorX, anchorY ->
            hunoia.luno.quicklaunch.QuickAppLauncherOverlay.show(anchorX, anchorY)
        },
        onShowQuickSearch = { hunoia.luno.quicklaunch.QuickSearchActivity.start(host.context) },
        onShowGridPanel = { panelId, anchorX, anchorY ->
            hunoia.luno.gridpanel.GridPanelOverlay.show(panelId, anchorX, anchorY)
        },
        onShowVolumeScrub = { config -> volumeScrubRuntime.show(config) },
        onShowPointer = { pointerRuntime.show() },
        onHideGestureButton = { button, delayMs ->
            if (button != null) buttonHideRuntime.hideTemporarily(listOf(button.id), delayMs)
        },
        onHideGestureButtons = { target, buttonIds, delayMs ->
            val allIds = runtimeSettingsStore.snapshot().gestureButtons.map { it.id }
            buttonHideRuntime.hideTemporarily(
                hunoia.luno.action.model.resolveButtonTargetIds(target, allIds, buttonIds),
                delayMs,
            )
        },
        onGlobalVariableChanged = { name, value ->
            // 跨规则嵌套调用发生在同一条协程链里：DataStore 回流前先更新运行时快照，
            // 否则紧接着的 runRule/IF 会读到写入前的全局变量。
            val updated = runtimeSettingsStore.applyGlobalVariable(name, value)
            refreshGestureButtons(
                delayMs = 0L,
                automationKeys = setOf(hunoia.luno.runtime.automation.AutomationEventKey.VARIABLE),
                globalVariablesOverride = updated,
            )
        },
    )

    /** 同规则重入控制：规则 id → 正在跑的 Job 列表（ALLOW_PARALLEL 时一个 id 可有多条）。 */
    private val runningRuleJobs = mutableMapOf<String, MutableList<Job>>()

    /** 跳过诊断节流：同规则同原因 60s 内只落一条，避免通知风暴把历史写爆。 */
    private val skipLogGate = mutableMapOf<Pair<String, String>, Long>()

    private val sequenceExecutor = SequenceExecutor(
        runSingleAction = { action, ctx, variables ->
            actionDispatcher.runStepAction(action, ctx, variables)
        },
        resolveTemplate = { text, ctx, variables ->
            actionDispatcher.resolveStepTemplate(text, ctx, variables)
        },
        refreshContext = { buildRuntimeState(peekPulses = true).toConditionContext(
            runtimeSettingsStore.snapshot().globalVariables.toTemplateMap(),
        ) },
        writeGlobalVariable = { name, value ->
            // 先等落盘完成再更新运行时快照：DataStore 写失败时本次运行只保留局部变量，
            // 不能让引擎误以为全局值已经生效。
            ConfigProvider.setGlobalVariable(name, value)
            val updated = runtimeSettingsStore.applyGlobalVariable(name, value)
            refreshGestureButtons(
                delayMs = 0L,
                automationKeys = setOf(hunoia.luno.runtime.automation.AutomationEventKey.VARIABLE),
                globalVariablesOverride = updated,
            )
        },
    )

    /** 权限授予/撤销后重读 Wi-Fi/蓝牙条件源；主进程经 Binder 调用。 */
    fun refreshConditionSources() {
        if (!started) return
        conditionSources.refresh()
    }

    /** 运行中的规则 id：规则列表页展示「运行中」与停止入口。 */
    fun runningRuleIds(): Set<String> = synchronized(runningRuleJobs) {
        runningRuleJobs.filterValues { it.isNotEmpty() }.keys.toSet()
    }

    /** 手动停止一条规则的所有实例；返回是否真的有实例被取消。 */
    fun stopRule(ruleId: String): Boolean {
        val jobs = synchronized(runningRuleJobs) {
            runningRuleJobs[ruleId]?.toList().orEmpty()
        }
        jobs.forEach { it.cancel() }
        return jobs.isNotEmpty()
    }

    /**
     * 编辑页「测试运行」：条件/约束按当前快照求值，步骤真实跑一次。
     * 不经过引擎仲裁/节流，也不占单飞名额；结果经回调回到主线程。
     */
    fun runRuleTest(
        rule: AutomationRule,
        executeSteps: Boolean = true,
        onResult: (RuleTestResult) -> Unit,
    ) {
        host.coroutineScope.launch {
            val ctx = try {
                buildRuntimeState(consumePulses = false, peekPulses = true).toConditionContext(
                    runtimeSettingsStore.snapshot().globalVariables.toTemplateMap(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 快照构建失败也必须回调，否则编辑页会一直停在「测试运行中」。
                withContext(Dispatchers.Main) {
                    onResult(
                        RuleTestResult(
                            conditionHit = false,
                            constraintsHit = false,
                            executed = false,
                            reason = (e.message ?: e.javaClass.name).take(120),
                        )
                    )
                }
                return@launch
            }
            val conditionHit = try {
                rule.condition.matches(ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            val constraintsEmpty = rule.constraints.items.isEmpty()
            val constraintsHit = try {
                constraintsEmpty || rule.constraints.matches(ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (!executeSteps) {
                withContext(Dispatchers.Main) {
                    onResult(
                        RuleTestResult(
                            conditionHit = conditionHit,
                            constraintsHit = constraintsHit,
                            constraintsEmpty = constraintsEmpty,
                            executed = false,
                            reason = RuleTestResult.CONDITION_ONLY,
                        )
                    )
                }
                return@launch
            }
            val result = try {
                val records = if (rule.maxRunMs > 0L) {
                    withTimeoutOrNull(rule.maxRunMs) {
                        sequenceExecutor.runSteps(
                            rule.effect.steps,
                            ctx,
                            runtimeSettingsStore.snapshot().globalVariables.toTemplateMap().toMutableMap(),
                        )
                    }
                } else {
                    sequenceExecutor.runSteps(
                        rule.effect.steps,
                        ctx,
                        runtimeSettingsStore.snapshot().globalVariables.toTemplateMap().toMutableMap(),
                    )
                }
                if (records == null) {
                    RuleTestResult(
                        conditionHit = conditionHit,
                        constraintsHit = constraintsHit,
                        constraintsEmpty = constraintsEmpty,
                        executed = true,
                        reason = ExecutionReason.TIMEOUT,
                    )
                } else {
                    RuleTestResult(
                        conditionHit = conditionHit,
                        constraintsHit = constraintsHit,
                        constraintsEmpty = constraintsEmpty,
                        executed = true,
                        steps = records,
                        reason = records.firstOrNull { !it.ok && !it.skipped }?.let {
                            ExecutionReason.INTERRUPTED_PREFIX + it.index
                        } ?: ExecutionReason.OK,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RuleTestResult(
                    conditionHit = conditionHit,
                    constraintsHit = constraintsHit,
                    constraintsEmpty = constraintsEmpty,
                    executed = true,
                    reason = (e.message ?: e.javaClass.name).take(120),
                )
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /** 跳过诊断：同规则同原因窗口内只留一条，内容不含通知原文。 */
    private fun logSkip(ruleId: String, ruleName: String, nowMs: Long, reason: String) {
        val at = if (nowMs > 0L) nowMs else SystemClock.uptimeMillis()
        val shouldLog = synchronized(skipLogGate) {
            val key = ruleId to reason
            val lastAt = skipLogGate[key]
            if (lastAt != null && at - lastAt < SKIP_LOG_WINDOW_MS) {
                false
            } else {
                skipLogGate[key] = at
                true
            }
        }
        if (!shouldLog) return
        host.coroutineScope.launch {
            ConfigProvider.appendAutomationEvent(
                AutomationEvent(
                    ruleId = ruleId,
                    ruleName = ruleName,
                    timeMs = at,
                    wallTimeMs = System.currentTimeMillis(),
                    fired = false,
                    reason = reason,
                )
            )
        }
    }

    private val automationEngine = AutomationEngine(
        onFireRule = { rule, ctx ->
            if (!launchRuleExecution(rule, ctx)) {
                logSkip(rule.id, rule.name, ctx.nowMs, SkipReason.RULE_BUSY.code)
            }
        },
        onEvents = { events ->
            // 一轮求值的跳过事件合成一次写盘（引擎侧已攒批）。
            // Release 也保留诊断，但同规则同原因 60s 内只留一条：
            // 既能让用户看到「为什么没触发」，又不会让吵闹通知源把历史写爆。
            if (events.isNotEmpty()) {
                val toWrite = if (BuildConfig.DEBUG) {
                    events
                } else {
                    events.filter { logSkipGateAllows(it) }
                }
                if (toWrite.isNotEmpty()) {
                    host.coroutineScope.launch { ConfigProvider.appendAutomationEvents(toWrite) }
                }
            }
        },
        preflightBlockReason = { rule ->
            // 权限预检必须在 markFired 之前：缺权限时规则没有执行，不能吃掉冷却/窗口次数。
            val snapshot = runtimeSettingsStore.snapshot()
            if (hunoia.luno.action.capability.RuleCapabilityScanner.report(
                    host.context,
                    rule,
                    snapshot.automationRules,
                    snapshot.actionLibrarySettings,
                ).hasMissing
            ) {
                SkipReason.PERMISSION_MISSING
            } else {
                null
            }
        },
    )

    /**
     * 启动一条规则实例：重入判定、运行中集合登记、超时与执行记录都在这里收口。
     * 返回 false = 被 BLOCK_NEW 拦下（调用方记 RULE_BUSY）。
     */
    private fun launchRuleExecution(
        rule: AutomationRule,
        ctx: ConditionContext,
    ): Boolean {
        // P3-3 触发前预检已前移到 AutomationEngine：缺能力时不会消耗节流窗口。
        val job = synchronized(runningRuleJobs) {
            val existing = runningRuleJobs[rule.id]?.toList().orEmpty()
            if (existing.isNotEmpty() && rule.reentry == RuleReentry.BLOCK_NEW) {
                null
            } else {
                if (existing.isNotEmpty() && rule.reentry == RuleReentry.STOP_CURRENT_AND_RUN_NEW) {
                    // 取消是协作式的：旧实例若卡在不响应取消的动作里，新旧会短暂重叠。
                    // 不在这里 join，避免新触发被旧动作拖住。
                    existing.forEach { it.cancel() }
                }
                val newJob = host.coroutineScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val records = if (rule.maxRunMs > 0L) {
                            withTimeoutOrNull(rule.maxRunMs) {
                                sequenceExecutor.runSteps(
                                    rule.effect.steps,
                                    ctx,
                                    runtimeSettingsStore.snapshot().globalVariables.toTemplateMap().toMutableMap(),
                                )
                            }
                        } else {
                            sequenceExecutor.runSteps(
                                rule.effect.steps,
                                ctx,
                                runtimeSettingsStore.snapshot().globalVariables.toTemplateMap().toMutableMap(),
                            )
                        }
                        // STOP_CURRENT_AND_RUN_NEW / 手动停止是协作式取消：动作即使吞掉取消，
                        // 也不能再落一条“已完成”的历史。
                        if (!isActive) return@launch
                        if (records == null) {
                            ConfigProvider.appendAutomationEvent(
                                AutomationEvent(
                                    ruleId = rule.id,
                                    ruleName = rule.name,
                                    timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                                    fired = true,
                                    reason = ExecutionReason.TIMEOUT,
                                )
                            )
                        } else {
                            val reason = records.firstOrNull { !it.ok && !it.skipped }?.let {
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
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 同上：已取消的实例不再补写历史。
                        if (!isActive) return@launch
                        ConfigProvider.appendAutomationEvent(
                            AutomationEvent(
                                ruleId = rule.id,
                                ruleName = rule.name,
                                timeMs = ctx.nowMs, wallTimeMs = System.currentTimeMillis(),
                                fired = true,
                                reason = (e.message ?: e.javaClass.name).take(120),
                            )
                        )
                    }
                }
                runningRuleJobs.getOrPut(rule.id) { mutableListOf() }.add(newJob)
                newJob
            }
        } ?: return false
        // 先登记再挂完成回调：scope 已取消时 LAZY 任务可能在挂回调前就完成，
        // 先入表能保证回调仍会把它清掉。
        job.invokeOnCompletion {
            synchronized(runningRuleJobs) {
                val list = runningRuleJobs[rule.id] ?: return@invokeOnCompletion
                list.remove(job)
                if (list.isEmpty()) runningRuleJobs.remove(rule.id)
            }
        }
        job.start()
        return true
    }

    /** onEvents 用：与 [logSkip] 共用窗口，但返回是否应写入。 */
    private fun logSkipGateAllows(event: AutomationEvent): Boolean = synchronized(skipLogGate) {
        val at = if (event.timeMs > 0L) event.timeMs else SystemClock.uptimeMillis()
        val key = event.ruleId to event.reason
        val lastAt = skipLogGate[key]
        if (lastAt != null && at - lastAt < SKIP_LOG_WINDOW_MS) {
            false
        } else {
            skipLogGate[key] = at
            true
        }
    }

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

    /** 动作面板 Dismissed 延迟发布的代际：面板在淡出期间重开则旧事件作废。 */
    private var actionPanelDismissGeneration = 0

    /** 动作面板的 Shown/Dismissed 是否由「交互窗」路径发过（needsPanelOverlay=true）。 */
    private var actionPanelOverlayEventPosted = false

    /** 动作面板需要全屏宿主窗（面板内容画在主 overlay 里）。 */
    private var actionPanelFullscreen = false

    /**
     * 按住类触发的指针光标画在主 overlay 里（[hunoia.luno.runtime.pointer.rememberPointerHandle]）。
     * 主 overlay 平时收成 1x1 省 buffer，光标就看不见 —— 指针会话期间必须临时撑全屏。
     */
    private var pointerCursorHosted = false

    private fun syncMainOverlayFullscreen() {
        mainOverlayController.setPanelVisible(actionPanelFullscreen || pointerCursorHosted)
    }

    /** 动作面板的 Shown/Dismissed 是否由「visible」路径补发（触钮直发路径）。 */
    private var actionPanelVisibilityEventPosted = false
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
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "volume scrub overlay requested")
            volumeScrubRuntime.show(config)
        }

        override fun onPointerStart(settings: hunoia.luno.config.model.GestureSettings.Pointer): Boolean {
            val started = pointerRuntime.beginBridge()
            // 已在会话中（start=false）时不要收回宿主窗：光标还在画。
            if (started) pointerCursorHosted = true
            syncMainOverlayFullscreen()
            return started
        }

        override fun onPointerShow(settings: hunoia.luno.config.model.GestureSettings.Pointer): Boolean {
            return pointerRuntime.showBridge(settings)
        }

        override fun onPointerEnd() {
            pointerRuntime.end()
            pointerCursorHosted = false
            syncMainOverlayFullscreen()
        }

        override fun onPointerActionAtPosition(x: Int, y: Int) {
            pointerRuntime.performBridgeActionAt(x, y)
            // 松手后点击由无障碍延迟派发，光标不必再留着，宿主窗可以收回。
            pointerCursorHosted = false
            syncMainOverlayFullscreen()
        }

        override fun onActionPanelOverlayChanged(show: Boolean) {
            if (show) {
                actionPanelDismissGeneration++
                actionPanelOverlayEventPosted = true
                transientOverlayController.attachActionPanelOverlay()
                Events.post(PanelShownEvent())
            } else {
                actionPanelOverlayEventPosted = false
                // 交互窗立即拆（不挡住底下），但面板本体还在 a11y overlay 里淡出 400ms；
                // Dismissed 延到淡出结束再发，否则识屏截屏/悬浮球恢复会看到正在消失的面板。
                transientOverlayController.detachActionPanelOverlay()
                val generation = ++actionPanelDismissGeneration
                host.coroutineScope.launch {
                    delay(ACTION_PANEL_FADE_OUT_MS)
                    if (generation == actionPanelDismissGeneration) {
                        Events.post(PanelDismissedEvent())
                    }
                }
            }
        }

        override fun onActionPanelVisibilityChanged(visible: Boolean) {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "action panel visible=$visible")
            actionPanelFullscreen = visible
            syncMainOverlayFullscreen()
            // 触钮直发动作面板时 needsPanelOverlay=false，交互窗不会发事件；用这里的
            // visible 配对补上，保证识屏截图能看到「面板已真正退场」。
            // false 回调本身已经延后 400ms（淡出结束），所以立即发 Dismissed 即可。
            if (visible) {
                if (!actionPanelOverlayEventPosted && !actionPanelVisibilityEventPosted) {
                    actionPanelVisibilityEventPosted = true
                    Events.post(PanelShownEvent())
                }
            } else if (actionPanelVisibilityEventPosted) {
                actionPanelVisibilityEventPosted = false
                Events.post(PanelDismissedEvent())
            }
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

    /**
     * 「需要看见底下真实屏幕」的动作前置：先把自家全屏面板收掉。
     * 网格面板在非「拉起应用」的动作上不会自动关闭，留着会挡住识屏取链的节点扫描。
     */
    fun dismissPanelsForScreenAction() {
        hunoia.luno.gridpanel.GridPanelOverlay.dismissNow()
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
                LOG_TAG,
                "gesture decision trigger=${decision?.triggerType} dir=${decision?.actionDirection} " +
                    "button=${sourceButton?.id} action=${action.typeId}",
            )
        }
        // 手势触发动作与手势事件进总线是两条独立路径：前者立即执行，后者供自动化规则订阅。
        environmentTracker.onGesturePerformed(sourceButton?.id ?: "", sourceButton?.name.orEmpty())
        // 真手势按物理触点归侧广播：悬浮球碰哪侧即时跟到哪侧。
        // 判据包含「有 sourceButton」：触钮上的**点按**动作 decision 可能为空，
        // 旧实现只认 decision != null，表现为只有会产 decision 的按钮能跟手换位。
        // 球点按 / 面板点选走 BallTapActionEvent（sourceButton = null），仍不参与。
        if ((decision != null || sourceButton != null) && touchPosition.x.isFinite()) {
            Events.post(
                TouchButtonSideEvent(
                    leftSide = touchPosition.x < DensityProvider.screenWidthPx / 2f,
                ),
            )
        }
        val touch = if (touchPosition.x.isFinite() && touchPosition.y.isFinite()) {
            touchPosition.x.roundToInt() to touchPosition.y.roundToInt()
        } else {
            null
        }
        actionDispatcher.dispatch(
            action.withoutVariantKey(),
            sourceButton,
            touch,
            { buildRuntimeState(peekPulses = true).toConditionContext(
                runtimeSettingsStore.snapshot().globalVariables.toTemplateMap(),
            ) },
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

    /** 通知脉冲：驱动 NOTIFICATION 域评估（Hub 现为多观察者，胶囊悬浮球也订阅同一源）。 */
    private val notificationObserver: (hunoia.luno.runtime.condition.NotificationPulse) -> Unit = { pulse ->
        environmentTracker.onNotificationPosted(pulse)
        refreshGestureButtons(automationKeys = setOf(AutomationEventKey.NOTIFICATION))
    }

    private val notificationRemovedObserver: (hunoia.luno.runtime.condition.NotificationPulse) -> Unit = { pulse ->
        environmentTracker.onNotificationRemoved(pulse)
        refreshGestureButtons(automationKeys = setOf(AutomationEventKey.NOTIFICATION))
    }

    private val notificationStateObserver: () -> Unit = {
        refreshGestureButtons(delayMs = 0L, automationKeys = setOf(AutomationEventKey.NOTIFICATION))
    }

    /** 剪贴板球/面板读到新文本；不承诺后台实时监听。 */
    private val clipboardObserver: (String) -> Unit = { text ->
        environmentTracker.onClipboardChanged(
            hunoia.luno.runtime.condition.ClipboardPulse(text),
        )
        refreshGestureButtons(delayMs = 0L, automationKeys = setOf(AutomationEventKey.CLIPBOARD))
    }

    private val packageChangeListener: (hunoia.luno.bridge.system.PackageChangeEvent) -> Unit = { event ->
        environmentTracker.onPackageChanged(
            hunoia.luno.runtime.condition.PackageChangePulse(event.action, event.packageName),
        )
        refreshGestureButtons(delayMs = 0L, automationKeys = setOf(AutomationEventKey.PACKAGE))
    }

    private val shareReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AutomationShareBridge.ACTION) return
            val text = intent.getStringExtra(AutomationShareBridge.EXTRA_TEXT) ?: return
            val source = intent.getStringExtra(AutomationShareBridge.EXTRA_SOURCE).orEmpty()
            val url = hunoia.luno.link.LinkUrls.extractUrls(text).firstOrNull()
            environmentTracker.onShareReceived(
                hunoia.luno.runtime.condition.SharePulse(text, url, source),
            )
            refreshGestureButtons(delayMs = 0L, automationKeys = setOf(AutomationEventKey.SHARE))
        }
    }

    /** 勿扰变化由通知监听服务推入（需通知访问权限）。 */
    private val interruptionObserver: (Boolean) -> Unit = { on -> dndTracker.onSystemChanged(on) }

    /**
     * 部件被点后每 40ms 看一次应用窗口列表：新应用窗口一出现就收口，
     * 不等 MIUI 那个约 0.5s 后才到的 TYPE_WINDOW_STATE_CHANGED。
     * 只看应用窗口，音量球/媒体胶囊等系统窗口不是 TYPE_APPLICATION，不会误关。
     */
    private fun startWidgetLaunchPoll() {
        if (!started) return
        val now = SystemClock.elapsedRealtime()
        if (widgetLaunchPoll != null) {
            // 同一次点击可能先 DOWN 后 UP：保留首次基线，只把观察窗口往后延。
            widgetLaunchDeadline = maxOf(widgetLaunchDeadline, now + 1_200L)
            return
        }
        widgetLaunchPackages = currentAppWindowPackages()
        // WINDOWS_CHANGED 差集基线同步播种：事件路径只在部件点击期间维护它（见 onAccessibilityEvent），
        // 常态不再为无关窗口变化付全量扫描。
        appWindowPackages = widgetLaunchPackages
        // 点击当下的可见窗口 id：新应用哪怕第一个 a11y 事件比 WINDOW_STATE_CHANGED 早，
        // 只要它带的 windowId 不在这个集合里，就能立刻收口。
        widgetLaunchWindowIds = currentWindowIds()
        widgetLaunchDeadline = now + 1_200L
        val poll = object : Runnable {
            override fun run() {
                if (!started) {
                    stopWidgetLaunchPoll()
                    return
                }
                val current = currentAppWindowPackages()
                val added = current - widgetLaunchPackages
                val target = added.firstOrNull {
                    it != host.context.packageName && !hunoia.luno.gridpanel.isSystemOverlayPackage(it)
                }
                if (target != null) {
                    if (BuildConfig.DEBUG) {
                        Log.i(LOG_TAG, "widget launch window added: $target (added=$added)")
                    }
                    hunoia.luno.gridpanel.GridPanelOverlay.onForegroundPackageChanged(
                        fromPackage = environmentTracker.currentPackageName.ifEmpty { target },
                        toPackage = target,
                    )
                    stopWidgetLaunchPoll()
                    return
                }
                if (SystemClock.elapsedRealtime() >= widgetLaunchDeadline) {
                    stopWidgetLaunchPoll()
                    return
                }
                mainHandler.postDelayed(this, 40L)
            }
        }
        widgetLaunchPoll = poll
        mainHandler.postDelayed(poll, 40L)
    }

    private fun stopWidgetLaunchPoll() {
        widgetLaunchPoll?.let { mainHandler.removeCallbacks(it) }
        widgetLaunchPoll = null
        widgetLaunchPackages = emptySet()
        widgetLaunchDeadline = 0L
    }

    private fun onKeyboardStateChanged(active: Boolean) {
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "keyboard active=$active")
        refreshGestureButtons(automationKeys = setOf(AutomationEventKey.KEYBOARD))
        if (active) startKeyboardWatch() else stopKeyboardWatch()
    }

    private fun startKeyboardWatch() {
        if (keyboardWatchJob?.isActive == true) return
        keyboardWatchJob = host.coroutineScope.launch {
            while (isActive) {
                delay(KEYBOARD_WATCH_MS)
                environmentTracker.pollKeyboardVisibility()
                if (!environmentTracker.isKeyboardInputActive) break
            }
        }
    }

    private fun stopKeyboardWatch() {
        keyboardWatchJob?.cancel()
        keyboardWatchJob = null
    }

    fun onSetOverlay() {
        if (started) return
        started = true
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "GestureCoordinator start")
        hunoia.luno.gridpanel.widget.PanelAppWidgetHostView.setTapListener { startWidgetLaunchPoll() }
        hunoia.luno.core.Events.subscribe(
            hunoia.luno.action.dispatcher.GestureDispatchEvent::class,
            gestureDispatchSubscriber,
        )
        runtimeSettingsStore.start()
        broadcastObserver.register()
        batteryTracker.register()
        audioVolumeTracker.sync(volumePollNeeded())
        conditionSources.start()
        // 键盘状态无法像 orientation/锁屏那样在构造期播种（rootInActiveWindow 需服务连接后才有），
        // 放这里：早于首评的设置收集器，此刻 root 已可查询。
        environmentTracker.seedKeyboardInputActive()
        // 通知脉冲：同 :service 进程静态直连；destroy 时移除，避免野回调。
        AutomationPulseHub.addNotificationObserver(notificationObserver)
        AutomationPulseHub.addNotificationRemovedObserver(notificationRemovedObserver)
        AutomationPulseHub.addNotificationStateObserver(notificationStateObserver)
        AutomationPulseHub.addClipboardObserver(clipboardObserver)
        PackageChangeReceiver.registerForEvents(host.context, packageChangeListener)
        runCatching {
            host.context.registerReceiver(
                shareReceiver,
                IntentFilter(AutomationShareBridge.ACTION),
                Context.RECEIVER_NOT_EXPORTED,
            )
        }
        // 勿扰变化由通知监听服务推入（需通知访问权限）；订阅即回放当前基线。
        AutomationPulseHub.addInterruptionObserver(interruptionObserver)
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
                    if (BuildConfig.DEBUG) Log.i(LOG_TAG, "gesture buttons changed: count=${state.gestureButtons.size}")
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

    /** 无障碍窗口列表里的应用窗口包名；需要 flagRetrieveInteractiveWindows，失败时返回空集。 */
    private fun currentAppWindowPackages(): Set<String> = runCatching {
        host.accessibilityService.windows.asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { window ->
                window.root?.packageName?.toString()?.takeIf { it.isNotEmpty() }
            }
            .toSet()
    }.getOrDefault(emptySet())

    private fun currentWindowIds(): Set<Int> = runCatching {
        host.accessibilityService.windows.asSequence()
            .map { it.id }
            .filter { it > 0 }
            .toSet()
    }.getOrDefault(emptySet())

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        previousAppTracker.onAccessibilityEvent(event)
        val beforePackage = environmentTracker.currentPackageName
        environmentTracker.onAccessibilityEvent(event)
        // 新应用先到的往往不是 WINDOW_STATE_CHANGED：只要事件来自一个点击时不可见的
        // 新窗口，就提前收口。底层应用/面板自身窗口的 id 在基线里，不会误触发。
        // 廉价短路（widgetLaunchWindowIds 常态为空）放在 packageName.toString() 分配之前。
        if (event != null && widgetLaunchWindowIds.isNotEmpty() &&
            event.windowId > 0 && event.windowId !in widgetLaunchWindowIds
        ) {
            val eventPackage = event.packageName?.toString().orEmpty()
            if (eventPackage.isNotEmpty() &&
                eventPackage !in widgetLaunchPackages &&
                eventPackage != host.context.packageName &&
                !hunoia.luno.gridpanel.isSystemOverlayPackage(eventPackage) &&
                hunoia.luno.gridpanel.widget.PanelAppWidgetHostView.hasRecentTap()
            ) {
                if (BuildConfig.DEBUG) {
                    Log.i(
                        LOG_TAG,
                        "early window event after widget tap: type=${event.eventType} " +
                            "pkg=$eventPackage window=${event.windowId}",
                    )
                }
                hunoia.luno.gridpanel.GridPanelOverlay.onForegroundPackageChanged(
                    fromPackage = beforePackage.ifEmpty { eventPackage },
                    toPackage = eventPackage,
                )
            }
        }
        // 窗口列表变化比 TYPE_WINDOW_STATE_CHANGED 更早：部件点击后新应用窗口一出现就收口，
        // 不然要等 0.5s 左右的应用窗口状态事件，面板会盖在新应用上“愣一下”。
        // 只看 TYPE_APPLICATION 窗口，音量球/胶囊等系统窗口天然被排除。
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            // 无部件点击在跟踪时整段跳过：currentAppWindowPackages 是全量窗口扫描
            // （每窗口 root 一次 Binder），动画/弹窗/IME 引起的窗口变化不该白付；基线已由
            // startWidgetLaunchPoll 在点击时播种。
            if (hunoia.luno.gridpanel.widget.PanelAppWidgetHostView.hasRecentTap()) {
                val current = currentAppWindowPackages()
                val added = current - appWindowPackages
                appWindowPackages = current
                if (added.isNotEmpty()) {
                    val target = added.firstOrNull {
                        it != host.context.packageName && !hunoia.luno.gridpanel.isSystemOverlayPackage(it)
                    }
                    if (target != null) {
                        if (BuildConfig.DEBUG) {
                            Log.i(LOG_TAG, "window added after widget tap: $target (added=$added)")
                        }
                        hunoia.luno.gridpanel.GridPanelOverlay.onForegroundPackageChanged(
                            fromPackage = beforePackage.ifEmpty { target },
                            toPackage = target,
                        )
                    }
                }
            }
        }
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "window changed: pkg=${event.packageName}")
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
            // 部件点击跟踪期间才维持差集基线（无点击不扫，见上方 WINDOWS_CHANGED 分支）。
            if (hunoia.luno.gridpanel.widget.PanelAppWidgetHostView.hasRecentTap()) {
                appWindowPackages = currentAppWindowPackages()
            }
            refreshGestureButtons(automationKeys = setOf(AutomationEventKey.APP))
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        val oldOrientation = environmentTracker.orientation
        if (oldOrientation != newConfig.orientation) {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "orientation=${newConfig.orientation}")
            environmentTracker.onOrientationChanged(newConfig.orientation)
            mainOverlayController.updateLayout()
            refreshGestureButtons()
        }
    }

    fun onDestroy() {
        // 属性初始化即 launch、不经 scopeJobs 登记：started 守卫之前就要取消，
        // 否则 onSetOverlay 换代时这条永不完结的 collect 会钉住整个旧协调器对象图。
        recentAppsBlocklistObserver.cancel()
        if (!started) return
        started = false
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "GestureCoordinator destroy")
        hunoia.luno.gridpanel.widget.PanelAppWidgetHostView.setTapListener(null)
        stopWidgetLaunchPoll()
        stopKeyboardWatch()
        hunoia.luno.core.Events.unsubscribe(
            hunoia.luno.action.dispatcher.GestureDispatchEvent::class,
            gestureDispatchSubscriber,
        )
        AutomationPulseHub.removeNotificationObserver(notificationObserver)
        AutomationPulseHub.removeNotificationRemovedObserver(notificationRemovedObserver)
        AutomationPulseHub.removeNotificationStateObserver(notificationStateObserver)
        AutomationPulseHub.removeClipboardObserver(clipboardObserver)
        PackageChangeReceiver.unregisterForEvents(host.context, packageChangeListener)
        runCatching { host.context.unregisterReceiver(shareReceiver) }
        AutomationPulseHub.removeInterruptionObserver(interruptionObserver)
        runtimeSettingsStore.stop()
        timeConditionTicker.stop()
        batteryTracker.unregister()
        audioVolumeTracker.unregister()
        musicTracker.unregister()
        torchTracker.unregister()
        conditionSources.stop()
        scopeJobs.forEach { it.cancel() }
        scopeJobs.clear()
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "GestureCoordinator release")
        mainOverlayController.release()
        buttonWindowController.release()
        // 销毁不经过 callbacks 的配对隐藏（那里各自发 PanelDismissed）：挂着面板被销毁时
        // 按拆掉的数量补发，否则 FloatBallService 的 panelCount 卡住、球一直隐藏。
        actionPanelDismissGeneration++
        if (actionPanelVisibilityEventPosted) {
            actionPanelVisibilityEventPosted = false
            Events.post(PanelDismissedEvent())
        }
        repeat(transientOverlayController.detachAll()) {
            Events.post(PanelDismissedEvent())
        }
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
            wifiSsid = conditionSources.wifiSsid,
            headphonesConnected = conditionSources.headphonesConnected,
            bluetoothAdapterOn = conditionSources.bluetoothAdapterOn,
            bluetoothAudioConnected = conditionSources.bluetoothAudioConnected,
            bluetoothDeviceName = conditionSources.bluetoothDeviceName,
            bluetoothDeviceAddress = conditionSources.bluetoothDeviceAddress,
            activeNotifications = hunoia.luno.runtime.automation.AutomationNotificationListener.activePulses(),
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

    private fun refreshGestureButtons(
        delayMs: Long = 100L,
        automationKeys: Set<String> = emptySet(),
        globalVariablesOverride: GlobalVariables? = null,
    ) {
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
            automationRuntime.evaluate(keys, globalVariablesOverride)
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
