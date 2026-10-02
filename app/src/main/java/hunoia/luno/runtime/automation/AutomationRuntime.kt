package hunoia.luno.runtime.automation

import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.condition.toConditionContext
import hunoia.luno.runtime.settings.SettingsStore

/**
 * 自动化运行时：条件源变化时直接执行动作，再刷新按钮显隐。
 * RUN_ACTION 与按钮是否存在、是否启用无关。
 */
class AutomationRuntime(
    private val runtimeSettingsStore: SettingsStore,
    private val buildRuntimeState: () -> GestureRuntimeState,
    private val automationEngine: AutomationEngine,
    private val onVisibilityRefresh: (GestureRuntimeState) -> Unit,
) {
    fun evaluate() {
        val settings = runtimeSettingsStore.snapshot()
        val runtimeState = buildRuntimeState()
        automationEngine.evaluate(settings.automationRules, runtimeState.toConditionContext())
        onVisibilityRefresh(runtimeState)
    }
}
