package hunoia.luno.action.dispatcher

import android.content.Context
import android.util.Log
import hunoia.luno.BuildConfig
import hunoia.luno.action.capability.AccessibilityCapabilityChecker
import hunoia.luno.action.capability.CompositeCapabilityChecker
import hunoia.luno.action.capability.ShizukuCapabilityChecker
import hunoia.luno.action.controller.*
import hunoia.luno.action.execution.*
import hunoia.luno.action.executor.*
import hunoia.luno.action.model.*
import hunoia.luno.bridge.isAccessibilitySettingsOn
import hunoia.luno.config.model.ActionLibrarySettings as LegacyActionLibrarySettings
import hunoia.luno.config.model.ActionSettings as LegacyActionSettings
import hunoia.luno.config.model.AdvancedSettings as LegacyAdvancedSettings
import hunoia.luno.config.model.GestureButton as LegacyGestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride as LegacyGestureButtonActionSettingsOverride
import hunoia.luno.config.model.GestureSettings as LegacyGestureSettings
import hunoia.luno.config.model.effectiveFor
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.action.KeepScreenOnController
import hunoia.luno.runtime.action.PreviousAppTracker
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
    private val keepScreenOnController: KeepScreenOnController,
    private val settingsSnapshot: () -> SettingsSnapshot,
    private val onToggleQuickAppLauncher: () -> Unit,
    private val onShowVolumeScrub: () -> Boolean,
    private val onHideGestureButton: (LegacyGestureButton?, Long) -> Unit,
) {
    private val capabilityChecker = CompositeCapabilityChecker(
        listOf(
            ShizukuCapabilityChecker(),
            AccessibilityCapabilityChecker {
                val appContext = host.context.applicationContext
                appContext.isAccessibilitySettingsOn(SideGestureService::class.java) ||
                    SideGestureService.current != null
            },
        )
    )

    private val registry: ActionExecutorRegistry = ActionExecutorRegistry(
        executors = listOf(
            AppExecutors(IntentController(host.context.applicationContext)),
            IntentExecutors(IntentController(host.context.applicationContext)),
            SystemExecutors(
                AudioController(host.context.applicationContext),
                ClipboardController(host.context.applicationContext),
                SystemController(host.context.applicationContext),
                VibrateController(host.context.applicationContext),
            ),
            AccessibilityExecutors(AccessibilityController(host.accessibilityService)),
            PackageExecutors(PackageController(host.context.applicationContext)),
            SettingsExecutors(SettingsController(host.context.applicationContext)),
            SystemCmdExecutors(ShellController(host.context.applicationContext)),
            ShellExecutor(ShellController(host.context.applicationContext)),
            InternalExecutors(),
        ),
        capabilityChecker = capabilityChecker,
    )

    private val resolver = ActionResolver(registry)

    fun dispatch(
        stored: StoredAction,
        sourceButton: LegacyGestureButton?,
        sourceOverride: LegacyGestureButtonActionSettingsOverride? = null,
    ) {
        if (BuildConfig.DEBUG) Log.d("LunoLauncher", "dispatch storedAction typeId=${stored.typeId}")
        scope.launch(Dispatchers.Main.immediate) {
            val ctx = buildContext(sourceButton, sourceOverride)
            val result = resolver.resolveFromLibrary(stored, ctx)
            if (BuildConfig.DEBUG) Log.d("LunoLauncher", "result=$result")
            when (result) {
                is ActionResult.Failed -> {
                    val msg = when (result.reason) {
                        ActionFailure.AppNotFound -> "应用未找到"
                        ActionFailure.ActivityNotFound -> "Activity 未找到"
                        ActionFailure.InvalidParameter -> "参数无效"
                        ActionFailure.ExecutionFailed -> "执行失败"
                        ActionFailure.PermissionDenied -> "权限不足"
                        ActionFailure.Timeout -> "执行超时"
                        ActionFailure.Unsupported -> "不支持的动作类型"
                    }
                    ctx.showToast(msg)
                }
                is ActionResult.RequiresCapability -> {
                    val msg = when (result.capability) {
                        Capability.Accessibility -> "需要无障碍服务"
                        Capability.Shizuku -> "需要 Shizuku 权限"
                        Capability.Root -> "需要 Root 权限"
                        Capability.None -> "执行失败"
                    }
                    ctx.showToast(msg)
                }
                ActionResult.Success -> {}
            }
        }
    }

    private fun buildContext(
        sourceButton: LegacyGestureButton?,
        sourceOverride: LegacyGestureButtonActionSettingsOverride? = null,
    ): ExecutorContext {
        val snap = settingsSnapshot()
        val context: Context = host.context.applicationContext
        return ExecutorContext(
            accessibilityService = host.accessibilityService,
            appContext = context,
            scope = scope,
            actionSettings = snap.actionSettings.effectiveFor(sourceOverride),
            advancedSettings = snap.advancedSettings.effectiveFor(sourceOverride),
            showToast = { showToastUtil(it) },
            showLongToast = { showToastLongUtil(it) },
            currentPackageName = { host.getCurrentPackageName() },
            nowInLauncher = { host.nowInLauncher() },
            requestEnableDisabledPackage = { packageName, onResult ->
                host.requestEnableDisabledPackage(packageName, onResult)
            },
            toggleQuickAppLauncher = onToggleQuickAppLauncher,
            showVolumeScrub = onShowVolumeScrub,
            hideGestureButton = { delayMs ->
                if (sourceButton != null) onHideGestureButton(sourceButton, delayMs)
            },
            toggleKeepScreenOn = { keepScreenOnController.toggle() },
            showVersionTooLowToast = { resId -> showVersionTooLowToastUtil(context, resId) },
            previousApp = { previousAppTracker.previousApp() },
        )
    }
}

data class SettingsSnapshot(
    val actionSettings: LegacyActionSettings,
    val advancedSettings: LegacyAdvancedSettings,
    val gestureSettings: LegacyGestureSettings,
    val actionLibrarySettings: LegacyActionLibrarySettings,
)
