package hunoia.luno.action.dispatcher

import hunoia.luno.core.LunoLog as Log
import hunoia.luno.action.capability.CapabilityGate
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ActionExecutorRegistry
import hunoia.luno.action.execution.ActionResolver
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.core.LOG_TAG
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionRequest
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.execute.ActionCatalog
import hunoia.luno.execute.ActionFactory
import hunoia.luno.execute.ExecuteCall
import hunoia.luno.execute.ExecuteEnvironment
import hunoia.luno.execute.ExecuteMessages
import hunoia.luno.execute.ExecuteSettlement
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
    /** 动作库引用解析：entryId → 条目动作。 */
    private val libraryEntry: (String) -> StoredAction?,
    executors: List<ActionExecutor>,
    capabilityGate: CapabilityGate,
    /** 幂等窗口：同键重放返回上次落定，失败不缓存（architecture §4.7）。 */
    private val idempotency: IdempotencyCache = IdempotencyCache(),
) {
    private val registry: ActionExecutorRegistry = ActionExecutorRegistry(
        executors = executors,
        catalog = catalog,
        isCapabilityAvailable = capabilityGate::isAvailable,
    )

    private val resolver = ActionResolver(registry, catalog, factory)

    /** 可等待分发：调用方需要真实成败时用（如链接分发经执行口启动）。 */
    suspend fun execute(call: ExecuteCall): ExecuteSettlement = withContext(Dispatchers.Main.immediate) {
        dispatchAwait(call)
    }

    /** 异步分发：等执行落定后回调 [onSettled]（触发侧据此发事件/驱动自动化评估）。 */
    fun submit(call: ExecuteCall, onSettled: (ExecuteSettlement) -> Unit = {}) {
        scope.launch(Dispatchers.Main.immediate) {
            onSettled(dispatchAwait(call))
        }
    }

    private suspend fun dispatchAwait(call: ExecuteCall): ExecuteSettlement =
        idempotency.run(call.request.idempotencyKey) { dispatchOnce(call) }

    private suspend fun dispatchOnce(call: ExecuteCall): ExecuteSettlement {
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
            // 点击只在触点路径有意义：没有触点就判参数无效。
            if (call.touchPosition == null && effective.typeId == "accessibility.tap") {
                messages.showToast(messages.failureText(ActionFailure.InvalidParameter))
                return ExecuteSettlement(
                    call.request,
                    effective.typeId,
                    ActionResult.Failed(ActionFailure.InvalidParameter, "needs_touch_position"),
                )
            }
            val result = resolver.resolveFromLibrary(effective, ctx)
            if (hunoia.luno.execute.BuildConfig.DEBUG) Log.i(LOG_TAG, "result=$result")
            when (result) {
                is ActionResult.Failed -> messages.showToast(
                    result.userMessage ?: messages.failureText(result.reason),
                )
                is ActionResult.RequiresCapability -> messages.showToast(messages.capabilityText(result.capability))
                is ActionResult.Success -> result.message?.let { msg -> messages.showToast(msg) }
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

    private fun buildContext(call: ExecuteCall, effective: StoredAction): ExecutorContext =
        ExecutorContext(
            environment = environment,
            surfaces = surfaces,
            messages = messages,
            scope = scope,
            touchPosition = call.touchPosition,
            sourceButtonId = call.sourceButtonId,
            triggerContext = call.request.triggerContext,
        )
}
