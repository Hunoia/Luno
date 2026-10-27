package hunoia.luno.runtime.automation

import android.os.SystemClock
import hunoia.luno.action.model.ActionResult
import hunoia.luno.action.model.RUN_RULE_TYPE_ID
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.RuleStepKind
import hunoia.luno.config.model.SequenceLimits
import hunoia.luno.config.model.StepFailure
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.matches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 步骤执行守卫：步数 / IF 深度 / delay 单步与总量上限 / 单动作挂起上限。 */
object SequenceGuards {
    const val MAX_STEPS = SequenceLimits.MAX_STEPS
    const val MAX_IF_DEPTH = 5
    const val MAX_DELAY_MS = SequenceLimits.MAX_DELAY_MS
    const val MAX_TOTAL_DELAY_MS = 300_000L
    const val MAX_REPEAT_COUNT = SequenceLimits.MAX_REPEAT_COUNT
    const val MAX_TOTAL_STEPS = SequenceLimits.MAX_TOTAL_STEPS
    const val MAX_WAIT_MS = SequenceLimits.MAX_WAIT_MS
    const val MIN_WAIT_INTERVAL_MS = SequenceLimits.MIN_WAIT_INTERVAL_MS
    const val MAX_WAIT_INTERVAL_MS = SequenceLimits.MAX_WAIT_INTERVAL_MS

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
    data class Done(val result: ActionResult) : StepOutcome
}

/** 一次序列执行的完整结果：记录 + 结束时的变量（runRule 的 RETURN 从 variables["return"] 取）。 */
data class SequenceResult(
    val records: List<StepRecord>,
    val variables: Map<String, String>,
)

/**
 * 步骤序列执行器：顺序跑 steps，默认某步失败即中断（动作异常 / entry 缺失 / Failed|RequiresCapability / 动作超时）。
 * ACTION 步可按 [StepFailure] 配「跳过」或「重试」；delay/if/setVar 的配置错误仍按中断处理。
 * REPEAT/WAIT_UNTIL/RETURN 都是 P2 的有界扩展，循环次数和等待时长在模型与执行层双重夹取。
 */
