package hunoia.luno.runtime.recognition

import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionDisplay
import hunoia.luno.config.model.RecognitionMatch
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionRule
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.config.model.RecognitionSource
import hunoia.luno.core.domain.StoredAction

/** 识别输入：来源 + 一段文本。生产者在各自的既有时机产出一条，不引入新的监听。 */
data class RecognitionInput(
    val source: RecognitionSource,
    val text: String,
    /** 来源去重键（通知 key / 剪贴板指纹 / 界面指纹）；消费者用它做同源抑制。 */
    val sourceKey: String = "",
)

/**
 * 一条识别结果。
 *
 * - [ruleId]：命中规则（恒非空——内置链接识别也是一条规则，不再有「无规则」的特殊项）；
 * - [value]：要落地的值（链接规则是被模板改写后的 URL，文本规则是捕获组）；
 * - [urls]：与 [value] 相关的链接；多链接时消费者应进选择页而不是直接打开；
 * - [output]：规则声明的输出，原样透传，本层不做降级；
 * - [display]：规则声明的呈现档，消费者用它分流（直执行 / 进胶囊），不必回查规则表。
 */
data class RecognizedItem(
    val ruleId: String,
    val ruleName: String,
    val value: String,
    val urls: List<String>,
    val output: RecognitionOutput,
    val display: RecognitionDisplay = RecognitionDisplay.Capsule,
) {
    /** 这条结果会不会自己落地（true = 不进胶囊，命中即执行）。 */
    val isDirect: Boolean get() = display == RecognitionDisplay.Direct

    /** 内置输出种类；动作输出时为 null。 */
    val builtin: RecognitionBuiltin?
        get() = (output as? RecognitionOutput.Builtin)?.kind

    /** 动作输出要执行的动作；内置输出时为 null。 */
    val action: StoredAction?
        get() = (output as? RecognitionOutput.Action)?.action

    /** 这条结果在界面上能做的动作集（消费者只展示它，不再自己判输出类型）。 */
    val actions: List<RecognitionAction> get() = actionsOf(output, urls)
}

/**
 * 输出 → 动作集。纯函数，消费者与测试共用同一份，避免「胶囊有复制、识屏没有」这类漂移。
 *
 * 口径：
 * - 内置复制类（[RecognitionBuiltin.Copy] / [RecognitionBuiltin.CopyAndClear]）→ 复制；
 * - [RecognitionBuiltin.OpenLink] → 打开（值不是链接时执行口自己降级为复制，动作集照旧给复制）；
 * - [RecognitionBuiltin.Share] → 分享 + 复制（分享之外总得能复制）；
 * - 动作输出 → 跑动作 + 复制 + 分享；链接可分享/可打开时再补上；
 * - 通知输出 → 发通知 + 复制（用户常想先把值拿走再关通知）。
 */
fun actionsOf(output: RecognitionOutput, urls: List<String>): List<RecognitionAction> {
    val out = LinkedHashSet<RecognitionAction>(4)
    when (output) {
        is RecognitionOutput.Builtin -> when (output.kind) {
            RecognitionBuiltin.Copy, RecognitionBuiltin.CopyAndClear ->
                out += RecognitionAction.Copy
            RecognitionBuiltin.OpenLink -> {
                if (urls.isNotEmpty()) out += RecognitionAction.Open
                out += RecognitionAction.Copy
            }
            RecognitionBuiltin.Share -> {
                out += RecognitionAction.Share
                out += RecognitionAction.Copy
            }
        }
        is RecognitionOutput.Action -> {
            out += RecognitionAction.Run
            out += RecognitionAction.Copy
            out += RecognitionAction.Share
            if (urls.isNotEmpty()) out += RecognitionAction.Open
        }
        is RecognitionOutput.Notify -> {
            out += RecognitionAction.Notify
            out += RecognitionAction.Copy
        }
    }
    return out.toList()
}

/**
 * 一条识别结果在界面上**能做什么**。
 *
 * 派生自 [RecognizedItem.output]（+ `urls` 是否为空），不是各消费者自己拼的：
 * 建议胶囊的菜单、识屏工具条的建议槽、选择页的动作都读这一份，输出模型一改就全部跟上。
 */
enum class RecognitionAction {
    /** 复制捕获值（含「复制后清除」：执行口会按设置挂延迟清理）。 */
    Copy,

    /** 打开链接（多链接时执行口进选择页）。 */
    Open,

    /** 分享捕获值。 */
    Share,

    /** 跑规则声明的动作。 */
    Run,

    /** 发通知（LIVE 胶囊 / 标准通知）。 */
    Notify,
}

/**
 * 编译后的识别器：配置更新时编译一次，事件到达时只做匹配。纯函数、无 Android 依赖。
 *
 * 匹配顺序与规则表一致；同一段文本命中几条规则就出几条结果。
 * 文本规则捕获到的链接会从链接规则的结果里剔除——同一链接不出两条建议。
 */
