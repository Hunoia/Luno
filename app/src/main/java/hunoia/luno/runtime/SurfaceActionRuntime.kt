package hunoia.luno.runtime

import android.content.Context
import android.content.Intent
import android.util.Log
import hunoia.luno.action.dispatcher.ActionDispatcher
import hunoia.luno.action.model.QueryParam
import hunoia.luno.action.model.resolveButtonTargetIds
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.core.AppContext
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.blankRuntimeSnapshot
import hunoia.luno.execute.ExecuteCall
import hunoia.luno.execute.ExecuteEnvironment
import hunoia.luno.execute.ExecuteMessages
import hunoia.luno.execute.LaunchSpec
import hunoia.luno.execute.SurfaceActionDispatch
import hunoia.luno.execute.SurfaceDispatch
import hunoia.luno.execute.SurfaceHost
import hunoia.luno.launch.DefaultAppLauncher
import hunoia.luno.quicklaunch.launch.DisabledPackageEnabler
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

    private var installed = false

    /** 由 `App.onCreate` 调用；两进程都执行。 */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        AppContext.applicationScope?.launch(Dispatchers.IO) {
            runCatching { ConfigProvider.getAdvancedSettings() }
                .onSuccess { advanced = it }
            ConfigProvider.advancedSettings.collect { advanced = it }
        }
        SurfaceDispatch.install(SurfaceActionDispatch { request, onSettled ->
            dispatcher(appContext).submit(
                ExecuteCall(request = request),
                onSettled = { settlement -> onSettled(settlement.succeeded) },
            )
        })
    }

    private fun dispatcher(context: Context): ActionDispatcher = ActionDispatcher(
        environment = environment(context),
        surfaces = surfaces(context),
        messages = LunoExecuteMessages(context),
        catalog = hunoia.luno.actions.LunoActionCatalog,
        factory = hunoia.luno.actions.LunoActionFactory,
        scope = scope,
        snapshot = { blankRuntimeSnapshot() },
        libraryEntry = { null },
        executors = listOf(
            hunoia.luno.action.executor.AppExecutors(),
            hunoia.luno.action.executor.IntentExecutors(
                hunoia.luno.action.controller.IntentController(context),
            ),
            hunoia.luno.action.executor.SystemExecutors(
                hunoia.luno.action.controller.AudioController(context),
                hunoia.luno.action.controller.ClipboardController(context),
                hunoia.luno.action.controller.VibrateController(context),
            ),
            hunoia.luno.action.executor.ShellExecutor(hunoia.luno.action.controller.ShellController()),
            hunoia.luno.action.executor.AutomationActionExecutors(
                hunoia.luno.action.controller.ClipboardController(context),
            ),
            hunoia.luno.action.executor.InternalExecutors(),
        ),
        capabilityGate = hunoia.luno.action.capability.CapabilityGate(
            appContext = context,
            // Surface 直派发没有无障碍执行器；一律按「需授权」收口。
            isAccessibilityAvailable = { false },
        ),
    )

    private fun environment(context: Context): ExecuteEnvironment = object : ExecuteEnvironment {
        override val appContext: Context = context

        override fun currentPackageName(): String? =
            hunoia.luno.service.SideGestureService.current?.getCurrentPackageName()
                ?.takeIf { it.isNotBlank() }

        override fun nowInLauncher(): Boolean =
            hunoia.luno.service.SideGestureService.current?.nowInLauncher() == true

        override fun requestEnableDisabledPackage(
            packageName: String,
            onResult: (Boolean) -> Unit,
        ) = enabler(context).request(packageName, onResult)
    }

    private fun surfaces(context: Context): SurfaceHost = object : SurfaceHost {
        private val launcher by lazy { appLauncher(context) }

        override fun toggleQuickAppLauncher(anchorX: Int, anchorY: Int) {
            hunoia.luno.quicklaunch.QuickAppLauncherOverlay.show(anchorX, anchorY)
        }

        override fun showQuickSearch() {
            hunoia.luno.quicklaunch.QuickSearchActivity.start(context)
        }

        override fun showGridPanel(panelId: String, anchorX: Int, anchorY: Int) {
            hunoia.luno.gridpanel.GridPanelOverlay.show(panelId, anchorX, anchorY)
        }

        // Surface 直派发的面板动作不在这里做存在性预检：真正的渲染入口会自行收口悬空 id。
        override fun gridPanelExists(panelId: String): Boolean = true

        override fun showVolumeScrub(stepThresholdDp: Int?): Boolean = false
        override fun showPointer(): Boolean = false
        override fun hideGestureButton(buttonId: String, delayMs: Long) = Unit
        override fun hideGestureButtons(
            target: hunoia.luno.action.model.ButtonTarget,
            buttonIds: List<String>,
            delayMs: Long,
        ) = Unit

        override suspend fun previousApp() = Unit

        override suspend fun launchApp(spec: LaunchSpec): Boolean = launcher.launch(spec)

        override suspend fun openUrl(
            url: String,
            miniWindow: Boolean,
            queryParameters: List<QueryParam>,
        ): Boolean = launcher.openUrl(url, miniWindow, queryParameters)

        override suspend fun launchShortcut(dataJson: String): Boolean =
            launcher.launchShortcut(dataJson)

        override suspend fun openAppDetails(packageName: String): ActionResult =
            launcher.openAppDetails(packageName)

        override suspend fun launchInPopup(packageName: String): ActionResult =
            launcher.launchInPopup(packageName)

        override fun shareFile(): ActionResult = runCatching {
            context.startActivity(
                Intent(context, hunoia.luno.ui.share.ShareFilePickActivity::class.java).apply {
                    action = hunoia.luno.ui.share.ShareFilePickActivity.ACTION_SHARE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            ActionResult.Success()
        }.getOrElse { e ->
            ActionResult.Failed(ActionFailure.ExecutionFailed, e.message ?: e.javaClass.name)
        }

        override fun showScreenPick(): Boolean =
            hunoia.luno.service.SideGestureService.current?.showScreenPick() == true

        override fun showScreenLinks(): Boolean =
            hunoia.luno.service.SideGestureService.current?.showScreenLinks() == true

        override suspend fun setFloatBallVisible(
            visible: Boolean?,
            hideForMs: Long,
        ): ActionResult {
            if (hideForMs > 0) return ActionResult.Failed(ActionFailure.Unsupported)
            val ok = runCatching {
                var enabled = false
                ConfigProvider.updateActionSettings { current ->
                    enabled = visible ?: !current.floatBall.enabled
                    current.copy(floatBall = current.floatBall.copy(enabled = enabled))
                }
                if (enabled) hunoia.luno.runtime.floatball.FloatBallService.start(context)
                true
            }.getOrDefault(false)
            return if (ok) ActionResult.Success()
            else ActionResult.Failed(ActionFailure.ExecutionFailed)
        }

        override suspend fun setButtonsVisible(
            visible: Boolean,
            target: hunoia.luno.action.model.ButtonTarget,
            buttonIds: List<String>,
            hideForMs: Long,
        ): ActionResult {
            if (hideForMs > 0) return ActionResult.Failed(ActionFailure.Unsupported)
            val ok = runCatching {
                ConfigProvider.updateGestureButtons { list ->
                    val ids = resolveButtonTargetIds(target, list.map { it.id }, buttonIds)
                    list.map { button -> if (button.id in ids) button.copy(enabled = visible) else button }
                }
                true
            }.getOrDefault(false)
            return if (ok) ActionResult.Success()
            else ActionResult.Failed(ActionFailure.ExecutionFailed)
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
