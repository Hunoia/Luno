package hunoia.luno.runtime

import hunoia.luno.config.model.AutomationRule
import hunoia.luno.core.domain.ActionFailure
import hunoia.luno.core.domain.ActionResult
import hunoia.luno.core.domain.RuntimeSnapshot
import hunoia.luno.core.domain.StepOutcome
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.execute.NestedRuleRunner
import hunoia.luno.runtime.automation.SequenceExecutor
import hunoia.luno.runtime.automation.SequenceGuards
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * 「规则即动作」的嵌套执行：执行口（`:feature:execute`）只声明 [NestedRuleRunner] 契约，
 * 序列执行器归自动化侧（Phase 2 的 `:feature:automation`），实现留在这里。
 *
 * 语义与迁移前一致：只跑目标规则的 effect steps，目标自身的触发条件/节流/优先级
 * 全部忽略，也不占引擎的「同规则单飞」名额；递归深度随协程上下文传递，
 * A→B→A 循环由 [SequenceGuards.MAX_RULE_DEPTH] 截断。
 */
class AutomationNestedRuleRunner(
    private val ruleById: (String) -> AutomationRule?,
    private val globalVariables: () -> Map<String, String>,
    private val runSingleAction: suspend (StoredAction, RuntimeSnapshot, Map<String, String>) -> StepOutcome,
    private val resolveTemplate: suspend (String, RuntimeSnapshot, Map<String, String>) -> String,
    private val refreshSnapshot: () -> RuntimeSnapshot,
    private val writeGlobalVariable: suspend (String, String) -> Unit,
) : NestedRuleRunner {

    override suspend fun run(
        ruleId: String,
        params: Map<String, String>,
        snapshot: RuntimeSnapshot?,
    ): ActionResult {
        val depth = currentCoroutineContext()[RuleDepth]?.depth ?: 0
        if (depth >= SequenceGuards.MAX_RULE_DEPTH) {
            return ActionResult.Failed(ActionFailure.ExecutionFailed, "rule_depth_capped")
        }
        val rule = ruleById(ruleId)
            ?: return ActionResult.Failed(ActionFailure.ExecutionFailed, "rule_missing:$ruleId")
        if (!rule.enabled) {
            return ActionResult.Failed(ActionFailure.ExecutionFailed, "rule_disabled:$ruleId")
        }
        val steps = rule.effect.steps
        if (steps.isEmpty()) {
            return ActionResult.Failed(ActionFailure.ExecutionFailed, "rule_no_steps:$ruleId")
        }
        val templateCtx = snapshot
            ?: return ActionResult.Failed(ActionFailure.ExecutionFailed, "no_snapshot")
        // 子规则作用域：全局变量 + 本次调用参数（缺省用规则定义里的默认值）。
        // 父规则局部变量不向下泄漏，避免同名变量被意外覆盖。
        val variables = globalVariables().toMutableMap()
        rule.params.forEach { param ->
            val provided = params[param.name]
            val value = provided ?: runCatching {
                resolveTemplate(param.defaultValue, templateCtx, variables)
            }.getOrDefault(param.defaultValue)
            variables["param.${param.name}"] = value
        }
        return withContext(RuleDepth(depth + 1)) {
            val nested = SequenceExecutor(
                // 用 Runner 传入的当前上下文：WAIT_UNTIL 刷新后，后续动作/条件不能继续读子规则启动时的旧快照。
                runSingleAction = { action, stepCtx, vars -> runSingleAction(action, stepCtx, vars) },
                resolveTemplate = { text, stepCtx, vars -> resolveTemplate(text, stepCtx, vars) },
                refreshContext = { base ->
                    try {
                        refreshSnapshot()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        base
                    }
                },
                writeGlobalVariable = { name, value -> writeGlobalVariable(name, value) },
            )
            val result = nested.runStepsWithResult(steps, templateCtx, variables)
            val failed = result.records.firstOrNull { !it.ok && !it.skipped }
            if (failed == null) {
                val returned = result.variables["return"]
                ActionResult.Success(
                    outputs = if (returned == null) emptyMap() else mapOf("return" to returned),
                )
            } else {
                ActionResult.Failed(
                    ActionFailure.ExecutionFailed,
                    "step ${failed.index}: ${failed.message}",
                )
            }
        }
    }
}

/** runRule 嵌套深度：随协程上下文传递，顶层触发天然从 0 起。 */
data class RuleDepth(val depth: Int) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<RuleDepth>
    override val key: CoroutineContext.Key<*> get() = Key
}
