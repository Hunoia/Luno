package hunoia.luno.action.dispatcher

import android.content.Context
import android.util.Log
import hunoia.luno.BuildConfig
import hunoia.luno.R
import hunoia.luno.action.capability.CapabilityGate
import hunoia.luno.action.controller.*
import hunoia.luno.action.execution.*
import hunoia.luno.action.executor.*
import hunoia.luno.action.model.*
import hunoia.luno.bridge.feedback.showToast as showToastUtil
import hunoia.luno.bridge.feedback.showToastLong as showToastLongUtil
import hunoia.luno.bridge.feedback.showVersionTooLowToast as showVersionTooLowToastUtil
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.config.model.effectiveFor
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.action.PreviousAppTracker
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.template.RuntimeVariables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 统一分发口（单 Dispatcher）：找定义 → 校验参数 → 查能力闸 → 调执行，同步返回三态。
 * 动作库引用（internal.libraryRef）就地解析。能力检查收归独立 [CapabilityGate]。
 */
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
    private val capabilityGate = CapabilityGate(host.context.applicationContext)

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
        isCapabilityAvailable = capabilityGate::isAvailable,
    )

    private val resolver = ActionResolver(registry)

    /** v2 单步骤执行：entry 缺失返回 false；异常抛给调用方（SequenceExecutor 捕获即中断）。 */
    fun runStepAction(action: StoredAction?, entryId: String, ctx: ConditionContext): Boolean {
        val automationContext: () -> ConditionContext? = { ctx }
        if (action != null) {
            dispatch(action.withoutVariantKey(), null, null, null, automationContext)
            return true
        }
        if (entryId.isBlank()) return false
        return dispatchEntry(entryId, null, null, null, automationContext)
    }

    /** 供 overlay 等场景直接执行一个已构造好的动作。 */
    fun dispatchAction(action: StoredAction) {
        dispatch(action.withoutVariantKey(), null)
    }

    fun dispatch(
        stored: StoredAction,
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride? = null,
        touchPosition: Pair<Int, Int>? = null,
        templateContext: () -> ConditionContext? = { null },
        /** 手势触发类型（手势链路传入）；overlay 直调用默认 null。 */
        gestureTrigger: hunoia.luno.config.model.GestureTriggerType? = null,
        /** 是否源自手势链路：只有它为 true，分发落定事件才会驱动 GESTURE 域评估。 */
        fromGesture: Boolean = false,
    ) {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "dispatch storedAction typeId=${stored.typeId}")
        scope.launch(Dispatchers.Main.immediate) {
            fun postDispatch(typeId: String, ok: Boolean) {
                hunoia.luno.core.Events.post(
                    GestureDispatchEvent(
                        typeId = typeId,
                        buttonId = sourceButton?.id,
                        trigger = gestureTrigger,
                        fromGesture = fromGesture,
                        ok = ok,
                    )
                )
            }
            // 动作库引用就地解析为条目动作（单轨：引用本身也是 StoredAction）。
            val effective = if (stored.typeId == "internal.libraryRef") {
                val entryId = (stored.params["entryId"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
                val entry = settingsSnapshot().newActionLibrarySettings.entries.find { it.id == entryId }
                if (entry == null) {
                    if (BuildConfig.DEBUG) Log.w("LunoLauncher", "library entry not found id=$entryId")
                    postDispatch(stored.typeId, ok = false)
                    return@launch
                }
                entry.storedAction
            } else {
                stored
            }
            val ctx = buildContext(sourceButton, sourceOverride, touchPosition, templateContext)
            // 点按类动作的前置条件：坐标来自手势上下文，不是 StoredAction 参数，
            // 在分发层校验，不进 Executor。
            if (touchPosition == null && (effective.typeId == "accessibility.tap" || effective.typeId == "accessibility.longPress")) {
                ctx.showToast(failureText(appContext, ActionFailure.InvalidParameter))
                postDispatch(effective.typeId, ok = false)
                return@launch
            }
            val result = resolver.resolveFromLibrary(effective, ctx)
            if (BuildConfig.DEBUG) Log.i("LunoLauncher", "result=$result")
            when (result) {
                is ActionResult.Failed -> ctx.showToast(result.message ?: failureText(appContext, result.reason))
                is ActionResult.RequiresCapability -> ctx.showToast(capabilityText(appContext, result.capability))
                is ActionResult.Success -> result.message?.let { msg -> ctx.showToast(msg) }
            }
            postDispatch(effective.typeId, ok = result is ActionResult.Success)
        }
    }

    private fun dispatchEntry(
        entryId: String,
        sourceButton: GestureButton?,
        sourceOverride: GestureButtonActionSettingsOverride?,
        touchPosition: Pair<Int, Int>?,
        templateContext: () -> ConditionContext?,
    ): Boolean {
        val entry = settingsSnapshot().newActionLibrarySettings.entries.find { it.id == entryId }
        if (entry == null) {
            if (BuildConfig.DEBUG) Log.w("LunoLauncher", "entry not found id=$entryId")
            return false
        }
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "dispatch entry id=$entryId typeId=${entry.storedAction.typeId}")
        dispatch(entry.storedAction, sourceButton, sourceOverride, touchPosition, templateContext)
        return true
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

data class SettingsSnapshot(
    val actionSettings: ActionSettings,
    val advancedSettings: AdvancedSettings,
    val gestureSettings: hunoia.luno.config.model.GestureSettings,
    val newActionLibrarySettings: NewActionLibrarySettings = NewActionLibrarySettings(),
)
