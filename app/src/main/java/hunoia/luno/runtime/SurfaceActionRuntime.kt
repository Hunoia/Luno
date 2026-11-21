package hunoia.luno.runtime

import android.app.Application
import android.content.Context
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.action.dispatcher.ActionDispatcher
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.core.AppContext
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.execute.ExecuteCall
import hunoia.luno.execute.ExecuteEnvironment
import hunoia.luno.core.domain.ActionDispatchPort
import hunoia.luno.core.domain.ActionLibrarySettings
import hunoia.luno.core.domain.AwaitableActionDispatch
import hunoia.luno.core.domain.SurfaceActionDispatch
import hunoia.luno.service.SendOutcome
import hunoia.luno.execute.SurfaceHost
import hunoia.luno.runtime.dispatch.ActionDispatch
import hunoia.luno.runtime.surface.SurfaceDispatch
import hunoia.luno.launch.DefaultAppLauncher
import hunoia.luno.quickaccess.launch.DisabledPackageEnabler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 进程级 Surface 执行门面。
 *
 * 与手势链路不同，它不依赖 `AccessibilityService` 生命周期：只要 `:service` 进程在跑，
 * 悬浮球胶囊 / 网格面板 / 快速启动器都能把「启动应用」收敛为
 * `ActionRequest → Dispatcher → Capability Gate → Executor`。
 *
 * 需要无障碍的无障碍动作不在此注册执行器；能力闸会返回「需授权」，避免在无宿主时
 * 走到半截执行。
 */
object SurfaceActionRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var advanced = AdvancedSettings()

    /** 动作库快照：`internal.libraryRef` 需要同步解析，不能每次 await 配置流。 */
    @Volatile
    private var actionLibrary = ActionLibrarySettings()

    /** 面板快照：快速启动动作要同步判断是否已配置单功能面板。 */
    @Volatile
    private var actionSettings = ActionSettings()

    private var appContextRef: Context? = null

    private var installed = false

    /**
     * 由 `App.onCreate` 调用；两进程都执行。
     *
     * 只有 `:service` 进程持有本地 Dispatcher。主进程的 Surface 入口只做 IPC 转发，
     * 避免主进程绕过服务进程的执行上下文与能力状态。
     */
    @Synchronized
    fun install(context: Context) {
        if (installed) return
        val appContext = context.applicationContext
        val serviceProcess = isServiceProcess()
        if (serviceProcess) {
            appContextRef = appContext
            // 本进程有 Dispatcher：提交即视为送达，执行结果由 Surface/调用方另行处理。
            ActionDispatch.installSender(ActionDispatchPort { action, origin, idempotencyKey ->
                dispatcher.submit(
                    ExecuteCall(
                        request = hunoia.luno.core.domain.ActionRequest(
                            stored = action,
                            origin = origin,
                            idempotencyKey = idempotencyKey,
                        ),
                    ),
                )
                true
            })
            AppContext.applicationScope?.launch(Dispatchers.IO) {
                runCatching { ConfigProvider.getAdvancedSettings() }
                    .onSuccess { advanced = it }
                ConfigProvider.advancedSettings.collect { advanced = it }
            }
            // 动作库引用（internal.libraryRef）在 Surface 侧同样要能解析。
            AppContext.applicationScope?.launch(Dispatchers.IO) {
                runCatching { ConfigProvider.getActionLibrarySettings() }
                    .onSuccess { actionLibrary = it }
                ConfigProvider.actionLibrarySettings.collect { actionLibrary = it }
            }
            AppContext.applicationScope?.launch(Dispatchers.IO) {
                runCatching { ConfigProvider.getActionSettings() }
                    .onSuccess { actionSettings = it }
                ConfigProvider.actionSettings.collect { actionSettings = it }
            }
            SurfaceDispatch.install(SurfaceActionDispatch { request, launchAnchor, onSettled ->
                // 单例复用：执行器与能力闸不应每次点击重建（见 ExecutionSinglePortTest）。
                dispatcher.submit(
                    ExecuteCall(request = request, launchAnchor = launchAnchor),
                    onSettled = { settlement -> onSettled(settlement.succeeded) },
                )
            })
        } else {
            // 主进程没有本地 Dispatcher：提交走 IPC，最多等 500ms 的“已受理”。
            ActionDispatch.installSender(ActionDispatchPort { action, origin, idempotencyKey ->
                hunoia.luno.service.DispatchClient.send(
                    action = action,
                    idempotencyKey = idempotencyKey,
                    origin = origin,
                ) == SendOutcome.Delivered
            })
            // 链接等需要真实结果的入口：等待 :service 回推三态（最长 10s）。
            ActionDispatch.installAwaitable(AwaitableActionDispatch { request ->
                hunoia.luno.service.DispatchClient.await(
                    action = request.stored,
                    idempotencyKey = request.idempotencyKey,
                    origin = request.origin,
                ) is ActionResult.Success
            })
            // Surface 入口：先受理，再异步等执行结果；超时 / 未送达按失败回调。
            SurfaceDispatch.install(SurfaceActionDispatch { request, _, onSettled ->
                // 主进程的 Surface 入口只做 IPC：触点锚点仅服务进程内有效，跨进程不携带。
                scope.launch {
                    val result = hunoia.luno.service.DispatchClient.await(
                        action = request.stored,
                        idempotencyKey = request.idempotencyKey,
                        origin = request.origin,
                    )
                    onSettled(result is ActionResult.Success)
                }
            })
        }
        installed = true
    }

    private fun isServiceProcess(): Boolean =
        Application.getProcessName()?.endsWith(":service") == true

    /**
     * 进程级执行口：整个 `:service` 进程共用一个实例（执行器 / 能力闸只建一次）。
     * 与手势链路各自持有一个 Dispatcher，但两者都走同一套
     * `ActionRequest → Dispatcher → Capability Gate → Executor`，不构成旁路。
     */
    private val dispatcher: ActionDispatcher by lazy {
        buildDispatcher(requireNotNull(appContextRef) { "SurfaceActionRuntime.install 未调用" })
    }

    private fun buildDispatcher(context: Context): ActionDispatcher = ActionDispatcher(
        environment = environment(context),
        surfaces = surfaces(context),
        messages = LunoExecuteMessages(context),
        catalog = hunoia.luno.actions.LunoActionCatalog,
        factory = hunoia.luno.actions.LunoActionFactory,
        scope = scope,
        libraryEntry = { entryId ->
            actionLibrary.entries.find { it.id == entryId }?.storedAction
        },
        executors = listOf(
            hunoia.luno.action.executor.AppExecutors(),
            hunoia.luno.action.executor.IntentExecutors(
                hunoia.luno.action.controller.IntentController(context),
            ),
            hunoia.luno.action.executor.SystemExecutors(
                hunoia.luno.action.controller.AudioController(context),
                hunoia.luno.action.controller.ClipboardController(context),
            ),
            hunoia.luno.action.executor.ShellExecutor(),
            hunoia.luno.action.executor.MiscActionExecutors(),
            hunoia.luno.action.executor.AppControlExecutors(),
            hunoia.luno.action.executor.InternalExecutors(),
        ),
        capabilityGate = hunoia.luno.action.capability.CapabilityGate(
            // Surface 直派发没有无障碍执行器；一律按「需授权」收口。
            isAccessibilityAvailable = { false },
        ),
    )

    private fun environment(context: Context): ExecuteEnvironment = object : ExecuteEnvironment {
        override val appContext: Context = context

        override fun currentPackageName(): String? =
            hunoia.luno.service.SideGestureService.current?.getCurrentAppForWindow()
                ?.takeIf { it.isNotBlank() }
    }

    /**
     * 直派发上下文的 Surface 端口：共享转发在 [SurfaceHostBridge]（launcher 在首次使用时才建）。
     * 本上下文没有无障碍服务窗口：面板存在性恒真、显隐 / 触钮 / 指针 / 音量刮擦钩子一律静默不作用。
     */
    private fun surfaces(context: Context): SurfaceHost {
        val launcher by lazy { appLauncher(context) }
        return object : SurfaceHostBridge(context, { launcher }) {
            // Surface 直派发的面板动作不在这里做存在性预检：真正的渲染入口会自行收口悬空 id。
            override fun gridPanelExists(panelId: String): Boolean = true

            override fun showVolumeScrub(stepThresholdDp: Int?): Boolean = false
            override fun showPointer(): Boolean = false
            override fun hideGestureButton(buttonId: String, delayMs: Long) = Unit

            override suspend fun previousApp() = Unit

            override fun hideTriggerButton(buttonId: String) = Unit

            override fun toggleFloatBallHide() = Unit
        }
    }

    private fun appLauncher(context: Context): DefaultAppLauncher = DefaultAppLauncher(
        context = context,
        advancedSettings = { advanced },
        unfreezePackage = { pkg ->
            suspendCancellableCoroutine { cont ->
                enabler(context).request(pkg) { ok -> if (cont.isActive) cont.resume(ok) }
            }
        },
    )

    private fun enabler(context: Context): DisabledPackageEnabler = DisabledPackageEnabler(
        context = context,
        scopeProvider = { scope },
        log = { message -> Log.i(LOG_TAG, message) },
    )
}
