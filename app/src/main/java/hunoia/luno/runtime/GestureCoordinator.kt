package hunoia.luno.runtime

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import hunoia.luno.core.LunoLog as Log
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
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.actionPanelId
import hunoia.luno.core.domain.withoutVariantKey
import hunoia.luno.runtime.gesturepanel.ACTION_PANEL_FADE_OUT_MS
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import hunoia.luno.bridge.system.isAccessibilitySettingsOn
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.QuickAppSnapshot
import hunoia.luno.quickaccess.query.AppSearch
import hunoia.luno.quickaccess.query.DisabledAppQuery
import java.util.Locale
import hunoia.luno.action.dispatcher.ActionDispatcher
import hunoia.luno.runtime.environment.PreviousAppTracker
import hunoia.luno.runtime.button.shouldShowSurface
import hunoia.luno.runtime.RuntimeStateHub
import hunoia.luno.runtime.button.ButtonHideRuntime
import hunoia.luno.runtime.button.ButtonRefreshCoordinator
import hunoia.luno.runtime.environment.BroadcastObserver
import hunoia.luno.runtime.environment.EnvironmentTracker
import hunoia.luno.runtime.overlay.GestureOverlayCallbacks
import hunoia.luno.runtime.overlay.ButtonWindowController
import hunoia.luno.runtime.overlay.MainOverlayController
import hunoia.luno.runtime.overlay.TransientOverlayController
import hunoia.luno.runtime.settings.SettingsStore
import hunoia.luno.runtime.volume.VolumeScrubRuntime
import hunoia.luno.runtime.pointer.PointerRuntime
import hunoia.luno.runtime.recentspicker.RecentsPickerRuntime
import hunoia.luno.runtime.recentspicker.RecentsPickerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import hunoia.luno.gesture.touch.TouchAction
import hunoia.luno.gesture.touchsource.GlobalLongPressTrigger
import hunoia.luno.core.LOG_TAG

/** 冷启动/悬浮窗重建期间容器的面板列表未灌满时，揭示事件的等待重试参数。 */
private const val BALL_PANEL_REVEAL_MAX_ATTEMPTS = 40

/**
 * 识屏让位带在状态栏高度之外再加的余量（dp）。
 *
 * 0 = 只让出状态栏本身（超级岛胶囊所在带）：岛卡片的「长按 + 拖动」和识屏是同一手势形态，
 * 只能按起点位置区分。展开态大岛卡片伸到状态栏以下的部分**不**让位，否则屏幕顶部会多出
 * 一条不能识屏的死区；代价是从卡片下半部分起手时识屏仍可能先弹出来。
 */
private const val SYSTEM_BAR_GESTURE_BAND_DP = 0f

/** 键盘在屏期间的短轮询间隔：捕捉没有 a11y 事件的 IME 收起（如返回键/手势）。 */
private const val KEYBOARD_WATCH_MS = 600L

private val BALL_PANEL_REVEAL_RETRY_MS = 250.milliseconds

