package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 识别器：**输入 → 规则 → 输出**的唯一事实源。
 *
 * 一条规则回答三件事，缺一不可：
 * - [sources]：这条规则对哪些输入生效（剪贴板 / 通知 / 识屏 / 情景识别 / 外部 Intent）。
 *   作用范围是**规则数据**，不是运行时代码分支——否则「为什么通知里的链接不出建议」
 *   永远只能在代码里找答案。
 * - [match]：怎么从文本里认出东西（链接语义 / 正则捕获）。
 * - [output]：认出来之后做什么（内置：复制 / 打开链接 / 分享；或任意动作）。
 *
 * 规则表按顺序匹配，第一条命中的规则决定这一条候选的输出；文本里能认出多少条候选
 * 就出多少条（同一条规则对同一条输入只取第一个捕获）。
 *
 * 消费者（建议胶囊、识屏工具条、外部 Intent 入口）只消费识别结果，不自己判规则、
 * 不自己判链接——判定只有 `:core:runtime` 的 `Recognizer` 一份。
 */
@Serializable
@Keep
data class RecognitionRule(
    val id: String = UUID.randomUUID().toString(),
    /** 展示名；空串时列表回退显示匹配串，胶囊标题回退「建议」。 */
    val name: String = "",
    val enabled: Boolean = true,
    /** 对哪些输入生效；空集 = 对任何输入都不生效（编辑页拦截保存）。 */
    val sources: Set<RecognitionSource> = RecognitionSource.Default,
    val match: RecognitionMatch = RecognitionMatch.Text(),
    val output: RecognitionOutput = RecognitionOutput.Builtin(RecognitionBuiltin.Copy),
) {
    /** 能被识别器使用的最小条件：匹配串非空，且输出可执行。 */
    val isValid: Boolean
        get() = match.isValid && output.isValid
}

/** 识别输入来源。新增来源 = 加一个枚举值 + 在输入侧产出一条 `RecognitionInput`。 */
@Serializable
@Keep
enum class RecognitionSource {
    /** 剪贴板内容变化。 */
    Clipboard,

    /** 通知（标题 + 正文）。 */
    Notification,

    /** 屏幕识别（识屏取词工具条的候选格）。 */
    ScreenPick,

    /** 情景识别（前台界面文本）。 */
    Scene,

    /** 外部 Intent（Luno 被设为默认浏览器 / 分享到 Luno）。 */
    ExternalIntent,
    ;

    companion object {
        /**
         * 新规则默认对哪些来源生效：**全部**。
         *
         * 默认全域是有意的：规则本身就是「我关心这类文本」，用户写一条验证码规则不会
         * 只想在剪贴板生效。要收窄自己在编辑页勾。
         */
        val Default: Set<RecognitionSource> = entries.toSet()

        /**
         * 文本类规则的默认来源：不含 [ExternalIntent]。
         *
         * 外部 Intent 入口只可能送来链接（默认浏览器 / 分享到 Luno），文本规则在那里
         * 永远不会命中，勾着只会让编辑页看起来像配错了。
         */
        val TextDefault: Set<RecognitionSource> = entries.toSet() - ExternalIntent

        /**
         * 链接类规则的默认来源：不含 [Notification]。
         *
         * 通知里的链接点一下就走系统自己那一套，再弹一条「发往 XX」的建议是纯噪声；
         * 需要的人自己在编辑页勾上。
         */
        val LinkDefault: Set<RecognitionSource> = entries.toSet() - Notification
    }
}

/**
 * 匹配方式。两种语义互不重叠，各自保留自己的判定口径：
 *
 * - [Text]：正则，第 1 个捕获组（无捕获组取整段）。命中值是一段**文本**。
 * - [Link]：先提取链接再按规则比对。命中值是**链接**，可以带分发细节
 *   （目标应用 / Activity / 模板改写 / 短链解析 / 排除串）。`pattern` 为空 = 任意可分发的链接。
 */
@Serializable
@Keep
sealed interface RecognitionMatch {

    /** 正则捕获。规则串非法时 [isValid] 为 false（编辑页拦截，运行时静默跳过）。 */
    @Serializable
    @Keep
    data class Text(
        /** Kotlin/Java 正则；第 1 个捕获组为正文，无捕获组取整段。 */
        override val pattern: String = "",
    ) : RecognitionMatch {
        override val isValid: Boolean get() = pattern.isNotBlank()
    }

