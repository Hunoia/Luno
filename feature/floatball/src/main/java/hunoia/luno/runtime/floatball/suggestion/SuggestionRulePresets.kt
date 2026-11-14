package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.config.model.SuggestionRule
import hunoia.luno.config.model.SuggestionRuleDefaults
import java.util.UUID

/**
 * 常用建议规则预设。
 *
 * 与链接分发预设一样：按 pattern 判重，一次把所有可添加项并入规则表。
 * 默认规则已经包含中英文验证码，所以新装用户看到的预设通常只剩取件码/快递/订单；
 * 用户删掉默认规则后仍可通过预设加回来。
 */
internal object SuggestionRulePresets {

    private val presets: List<SuggestionRule> = listOf(
        SuggestionRule(
            id = "preset-code-zh",
            name = "验证码",
            pattern = SuggestionRuleDefaults.codeRules[0].pattern,
        ),
        SuggestionRule(
            id = "preset-code-en",
            name = "验证码（英文）",
            pattern = SuggestionRuleDefaults.codeRules[1].pattern,
        ),
        SuggestionRule(
            id = "preset-pickup",
            name = "取件码",
            pattern = """(?i)(?:取件码|取货码|提货码|取餐码|取件号)[^0-9A-Za-z]{0,12}([0-9A-Za-z][0-9A-Za-z-]{2,15})(?![0-9A-Za-z-])""",
        ),
        SuggestionRule(
            id = "preset-tracking",
            name = "快递单号",
            pattern = """(?i)(?:快递单号|运单号|物流单号|运单编号)[^0-9]{0,12}([0-9]{8,24})(?![0-9])""",
        ),
        SuggestionRule(
            id = "preset-order",
            name = "订单号",
            pattern = """(?i)(?:订单号|订单编号|订单号码)[^0-9]{0,12}([0-9]{6,24})(?![0-9])""",
        ),
    )

    /** 本机可添加的预设：同 pattern 的规则尚不存在。 */
    fun addable(existing: List<SuggestionRule>): List<SuggestionRule> {
        val used = existing
            .map { it.pattern.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
        return presets.mapNotNull { preset ->
            if (!used.add(preset.pattern.trim().lowercase())) return@mapNotNull null
            preset.copy(id = UUID.randomUUID().toString())
        }
    }

    val all: List<SuggestionRule> get() = presets
}
