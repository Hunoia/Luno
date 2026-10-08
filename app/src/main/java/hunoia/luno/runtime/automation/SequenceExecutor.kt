package hunoia.luno.runtime.automation

import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.matches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** 步骤执行守卫：步数 / IF 深度 / delay 单步与总量上限 / 单动作挂起上限。 */
object SequenceGuards {
    const val MAX_STEPS = 20
    const val MAX_IF_DEPTH = 5
    const val MAX_DELAY_MS = 60_000L
    const val MAX_TOTAL_DELAY_MS = 300_000L

    /**
     * 单个动作步的挂起上限。以前动作是 fire-and-forget 所以用不上；现在序列真等结果，
     * 若某个执行器永不回调（Shizuku enablePackage 挂住）整条序列会永久占着单飞名额，
     * 该规则从此一直 RULE_BUSY。
     */
    const val MAX_ACTION_MS = 30_000L

    /**
     * 规则嵌套（internal.runRule）深度上限：A→B→A 这类循环在这里截断，
     * 选择页同时禁自引用。只跑 steps，不经过引擎仲裁。
     */
    const val MAX_RULE_DEPTH = 5
}

/** 单步执行结果：区分"条目没了"与"执行了但失败"。 */
sealed interface StepOutcome {
    data object EntryMissing : StepOutcome
    data class Done(val result: hunoia.luno.action.model.ActionResult) : StepOutcome
}

/**
 * 步骤序列执行器：顺序跑 steps，某步失败即中断。
 * 失败 = 动作抛异常 / entry 引用缺失 / 执行结果是 Failed|RequiresCapability / 动作步超时；
 * delay 等的是上一步**真正落定**之后（动作是 await 的）。
 * 每条规则独立协程跑一份，一条的 delay 不阻塞其他规则。
 */
class SequenceExecutor(
    private val runSingleAction: suspend (action: StoredAction?, entryId: String, ctx: ConditionContext) -> StepOutcome,
) {
    suspend fun runSteps(steps: List<RuleStep>, ctx: ConditionContext): List<StepRecord> {
        val runner = Runner(ctx)
        runner.runList(steps.take(SequenceGuards.MAX_STEPS), prefix = "", depth = 0)
        return runner.records
    }

    private inner class Runner(val ctx: ConditionContext) {
        val records = mutableListOf<StepRecord>()
        var totalDelayMs = 0L
        var interrupted = false

        suspend fun runList(steps: List<RuleStep>, prefix: String, depth: Int) {
            for ((i, step) in steps.withIndex()) {
                if (interrupted) return
                val index = if (prefix.isEmpty()) "$i" else "$prefix.$i"
                when (step.kind) {
                    RuleStepKind.DELAY -> runDelay(step, index)
                    RuleStepKind.IF -> runIf(step, index, depth)
                    else -> runAction(step, index)
                }
            }
        }

        suspend fun runDelay(step: RuleStep, index: String) {
            val ms = step.delayMs.coerceIn(0L, SequenceGuards.MAX_DELAY_MS)
            if (totalDelayMs + ms > SequenceGuards.MAX_TOTAL_DELAY_MS) {
                records += StepRecord(index, RuleStepKind.DELAY, ok = false, message = "delay_total_capped")
                interrupted = true
                return
            }
            totalDelayMs += ms
            try {
                delay(ms)
            } catch (e: CancellationException) {
                // 协程取消（规则被删/进程退出）不是步骤失败，直接向上传播。
                records += StepRecord(index, RuleStepKind.DELAY, ok = false, message = "cancelled")
                throw e
            }
            records += StepRecord(index, RuleStepKind.DELAY, ok = true, message = "${ms}ms")
        }

        suspend fun runIf(step: RuleStep, index: String, depth: Int) {
            if (depth >= SequenceGuards.MAX_IF_DEPTH) {
                records += StepRecord(index, RuleStepKind.IF, ok = false, message = "if_depth_capped")
                interrupted = true
                return
            }
            val thenBranch = step.condition.matches(ctx)
            val branch = (if (thenBranch) step.thenSteps else step.elseSteps)
                .take(SequenceGuards.MAX_STEPS)
            records += StepRecord(
                index, RuleStepKind.IF, ok = true,
                message = "branch=${if (thenBranch) "then" else "else"} n=${branch.size}",
            )
            runList(branch, prefix = index, depth = depth + 1)
        }

        suspend fun runAction(step: RuleStep, index: String) {
            if (!step.isEffectiveAction) {
                records += StepRecord(index, RuleStepKind.ACTION, ok = true, message = "empty_skipped")
                return
            }
            try {
                val outcome = withTimeoutOrNull(SequenceGuards.MAX_ACTION_MS) {
                    runSingleAction(step.action, step.entryId, ctx)
                }
                if (outcome == null) {
                    records += StepRecord(index, RuleStepKind.ACTION, ok = false, message = "action_timeout")
                    interrupted = true
                    return
                }
                when (outcome) {
                    is StepOutcome.EntryMissing -> {
                        records += StepRecord(index, RuleStepKind.ACTION, ok = false, message = "entry_missing")
                        interrupted = true
                    }
                    is StepOutcome.Done -> {
                        val failure = failureOf(outcome.result)
                        records += StepRecord(
                            index, RuleStepKind.ACTION, ok = failure == null,
                            message = failure ?: "dispatched",
                        )
                        if (failure != null) interrupted = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                records += StepRecord(
                    index, RuleStepKind.ACTION, ok = false,
                    message = (e.message ?: e.javaClass.name).take(120),
                )
                interrupted = true
            }
        }

        /** 成功返回 null；失败返回可读原因（写进执行历史）。 */
        private fun failureOf(result: hunoia.luno.action.model.ActionResult): String? = when (result) {
            is hunoia.luno.action.model.ActionResult.Success -> null
            is hunoia.luno.action.model.ActionResult.Failed ->
                (result.message ?: result.reason.name).lowercase().take(120)
            is hunoia.luno.action.model.ActionResult.RequiresCapability -> "requires_capability"
        }
    }
}