    /**
     * 链接匹配：从输入文本里提取链接（[allowBareDomains] 时额外认裸域名），
     * 再按 [type] 逐条比对，命中项按 [template] 改写后作为输出值。
     *
     * `pattern` 为空 = 任意可分发的链接（内置「链接」规则就用这个形态）。
     * [excludes] 是 URL 里出现即跳过本规则的排除串（比写复杂正则直观）；
     * [resolveShortLink] 命中后先跟随短链再重新匹配一次规则表。
     */
    @Serializable
    @Keep
    data class Link(
        /** 链接比对方式（不叫 `type`：那是多态序列化的 discriminator 键）。 */
        val matchType: LinkMatchType = LinkMatchType.CONTAINS,
        /** 目标应用包名；空 + [viaBrowser] = 自动挑浏览器。 */
        val targetPackage: String = "",
        /** 目标 Activity（可选）：留空由系统按 ACTION_VIEW + 包名解析。 */
        val targetActivity: String = "",
        /** 小窗打开。 */
        val miniWindow: Boolean = false,
        /** 交给浏览器打开（不联网解析短链的 B 方案）。 */
        val viaBrowser: Boolean = false,
        override val pattern: String = "",
        val excludes: List<String> = emptyList(),
        /** 打开前改写 URL；空 = 原样。支持 `{url}` `{host}` `{path}` `{query}` `{scheme}`。 */
        val template: String = "",
        val resolveShortLink: Boolean = false,
        /**
         * 额外认「不带协议」的裸域名。剪贴板是用户主动复制的，仍要求完整协议；
         * 识屏 / 情景识别看到的常是地址栏文本，需要放开。
         */
        val allowBareDomains: Boolean = false,
        /**
         * 本规则**打不开**时的回退（目标应用没起来 / 短链解析失败 / 指向自己）；
         * null = 跟随全局 [RecognitionSettings.fallback]。字段名沿用 `missAction` 以免丢配置。
         */
        val missAction: LinkMissMode? = null,
    ) : RecognitionMatch {
        // 正则型必须能编译；其余类型空匹配串是合法语义（= 任意链接）。
        override val isValid: Boolean
            get() = matchType != LinkMatchType.REGEX || runCatching { Regex(pattern) }.isSuccess

        /** 有效排除串（去空白、去空项）。 */
        val effectiveExcludes: List<String>
            get() = excludes.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 用于列表展示的匹配串。 */
    val pattern: String

    val isValid: Boolean
}

/** 识别输出。 */
@Serializable
@Keep
sealed interface RecognitionOutput {

    /** 内置输出：走各入口既有的手感（敏感复制、多链接选择页、未命中回退），不经过动作库。 */
    @Serializable
    @Keep
    data class Builtin(val kind: RecognitionBuiltin) : RecognitionOutput {
        override val isValid: Boolean get() = true
    }

    /**
     * 动作输出：命中后把捕获值填进动作参数再交给统一执行口。
     *
     * 参数里的 `%s`（已 URL 编码）/ `%q`（原样）/ `{value}` `{url}` `{host}` 会被替换；
     * 因此「打开应用并把验证码塞进深链」「复制到剪贴板」这类输出不必新写执行器——
     * 复用动作库已有的定义与表达式替换口径。
     */
    @Serializable
    @Keep
    data class Action(val action: StoredAction) : RecognitionOutput {
        override val isValid: Boolean get() = action.typeId.isNotBlank()
    }

    val isValid: Boolean
}

/**
 * 内置输出种类。
 *
 * 只保留这三条是因为它们各自带着**不能交给动作库的手感**（敏感文本复制不进历史、
 * 多链接进选择页、链接未命中按 `missAction` 回退）。其余输出一律用
 * `RecognitionOutput.Action` 表达，不在这里长枚举。
 */
@Serializable
@Keep
enum class RecognitionBuiltin {
    /** 复制捕获值；不是链接时按敏感文本处理（不进剪贴板历史）。 */
    Copy,

    /** 打开捕获到的链接；捕获不到链接时降级为复制。 */
    OpenLink,