class CompiledRecognizer internal constructor(
    private val rules: List<CompiledRule>,
) {
    val isEmpty: Boolean get() = rules.isEmpty()

    /** 本规则表里是否存在对 [source] 生效的链接规则（决定要不要做链接提取）。 */
    fun hasLinkRulesFor(source: RecognitionSource): Boolean =
        rules.any { it.rule.match is RecognitionMatch.Link && source in it.rule.sources }

    fun match(input: RecognitionInput): List<RecognizedItem> {
        if (input.text.isEmpty() || rules.isEmpty()) return emptyList()
        val scan = if (input.text.length > MaxScanChars) {
            input.text.substring(0, MaxScanChars)
        } else {
            input.text
        }
        val out = ArrayList<RecognizedItem>(rules.size)
        // 文本规则先跑：它们捕获到的链接要从未命中的链接候选里剔除。
        val consumedUrls = LinkedHashSet<String>()
        for (compiled in rules) {
            val rule = compiled.rule
            if (input.source !in rule.sources) continue
            when (val match = rule.match) {
                is RecognitionMatch.Text -> {
                    val regex = compiled.regex ?: continue
                    val hit = runCatching { regex.find(scan) }.getOrNull() ?: continue
                    val value = if (hit.groups.size > 1) {
                        hit.groups[1]?.value
                    } else {
                        hit.value
                    }?.trim().orEmpty()
                    if (value.isEmpty()) continue
                    consumedUrls += LinkUrls.extractUrls(value)
                    out += itemOf(rule, value, LinkUrls.extractUrls(value))
                }
                is RecognitionMatch.Link -> {
                    val allowBare = match.allowBareDomains || LinkUrls.allowsBareDomains(input.source)
                    val urls = LinkUrls.extractUrls(scan, allowBareDomains = allowBare)
                        .filterNot { it in consumedUrls }
                        .filter { LinkUrls.matches(it, match) }
                        .distinct()
                    if (urls.isEmpty()) continue
                    // 一条链接规则只产出一条结果：多链接交给消费者进选择页，
                    // 否则「3 条链接」会变成 3 个几乎一样的胶囊。
                    // value 是「要打开/展示的地址」（可能被模板改写），urls 始终是原始链接：
                    // 分发与选择页需要真实地址，展示才用改写后的。
                    val value = LinkUrls.applyTemplate(urls.first(), match.template)
                    out += itemOf(rule, value, urls)
                }
            }
        }
        return out
    }

    private fun itemOf(
        rule: RecognitionRule,
        value: String,
        urls: List<String>,
    ): RecognizedItem = RecognizedItem(
        ruleId = rule.id,
        ruleName = rule.name.trim(),
        value = value,
        urls = urls,
        output = rule.output,
        display = rule.effectiveDisplay,
    )

    internal data class CompiledRule(
        val rule: RecognitionRule,
        val regex: Regex?,
    )

    companion object {
        /** 剪贴板长文本只扫前 16KB：足够覆盖正常复制内容，又限制病态正则的输入规模。 */
        const val MaxScanChars = 16_384
    }
}

/**
 * 识别器：**输入 → 规则 → 输出**的唯一入口。
 *
 * 谁都不许自己判规则：建议胶囊、识屏工具条、外部 Intent 入口、链接选择页都只调
 * [compile] + [CompiledRecognizer.match] 拿结果，再按自己的展示/执行口径消费。
 */
object Recognizer {

    /**
     * 最近一次编译结果：识屏工具条每次识别成形都会取一次识别器，而规则表基本不变。
     * 按规则表内容缓存（数据类相等），配置一改自然失效——不需要手动 invalidate。
     */
    @Volatile
    private var cachedFor: List<RecognitionRule>? = null

    @Volatile
    private var cached: CompiledRecognizer? = null

    /**
     * 规则表 → 编译结果；空表 / 全部非法时返回空编译结果。
     *
     * 没有「总开关」：规则全删即不识别。少一个状态，语义唯一。
     */
    fun compile(settings: RecognitionSettings): CompiledRecognizer {
        val rules = settings.rules
        cached.takeIf { cachedFor == rules }?.let { return it }
        return build(rules).also {
            cachedFor = rules
            cached = it
        }
    }

    private fun build(rules: List<RecognitionRule>): CompiledRecognizer {
        val compiled = rules.mapNotNull { rule ->
            if (!rule.enabled || !rule.isValid) return@mapNotNull null
            val regex = when (val match = rule.match) {
                is RecognitionMatch.Text -> runCatching { Regex(match.pattern) }.getOrNull()
                    ?: return@mapNotNull null
                is RecognitionMatch.Link -> null
            }
            CompiledRecognizer.CompiledRule(rule, regex)
        }
        return CompiledRecognizer(compiled)
    }

    /** 便捷入口：一次编译 + 一次匹配。热路径请自己持有编译结果。 */
    fun match(settings: RecognitionSettings, input: RecognitionInput): List<RecognizedItem> =
        compile(settings).match(input)
}
