package hunoia.luno.runtime.floatball.suggestion

import hunoia.luno.config.model.SuggestionRuleAction
import hunoia.luno.core.domain.NotificationPulse
import hunoia.luno.link.LinkUrls
import hunoia.luno.runtime.floatball.inbox.SuggestionItem
import hunoia.luno.runtime.floatball.inbox.SuggestionPrimaryAction

/**
 * 建议项构造：通知/剪贴板文本按规则抓取，剪贴板再补内置链接识别。
 * 纯逻辑（链接目标应用解析留给服务层），方便 JVM 单测。
 */
internal object SuggestionFactory {

    const val LinkLabel = "链接"
    private const val GenericLabel = "建议"

    /**
     * 通知规则扫描：标题 + 正文（bigText 存在时优先，避免正文与展开文本重复）。
     * 规则命中多少条就生成多少条，顺序与规则表一致。
     */
    fun fromNotification(
        pulse: NotificationPulse,
        rules: CompiledSuggestionRules,
        sourceKey: String,
        nowMs: Long,
    ): List<SuggestionItem> {
        val text = notificationText(pulse)
        return rules.match(text).map { hit -> ruleItem(hit, sourceKey, nowMs) }
    }

    /**
     * 剪贴板规则扫描 + 内置链接识别。
     *
     * 规则捕获到的 URL 会从链接建议里剔除：同一链接不再既生成规则建议又生成链接建议。
     * 规则全部命中失败时，行为与旧版一致，只生成链接建议。
     */
    fun fromClipboard(
        text: String,
        rules: CompiledSuggestionRules,
        sourceKey: String,
        nowMs: Long,
    ): List<SuggestionItem> {
        val hits = rules.match(text)
        val hitUrls = hits
            .flatMap { LinkUrls.extractUrls(it.value) }
            .toSet()
        val linkUrls = LinkUrls.extractUrls(text).filterNot { it in hitUrls }
        val items = hits.mapTo(ArrayList(hits.size + 1)) { hit ->
            ruleItem(hit, sourceKey, nowMs)
        }
        if (linkUrls.isNotEmpty()) {
            items += SuggestionItem(
                sourceKey = sourceKey,
                title = LinkLabel,
                text = linkUrls.first(),
                receivedAtElapsedMs = nowMs,
                ttlMs = null,
                action = SuggestionPrimaryAction.OPEN,
                sensitive = false,
                urls = linkUrls,
            )
        }
        return items
    }

    private fun ruleItem(
        hit: SuggestionRuleHit,
        sourceKey: String,
        nowMs: Long,
    ): SuggestionItem {
        val raw = hit.value
        val urls = LinkUrls.extractUrls(raw)
        val exactUrl = urls.firstOrNull { it == raw }
        val openUrl = if (hit.rule.action == SuggestionRuleAction.OPEN) urls.firstOrNull() else null
        val action = when {
            hit.rule.action == SuggestionRuleAction.OPEN && openUrl == null ->
                SuggestionPrimaryAction.COPY
            hit.rule.action == SuggestionRuleAction.OPEN -> SuggestionPrimaryAction.OPEN
            hit.rule.action == SuggestionRuleAction.SHARE -> SuggestionPrimaryAction.SHARE
            else -> SuggestionPrimaryAction.COPY
        }
        val itemText = if (openUrl != null) openUrl else raw
        val itemUrls = when {
            openUrl != null -> listOf(openUrl)
            urls.isNotEmpty() -> urls
            else -> emptyList()
        }
        return SuggestionItem(
            sourceKey = sourceKey,
            title = hit.rule.name.trim().ifBlank { GenericLabel },
            text = itemText,
            receivedAtElapsedMs = nowMs,
            ttlMs = null,
            ruleId = hit.rule.id,
            action = action,
            sensitive = openUrl == null && exactUrl == null,
            urls = itemUrls,
        )
    }

    internal fun notificationText(pulse: NotificationPulse): String {
        val text = pulse.text
        val bigText = pulse.bigText
        val body = when {
            bigText.isNullOrBlank() -> text
            text.isBlank() -> bigText
            bigText.contains(text) -> bigText
            else -> "$text\n$bigText"
        }
        return listOf(pulse.title, body)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}