    /** 分享捕获值。 */
    Share,
}

/** 规则匹配方式（链接语义）。 */
@Serializable
@Keep
enum class LinkMatchType {
    /** 域名（含子域）或完整 URL 包含即可。 */
    CONTAINS,
    /** 只比对域名：相等或子域，`bilibili.com` 不会命中 `bilibili.com.evil.com`。 */
    DOMAIN,
    /** 正则 `find`（部分匹配）。 */
    REGEX,
    /** 整串相等（忽略大小写）。 */
    EXACT,
}

/** 链接没有命中规则时的行为。 */
@Serializable
@Keep
enum class LinkMissMode {
    /** 系统分享面板（默认）。 */
    SHARE,
    /** 用其他浏览器打开（解析到 Luno 自身时改用选择器，避免自我循环）。 */
    BROWSER,
    /** 列出能打开 http(s) 的应用让用户挑。 */
    PICKER,
}

/**
 * 识别器设置：规则表 + 回退策略 + 情景识别（第五个输入源）的开关与范围。
 *
 * 挂在这里（`ActionSettings.recognition`）才能顺着配置通路到达 `:service` 与识屏。
 *
 * **没有总开关**：规则表全空 = 不识别任何文本。少一个状态，语义唯一——
 * 否则「关掉总开关但内置链接还在识别」和「关掉就是全关」两种理解会长期打架。
 */
@Serializable
@Keep
data class RecognitionSettings(
    /** 规则表：按顺序取第一条命中（列表顺序即优先级）；空表 = 什么都不识别。 */
    val rules: List<RecognitionRule> = RecognitionDefaults.rules,
    /**
     * 链接打不开时怎么办——**一个开关覆盖两种情况**：没有任何规则命中，或命中了但目标
     * 应用没起来。两者对用户来说是同一件事（「点了没反应」），分成两个开关只会让人
     * 分不清该调哪个。规则可用自己的 `missAction` 单独覆盖。
     */
    val fallback: LinkMissMode = LinkMissMode.SHARE,
    /**
     * 情景识别：前台界面稳定后读一次其无障碍文本，命中规则时弹建议。
     * 默认关；开启即对规则表里所有 `RecognitionSource.Scene` 的规则生效。
     */
    val sceneEnabled: Boolean = false,
    /** 情景识别应用名单模式（黑 / 白名单）。只拦**自动扫描**。 */
    val sceneListMode: SceneListMode = SceneListMode.Blocklist,
    /**
     * 情景识别应用名单（裸包名）：黑名单 = 排除这些；白名单 = 只在这些里识别
     * （空 = 全部允许，两种模式同一口径）。不处理分身后缀。
     */
    val scenePackages: List<String> = emptyList(),
)

/** 新增 / 更新 / 删除都走这里，保持 `ActionSettings` 的字段级合并语义。 */
fun RecognitionSettings.upsertRule(rule: RecognitionRule): RecognitionSettings {
    val exists = rules.any { it.id == rule.id }
    return copy(
        rules = if (exists) rules.map { if (it.id == rule.id) rule else it } else rules + rule,
    )
}

fun RecognitionSettings.removeRule(ruleId: String): RecognitionSettings =
    copy(rules = rules.filterNot { it.id == ruleId })

/**
 * 情景识别的应用名单模式：
 * - [Blocklist]：排除 [RecognitionSettings.scenePackages] 中的应用；
 * - [Allowlist]：只在这些应用里识别（空 = 全部允许）。
 */
@Serializable
@Keep
enum class SceneListMode {
    Blocklist,
    Allowlist,
}

/**
 * 默认规则表：开箱即用的最常用几条（验证码中英文 + 内置链接识别）。
 *
 * 具体应用的分发规则（B 站 / 抖音 / 小红书…）不预置：目标包名因设备而异，
 * 由用户在识别器页从预设一键添加。
 */
object RecognitionDefaults {

    private const val CodePatternZh =
        """(?i)(?:验证码|校验码|动态码|确认码|安全码|授权码|登录码|一次性密码|短信验证码|邮件验证码)[^0-9]{0,24}([0-9]{4,8})(?![0-9])"""

    private const val CodePatternEn =
        """(?i)(?:verification\s+code|verify\s+code|security\s+code|one[\s-]?time\s+(?:password|code)|\botp\b|passcode)[^0-9]{0,24}([0-9]{4,8})(?![0-9])"""

    const val CodeRuleIdZh = "default-code-zh"
    const val CodeRuleIdEn = "default-code-en"
    const val LinkRuleId = "default-link"

