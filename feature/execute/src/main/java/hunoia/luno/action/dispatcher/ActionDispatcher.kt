package hunoia.luno.action.dispatcher

import android.util.Log
import hunoia.luno.action.capability.CapabilityGate
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ActionExecutorRegistry
import hunoia.luno.action.execution.ActionResolver
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.template.ParamTemplates
import hunoia.luno.action.template.RuntimeVariables
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionRequest
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.RuntimeSnapshot
import hunoia.luno.core.domain.StepOutcome
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.hasPointParams
import hunoia.luno.core.domain.withoutVariantKey
import hunoia.luno.execute.ActionCatalog
import hunoia.luno.execute.ActionFactory
import hunoia.luno.execute.ExecuteCall
import hunoia.luno.execute.ExecuteEnvironment
import hunoia.luno.execute.ExecuteMessages
import hunoia.luno.execute.ExecuteSettlement
import hunoia.luno.execute.NestedRuleRunner
import hunoia.luno.execute.SurfaceHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 统一分发口（单 Dispatcher）：找定义 → 校验参数 → 查能力闸 → 调执行，同步返回三态。
 * 动作库引用（internal.libraryRef）就地解析。
 *
 * 执行口只做 Execute：
 * - 输入只有 [ActionRequest]，触发源专属状态（按钮/锚点/触点）随 [ExecuteCall] 传入；
 * - 不 import `:app`、`core:runtime`、`config.model`：宿主能力、动作目录、启动基础设施
 *   全部走端口注入（见 `hunoia.luno.execute`）；
 * - 分发落定通过 [submit] 的回调回给触发侧，不再直接往事件总线发手势事件。
 */
