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
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.runtime.GestureHost
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.condition.toConditionContext
import hunoia.luno.runtime.environment.PreviousAppTracker
import hunoia.luno.runtime.automation.StepOutcome
import hunoia.luno.runtime.automation.SequenceGuards
import hunoia.luno.runtime.automation.SequenceExecutor
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.action.template.ParamTemplates
import hunoia.luno.action.template.RuntimeVariables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.CoroutineContext
import hunoia.luno.core.LOG_TAG

/**
 * 统一分发口（单 Dispatcher）：找定义 → 校验参数 → 查能力闸 → 调执行，同步返回三态。
 * 动作库引用（internal.libraryRef）就地解析。能力检查收归独立 [CapabilityGate]。
 */
class ActionDispatcher(
    private val host: GestureHost,
    private val scope: CoroutineScope,
    private val previousAppTracker: PreviousAppTracker,
    private val settingsSnapshot: () -> SettingsSnapshot,
    /**
     * 模板变量取值用的运行时快照。不消费脉冲：手势直接触发与 overlay 直派发都要用
     * 稳态值，消费脉冲会抢掉自动化评估该看到的那一次边沿。
     */
    private val runtimeState: () -> GestureRuntimeState,
    private val onToggleQuickAppLauncher: (anchorX: Int, anchorY: Int) -> Unit,
    private val onShowQuickSearch: () -> Unit,
    private val onShowGridPanel: (panelId: String, anchorX: Int, anchorY: Int) -> Unit,
    private val onShowVolumeScrub: (VolumeScrubConfig) -> Boolean,
    private val onShowPointer: () -> Boolean,
    private val onHideGestureButton: (GestureButton?, Long) -> Unit,
    private val onHideGestureButtons: (ButtonTarget, List<String>, Long) -> Unit,
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
    suspend fun runStepAction(
        action: StoredAction,
        ctx: ConditionContext,
        variables: Map<String, String> = emptyMap(),
    ): StepOutcome {
        val automationContext: () -> ConditionContext? = { ctx }
        return withContext(Dispatchers.Main.immediate) {
            dispatchAwait(action.withoutVariantKey(), null, null, automationContext, null, false, variables)
        }
    }

    /** 解析 SET_VAR 的值模板：系统/触发变量 + 本次运行已写入的局部变量。 */
    suspend fun resolveStepTemplate(
        text: String,
        ctx: ConditionContext,
        variables: Map<String, String>,
    ): String {
        if (text.isEmpty()) return text
        val used = ParamTemplates.tokensIn(text)
        val values = if (used.isEmpty()) {
            emptyMap()
        } else {
            RuntimeVariables.resolve(used, ctx, host.context.applicationContext)
        }
        return ParamTemplates.resolve(text, values + variables)
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
        if (BuildConfig.DEBUG) Log.i(LOG_TAG, "dispatch storedAction typeId=${stored.typeId}")
        scope.launch(Dispatchers.Main.immediate) {
            dispatchAwait(stored, sourceButton, touchPosition, templateContext, gestureTrigger, fromGesture)
        }
    }

    /**
     * dispatch 的可等待版本：等执行落定才返回，自动化序列靠它拿到真实成败
     *（否则 delay 只是「等什么都没发生的时间」）。
     */
    private suspend fun dispatchAwait(
        stored: StoredAction,
        sourceButton: GestureButton?,
        touchPosition: Pair<Int, Int>?,
        templateContext: () -> ConditionContext?,
        gestureTrigger: hunoia.luno.config.model.GestureTriggerType?,
        fromGesture: Boolean,
        localVariables: Map<String, String> = emptyMap(),
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
                val entry = settingsSnapshot().actionLibrarySettings.entries.find { it.id == entryId }
                if (entry == null) {
                    if (BuildConfig.DEBUG) Log.w(LOG_TAG, "library entry not found id=$entryId")
                    postDispatch(stored.typeId, ok = false)
                    return StepOutcome.EntryMissing
                }
                entry.storedAction
            } else {
                stored
            }
            val ctx = buildContext(sourceButton, touchPosition, templateContext, localVariables)
            // 点按类动作的前置条件：手势触点优先，其次看参数坐标，两者都没有才判参数无效。
            // 勾了「使用触发位置坐标」的条目 hasPointParams() 恒 false，无触点路径（自动化）在这里被拦。
            // 只看 StoredAction 参数，进 Executor 前不再复验。
            if (touchPosition == null && (effective.typeId == "accessibility.tap" || effective.typeId == "accessibility.longPress" || effective.typeId == "accessibility.doubleTap") &&
                !effective.hasPointParams()
            ) {
                ctx.showToast(failureText(appContext, ActionFailure.InvalidParameter))
                postDispatch(effective.typeId, ok = false)
                return StepOutcome.Done(ActionResult.Failed(ActionFailure.InvalidParameter, "needs_touch_position"))
            }
            val result = resolver.resolveFromLibrary(effective, ctx)
            if (BuildConfig.DEBUG) Log.i(LOG_TAG, "result=$result")
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
            Log.w(LOG_TAG, "dispatch crashed typeId=${stored.typeId}", e)
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
        localVariables: Map<String, String> = emptyMap(),
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
            showGridPanel = onShowGridPanel,
            showVolumeScrub = onShowVolumeScrub,
            showPointer = onShowPointer,
            hideGestureButton = { delayMs ->
                if (sourceButton != null) onHideGestureButton(sourceButton, delayMs)
            },
            hideGestureButtons = { target, buttonIds, delayMs ->
                onHideGestureButtons(target, buttonIds, delayMs)
            },
            showVersionTooLowToast = { resId -> showVersionTooLowToastUtil(context, resId) },
            previousApp = { previousAppTracker.previousApp() },
            touchPosition = touchPosition,
            templateContext = templateContext,
            templateValues = { used ->
                val runtime = templateContext()
                val system = if (runtime == null) emptyMap() else RuntimeVariables.resolve(used, runtime, context)
                // 局部变量覆盖同名系统 token，SET_VAR 才能改写模板取值。
                system + localVariables
            },
            runRule = { ruleId, ctx -> runRuleSteps(ruleId, ctx) },
        )
    }

    /**
     * 规则即动作：只跑目标规则的 effect steps，目标自身的触发条件/节流/优先级
     * 全部忽略，也不占引擎的「同规则单飞」名额。递归深度随协程上下文传递，
     * A→B→A 循环由 [SequenceGuards.MAX_RULE_DEPTH] 截断。steps 复用
     * [SequenceExecutor]，delay/if 与步数/超时守卫和引擎路径一致；
     * 内层步骤按自身失败策略处理，未被 SKIP 的失败使整体失败。
     */
    private suspend fun runRuleSteps(ruleId: String, ctx: ExecutorContext): ActionResult {
        val depth = currentCoroutineContext()[RuleDepth]?.depth ?: 0
        if (depth >= SequenceGuards.MAX_RULE_DEPTH) {
            return ActionResult.Failed(ActionFailure.ExecutionFailed, "rule_depth_capped")
        }
        val rule = settingsSnapshot().automationRules.find { it.id == ruleId }
            ?: return ActionResult.Failed(ActionFailure.ExecutionFailed, "rule_missing:$ruleId")
        if (!rule.enabled) {
            return ActionResult.Failed(ActionFailure.ExecutionFailed, "rule_disabled:$ruleId")
        }
        val steps = rule.effect.steps
        if (steps.isEmpty()) {
            return ActionResult.Failed(ActionFailure.ExecutionFailed, "rule_no_steps:$ruleId")
        }
        val templateCtx = ctx.templateContext() ?: return ActionResult.Failed(ActionFailure.ExecutionFailed)
        return withContext(RuleDepth(depth + 1)) {
            val nested = SequenceExecutor(
                runSingleAction = { action, _, vars ->
                    runStepAction(action, templateCtx, vars)
                },
                resolveTemplate = { text, _, vars ->
                    resolveStepTemplate(text, templateCtx, vars)
                },
            )
            val records = nested.runSteps(steps, templateCtx)
            val failed = records.firstOrNull { !it.ok && !it.skipped }
            if (failed == null) {
                ActionResult.Success()
            } else {
                ActionResult.Failed(
                    ActionFailure.ExecutionFailed,
                    "step ${failed.index}: ${failed.message}",
                )
            }
        }
    }

}

data class SettingsSnapshot(
    val actionSettings: ActionSettings,
    val advancedSettings: AdvancedSettings,
    val gestureSettings: hunoia.luno.config.model.GestureSettings,
    val actionLibrarySettings: ActionLibrarySettings = ActionLibrarySettings(),
    /** runRule 目标查找：快照直读，不额外走 DataStore（Main 上安全）。 */
    val automationRules: List<AutomationRule> = emptyList(),
)

/** runRule 嵌套深度：随协程上下文传递，顶层触发天然从 0 起。 */
data class RuleDepth(val depth: Int) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<RuleDepth>
    override val key: CoroutineContext.Key<*> get() = Key
}