    /** 规则捕获到验证码时对哪些来源生效：除外部 Intent（那是链接入口）以外全部。 */
    private val CodeSources: Set<RecognitionSource> = RecognitionSource.TextDefault

    // ---- 常用文本规则：默认表与「添加常用规则」共用同一份定义 ----
    //
    // 以前默认表和预设各写一遍正则，两边必须手抄同步；这里只写一次，
    // 默认表 = 前 [DefaultTextRuleCount] 条，预设 = 整张表（按 pattern 判重后补进规则表）。

    private val CodeZh = textRule(CodeRuleIdZh, "验证码", CodePatternZh)
    private val CodeEn = textRule(CodeRuleIdEn, "验证码（英文）", CodePatternEn)

    private val Pickup = textRule(
        "preset-pickup",
        "取件码",
        """(?i)(?:取件码|取货码|提货码|取餐码|取件号)[^0-9A-Za-z]{0,12}([0-9A-Za-z][0-9A-Za-z-]{2,15})(?![0-9A-Za-z-])""",
    )

    /**
     * 快递单号：真实单号常带字母（SF / YT / JD + 12 位数字），只认纯数字会把大半漏掉。
     * 用「字母开头则为 2 字母 + 8–20 位」，纯数字仍要求 8–24 位，避免把普通数字串抓走。
     */
    private val Tracking = textRule(
        "preset-tracking",
        "快递单号",
        """(?i)(?:快递单号|运单号|物流单号|运单编号)[^0-9A-Za-z]{0,12}((?:[A-Z]{2}[0-9]{8,20})|(?:[0-9]{8,24}))(?![0-9A-Za-z])""",
    )

    private val Order = textRule(
        "preset-order",
        "订单号",
        """(?i)(?:订单号|订单编号|订单号码)[^0-9]{0,12}([0-9]{6,24})(?![0-9])""",
    )

    /**
     * 密码 / 一次性口令（TOTP 之外的「登录口令」）：只认明确带「密码」上下文的短串，
     * 宁可漏也不误抓普通数字。
     */
    private val Password = textRule(
        "preset-password",
        "临时密码",
        """(?i)(?:临时密码|初始密码|登录密码|一次性密码|动态密码)\s*(?:是|为|[:：])?\s*([0-9A-Za-z]{6,20})(?![0-9A-Za-z])""",
    )

    /**
     * 银行卡 / 支付账号后四位（客服核对场景）：只取尾号，绝不匹配整串卡号。
     */
    private val CardTail = textRule(
        "preset-card-tail",
        "卡号后四位",
        """(?i)(?:尾号|后四位|末四位|卡号后4位)\s*(?:是|为|[:：])?\s*([0-9]{4})(?![0-9])""",
    )

    private val AllTextRules: List<RecognitionRule> =
        listOf(CodeZh, CodeEn, Pickup, Tracking, Order, Password, CardTail)

    /** 默认表里预置的文本规则条数：只要两条验证码，其余按需从预设添加。 */
    private const val DefaultTextRuleCount = 2

    private fun textRule(id: String, name: String, pattern: String) = RecognitionRule(
        id = id,
        name = name,
        sources = CodeSources,
        match = RecognitionMatch.Text(pattern),
        output = RecognitionOutput.Builtin(RecognitionBuiltin.Copy),
    )

    /** 内置链接识别：不再是散在各处的隐式分支，而是一条可见、可关、可调顺序的规则。 */
    private val LinkRule = RecognitionRule(
        id = LinkRuleId,
        name = "链接",
        sources = RecognitionSource.LinkDefault,
        match = RecognitionMatch.Link(matchType = LinkMatchType.CONTAINS, pattern = ""),
        output = RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink),
    )

    val rules: List<RecognitionRule> = AllTextRules.take(DefaultTextRuleCount) + LinkRule

    val codeRules: List<RecognitionRule> get() = AllTextRules

    /**
     * 「添加常用规则」里的文本类预设（链接类见 `LinkPresets`），整张常用表。
     *
     * 与默认表同源，按 pattern 判重：用户删掉默认规则后也能从这里加回来，
     * 不会出现「默认表里是 A 正则、预设里是改过的 B 正则」这种漂移。
     */
    val textPresets: List<RecognitionRule> get() = AllTextRules
}