class GestureCoordinator(
    private val host: GestureHost,
) {
    val runtimeSettingsStore = SettingsStore(host.coroutineScope)

    /** 用户意图序号：同一进程内保证幂等键唯一，重放同一请求时由调用方复用原键。 */
    private val intentSeq = java.util.concurrent.atomic.AtomicLong()
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

    /**
     * 识屏长按配置：跟随手势总开关。总开关关闭时不启动内核触摸流，
     * 也不接受全屏长按（与侧边手势保持一致）。
     */
    private val screenPickGestureConfig: hunoia.luno.config.model.ScreenPickGesture
        get() = runtimeSettingsStore.snapshot().initialSettings.let { initial ->
            if (!initial.gestureEnabled) {
                initial.screenPickGesture.copy(enabled = false)
            } else {
                initial.screenPickGesture
            }
        }

    /** 供 Surface 层复用同一套长按/拖动阈值。 */
    fun currentScreenPickGesture(): hunoia.luno.config.model.ScreenPickGesture =
        screenPickGestureConfig

    /** DOWN 后 100ms 抓一次 a11y 快照，避免应用自己的长按菜单污染取词结果。 */
    private val screenTextPreCapture by lazy {
        hunoia.luno.runtime.overlay.text.ScreenTextPreCapture(
            service = host.accessibilityService,
            ownPackage = host.context.packageName,
            scope = host.coroutineScope,
        )
    }

    /**
     * 全屏长按识屏检测器：复用 architecture §2.3 内核触摸流（需 Shizuku），不消费触摸。
     * 触发后由同一股原始触摸流继续喂拖动/抬手，幽灵卡靠它跟随手指。
     */
    /**
     * 识屏的「让位」区域：状态栏 + 超级岛胶囊。
     *
     * 岛卡片的长按拖动分享和「长按后拖动识屏」完全同形，只能按起点位置区分：
     * 起点落在状态栏带内就交给 SystemUI，否则拖拽分享会先弹识屏卡。
     */
    private val systemBarGestureBandPx by lazy {
        val res = host.context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (id > 0) {
            runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0)
        } else {
            0
        }
        bar + (SYSTEM_BAR_GESTURE_BAND_DP * hunoia.luno.bridge.ui.DensityProvider.density).toInt()
    }

    private val globalScreenPickTrigger = GlobalLongPressTrigger(
        config = { screenPickGestureConfig },
        shouldIgnoreStart = { _, y -> y <= systemBarGestureBandPx },
        onTrigger = { x, y ->
            hunoia.luno.service.SideGestureService.current?.showScreenPickPortal(
                x,
                y,
                screenTextPreCapture.peek(),
            )
        },
        onDrag = { x, y ->
            hunoia.luno.service.SideGestureService.current?.moveScreenPickPortal(x, y)
        },
        onRelease = { x, y ->
            hunoia.luno.service.SideGestureService.current?.releaseScreenPickPortal(x, y)
        },
        onPreCapture = { x, y, _ -> screenTextPreCapture.start(x, y) },
        onPreCaptureCancel = { screenTextPreCapture.cancel() },
    )

    /** 自家面板（搜索/网格/动作面板）在屏计数：面板上的长按不能当“识屏”。 */
    private val openPanels = hunoia.luno.runtime.overlay.OpenPanelCounter()
    private var refreshJob: Job? = null

    /** 键盘在屏期间的可见性短轮询：IME 可能无 a11y 事件地消失，只靠事件会一直停在「弹出」。 */
    private var keyboardWatchJob: Job? = null

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
            // 锁屏收口：无障碍 overlay 会盖在 keyguard 上。动作面板的内容画在主 a11y overlay
            // （MainOverlayController）里，只 closeImmediately 面板窗不够 —— 必须同时取消会话
            // 并把主窗收回 1x1。解锁不自动恢复，需用户重新触发。
            closeTransientSurfaces()
            hunoia.luno.service.SideGestureService.current?.closeOverlaysForLock()
            host.runtimePanelOverlay.closeImmediately()
            refreshGestureButtons()
        },
        onScreenOn = {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "screen on")
            refreshGestureButtons()
        },
        onUserPresent = {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "user present")
            environmentTracker.isNowInLockScreenPage = false
            refreshGestureButtons()
        }
    )

    private val buttonHideRuntime = ButtonHideRuntime(
        scope = host.coroutineScope,
        // 显式动作路径不吃 a11y 事件的 100ms 防抖。
        onStateChanged = { refreshGestureButtons(delayMs = 0L) },
    )

    /** 启动能力库的默认实现（`:capability:launch`）：解冻与小窗设置由这里注入。 */
    private val appLauncher: hunoia.luno.core.domain.AppLauncher =
        hunoia.luno.launch.DefaultAppLauncher(
            context = host.context.applicationContext,
            advancedSettings = { runtimeSettingsStore.snapshot().advancedSettings },
            unfreezePackage = { pkg ->
                suspendCancellableCoroutine { cont ->
                    host.requestEnableDisabledPackage(pkg) { ok -> if (cont.isActive) cont.resume(ok) }
                }
            },
        )

    private val previousAppTracker = PreviousAppTracker(
        context = host.context,
        packageManager = host.context.packageManager,
        scope = host.coroutineScope,
        // 走统一启动口：历史里已带上前台实例的 userId，Shizuku 直达分身，
        // 不再被 MIUI XSpace 拦成「主应用 / 分身」选择器。
        launchPackage = { app ->
            appLauncher.launch(
                hunoia.luno.core.domain.LaunchSpec(
                    packageName = app.packageName,
                    userId = app.userId,
                ),
            )
        },
        // a11y 只给包名：成为前台时解析一次实际实例（仅分身包会查任务栈）。
        resolveUserId = { packageName ->
            hunoia.luno.quickaccess.launch.Launcher
                .currentUserIdForPackage(host.context, packageName)
        },
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

    private val executeEnvironment: hunoia.luno.execute.ExecuteEnvironment = LunoExecuteEnvironment(host)
    private val executeMessages = LunoExecuteMessages(host.context)

    /** 执行口的 Surface 副作用端口：执行口只说要弹哪个面，窗口控制留在这里。 */
    private val executeSurfaces = object : hunoia.luno.execute.SurfaceHost {
        override fun toggleQuickAppLauncher(anchorX: Int, anchorY: Int) {
            hunoia.luno.quickaccess.QuickAppLauncherOverlay.show(anchorX, anchorY)
        }

        override fun showQuickSearch() {
            hunoia.luno.quickaccess.QuickSearchActivity.start(host.context)
        }

        override fun showSpeechRecognition(): Boolean {
            hunoia.luno.quickaccess.SpeechRecognitionActivity.start(host.context)
            return true
        }

        override fun showGridPanel(panelId: String, anchorX: Int, anchorY: Int) {
            hunoia.luno.gridpanel.GridPanelOverlay.show(panelId, anchorX, anchorY)
        }

        override fun gridPanelExists(panelId: String): Boolean =
            runtimeSettingsStore.snapshot().actionSettings.actionGridPanels.panelById(panelId) != null

        override fun showVolumeScrub(stepThresholdDp: Int?): Boolean = volumeScrubRuntime.show(
            hunoia.luno.config.model.VolumeScrubConfig(
                stepThresholdDp = stepThresholdDp
                    ?: hunoia.luno.config.defaults.ActionSettingsDefaults.VolumeScrubStepThresholdDp,
            ),
        )

        override fun showPointer(): Boolean = pointerRuntime.show()

        override fun hideGestureButton(buttonId: String, delayMs: Long) {
            buttonHideRuntime.hideTemporarily(listOf(buttonId), delayMs)
        }

        override suspend fun previousApp() {
            previousAppTracker.previousApp()
        }

        override suspend fun launchApp(spec: hunoia.luno.core.domain.LaunchSpec): Boolean =
            appLauncher.launch(spec)

        override suspend fun openUrl(
            url: String,
            miniWindow: Boolean,
            queryParameters: List<hunoia.luno.core.domain.QueryParam>,
            anchor: Pair<Int, Int>?,
        ): Boolean = appLauncher.openUrl(url, miniWindow, queryParameters, anchor)

        override suspend fun launchShortcut(dataJson: String): Boolean =
            appLauncher.launchShortcut(dataJson)

        override suspend fun openAppDetails(packageName: String, userId: Int) =
            appLauncher.openAppDetails(packageName, userId)

        override suspend fun launchInPopup(packageName: String, anchor: Pair<Int, Int>?) =
            appLauncher.launchInPopup(packageName, anchor)

        override suspend fun launchMiniWindow(packageName: String, anchor: Pair<Int, Int>?) =
            appLauncher.launchMiniWindow(packageName, anchor)

        override fun showScreenPick(): Boolean =
            hunoia.luno.service.SideGestureService.current?.showScreenPick() == true

        override fun showScreenAnnotate(): Boolean =
            hunoia.luno.service.SideGestureService.current?.showScreenAnnotate() == true

        override fun openImageAnnotate(): Boolean =
            hunoia.luno.ui.annotate.ImageAnnotateActivity.startPicker(host.context)

        override fun showOcrSegmentText(text: String): Boolean =
            hunoia.luno.domain.ocr.ClipboardOcrLauncher.launchSegmentText(host.context, text)

        override suspend fun showClipboardOcr(
            uri: android.net.Uri,
            mimeType: String,
        ): hunoia.luno.core.domain.ActionResult =
            hunoia.luno.domain.ocr.ClipboardOcrLauncher.launchImage(host.context, uri, mimeType)

        override fun shareFile(): hunoia.luno.core.domain.ActionResult = runCatching {
            host.context.startActivity(
                Intent(host.context, hunoia.luno.ui.share.ShareFilePickActivity::class.java).apply {
                    setAction(hunoia.luno.ui.share.ShareFilePickActivity.ACTION_SHARE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            hunoia.luno.core.domain.ActionResult.Success()
        }.getOrElse { e ->
            hunoia.luno.core.domain.ActionResult.Failed(
                hunoia.luno.core.domain.ActionFailure.ExecutionFailed,
                e.message ?: e.javaClass.name,
            )
        }

        override suspend fun setFloatBallVisible(
            visible: Boolean?,
            hideForMs: Long,
        ): hunoia.luno.core.domain.ActionResult {
            if (hideForMs > 0) {
                Events.post(hunoia.luno.runtime.floatball.FloatBallTempHideEvent(hideForMs))
                return hunoia.luno.core.domain.ActionResult.Success()
            }
            val ok = runCatching {
                var enabled = false
                ConfigProvider.updateActionSettings { current ->
                    enabled = visible ?: !current.floatBall.enabled
                    current.copy(floatBall = current.floatBall.copy(enabled = enabled))
                }
                // 服务不在跑的场景（装更新时 restore 读到关闭态没拉球，随后规则把球启用）：
                // 只写盘上的值没有 settings flow 消费，必须把服务拉起来球才会回来。
                if (enabled) hunoia.luno.runtime.floatball.FloatBallService.start(host.context)
                true
            }.getOrDefault(false)
            return if (ok) {
                hunoia.luno.core.domain.ActionResult.Success()
            } else {
                hunoia.luno.core.domain.ActionResult.Failed(
                    hunoia.luno.core.domain.ActionFailure.ExecutionFailed,
                )
            }
        }

        override suspend fun setButtonsVisible(
            visible: Boolean,
            target: hunoia.luno.action.model.ButtonTarget,
            buttonIds: List<String>,
            hideForMs: Long,
        ): hunoia.luno.core.domain.ActionResult {
            if (hideForMs > 0) {
                val allIds = runtimeSettingsStore.snapshot().gestureButtons.map { it.id }
                buttonHideRuntime.hideTemporarily(
                    hunoia.luno.action.model.resolveButtonTargetIds(target, allIds, buttonIds),
                    hideForMs,
                )
                return hunoia.luno.core.domain.ActionResult.Success()
            }
            val ok = runCatching {
                ConfigProvider.updateGestureButtons { list ->
                    val ids = hunoia.luno.action.model.resolveButtonTargetIds(
                        target,
                        list.map { it.id },
                        buttonIds,
                    )
                    list.map { button -> if (button.id in ids) button.copy(enabled = visible) else button }
                }
                true
            }.getOrDefault(false)
            return if (ok) {
                hunoia.luno.core.domain.ActionResult.Success()
            } else {
                hunoia.luno.core.domain.ActionResult.Failed(
                    hunoia.luno.core.domain.ActionFailure.ExecutionFailed,
                )
            }
        }
    }

    /**
     * 执行口实例。`by lazy`：嵌套规则运行器（runRule）要回指分发口，
     * 而它本身又是分发口的构造参数。
     */
    private val actionDispatcher: ActionDispatcher by lazy {
        ActionDispatcher(
            environment = executeEnvironment,
            surfaces = executeSurfaces,
            messages = executeMessages,
            catalog = hunoia.luno.actions.LunoActionCatalog,
            factory = hunoia.luno.actions.LunoActionFactory,
            scope = host.coroutineScope,
            libraryEntry = { entryId ->
                runtimeSettingsStore.snapshot().actionLibrarySettings.entries
                    .find { it.id == entryId }?.storedAction
            },
            executors = listOf(
                hunoia.luno.action.executor.AppExecutors(),
                hunoia.luno.action.executor.IntentExecutors(
                    hunoia.luno.action.controller.IntentController(host.context.applicationContext),
                ),
                hunoia.luno.action.executor.SystemExecutors(
                    hunoia.luno.action.controller.AudioController(host.context.applicationContext),
                    hunoia.luno.action.controller.ClipboardController(host.context.applicationContext),
                ),
                hunoia.luno.action.executor.AccessibilityExecutors(
                    hunoia.luno.action.controller.AccessibilityController(host.accessibilityService),
                ),
                hunoia.luno.action.executor.ShellExecutor(),
                hunoia.luno.action.executor.MiscActionExecutors(),
                hunoia.luno.action.executor.InternalExecutors(),
            ),
            capabilityGate = hunoia.luno.action.capability.CapabilityGate(
                isAccessibilityAvailable = {
                    runCatching {
                        host.context.isAccessibilitySettingsOn(hunoia.luno.service.SideGestureService::class.java)
                    }.getOrDefault(false) || hunoia.luno.service.SideGestureService.current != null
                },
            ),
        )
    }

    private val buttonRefreshCoordinator = ButtonRefreshCoordinator(
        runtimeSettingsStore = runtimeSettingsStore,
        buttonWindowController = buttonWindowController,
    )

    /** 触钮 Surface：软隐藏只改输入位，恢复按真实显隐策略重算，长态统一 release。 */
    private val buttonSurfaceHandle = object : hunoia.luno.runtime.surface.SurfaceHandle {
        override val surfaceId: String = "gesture-buttons"
        override val surfaceKind: hunoia.luno.runtime.surface.SurfaceKind =
            hunoia.luno.runtime.surface.SurfaceKind.BUTTON

        override fun softHide() {
            buttonWindowController.setSoftHidden(true)
        }

        override fun restore() {
            buttonWindowController.setSoftHidden(false)
            buttonRefreshCoordinator.refresh(buildRuntimeState())
        }

        override fun detach() {
            hunoia.luno.runtime.surface.SurfaceController.unregister(surfaceId)
            buttonWindowController.release()
        }
    }


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

    private val recentsPickerRuntime = RecentsPickerRuntime(
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

    /**
     * 手势会话管线：输入源无关。触钮固定由无障碍按钮窗投喂；
     * Shizuku 内核触摸流只服务全屏长按识屏，不再参与触钮识别。
     */
    private var gesturePipelineRef: hunoia.luno.runtime.gesturepanel.GestureSessionPipeline? = null

    private val gesturePipeline: hunoia.luno.runtime.gesturepanel.GestureSessionPipeline
        get() = gesturePipelineRef ?: hunoia.luno.runtime.gesturepanel.GestureSessionPipeline(
            host.coroutineScope,
            callbacks,
        ).also { gesturePipelineRef = it }

    /** 触钮的唯一触摸路径：无障碍按钮窗投递的 `MotionEvent`。 */
    private var motionEventInputRef: hunoia.luno.gesture.touchsource.MotionEventInputSource? = null

    private val motionEventInput: hunoia.luno.gesture.touchsource.MotionEventInputSource
        get() = motionEventInputRef ?: hunoia.luno.gesture.touchsource.MotionEventInputSource(
            pipeline = gesturePipeline,
        ).also { motionEventInputRef = it }

    private var shizukuTouchInputRef: hunoia.luno.gesture.touchsource.ShizukuTouchInputSource? = null

    /**
     * 内核触摸流输入源：只服务全屏长按识屏（不拦截、不仲裁、不参与触钮）。
     * 宿主就是无障碍服务本身：手势管线与面板都依赖无障碍窗口。
     */
    private val shizukuTouchInput: hunoia.luno.gesture.touchsource.ShizukuTouchInputSource
        get() = shizukuTouchInputRef ?: hunoia.luno.gesture.touchsource.ShizukuTouchInputSource(
            context = host.context,
            scope = host.coroutineScope,
        ).also { shizukuTouchInputRef = it }

    /** 把配置快照推给管线；按钮/手势参数变化会重建识别状态，此时旧会话作废。 */
    private fun syncGesturePipeline() {
        val state = runtimeSettingsStore.snapshot()
        val recreated = gesturePipeline.update(
            hunoia.luno.runtime.gesturepanel.GesturePipelineSettings(
                buttons = state.gestureButtons,
                actionPanels = state.actionSettings.actionPanels.panels,
                advancedSettings = state.advancedSettings,
                gestureSettings = state.gestureSettings,
                subGestureSettings = state.subGestureSettings,
                actionPanelStyle = state.actionSettings.actionPanelStyle,
            ),
        )
        if (recreated) motionEventInput.reset()
    }

    /**
     * 内核触摸流只为「全屏长按识屏」而启停：手势总开关与识屏开关都开才跑。
     * 触钮固定走无障碍按钮窗，不再触发内核触摸流。
     */
    private fun syncTouchInputSources() {
        val state = runtimeSettingsStore.snapshot()
        val gestureEnabled = state.initialSettings.gestureEnabled
        motionEventInput.start()
        if (gestureEnabled && state.initialSettings.screenPickGesture.enabled) {
            shizukuTouchInput.start()
        } else if (shizukuTouchInput.isRunning) {
            // 只在真的在跑时停：无谓 stop 会掐断进行中的全局长按会话。
            shizukuTouchInput.stop()
        }
        // 识屏长按复用这一股内核触摸流：把每帧喂给长按检测器。
        // 触钮固定走无障碍窗，内核流不消费也不仲裁，因此这里只做“自家界面让位”。
        shizukuTouchInput.globalFrameListener = listener@{ point, action ->
            if (action == TouchAction.Down) {
                val service = hunoia.luno.service.SideGestureService.current
                // Luno 自己的界面不参与“全局长按识屏”：
                // - 识屏层在屏：长按由窗口自己的手势处理（否则会双触发）；
                // - 搜索/网格/动作面板在屏：那是面板交互；
                // - 前台是自家 Activity，或触点落在自家窗口（浮球/面板）上。
                if (service == null ||
                    service.isScreenPickVisible() ||
                    service.isScreenPickPortalVisible()
                ) {
                    return@listener
                }
                if (openPanels.hasOpenPanels) return@listener
                if (service.isOwnUiForeground() ||
                    service.isOwnWindowAt(point.x.toInt(), point.y.toInt())
                ) {
                    return@listener
                }
                // 场景显隐统一裁决：长按识别作为 VisibilityTarget，profile 命中且
                // visible=false 时完全不触发（不出卡、不预捕获、不碰触摸）。
                // visible=true 不强制开启：功能基线仍是识屏总开关 + Shizuku（config.enabled）。
                if (!shouldShowSurface(
                        hunoia.luno.config.model.VisibilityTargets.SCREEN_PICK,
                        runtimeSettingsStore.snapshot().initialSettings,
                        RuntimeStateHub.current(),
                    )
                ) {
                    return@listener
                }
            }
            globalScreenPickTrigger.onFrame(point, action)
        }
    }

    private val callbacks: GestureOverlayCallbacks = object : GestureOverlayCallbacks {
        override fun onSubGestureModeChanged(inSubGesture: Boolean, center: Offset, radiusPx: Int) {
            if (inSubGesture) {
                transientOverlayController.attachSubGestureOverlay(center, radiusPx)
                Events.post(PanelShownEvent())
            } else {
                transientOverlayController.detachSubGestureOverlay()
                Events.post(PanelDismissedEvent())
                // 面板项长按点火直接进子手势：那时面板视觉已收但交互窗还挂着，它是这段手势的
                // 触摸源。子手势会话一结束必须把它拆掉（否则全屏不可见窗会一直挡在应用上），
                // 并把面板路的 Shown 配平。取消 / 超时 / 出方向都经过这里。
                if (actionPanelOverlayEventPosted) {
                    actionPanelOverlayEventPosted = false
                    transientOverlayController.detachActionPanelOverlay()
                    Events.post(PanelDismissedEvent())
                }
            }
        }

        override fun onVolumeScrubOverlayRequested(config: hunoia.luno.config.model.VolumeScrubConfig) {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "volume scrub overlay requested")
            volumeScrubRuntime.show(config)
        }

        override fun onRecentsPickerOverlayRequested(state: RecentsPickerState) {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "recents picker overlay requested")
            recentsPickerRuntime.show(state)
        }

        override fun onRecentsPickerOverlayDismissed() {
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "recents picker overlay dismissed")
            recentsPickerRuntime.end()
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
            action: hunoia.luno.core.domain.StoredAction,
            sourceButton: GestureButton?,
            touchPosition: Offset,
            decision: hunoia.luno.gesture.GestureDecision.Trigger?,
            launchAnchor: Offset,
            foregroundHint: String?,
        ) {
            onGestureDecision(decision, action, sourceButton, touchPosition, launchAnchor, foregroundHint)
        }
    }

    /**
     * 「需要看见底下真实屏幕」的动作前置：先把自家全屏面板收掉。
     * 网格面板在非「拉起应用」的动作上不会自动关闭，留着会挡住识屏取链的节点扫描。
     */
    fun dismissPanelsForScreenAction() {
        // 所有全屏面板都可能挡住识屏扫描：统一按 Surface 类收起，不再只认网格面板。
        hunoia.luno.runtime.surface.SurfaceController.detachAll(
            hunoia.luno.runtime.surface.SurfaceKind.PANEL,
        )
    }

    /**
     * 临时会话收口：总开关关闭 / 锁屏共用。把已展开的会话与浮层全部收口，
     * 避免“总闸关了或已经锁屏，面板还盖在屏幕上”。
     * 触钮/悬浮球各自的可见性由 ButtonRuntime / FloatBallService 处理；这里只负责
     * 已经打开的临时 Surface 与手势会话。
     */
    private fun closeTransientSurfaces() {
        gesturePipelineRef?.let { pipeline ->
            pipeline.actionPanelState.cancel()
            pipeline.sideGestureState.cancel()
            pipeline.subGestureState.clear(notifyService = false)
        }
        actionPanelFullscreen = false
        pointerCursorHosted = false
        syncMainOverlayFullscreen()
        transientOverlayController.detachAll()
        actionPanelDismissGeneration++
        if (actionPanelOverlayEventPosted || actionPanelVisibilityEventPosted) {
            actionPanelOverlayEventPosted = false
            actionPanelVisibilityEventPosted = false
            Events.post(PanelDismissedEvent())
        }
        volumeScrubRuntime.end()
        recentsPickerRuntime.end()
        pointerRuntime.end()
        dismissPanelsForScreenAction()
    }

    /** 手势决策唯一入口：记录后立即分发；仲裁/改写只改这里。 */
    private fun onGestureDecision(
        decision: hunoia.luno.gesture.GestureDecision.Trigger?,
        action: hunoia.luno.core.domain.StoredAction,
        sourceButton: GestureButton?,
        touchPosition: Offset,
        launchAnchor: Offset = touchPosition,
        foregroundHint: String? = null,
    ) {
        if (BuildConfig.DEBUG) {
            Log.i(
                LOG_TAG,
                "gesture decision trigger=${decision?.triggerType} dir=${decision?.actionDirection} " +
                    "button=${sourceButton?.id} action=${action.typeId}",
            )
        }
        // 真手势按物理触点归侧广播：悬浮球碰哪侧即时跟到哪侧。
        // 触钮走同步命令口（[FloatBallCommand]），在动作提交**之前**换位；否则异步事件
        // 晚于动作到达，球已被打开的面板/按压态占用，repositionTo 失败且不重试。
        // 其他手势仍走事件；球点按 / 面板点选（sourceButton = null 且无 decision）不参与。
        if (touchPosition.x.isFinite()) {
            val leftSide = touchPosition.x < DensityProvider.screenWidthPx / 2f
            if (sourceButton != null) {
                hunoia.luno.runtime.floatball.FloatBallCommand.requestFollowHandSwap?.invoke(leftSide)
            } else if (decision != null) {
                Events.post(TouchButtonSideEvent(leftSide = leftSide))
            }
        }
        val touch = if (touchPosition.x.isFinite() && touchPosition.y.isFinite()) {
            touchPosition.x.roundToInt() to touchPosition.y.roundToInt()
        } else {
            null
        }
        // 启动落点与「点击落点」分开传：面板/子手势可以把启动锚点换成面板打开时的触点，
        // 而 accessibility.tap 仍用真实手指位置。
        val anchor = if (launchAnchor.x.isFinite() && launchAnchor.y.isFinite()) {
            launchAnchor.x.roundToInt() to launchAnchor.y.roundToInt()
        } else {
            touch
        }
        // 只提交 ActionRequest；触发源专属状态（按钮/触点/手势类型）随调用传入，
        // 落定回执在这里发事件，执行口不碰事件总线。
        actionDispatcher.submit(
            hunoia.luno.execute.ExecuteCall(
                request = hunoia.luno.core.domain.ActionRequest(
                    stored = action.withoutVariantKey(),
                    origin = hunoia.luno.core.domain.DispatchOrigin.GESTURE,
                    idempotencyKey = nextIntentKey("gesture:${sourceButton?.id ?: "surface"}"),
                ),
                touchPosition = touch,
                launchAnchor = anchor,
                foregroundHint = foregroundHint,
                sourceButtonId = sourceButton?.id,
            ),
            onSettled = { settlement ->
                hunoia.luno.core.Events.post(
                    hunoia.luno.gesture.event.GestureDispatchEvent(
                        typeId = settlement.typeId,
                        buttonId = sourceButton?.id,
                        trigger = decision?.triggerType,
                        fromGesture = true,
                        ok = settlement.succeeded,
                    ),
                )
            },
        )
    }

    /** 供 overlay 等场景直接执行一个已构造好的动作；key 原样透传，null = 生成新意图键。 */
    fun dispatchAction(
        action: hunoia.luno.core.domain.StoredAction,
        idempotencyKey: String? = null,
        origin: hunoia.luno.core.domain.DispatchOrigin = hunoia.luno.core.domain.DispatchOrigin.PANEL,
        onSettled: ((typeId: String, result: hunoia.luno.core.domain.ActionResult) -> Unit)? = null,
    ) {
        actionDispatcher.submit(
            hunoia.luno.execute.ExecuteCall(
                request = hunoia.luno.core.domain.ActionRequest(
                    stored = action.withoutVariantKey(),
                    origin = origin,
                    idempotencyKey = idempotencyKey ?: nextIntentKey("panel"),
                ),
            ),
            onSettled = { settlement ->
                onSettled?.invoke(settlement.typeId, settlement.result)
            },
        )
    }

    private fun nextIntentKey(prefix: String): String = "$prefix:${intentSeq.incrementAndGet()}"

    /**
     * 悬浮球点按动作统一转发：所有动作广播进容器，由容器 `runGestureActions` 分类执行（与触钮同链路）。
     * 面板动作要等快照列表就绪再广播：a11y 冷启动后头几秒 `actionPanels` 为空，事件即发即忘，
     * 砸进去 = 面板静默不弹；非面板动作立即转发。
     */
    fun forwardBallTap(
        action: hunoia.luno.core.domain.StoredAction,
        anchorX: Int,
        anchorY: Int,
        colorSpec: hunoia.luno.config.model.ColorSpec,
        foregroundHint: String? = null,
    ) {
        val panelId = action.actionPanelId()
        if (panelId == null) {
            hunoia.luno.core.Events.post(
                hunoia.luno.core.BallTapActionEvent(
                    action, anchorX, anchorY, colorSpec, foregroundHint,
                ),
            )
            return
        }
        host.coroutineScope.launch {
            for (attempt in 1..BALL_PANEL_REVEAL_MAX_ATTEMPTS) {
                val panels = runtimeSettingsStore.snapshot().actionSettings.actionPanels.panels
                if (panels.any { it.id == panelId } || attempt == BALL_PANEL_REVEAL_MAX_ATTEMPTS) {
                    hunoia.luno.core.Events.post(
                        hunoia.luno.core.BallTapActionEvent(
                            action, anchorX, anchorY, colorSpec, foregroundHint,
                        ),
                    )
                    return@launch
                }
                delay(BALL_PANEL_REVEAL_RETRY_MS)
            }
        }
    }

    /** 分发落定后刷新触钮可见性。 */
    private val gestureDispatchSubscriber: (hunoia.luno.gesture.event.GestureDispatchEvent) -> Unit = { event ->
        if (event.fromGesture) refreshGestureButtons(delayMs = 0L)
    }

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
        refreshGestureButtons()
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
        hunoia.luno.runtime.surface.SurfaceController.register(buttonSurfaceHandle)
        hunoia.luno.gridpanel.widget.PanelAppWidgetHostView.setTapListener { startWidgetLaunchPoll() }
        hunoia.luno.core.Events.subscribe(
            hunoia.luno.gesture.event.GestureDispatchEvent::class,
            gestureDispatchSubscriber,
        )
        // 链接分发经执行口启动：本进程内可等待，主进程走 IPC 回退。
        hunoia.luno.runtime.dispatch.ActionDispatch.installAwaitable(
            hunoia.luno.core.domain.AwaitableActionDispatch { request ->
                actionDispatcher.execute(
                    hunoia.luno.execute.ExecuteCall(
                        request = request.copy(stored = request.stored.withoutVariantKey()),
                    ),
                ).succeeded
            },
        )
        runtimeSettingsStore.start()
        // 先按当前快照把管线和输入源建起来，再挂 overlay：否则首帧渲染时管线还是空配置，
        // 开局几毫秒内的触摸会解析不到任何触钮。
        syncGesturePipeline()
        syncTouchInputSources()
        broadcastObserver.register()
        // 键盘状态无法像 orientation/锁屏那样在构造期播种（rootInActiveWindow 需服务连接后才有），
        // 放这里：早于首评的设置收集器，此刻 root 已可查询。
        environmentTracker.seedKeyboardInputActive()
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
                    // 按钮集合变了（窗口增删）：旧会话不可能再收到 UP，先作废再重算。
                    motionEventInputRef?.reset()
                    refreshGestureButtons(delayMs = 0L)
                }
        }

        // 总开关（initialSettings.gestureEnabled）翻转时 gestureButtons 不变，
        // 上面两个 distinctUntilChangedBy 会过滤掉这次变更、不触发刷新；这里单独订阅兜底。
        scopeJobs += host.coroutineScope.launch {
            runtimeSettingsStore.state
                .distinctUntilChangedBy { it.initialSettings }
                .collect { refreshGestureButtons(delayMs = 0L) }
        }

        // 手势会话管线与输入源跟随配置：按钮、面板、参数、输入模式任一变化都走同一条同步。
        // 先同步配置再决定输入源，避免用旧快照建拦截窗。
        scopeJobs += host.coroutineScope.launch {
            runtimeSettingsStore.state.collect { state ->
                syncGesturePipeline()
                syncTouchInputSources()
                if (!state.initialSettings.gestureEnabled) closeTransientSurfaces()
            }
        }

        scopeJobs += host.coroutineScope.launch(Dispatchers.IO) {
            // 用面板真正展示的那份查询（allowRepeatPackage=false，含禁用应用/分身/系统包开关），
            // 而不是 queryApps —— 后者列表不同，预热出来的索引对不上。
            hunoia.luno.quickaccess.query.AppCatalog.refreshSystemSetting()
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
            refreshGestureButtons()
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
            hunoia.luno.gesture.event.GestureDispatchEvent::class,
            gestureDispatchSubscriber,
        )
        hunoia.luno.runtime.dispatch.ActionDispatch.installAwaitable(null)
        openPanels.close()
        runtimeSettingsStore.stop()
        scopeJobs.forEach { it.cancel() }
        scopeJobs.clear()
        // 只在真的建过时释放：这三个对象是惰性创建的，没建过就不该为了 release 而创建。
        gesturePipelineRef?.let { pipeline ->
            motionEventInputRef?.stop()
            shizukuTouchInputRef?.stop()
            pipeline.release()
        }
        motionEventInputRef = null
        shizukuTouchInputRef = null
        gesturePipelineRef = null
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "GestureCoordinator release")
        mainOverlayController.release()
        hunoia.luno.runtime.surface.SurfaceController.detachAll(
            hunoia.luno.runtime.surface.SurfaceKind.BUTTON,
        )
        // 销毁不经过 callbacks 的配对隐藏（那里各自发 PanelDismissed）：挂着面板被销毁时
        // 按拆掉的数量补发，否则 FloatBallService 的 panelCount 卡住、球一直隐藏。
        actionPanelDismissGeneration++
        refreshJob?.cancel()
        refreshJob = null
        if (actionPanelVisibilityEventPosted) {
            actionPanelVisibilityEventPosted = false
            Events.post(PanelDismissedEvent())
        }
        // 全屏面板由统一 Surface 控制器收口：未闭合的网格 / 快速启动面板在此补发 Dismissed。
        hunoia.luno.runtime.surface.SurfaceController.detachAll(
            hunoia.luno.runtime.surface.SurfaceKind.PANEL,
        )
        repeat(transientOverlayController.detachAll()) {
            Events.post(PanelDismissedEvent())
        }
        broadcastObserver.unregister()
        volumeScrubRuntime.onDestroy()
        recentsPickerRuntime.onDestroy()
        pointerRuntime.onDestroy()
        previousAppTracker.onRelease()
        wallpaperColorsListener?.let { listener ->
            WallpaperManager.getInstance(host.context).removeOnColorsChangedListener(listener)
            wallpaperColorsListener = null
        }
    }

    private fun buildRuntimeState(): GestureRuntimeState =
        environmentTracker.buildRuntimeState(
            hiddenGestureButtons = buttonHideRuntime.getSnapshot(),
        )

    private fun refreshGestureButtons(delayMs: Long = 100L) {
        refreshJob?.cancel()
        refreshJob = host.coroutineScope.launch {
            if (delayMs > 0L) delay(delayMs)
            buttonRefreshCoordinator.refresh(buildRuntimeState())
        }
    }

    @Composable
    private fun renderMainOverlay() {
        hunoia.luno.runtime.overlay.GestureOverlayView(
            pipeline = gesturePipeline,
            settingsState = runtimeSettingsStore.state,
        )
    }
}
