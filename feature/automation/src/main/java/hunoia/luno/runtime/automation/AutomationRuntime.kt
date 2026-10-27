package hunoia.luno.runtime.automation

import hunoia.luno.config.model.GlobalVariables
import hunoia.luno.runtime.GestureRuntimeState
import hunoia.luno.runtime.toConditionContext
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
    /**
     * @param eventKeys 本轮刷新的事件域（空集 = 通用刷新，只评估含状态叶子的规则）。
     * @param globalVariablesOverride 刚写入的全局变量快照：DataStore 回填到 SettingsState
     *   存在一次调度延迟，变量写入触发重评时必须直接用新值，否则会漏掉这次变量条件。
     */
    fun evaluate(
        eventKeys: Set<String> = emptySet(),
        globalVariablesOverride: GlobalVariables? = null,
    ) {
        val settings = runtimeSettingsStore.snapshot()
        val enabled = settings.initialSettings.gestureEnabled
        // 总闸：关闭期间丢弃堆积脉冲，规则一律不评估，重开后不会补触发过期事件。
        // 只把规则引擎放进闸门；触钮显隐依赖 gestureEnabled（policy 会因总闸关掉全部按钮），
        // 必须无条件刷新：提前 return 会让 ButtonRefreshCoordinator 收不到刷新，总闸关了触钮照样可点。
        if (!enabled) discardPulses()
        val runtimeState = buildRuntimeState()
        if (enabled) {
            // 覆盖值只补当前写入项：即使 SettingsState 尚未回填，也不会丢掉其它已有变量。
            val variables = settings.globalVariables.toTemplateMap() +
                globalVariablesOverride?.toTemplateMap().orEmpty()
            automationEngine.evaluate(
                settings.automationRules,
                runtimeState.toConditionContext(variables),
                eventKeys,
            )
        }
        onVisibilityRefresh(runtimeState)
    }
}
