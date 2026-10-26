package hunoia.luno.runtime.automation

import hunoia.luno.action.model.RUN_RULE_TYPE_ID
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.StepFailure
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

    /** 单个动作步的挂起上限：执行器永不回调（如 Shizuku enablePackage 挂住）时，序列真等结果会永久占着单飞名额。 */
    const val MAX_ACTION_MS = 30_000L

    /**
     * internal.runRule 复合动作步的挂起上限：目标规则自带 delay/if 守卫，30s 单动作上限会误判长延时目标规则超时；
     * 给「delay 总预算 + 单动作预算」的松弛，嵌套链仍被最外层时限兜住。
     */
    const val MAX_RULE_STEP_MS = MAX_TOTAL_DELAY_MS + MAX_ACTION_MS

    /** 规则嵌套（internal.runRule）深度上限：A→B→A 循环在此截断（选择页同时禁自引用）；只跑 steps，不经过引擎仲裁。 */
    const val MAX_RULE_DEPTH = 5
}

/** 单步执行结果：区分"条目没了"与"执行了但失败"。 */
sealed interface StepOutcome {
    data object EntryMissing : StepOutcome
    data class Done(val result: hunoia.luno.action.model.ActionResult) : StepOutcome
}

/**
 * 步骤序列执行器：顺序跑 steps，默认某步失败即中断（动作异常 / entry 缺失 / Failed|RequiresCapability / 动作超时）。
 * ACTION 步可按 [hunoia.luno.config.model.StepFailure] 配「跳过」或「重试」；delay/if/setVar 的配置错误仍按中断处理。
 * delay 等上一步真正落定之后；每条规则独立协程跑一份。
 */
class SequenceExecutor(
    private val runSingleAction: suspend (action: StoredAction, ctx: ConditionContext, variables: Map<String, String>) -> StepOutcome,
    /** 解析 SET_VAR 的值模板：系统/触发变量 + 本次运行已写入的局部变量。 */
    private val resolveTemplate: suspend (text: String, ctx: ConditionContext, variables: Map<String, String>) -> String,
) {
    suspend fun runSteps(
        steps: List<RuleStep>,
        ctx: ConditionContext,
        variables: MutableMap<String, String> = mutableMapOf(),
    ): List<StepRecord> {
        val runner = Runner(ctx, variables)
        runner.runList(steps.take(SequenceGuards.MAX_STEPS), prefix = "", depth = 0)
        return runner.records
    }

    private inner class Runner(
        val ctx: ConditionContext,
        val variables: MutableMap<String, String>,
    ) {
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
                    RuleStepKind.SET_VAR -> runSetVar(step, index)
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

        suspend fun runSetVar(step: RuleStep, index: String) {
            val name = step.varName.trim()
            if (name.isEmpty()) {
                records += StepRecord(index, RuleStepKind.SET_VAR, ok = false, message = "var_name_empty")
                interrupted = true
                return
            }
            val value = try {
                resolveTemplate(step.varTemplate, ctx, variables.toMap())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                records += StepRecord(
                    index, RuleStepKind.SET_VAR, ok = false,
                    message = (e.message ?: e.javaClass.name).take(120),
                )
                interrupted = true
                return
            }
            // 局部变量只在本次运行内有效；写成 var.<name> 与系统变量同形，模板扫描无需特殊分支。
            variables["var.$name"] = value
            records += StepRecord(
                index, RuleStepKind.SET_VAR, ok = true,
                message = "var.$name=${value.take(80)}",
            )
        }

        suspend fun runIf(step: RuleStep, index: String, depth: Int) {
            if (depth >= SequenceGuards.MAX_IF_DEPTH) {
                records += StepRecord(index, RuleStepKind.IF, ok = false, message = "if_depth_capped")
                interrupted = true
                return
            }
            // 用触发时冻结的 ConditionContext 求值：脉冲字段（通知/手势等）在触发瞬间
            // 之后仍可见，delay 后读到的也是触发时快照，不是实时环境。
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
            val action = step.action
            if (step.kind != RuleStepKind.ACTION || action == null) {
                records += StepRecord(index, RuleStepKind.ACTION, ok = true, message = "empty_skipped")
                return
            }
            val policy = when (step.onFailure) {
                StepFailure.SKIP -> StepFailure.SKIP
                StepFailure.RETRY -> StepFailure.RETRY
                else -> StepFailure.STOP
            }
            val maxRetries = if (policy == StepFailure.RETRY) {
                step.retryCount.coerceIn(0, StepFailure.MAX_RETRY_COUNT)
            } else {
                0
            }
            val retryIntervalMs = step.retryIntervalMs.coerceIn(0L, StepFailure.MAX_RETRY_INTERVAL_MS)
            // runRule 是复合步骤（内部可能含 delay/if，自带守卫），用放宽的时限；
            // 其余单动作保持 30s 挂起上限。
            val timeoutMs = if (action.typeId == RUN_RULE_TYPE_ID) {
                SequenceGuards.MAX_RULE_STEP_MS
            } else {
                SequenceGuards.MAX_ACTION_MS
            }

            var attemptsMade = 0
            var lastFailure: String? = null
            while (true) {
                if (attemptsMade > 0) {
                    // 重试等待也计入总延时预算，避免 20 步 × 5 次 × 30s 绕过序列时长上限。
                    if (totalDelayMs + retryIntervalMs > SequenceGuards.MAX_TOTAL_DELAY_MS) {
                        lastFailure = (lastFailure.orEmpty() + " retry_delay_capped").trim()
                        break
                    }
                    totalDelayMs += retryIntervalMs
                    delay(retryIntervalMs)
                }
                attemptsMade++
                lastFailure = try {
                    val outcome = withTimeoutOrNull(timeoutMs) {
                        runSingleAction(action, ctx, variables.toMap())
                    }
                    when (outcome) {
                        null -> "action_timeout"
                        is StepOutcome.EntryMissing -> "entry_missing"
                        is StepOutcome.Done -> failureOf(outcome.result)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    (e.message ?: e.javaClass.name).take(120)
                }

                if (lastFailure == null) {
                    records += StepRecord(
                        index, RuleStepKind.ACTION, ok = true,
                        message = if (attemptsMade > 1) {
                            "dispatched_retry_${attemptsMade - 1}"
                        } else {
                            "dispatched"
                        },
                    )
                    return
                }
                // RequiresCapability / entry_missing 重试也不会变，直接按策略收尾。
                val canRetry = policy == StepFailure.RETRY &&
                    isRetryable(lastFailure) &&
                    attemptsMade <= maxRetries
                if (canRetry) continue
                break
            }

            val message = buildString {
                append(lastFailure.orEmpty())
                if (policy == StepFailure.RETRY && attemptsMade > 1) {
                    append(" (attempts=").append(attemptsMade).append(')')
                }
            }
            records += StepRecord(
                index, RuleStepKind.ACTION, ok = false, message = message,
                skipped = policy == StepFailure.SKIP,
            )
            if (policy != StepFailure.SKIP) {
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

        /** 重试对这些失败无意义：能力缺失要用户授权，entry 缺失要用户修数据。 */
        private fun isRetryable(failure: String): Boolean =
            failure != "requires_capability" && failure != "entry_missing"
    }
}
