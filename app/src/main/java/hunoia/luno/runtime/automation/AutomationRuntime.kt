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
        // 总闸：手势总开关关闭后规则一律不触发（按钮显隐由 ButtonRuntime 另行处理）。
        // 同时丢掉关闭期间堆积的脉冲，否则重开后第一个刷新会把过期事件当新脉冲命中。
        if (!settings.initialSettings.gestureEnabled) {
            discardPulses()
            return
        }
        val runtimeState = buildRuntimeState()
        automationEngine.evaluate(settings.automationRules, runtimeState.toConditionContext(), eventKeys)
        onVisibilityRefresh(runtimeState)
    }
}
