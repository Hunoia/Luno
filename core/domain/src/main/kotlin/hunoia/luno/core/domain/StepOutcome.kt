package hunoia.luno.core.domain

/**
 * 单个动作步骤的执行落定结果。
 *
 * 执行口（Dispatcher）与序列执行器（自动化）都依赖它，因此放在契约层，
 * 不放在任一 feature 模块内部。
 */
sealed interface StepOutcome {
    data object EntryMissing : StepOutcome
    data class Done(val result: ActionResult) : StepOutcome
}
