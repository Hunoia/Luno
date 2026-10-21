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
    /** 总闸关闭时丢弃堆积脉冲（见 evaluate），避免重开后补触发过期事件。 */
    private val discardPulses: () -> Unit = {},
) {
    /** @param eventKeys 本轮刷新的事件域（空集 = 通用刷新，只评估含状态叶子的规则）。 */
    fun evaluate(eventKeys: Set<String> = emptySet()) {
        val settings = runtimeSettingsStore.snapshot()
        val enabled = settings.initialSettings.gestureEnabled
        // 总闸：关闭期间丢弃堆积脉冲，规则一律不评估，重开后不会补触发过期事件。
        // 只把规则引擎放进闸门；触钮显隐依赖 gestureEnabled（policy 会因总闸关掉全部按钮），
        // 必须无条件刷新：提前 return 会让 ButtonRefreshCoordinator 收不到刷新，总闸关了触钮照样可点。
        if (!enabled) discardPulses()
        val runtimeState = buildRuntimeState()
        if (enabled) {
            automationEngine.evaluate(settings.automationRules, runtimeState.toConditionContext(), eventKeys)
        }
        onVisibilityRefresh(runtimeState)
    }
}
