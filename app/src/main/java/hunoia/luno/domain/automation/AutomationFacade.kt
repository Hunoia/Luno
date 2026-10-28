package hunoia.luno.domain.automation

import android.content.Context
import hunoia.luno.action.model.ActionLibrarySettings
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.GlobalVariables
import hunoia.luno.config.model.WhenGroup
import kotlinx.coroutines.flow.Flow

typealias SkipReason = hunoia.luno.runtime.automation.SkipReason

/**
 * 自动化域门面契约（UI 唯一入口）。
 *
 * 实现留在 `:app` 的 `AutomationCenter`；UI 通过 `AppGraph.automation` 取得实现，
 * 不直接引用配置流、运行时引擎或 Binder 客户端。
 */
interface AutomationFacade {

    val rules: Flow<List<AutomationRule>>
    val variables: Flow<GlobalVariables>
    val log: Flow<List<AutomationEvent>>

    suspend fun getRules(): List<AutomationRule>
    suspend fun getVariables(): GlobalVariables
    suspend fun updateVariables(transform: suspend (GlobalVariables) -> GlobalVariables)
    suspend fun setVariable(name: String, value: String)
    suspend fun updateRules(transform: suspend (List<AutomationRule>) -> List<AutomationRule>)
    suspend fun removeRule(ruleId: String)

    val maxEventsPerRuleQuery: Int
    val skipNoSteps: SkipReason
    val skipThrottledCooldown: SkipReason
    val skipThrottledWindow: SkipReason
    val skipOutprioritized: SkipReason
    val skipConstraintUnmet: SkipReason
    val skipRuleBusy: SkipReason
    val skipPermissionMissing: SkipReason

    fun isOkReason(reason: String): Boolean
    fun isTimeoutReason(reason: String): Boolean
    fun isInterruptedReason(reason: String): Boolean
    fun stripInterruptedPrefix(reason: String): String

    fun hasLeafOf(group: WhenGroup): Boolean
    fun hasStateConditionOf(group: WhenGroup): Boolean

    fun capabilityReport(
        context: Context,
        rule: AutomationRule,
        allRules: List<AutomationRule>,
        library: ActionLibrarySettings?,
    ): AutomationCapabilityReport

    fun isNotificationAccessGranted(context: Context): Boolean

    /** 把外部分享进来的文本投进自动化事件源（链接分发入口用）。 */
    fun publishSharedText(context: Context, text: String, action: String)

    fun runningRuleIds(): Set<String>
    fun stopRule(ruleId: String): Boolean

    fun runRuleTest(
        rule: AutomationRule,
        executeSteps: Boolean = true,
        onResult: (RuleTestResult?) -> Unit,
    ): Boolean
}
