package hunoia.luno.action.dispatcher

import android.content.Context
import android.util.Log
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.action.controller.*
import hunoia.luno.action.execution.*
import hunoia.luno.action.executor.*
import hunoia.luno.action.model.*
import hunoia.luno.bridge.isAccessibilitySettingsOn
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
import hunoia.luno.config.model.effectiveFor
import hunoia.luno.runtime.GestureHost
import hunoia.luno.shizuku.ShizukuFacade
import hunoia.luno.runtime.action.PreviousAppTracker
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.runtime.action.SettingsSnapshot
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.template.RuntimeVariables
import hunoia.luno.service.SideGestureService
import hunoia.luno.bridge.feedback.showToast as showToastUtil
import hunoia.luno.bridge.feedback.showToastLong as showToastLongUtil
import hunoia.luno.bridge.feedback.showVersionTooLowToast as showVersionTooLowToastUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewActionDispatcher(
    private val host: GestureHost,
    private val scope: CoroutineScope,
    private val previousAppTracker: PreviousAppTracker,
    private val settingsSnapshot: () -> SettingsSnapshot,
    private val onToggleQuickAppLauncher: () -> Unit,
    private val onShowQuickSearch: () -> Unit,
    private val onShowVolumeScrub: (VolumeScrubConfig) -> Boolean,
    private val onHideGestureButton: (GestureButton?, Long) -> Unit,
) {
    /**
     * 每个 capability 由唯一对应的检查负责。
     *
     * 之前这里用 CompositeCapabilityChecker(List).any { it.isAvailable(cap) }，而每个
     * checker 对不归自己管的 capability 都返回 true，于是 any 恒为 true，闸门从未触发，
     * ActionResult.RequiresCapability 成了死路径 —— Shizuku 未授权时会一路走到 executor
     * 里失败，只弹通用的「执行失败」而不是指明缺哪个能力。
     */
    private val isCapabilityAvailable: (Capability) -> Boolean = { capability ->
        when (capability) {
            Capability.None -> true
            Capability.Shizuku -> ShizukuFacade.isReady()
            Capability.Accessibility ->
                host.context.applicationContext.isAccessibilitySettingsOn(SideGestureService::class.java) ||
                    SideGestureService.current != null
        }
    }

    private val registry: ActionExecutorRegistry = ActionExecutorRegistry(
        executors = listOf(
            AppExecutors(IntentController(host.context.applicationContext)),
            IntentExecutors(IntentController(host.context.applicationContext)),
            SystemExecutors(
                AudioController(host.context.applicationContext),
                ClipboardController(host.context.applicationContext),
                VibrateController(host.context.applicationContext),
            ),
            AccessibilityExecutors(AccessibilityController(host.accessibilityService)),
            PackageExecutors(PackageController()),
            SettingsExecutors(SettingsController()),
            SystemCmdExecutors(ShellController()),
            ShellExecutor(ShellController()),
            InternalExecutors(),
        ),
        isCapabilityAvailable = isCapabilityAvailable,
    )

    private val resolver = ActionResolver(registry)

    fun dispatch(
        stored: StoredAction,
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride? = null,
        touchPosition: Pair<Int, Int>? = null,
        templateContext: () -> ConditionContext? = { null },
    ) {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "dispatch storedAction typeId=${stored.typeId}")
        scope.launch(Dispatchers.Main.immediate) {
            val ctx = buildContext(sourceButton, sourceOverride, touchPosition, templateContext)
            val result = resolver.resolveFromLibrary(stored, ctx)
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "result=$result")
            when (result) {
                is ActionResult.Failed -> ctx.showToast(result.message ?: failureText(appContext, result.reason))
                is ActionResult.RequiresCapability -> ctx.showToast(capabilityText(appContext, result.capability))
                is ActionResult.Success -> result.message?.let { msg -> ctx.showToast(msg) }
            }
        }
    }

    private val appContext: Context get() = host.context.applicationContext

    private fun failureText(context: Context, reason: ActionFailure): String = context.getString(
        when (reason) {
            ActionFailure.InvalidParameter -> R.string.toast_invalid_parameter
            ActionFailure.ExecutionFailed -> R.string.toast_execution_failed
            ActionFailure.PermissionDenied -> R.string.toast_permission_denied
            ActionFailure.Unsupported -> R.string.toast_unsupported_action
        }
    )

    private fun capabilityText(context: Context, capability: Capability): String = context.getString(
        when (capability) {
            Capability.Accessibility -> R.string.toast_accessibility_required
            Capability.Shizuku -> R.string.toast_shizuku_required
            Capability.None -> R.string.toast_execution_failed
        }
    )

    private fun buildContext(
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride? = null,
        touchPosition: Pair<Int, Int>? = null,
        templateContext: () -> ConditionContext? = { null },
    ): ExecutorContext {
        val snap = settingsSnapshot()
        val context: Context = host.context.applicationContext
        return ExecutorContext(
            accessibilityService = host.accessibilityService,
            appContext = context,
            scope = scope,
            actionSettings = snap.actionSettings,
            advancedSettings = snap.advancedSettings.effectiveFor(sourceOverride),
            showToast = { showToastUtil(it) },
            showLongToast = { showToastLongUtil(it) },
            currentPackageName = { host.getCurrentPackageName() },
            nowInLauncher = { host.nowInLauncher() },
            requestEnableDisabledPackage = { packageName, onResult ->
                host.requestEnableDisabledPackage(packageName, onResult)
            },
            toggleQuickAppLauncher = onToggleQuickAppLauncher,
            showQuickSearch = onShowQuickSearch,
            showVolumeScrub = onShowVolumeScrub,
            hideGestureButton = { delayMs ->
                if (sourceButton != null) onHideGestureButton(sourceButton, delayMs)
            },
            showVersionTooLowToast = { resId -> showVersionTooLowToastUtil(context, resId) },
            previousApp = { excludes -> previousAppTracker.previousApp(excludes) },
            touchPosition = touchPosition,
            templateValues = { used ->
                val runtime = templateContext()
                if (runtime == null) emptyMap() else RuntimeVariables.resolve(used, runtime, context)
            },
        )
    }
}
