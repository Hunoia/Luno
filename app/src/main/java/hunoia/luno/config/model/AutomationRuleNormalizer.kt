package hunoia.luno.config.model

/** 自动化配置读取时的轻量归一化：只修可安全修正的字段，不偷偷删用户步骤。 */
object AutomationRuleNormalizer {
    const val CURRENT_SCHEMA_VERSION = 3

    /** 条件树递归上限：导入畸形备份/手工 JSON 时避免 StackOverflow。 */
    private const val MAX_CONDITION_DEPTH = 8

    fun normalize(rules: List<AutomationRule>): List<AutomationRule> = rules.map { normalize(it) }

    fun normalize(rule: AutomationRule): AutomationRule {
        // 编辑中的参数名可能暂时为空；这里只裁剪不删除，保存前由编辑器校验。
        val params = rule.params
            .map { it.copy(name = it.name.trim(), defaultValue = it.defaultValue.take(4096)) }
            .take(20)
        return rule.copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            maxRunMs = rule.maxRunMs.coerceAtLeast(0L),
            params = params,
            condition = normalizeGroup(rule.condition),
            constraints = normalizeGroup(rule.constraints),
            effect = rule.effect.copy(steps = normalizeSteps(rule.effect.steps)),
        )
    }

    /** 条件树归一化：只夹取越界数值/未知枚举，不删除用户条件；超过深度上限的子树清空。 */
    private fun normalizeGroup(group: WhenGroup, depth: Int = 0): WhenGroup {
        if (depth > MAX_CONDITION_DEPTH) return WhenGroup()
        return group.copy(
            items = group.items.map { item ->
                item.copy(
                    notifyMatchMode = item.notifyMatchMode
                        .takeIf { it in NotificationMatchMode.all }
                        ?: NotificationMatchMode.CONTAINS,
                    notifyImportanceAtLeast = item.notifyImportanceAtLeast?.coerceIn(0, 5),
                    packageChangeAction = item.packageChangeAction
                        ?.takeIf { it in PackageChangeAction.all },
                    intervalMinutes = item.intervalMinutes?.coerceIn(1, 1440),
                    group = item.group?.let { normalizeGroup(it, depth + 1) },
                )
            },
        )
    }

    fun normalizeSteps(steps: List<RuleStep>, depth: Int = 0): List<RuleStep> {
        if (depth > 8) return emptyList()
        return steps.take(SequenceLimits.MAX_STEPS).map { step ->
            val kind = step.kind.takeIf {
                it == RuleStepKind.ACTION ||
                    it == RuleStepKind.DELAY ||
                    it == RuleStepKind.IF ||
                    it == RuleStepKind.SET_VAR ||
                    it == RuleStepKind.REPEAT ||
                    it == RuleStepKind.WAIT_UNTIL ||
                    it == RuleStepKind.RETURN
            } ?: RuleStepKind.ACTION
            step.copy(
                // 未知 kind 不丢：降级为禁用动作，历史详情能看到“已禁用”，用户可自行删除。
                kind = kind,
                enabled = step.enabled && step.kind == kind,
                delayMs = step.delayMs.coerceIn(0L, SequenceLimits.MAX_DELAY_MS),
                onFailure = when (step.onFailure) {
                    StepFailure.SKIP, StepFailure.RETRY, StepFailure.STOP -> step.onFailure
                    else -> StepFailure.STOP
                },
                retryCount = step.retryCount.coerceIn(0, StepFailure.MAX_RETRY_COUNT),
                retryIntervalMs = step.retryIntervalMs.coerceIn(0L, StepFailure.MAX_RETRY_INTERVAL_MS),
                repeatCount = step.repeatCount.coerceIn(1, SequenceLimits.MAX_REPEAT_COUNT),
                waitTimeoutMs = step.waitTimeoutMs.coerceIn(0L, SequenceLimits.MAX_WAIT_MS),
                waitIntervalMs = step.waitIntervalMs.coerceIn(
                    SequenceLimits.MIN_WAIT_INTERVAL_MS,
                    SequenceLimits.MAX_WAIT_INTERVAL_MS,
                ),
                condition = normalizeGroup(step.condition),
                thenSteps = normalizeSteps(step.thenSteps, depth + 1),
                elseSteps = normalizeSteps(step.elseSteps, depth + 1),
                varName = step.varName.trim(),
            )
        }
    }
}

/** 模型层与执行层共用的硬上限，避免编辑器和 normalizer 各写一套。 */
object SequenceLimits {
    const val MAX_STEPS = 20
    const val MAX_REPEAT_COUNT = 20
    const val MAX_TOTAL_STEPS = 200
    const val MAX_DELAY_MS = 60_000L
    const val MAX_WAIT_MS = 60_000L
    const val MIN_WAIT_INTERVAL_MS = 100L
    const val MAX_WAIT_INTERVAL_MS = 5_000L
}
