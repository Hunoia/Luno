package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 一条「文本 → 建议」规则。
 *
 * 规则只负责从通知/剪贴板文本里抓出候选值；值是不是链接、要不要按敏感文本处理，
 * 由运行时按 [action] 与捕获结果判定。规则表按顺序匹配，同一段文本命中多条时全部生成建议。
 */
@Serializable
@Keep
data class SuggestionRule(
    val id: String = UUID.randomUUID().toString(),
    /** 建议标题；空串时列表回退显示正则，胶囊标题回退「建议」。 */
    val name: String = "",
    /** Kotlin/Java 正则；第 1 个捕获组为建议正文，无捕获组取整段。 */
    val pattern: String = "",
    val action: SuggestionRuleAction = SuggestionRuleAction.COPY,
    val enabled: Boolean = true,
) {
    val isValid: Boolean get() = pattern.isNotBlank()
}

@Serializable
@Keep
enum class SuggestionRuleAction {
    /** 复制捕获文本；非链接按敏感文本处理。 */
    COPY,

    /** 打开捕获到的链接；捕获不到链接时运行时退回复制。 */
    OPEN,

    /** 分享捕获文本。 */
    SHARE,
}

/**
 * 默认规则：与旧内置验证码解析等价的近似正则。
 *
 * 旧解析的「唯一候选 + 排除词」无法用单条正则完全复刻；这里用「关键词 + 24 字符内 +
 * 4–8 位数字」近似，宁可少误判，也不引入额外的规则字段。
 */
object SuggestionRuleDefaults {

    private const val ChinesePattern =
        """(?i)(?:验证码|校验码|动态码|确认码|安全码|授权码|登录码|一次性密码|短信验证码|邮件验证码)[^0-9]{0,24}([0-9]{4,8})(?![0-9])"""

    private const val EnglishPattern =
        """(?i)(?:verification\s+code|verify\s+code|security\s+code|one[\s-]?time\s+(?:password|code)|\botp\b|passcode)[^0-9]{0,24}([0-9]{4,8})(?![0-9])"""

    /** 默认预置：中英文验证码各一条；用户删空后即关闭识别。 */
    val codeRules: List<SuggestionRule> = listOf(
        SuggestionRule(
            id = "default-code-zh",
            name = "验证码",
            pattern = ChinesePattern,
        ),
        SuggestionRule(
            id = "default-code-en",
            name = "验证码（英文）",
            pattern = EnglishPattern,
        ),
    )

    val rules: List<SuggestionRule> get() = codeRules
}

/** 新增/更新规则；与 `ActionSettings` 的字段级合并语义保持一致。 */
fun ClipboardBallSettings.upsertRule(rule: SuggestionRule): ClipboardBallSettings {
    val exists = rules.any { it.id == rule.id }
    return copy(
        rules = if (exists) rules.map { if (it.id == rule.id) rule else it } else rules + rule,
    )
}

fun ClipboardBallSettings.removeRule(ruleId: String): ClipboardBallSettings =
    copy(rules = rules.filterNot { it.id == ruleId })