class ActionDispatcher(
    private val environment: ExecuteEnvironment,
    private val surfaces: SurfaceHost,
    private val messages: ExecuteMessages,
    private val catalog: ActionCatalog,
    private val factory: ActionFactory,
    private val scope: CoroutineScope,
    /**
     * 模板变量取值用的缺省运行时快照。不消费脉冲：手势直接触发与 overlay 直派发都要用
     * 稳态值，消费脉冲会抢掉自动化评估该看到的那一次边沿。
     */
    private val snapshot: () -> RuntimeSnapshot,
    /** 动作库引用解析：entryId → 条目动作。 */
    private val libraryEntry: (String) -> StoredAction?,
    executors: List<ActionExecutor>,
    capabilityGate: CapabilityGate,
    /** 嵌套规则端口（automation 侧实现）；null = 该链路不支持 runRule。 */
    private val nestedRules: NestedRuleRunner? = null,
) {
    private val registry: ActionExecutorRegistry = ActionExecutorRegistry(
        executors = executors,
        catalog = catalog,
        isCapabilityAvailable = capabilityGate::isAvailable,
    )

    private val resolver = ActionResolver(registry, catalog, factory)

    /** 异步分发：等执行落定后回调 [onSettled]（触发侧据此发事件/驱动自动化评估）。 */
    fun submit(call: ExecuteCall, onSettled: (ExecuteSettlement) -> Unit = {}) {
        scope.launch(Dispatchers.Main.immediate) {
            onSettled(dispatchAwait(call))
        }
    }

    /**
     * v2 单步骤执行：**等执行落定**才返回，真实成败与异常都交给 SequenceExecutor 记账/中断。
     * 必须在 Main 上跑（与 dispatch 的 launch 目标一致），否则无障碍调用线程会变。
     */
    suspend fun runStepAction(
        action: StoredAction,
        ctx: RuntimeSnapshot,
        variables: Map<String, String> = emptyMap(),
    ): StepOutcome = withContext(Dispatchers.Main.immediate) {
        dispatchAwait(
            ExecuteCall(
                request = ActionRequest(action.withoutVariantKey(), hunoia.luno.core.domain.DispatchOrigin.AUTOMATION),
                snapshot = ctx,
                localVariables = variables,
            )
        ).toStepOutcome()
    }

    /** 解析 SET_VAR 的值模板：系统/触发变量 + 本次运行已写入的局部变量。 */
    suspend fun resolveStepTemplate(
        text: String,
        ctx: RuntimeSnapshot,
        variables: Map<String, String>,
    ): String {
        if (text.isEmpty()) return text
        val used = ParamTemplates.tokensIn(text)
        val values = if (used.isEmpty()) {
            emptyMap()
        } else {
            RuntimeVariables.resolve(used, ctx, environment.appContext)
        }
        return ParamTemplates.resolve(text, values + variables)
    }

    private suspend fun dispatchAwait(call: ExecuteCall): ExecuteSettlement {
        val stored = call.request.stored
        try {
            // 动作库引用就地解析为条目动作（单轨：引用本身也是 StoredAction）。
            val effective = if (stored.typeId == "internal.libraryRef") {
                val entryId = (stored.params["entryId"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val entry = libraryEntry(entryId)
                if (entry == null) {
                    if (hunoia.luno.execute.BuildConfig.DEBUG) Log.w(LOG_TAG, "library entry not found id=$entryId")
                    return ExecuteSettlement(
                        call.request,
                        stored.typeId,
                        ActionResult.Failed(ActionFailure.InvalidParameter, "library_entry_missing"),
                    )
                }
                entry
            } else {
                stored
            }
            val ctx = buildContext(call, effective)
            // 点按类动作的前置条件：手势触点优先，其次看参数坐标，两者都没有才判参数无效。
            // 勾了「使用触发位置坐标」的条目 hasPointParams() 恒 false，无触点路径（自动化）在这里被拦。
            // 只看 StoredAction 参数，进 Executor 前不再复验。
            if (call.touchPosition == null &&
                (effective.typeId == "accessibility.tap" ||
                    effective.typeId == "accessibility.longPress" ||
                    effective.typeId == "accessibility.doubleTap") &&
                !effective.hasPointParams()
            ) {
                ctx.showToast(messages.failureText(ActionFailure.InvalidParameter))
                return ExecuteSettlement(
                    call.request,
                    effective.typeId,
                    ActionResult.Failed(ActionFailure.InvalidParameter, "needs_touch_position"),
                )
            }
            val result = resolver.resolveFromLibrary(effective, ctx)
            if (hunoia.luno.execute.BuildConfig.DEBUG) Log.i(LOG_TAG, "result=$result")
            when (result) {
                is ActionResult.Failed -> ctx.showToast(messages.failureText(result.reason))
                is ActionResult.RequiresCapability -> ctx.showToast(messages.capabilityText(result.capability))
                is ActionResult.Success -> result.message?.let { msg -> ctx.showToast(msg) }
            }
            return ExecuteSettlement(call.request, effective.typeId, result)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 兜底：畸形数据/第三方执行器异常都不允许冒到协程外崩掉 App。
            Log.w(LOG_TAG, "dispatch crashed typeId=${stored.typeId}", e)
            return ExecuteSettlement(
                call.request,
                stored.typeId,
                ActionResult.Failed(ActionFailure.ExecutionFailed, e.message),
            )
        }
    }

    private fun buildContext(call: ExecuteCall, effective: StoredAction): ExecutorContext {
        val context = environment.appContext
        val buttonId = call.sourceButtonId
        val callSnapshot: () -> RuntimeSnapshot? = { call.snapshot }
        return ExecutorContext(
            accessibilityService = environment.accessibilityService,
            appContext = context,
            scope = scope,
            showToast = messages::showToast,
            showLongToast = messages::showLongToast,
            currentPackageName = environment::currentPackageName,
            nowInLauncher = { environment.nowInLauncher() },
            requestEnableDisabledPackage = { packageName, onResult ->
                environment.requestEnableDisabledPackage(packageName, onResult)
            },
            toggleQuickAppLauncher = surfaces::toggleQuickAppLauncher,
            showQuickSearch = surfaces::showQuickSearch,
            showGridPanel = surfaces::showGridPanel,
            gridPanelExists = surfaces::gridPanelExists,
            showVolumeScrub = surfaces::showVolumeScrub,
            showPointer = surfaces::showPointer,
            hideGestureButton = { delayMs ->
                if (buttonId != null) surfaces.hideGestureButton(buttonId, delayMs)
            },
            hideGestureButtons = surfaces::hideGestureButtons,
            previousApp = surfaces::previousApp,
            touchPosition = call.touchPosition,
            templateContext = callSnapshot,
            templateValues = { used ->
                val runtime = call.snapshot ?: snapshot()
                val system = RuntimeVariables.resolve(used, runtime, context)
                // 局部变量覆盖同名系统/全局 token，SET_VAR 才能改写模板取值。
                system + runtime.variables + call.localVariables
            },
            runRule = nestedRules?.let { runner ->
                { ruleId, params, ctx -> runner.run(ruleId, params, ctx.templateContext()) }
            },
            launchApp = surfaces::launchApp,
            openUrl = surfaces::openUrl,
            launchShortcut = surfaces::launchShortcut,
            openAppDetails = surfaces::openAppDetails,
            launchInPopup = surfaces::launchInPopup,
        )
    }

    private fun ExecuteSettlement.toStepOutcome(): StepOutcome = StepOutcome.Done(result)
}

