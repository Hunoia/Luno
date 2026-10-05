package hunoia.luno.runtime.automation

import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.matches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 步骤执行守卫（见 docs/automation-v2.md §3.3）。 */
object SequenceGuards {
    const val MAX_STEPS = 20
    const val MAX_IF_DEPTH = 5
    const val MAX_DELAY_MS = 60_000L
    const val MAX_TOTAL_DELAY_MS = 300_000L
}

/**
 * 步骤序列执行器：顺序跑 steps，某步失败即中断。
 * 失败 = 动作抛异常 / entry 引用缺失；动作内部执行结果不追踪（dispatcher 内部已吞）。
 * 每条规则独立协程跑一份，一条的 delay 不阻塞其他规则。
 */
class SequenceExecutor(
    private val runSingleAction: (action: StoredAction?, entryId: String, ctx: ConditionContext) -> Boolean,
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

        fun runAction(step: RuleStep, index: String) {
            if (!step.isEffectiveAction) {
                records += StepRecord(index, RuleStepKind.ACTION, ok = true, message = "empty_skipped")
                return
            }
            try {
                val ok = runSingleAction(step.action, step.entryId, ctx)
                records += StepRecord(
                    index, RuleStepKind.ACTION, ok = ok,
                    message = if (ok) "dispatched" else "entry_missing",
                )
                if (!ok) interrupted = true
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
    }
}
