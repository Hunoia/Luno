package hunoia.luno.recognition

import android.content.Context
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.RecognitionBuiltin
import hunoia.luno.config.model.RecognitionDefaults
import hunoia.luno.config.model.RecognitionOutput
import hunoia.luno.config.model.RecognitionSettings
import hunoia.luno.runtime.recognition.LinkUrls
import hunoia.luno.runtime.recognition.RecognizedItem

/**
 * 页面侧（UI）打开链接的唯一入口。
 *
 * 页面不能直接引用 `:core:runtime` 的识别器（UI 边界门禁），也不该自己判规则，
 * 所以「打开一条链接」收敛成这一个 domain 调用：内部走识别器执行口，
 * 与胶囊、识屏完全同一条路径。
 */
object RecognitionLinks {

    /** 单条链接按识别器规则分发；未命中 / 未送达按全局策略回退。 */
    suspend fun open(context: Context, url: String) {
        val settings = currentSettings()
        AppRecognitionHost(context).executor().execute(
            item = linkItem(url),
            settings = settings,
            label = null,
        )
    }

    /** 该链接会落到哪个应用（选择页副标题用）；没有命中链接规则时返回 null。 */
    suspend fun targetLabel(context: Context, url: String, settings: RecognitionSettings): String? {
        val rule = LinkUrls.firstMatch(url, settings.rules) ?: return null
        return AppRecognitionHost(context).let {
            hunoia.luno.link.Links.resolvedTargetFor(context, url, rule.id, settings)?.label
        }
    }

    /** 从一段文本里提取可分发的链接（外部 Intent 入口用）。 */
    fun extractUrls(text: String): List<String> = LinkUrls.extractUrls(text)

    /** 主机名（选择页标题用）；解析不到时退回整串。 */
    fun hostOf(url: String): String = LinkUrls.hostOf(url) ?: url

    /** 该链接命中「链接类规则」的 id；没有命中返回 null。 */
    fun matchedRuleId(url: String, settings: RecognitionSettings): String? =
        LinkUrls.firstMatch(url, settings.rules)?.id

    private suspend fun currentSettings(): RecognitionSettings =
        runCatching { ConfigProvider.getActionSettings().recognition }
            .getOrDefault(RecognitionSettings())

    private fun linkItem(url: String) = RecognizedItem(
        ruleId = RecognitionDefaults.LinkRuleId,
        ruleName = "",
        value = url,
        urls = listOf(url),
        output = RecognitionOutput.Builtin(RecognitionBuiltin.OpenLink),
    )
}