class SequenceExecutor(
    private val runSingleAction: suspend (action: StoredAction, ctx: ConditionContext, variables: Map<String, String>) -> StepOutcome,
    /** 解析 SET_VAR/RETURN 的值模板：系统/触发变量 + 本次运行已写入的局部变量。 */
    private val resolveTemplate: suspend (text: String, ctx: ConditionContext, variables: Map<String, String>) -> String,
    /** WAIT_UNTIL 轮询时刷新环境；默认保持触发时快照（嵌套 runRule 没有独立环境源）。 */
    private val refreshContext: suspend (ConditionContext) -> ConditionContext = { it },
    /** SET_VAR varGlobal=true 时写入全局变量存储。 */
    private val writeGlobalVariable: suspend (name: String, value: String) -> Unit = { _, _ -> },
) {
    suspend fun runSteps(
        steps: List<RuleStep>,
        ctx: ConditionContext,
        variables: MutableMap<String, String> = mutableMapOf(),
    ): List<StepRecord> = runStepsWithResult(steps, ctx, variables).records

    suspend fun runStepsWithResult(
        steps: List<RuleStep>,
        ctx: ConditionContext,
        variables: MutableMap<String, String> = mutableMapOf(),
    ): SequenceResult {
        val runner = Runner(ctx, variables)
        runner.runList(steps.take(SequenceGuards.MAX_STEPS), prefix = "", depth = 0)
        return SequenceResult(runner.records, runner.variables.toMap())
    }

    private inner class Runner(
        /** WAIT_UNTIL 成功轮询后会替换为最新环境，后续步骤/条件不再使用触发时旧快照。 */
        var ctx: ConditionContext,
        val variables: MutableMap<String, String>,
    ) {
        val records = mutableListOf<StepRecord>()
        val localKeys = mutableSetOf<String>()
        var totalDelayMs = 0L
        var executedSteps = 0
        var interrupted = false
        var returned = false

        suspend fun runList(steps: List<RuleStep>, prefix: String, depth: Int) {
            for ((i, step) in steps.withIndex()) {
                if (interrupted || returned) return
                val index = if (prefix.isEmpty()) "$i" else "$prefix.$i"
                if (!step.enabled) {
                    records += StepRecord(
                        index, step.kind, ok = true, message = "disabled", disabled = true,
                    )
                    continue
                }
                if (executedSteps >= SequenceGuards.MAX_TOTAL_STEPS) {
                    records += StepRecord(index, step.kind, ok = false, message = "steps_total_capped")
                    interrupted = true
                    return
                }
                executedSteps++
                when (step.kind) {
                    RuleStepKind.DELAY -> runDelay(step, index)
                    RuleStepKind.IF -> runIf(step, index, depth)
                    RuleStepKind.SET_VAR -> runSetVar(step, index)
                    RuleStepKind.REPEAT -> runRepeat(step, index, depth)
                    RuleStepKind.WAIT_UNTIL -> runWaitUntil(step, index)
                    RuleStepKind.RETURN -> runReturn(step, index)
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
            if (!hunoia.luno.config.model.VariableNames.isValid(name)) {
                records += StepRecord(index, RuleStepKind.SET_VAR, ok = false, message = "var_name_invalid")
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
            val key = "var.$name"
            variables[key] = value
            localKeys += key
            var suffix = ""
            if (step.varGlobal) {
                suffix = try {
                    writeGlobalVariable(name, value)
                    " global"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 全局落盘失败不影响本次运行：局部值仍生效，历史里留痕。
                    " global_write_failed"
                }
            }
            records += StepRecord(
                index, RuleStepKind.SET_VAR, ok = true,
                // 变量值可能来自剪贴板/通知正文/密码，历史只留变量名和写入范围。
                message = "var.$name=<redacted>$suffix",
            )
        }

        suspend fun runIf(step: RuleStep, index: String, depth: Int) {
            if (depth >= SequenceGuards.MAX_IF_DEPTH) {
                records += StepRecord(index, RuleStepKind.IF, ok = false, message = "if_depth_capped")
                interrupted = true
                return
            }
            // 使用当前上下文求值：WAIT_UNTIL 会把它替换成最新环境；脉冲字段在触发瞬间
            // 之后仍可见，变量则叠加本次运行的局部写入。
            val thenBranch = step.condition.matches(conditionCtx(ctx))
            val branch = (if (thenBranch) step.thenSteps else step.elseSteps)
                .take(SequenceGuards.MAX_STEPS)
            records += StepRecord(
                index, RuleStepKind.IF, ok = true,
                message = "branch=${if (thenBranch) "then" else "else"} n=${branch.size}",
            )
            runList(branch, prefix = index, depth = depth + 1)
        }

        suspend fun runRepeat(step: RuleStep, index: String, depth: Int) {
            if (depth >= SequenceGuards.MAX_IF_DEPTH) {
                records += StepRecord(index, RuleStepKind.REPEAT, ok = false, message = "loop_depth_capped")
                interrupted = true
                return
            }
            val count = step.repeatCount.coerceIn(1, SequenceGuards.MAX_REPEAT_COUNT)
            records += StepRecord(index, RuleStepKind.REPEAT, ok = true, message = "repeat=$count")
            val body = step.thenSteps.take(SequenceGuards.MAX_STEPS)
            for (iteration in 1..count) {
                if (interrupted || returned) break
                runList(body, prefix = "$index.then", depth = depth + 1)
            }
        }

        suspend fun runWaitUntil(step: RuleStep, index: String) {
            val timeoutMs = step.waitTimeoutMs.coerceIn(0L, SequenceGuards.MAX_WAIT_MS)
            val intervalMs = step.waitIntervalMs.coerceIn(
                SequenceGuards.MIN_WAIT_INTERVAL_MS,
                SequenceGuards.MAX_WAIT_INTERVAL_MS,
            )
            val started = SystemClock.elapsedRealtime()
            while (true) {
                val refreshed = try {
                    refreshContext(ctx)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ctx
                }
                // 轮询成功读到的新环境保留给后续步骤（IF/动作模板）；刷新失败才沿用旧快照。
                // 触发瞬间的脉冲/trigger.* 字段仍从旧上下文带过来，避免 WAIT_UNTIL 后
                // 通知正文、手势名等触发变量突然变成空。
                ctx = refreshed.preserveTriggerFields(ctx)
                val live = conditionCtx(refreshed)
                if (step.condition.matches(live)) {
                    records += StepRecord(
                        index, RuleStepKind.WAIT_UNTIL, ok = true,
                        message = "condition_met ${SystemClock.elapsedRealtime() - started}ms",
                    )
                    return
                }
                val elapsed = SystemClock.elapsedRealtime() - started
                if (elapsed >= timeoutMs) {
                    records += StepRecord(
                        index, RuleStepKind.WAIT_UNTIL, ok = false,
                        message = "wait_timeout ${timeoutMs}ms",
                    )
                    interrupted = true
                    return
                }
                val sleepMs = minOf(intervalMs, timeoutMs - elapsed).coerceAtLeast(0L)
                if (totalDelayMs + sleepMs > SequenceGuards.MAX_TOTAL_DELAY_MS) {
                    records += StepRecord(
                        index, RuleStepKind.WAIT_UNTIL, ok = false,
                        message = "wait_budget_capped",
                    )
                    interrupted = true
                    return
                }
                totalDelayMs += sleepMs
                try {
                    delay(sleepMs)
                } catch (e: CancellationException) {
                    records += StepRecord(index, RuleStepKind.WAIT_UNTIL, ok = false, message = "cancelled")
                    throw e
                }
            }
        }

        suspend fun runReturn(step: RuleStep, index: String) {
            val value = try {
                resolveTemplate(step.varTemplate, ctx, variables.toMap())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                records += StepRecord(
                    index, RuleStepKind.RETURN, ok = false,
                    message = (e.message ?: e.javaClass.name).take(120),
                )
                interrupted = true
                return
            }
            variables["return"] = value
            returned = true
            records += StepRecord(
                index, RuleStepKind.RETURN, ok = true,
                message = "return=<redacted>",
            )
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
            var success: ActionResult.Success? = null
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
                        is StepOutcome.Done -> {
                            (outcome.result as? ActionResult.Success)?.let { success = it }
                            failureOf(outcome.result)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    (e.message ?: e.javaClass.name).take(120)
                }

                if (lastFailure == null) {
                    // runRule 的 RETURN 绑定到父规则局部变量：resultVar 由步骤参数指定。
                    bindRunRuleResult(action, success)
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

        private fun bindRunRuleResult(action: StoredAction, success: ActionResult.Success?) {
            if (action.typeId != RUN_RULE_TYPE_ID || success == null) return
            val dest = (action.params["resultVar"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val value = success.outputs["return"] ?: return
            if (dest.isEmpty() || !hunoia.luno.config.model.VariableNames.isValid(dest)) return
            val key = "var.$dest"
            variables[key] = value
            localKeys += key
        }

        /** 刷新后的环境 + 本次运行局部变量；全局变量以刷新值为准，局部同名覆盖。 */
        private fun conditionCtx(base: ConditionContext): ConditionContext {
            val locals = variables.filterKeys { it in localKeys }
            return base.copy(variables = base.variables + locals)
        }

        /**
         * WAIT_UNTIL 刷新会得到最新系统状态，但不能把触发瞬间的脉冲/trigger.* 变量抹掉。
         * 旧值优先；只有旧值为空时才接受轮询期间新出现的脉冲。
         */
        private fun ConditionContext.preserveTriggerFields(previous: ConditionContext): ConditionContext = copy(
            appChangedTo = previous.appChangedTo ?: appChangedTo,
            screenEvent = previous.screenEvent ?: screenEvent,
            chargingChangedTo = previous.chargingChangedTo ?: chargingChangedTo,
            volumeChanged = previous.volumeChanged ?: volumeChanged,
            notificationPosted = previous.notificationPosted ?: notificationPosted,
            notificationRemoved = previous.notificationRemoved ?: notificationRemoved,
            packageChanged = previous.packageChanged ?: packageChanged,
            shareReceived = previous.shareReceived ?: shareReceived,
            clipboardChanged = previous.clipboardChanged ?: clipboardChanged,
            intervalDueKeys = intervalDueKeys + previous.intervalDueKeys,
            bluetoothChanged = previous.bluetoothChanged ?: bluetoothChanged,
            headphonesChanged = previous.headphonesChanged ?: headphonesChanged,
            scheduledTimeHit = scheduledTimeHit || previous.scheduledTimeHit,
            gesturePerformedId = previous.gesturePerformedId ?: gesturePerformedId,
            gesturePerformedName = previous.gesturePerformedName ?: gesturePerformedName,
            triggerRuleName = previous.triggerRuleName ?: triggerRuleName,
            keyboardEvent = previous.keyboardEvent ?: keyboardEvent,
            musicChanged = previous.musicChanged ?: musicChanged,
            torchChangedTo = previous.torchChangedTo ?: torchChangedTo,
            dndChangedTo = previous.dndChangedTo ?: dndChangedTo,
        )

        /** 成功返回 null；失败返回可读原因（写进执行历史）。 */
        private fun failureOf(result: ActionResult): String? = when (result) {
            is ActionResult.Success -> null
            is ActionResult.Failed ->
                (result.message ?: result.reason.name).lowercase().take(120)
            is ActionResult.RequiresCapability -> "requires_capability"
        }

        /** 重试对这些失败无意义：能力缺失要用户授权，entry 缺失要用户修数据。 */
        private fun isRetryable(failure: String): Boolean =
            !failure.startsWith("requires_capability") && failure != "entry_missing"
    }
}
