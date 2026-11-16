package hunoia.luno.runtime.suggestion

import hunoia.luno.config.model.SuggestionRule
import hunoia.luno.config.model.SuggestionRuleAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一条规则命中后的取值结果。
 *
 * 这是建议胶囊与识屏幽灵卡共用的**规则投影**：两边拿到同一个 [SuggestionRules.slots]，
 * 只共享「这条文本里能抓出什么值、规则要做什么」，不共享展示与执行方式
 * （凡是值能否变成可打开的链接这类派生，由各使用方按自己的既有口径判，
 * 避免同一个判断在两处以两种写法漂移）。
 *
 * - [label]：规则名（可能为空串，展示侧回退兜底标题）；
 * - [value]：已 trim 且非空的捕获值（验证码、链接或任意文本）；
 * - [action]：规则声明的动作，原样透传，不在本层降级。
 */
data class SuggestionSlot(
    val ruleId: String,
    val label: String,
    val value: String,
    val action: SuggestionRuleAction,
)

/**
 * 规则 → 建议槽的纯逻辑（无 Context / 无 Android 依赖，可直接 JVM 单测）。
 *
 * 与建议胶囊同一口径：按配置顺序匹配、每条规则取第一个捕获组（无捕获组取整段）、
 * 命中多少条出多少条；非法正则静默跳过、扫描长度上限见 [SuggestionRuleEngine.MaxScanChars]。
 */
object SuggestionRules {

    /** 规则表 → 编译结果；空表 / 全部非法时返回空编译结果（[CompiledSuggestionRules.isEmpty]）。 */
    fun compile(rules: List<SuggestionRule>): CompiledSuggestionRules =
        SuggestionRuleEngine.compile(rules)

    /**
     * 规则表 → 建议槽。空文本 / 没有命中时返回空列表。
     *
     * 正则扫描在 Default 上做：最坏要走满 `MaxScanChars`，识屏幽灵卡在识别完成后
     * 立刻要用结果，绝不能压在主线程。纯函数，结果与线程无关。
     */
    suspend fun slots(text: String?, rules: CompiledSuggestionRules): List<SuggestionSlot> {
        if (text.isNullOrEmpty() || rules.isEmpty) return emptyList()
        return withContext(Dispatchers.Default) { slotsOf(text, rules) }
    }

    /** 同步版本：[slots] 的实现本体，供已在线程池上的调用方与单测直接使用。 */
    fun slotsOf(text: String, rules: CompiledSuggestionRules): List<SuggestionSlot> =
        rules.match(text).map { hit ->
            SuggestionSlot(
                ruleId = hit.rule.id,
                label = hit.rule.name.trim(),
                value = hit.value,
                action = hit.rule.action,
            )
        }
}
