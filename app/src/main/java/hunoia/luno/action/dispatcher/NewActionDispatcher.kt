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
import hunoia.luno.bridge.ui.showToast as showToastUtil
import hunoia.luno.bridge.ui.showToastLong as showToastLongUtil
import hunoia.luno.bridge.ui.showVersionTooLowToast as showVersionTooLowToastUtil
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.config.model.VolumeScrubConfig
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.condition.toConditionContext
import hunoia.luno.runtime.environment.PreviousAppTracker
import hunoia.luno.runtime.automation.StepOutcome
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.action.template.RuntimeVariables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /**
     * 模板变量取值用的运行时快照。不消费脉冲（手势直接触发与 overlay 直派发都要用稳态值，
     * 消费脉冲会抢掉自动化评估该看到的那一次边沿）。
     */
    private val runtimeState: () -> GestureRuntimeState,
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

    /**
     * v2 单步骤执行：**等执行落定**才返回，真实成败与异常都交给 SequenceExecutor 记账/中断。
     * 必须在 Main 上跑（与 dispatch 的 launch 目标一致），否则无障碍调用线程会变。
     */
    suspend fun runStepAction(action: StoredAction?, entryId: String, ctx: ConditionContext): StepOutcome {
        val automationContext: () -> ConditionContext? = { ctx }
        val target = when {
            action != null -> action.withoutVariantKey()
            entryId.isBlank() -> return StepOutcome.EntryMissing
            else -> {
                val entry = settingsSnapshot().newActionLibrarySettings.entries.find { it.id == entryId }
                if (entry == null) {
                    if (BuildConfig.DEBUG) Log.w("LunoLauncher", "step entry not found id=$entryId")
                    return StepOutcome.EntryMissing
                }
                entry.storedAction.withoutVariantKey()
            }
        }
        return withContext(Dispatchers.Main.immediate) {
            dispatchAwait(target, null, null, automationContext, null, false)
        }
    }

    /** 供 overlay 等场景直接执行一个已构造好的动作。 */
    fun dispatchAction(action: StoredAction) {
        dispatch(action.withoutVariantKey(), null)
    }

    fun dispatch(
        stored: StoredAction,
        sourceButton: GestureButton?,
        touchPosition: Pair<Int, Int>? = null,
        templateContext: () -> ConditionContext? = { runtimeState().toConditionContext() },
        /** 手势触发类型（手势链路传入）；overlay 直调用默认 null。 */
        gestureTrigger: hunoia.luno.config.model.GestureTriggerType? = null,
        /** 是否源自手势链路：只有它为 true，分发落定事件才会驱动 GESTURE 域评估。 */
        fromGesture: Boolean = false,
    ) {
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "dispatch storedAction typeId=${stored.typeId}")
        scope.launch(Dispatchers.Main.immediate) {
            dispatchAwait(stored, sourceButton, touchPosition, templateContext, gestureTrigger, fromGesture)
        }
    }

    /**
     * dispatch 的可等待版本：主体与 [dispatch] 完全一致，但等执行落定才返回。
     * 自动化序列靠它拿到真实成败（否则 delay 只是"等什么都没发生的时间"）。
     */
    private suspend fun dispatchAwait(
        stored: StoredAction,
        sourceButton: GestureButton?,
        touchPosition: Pair<Int, Int>?,
        templateContext: () -> ConditionContext?,
        gestureTrigger: hunoia.luno.config.model.GestureTriggerType?,
        fromGesture: Boolean,
    ): StepOutcome {
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
        try {
        // 动作库引用就地解析为条目动作（单轨：引用本身也是 StoredAction）。
        val effective = if (stored.typeId == "internal.libraryRef") {
            val entryId = (stored.params["entryId"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
            val entry = settingsSnapshot().newActionLibrarySettings.entries.find { it.id == entryId }
            if (entry == null) {
                if (BuildConfig.DEBUG) Log.w("LunoLauncher", "library entry not found id=$entryId")
                postDispatch(stored.typeId, ok = false)
                return StepOutcome.EntryMissing
            }
            entry.storedAction
        } else {
            stored
        }
        val ctx = buildContext(sourceButton, touchPosition, templateContext)
        // 点按类动作的前置条件：坐标来自手势上下文，不是 StoredAction 参数，
        // 在分发层校验，不进 Executor。
        if (touchPosition == null && (effective.typeId == "accessibility.tap" || effective.typeId == "accessibility.longPress")) {
            ctx.showToast(failureText(appContext, ActionFailure.InvalidParameter))
            postDispatch(effective.typeId, ok = false)
            return StepOutcome.Done(ActionResult.Failed(ActionFailure.InvalidParameter, "needs_touch_position"))
        }
        val result = resolver.resolveFromLibrary(effective, ctx)
        if (BuildConfig.DEBUG) Log.i("LunoLauncher", "result=$result")
        when (result) {
            is ActionResult.Failed -> ctx.showToast(result.message ?: failureText(appContext, result.reason))
            is ActionResult.RequiresCapability -> ctx.showToast(capabilityText(appContext, result.capability))
            is ActionResult.Success -> result.message?.let { msg -> ctx.showToast(msg) }
        }
        postDispatch(effective.typeId, ok = result is ActionResult.Success)
        return StepOutcome.Done(result)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 兜底：畸形数据/第三方执行器异常都不允许冒到协程外崩掉 App。
            Log.w("LunoLauncher", "dispatch crashed typeId=${stored.typeId}", e)
            postDispatch(stored.typeId, ok = false)
            return StepOutcome.Done(ActionResult.Failed(ActionFailure.ExecutionFailed, e.message))
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
            advancedSettings = snap.advancedSettings,
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
