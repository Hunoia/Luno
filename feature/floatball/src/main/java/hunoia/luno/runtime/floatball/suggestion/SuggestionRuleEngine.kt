package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.config.model.SuggestionRule

/** 一条规则的命中结果；[value] 已 trim，且保证非空。 */
internal data class SuggestionRuleHit(
    val rule: SuggestionRule,
    val value: String,
)

/**
 * 规则引擎：配置更新时编译一次，事件到达时只做匹配。
 *
 * 每条规则只取第一次匹配的第 1 个捕获组；没有捕获组时取整段。扫描文本最多
 * [SuggestionRuleEngine.MaxScanChars] 个字符，避免超长剪贴板把病态正则拖死。
 * 非法正则不会崩服务：编译失败的那条规则静默跳过，编辑页负责拦截保存。
 */
internal class CompiledSuggestionRules(
    private val rules: List<CompiledRule>,
) {
    val isEmpty: Boolean get() = rules.isEmpty()

    fun match(text: String): List<SuggestionRuleHit> {
        if (text.isEmpty() || rules.isEmpty()) return emptyList()
        val scan = if (text.length > SuggestionRuleEngine.MaxScanChars) {
            text.substring(0, SuggestionRuleEngine.MaxScanChars)
        } else {
            text
        }
        val hits = ArrayList<SuggestionRuleHit>()
        for (compiled in rules) {
            val regex = compiled.regex ?: continue
            val match = runCatching { regex.find(scan) }.getOrNull() ?: continue
            val value = if (match.groups.size > 1) {
                match.groups[1]?.value
            } else {
                match.value
            }?.trim().orEmpty()
            if (value.isNotEmpty()) {
                hits += SuggestionRuleHit(compiled.rule, value)
            }
        }
        return hits
    }

    internal data class CompiledRule(
        val rule: SuggestionRule,
        val regex: Regex?,
    )
}

internal object SuggestionRuleEngine {

    /** 剪贴板长文本只扫前 16KB：足够覆盖正常复制内容，又限制病态正则的输入规模。 */
    const val MaxScanChars = 16_384

    fun compile(rules: List<SuggestionRule>): CompiledSuggestionRules {
        val compiled = rules.mapNotNull { rule ->
            if (!rule.enabled || rule.pattern.isBlank()) return@mapNotNull null
            val regex = runCatching { Regex(rule.pattern) }.getOrNull() ?: return@mapNotNull null
            CompiledSuggestionRules.CompiledRule(rule, regex)
        }
        return CompiledSuggestionRules(compiled)
    }
}
